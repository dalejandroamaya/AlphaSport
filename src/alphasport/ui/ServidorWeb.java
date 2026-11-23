package alphasport.ui;

import alphasport.persistence.PersistenciaException;
import alphasport.service.ReglaNegocioException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Servidor local: entrega la interfaz web (HTML, CSS, JS) y la API JSON en /api/.
 * Solo escucha en 127.0.0.1, asi que no es accesible desde otros equipos.
 * <p>
 * Cada navegador tiene su propia sesion: el token va en la cookie {@value #COOKIE} con HttpOnly (el JavaScript
 * no la puede leer) y SameSite=Strict (otra pagina no la puede usar). Ademas la API solo responde si el
 * Host es esta maquina (contra el «DNS rebinding») y las acciones (POST) solo si vienen de esta misma pagina.
 */
public class ServidorWeb {

    /** Nombre de la cookie de sesion. */
    public static final String COOKIE = "alphasport_sesion";

    private static final Map<String, String> TIPOS = Map.of(
            "html", "text/html; charset=utf-8", "css", "text/css; charset=utf-8",
            "js", "text/javascript; charset=utf-8", "json", "application/json; charset=utf-8",
            "png", "image/png", "svg", "image/svg+xml", "ico", "image/x-icon", "webp", "image/webp",
            "jpg", "image/jpeg", "gif", "image/gif");

    private final Path carpetaWeb;
    private final ApiControlador api;
    private HttpServer servidor;
    private int puerto;
    /** Que hacer cuando el administrador pulsa «Cerrar programa» (por defecto, solo detener el servidor). */
    private Runnable alSalir = () -> {
    };

    public ServidorWeb(Path carpetaWeb, ApiControlador api) {
        this.carpetaWeb = carpetaWeb.toAbsolutePath().normalize();
        this.api = api;
    }

    /** Accion extra al cerrar el programa desde la web (AlphaSportApp termina el proceso). */
    public void alSalir(Runnable accion) {
        this.alSalir = accion;
    }

    public int iniciar(int puertoInicial) throws IOException {
        for (int puerto = puertoInicial; puerto < puertoInicial + 20; puerto++) {
            try {
                servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), puerto), 0);
                servidor.createContext("/api/", this::atenderApi);
                servidor.createContext("/", this::atenderArchivo);
                servidor.start();
                this.puerto = puerto;
                return puerto;
            } catch (BindException ocupado) {
                servidor = null;
            }
        }
        throw new IOException("No hay puertos libres entre " + puertoInicial + " y " + (puertoInicial + 19));
    }

    public void detener() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    private void atenderApi(HttpExchange intercambio) throws IOException {
        String ruta = intercambio.getRequestURI().getPath().substring("/api/".length());
        String metodo = intercambio.getRequestMethod();
        String problema = origenInvalido(intercambio);
        if (problema != null) {
            responder(intercambio, 403, Presentador.mapa("ok", false, "codigo", "ORIGEN", "error", problema));
            return;
        }
        // Este navegador, con la sesion de su cookie. Si entra o sale, enviar() le manda la cookie nueva.
        String token = tokenDeCookie(intercambio);
        ApiControlador api = this.api.cliente(token);
        intercambio.setAttribute(ATRIBUTO_CLIENTE, new Cliente(api, token));
        try {
            // Archivos: el controlador exige el permiso y sincroniza, igual que para el escritorio.
            if (metodo.equals("GET") && ruta.equals("respaldo")) {
                descargar(intercambio, api.respaldo());
            } else if (metodo.equals("GET") && ruta.equals("exportar/historial")) {
                descargar(intercambio, api.historialEnTexto());
            } else if (metodo.equals("POST") && ruta.equals("respaldo")) {
                responder(intercambio, 200, api.abrirRespaldo(intercambio.getRequestBody().readAllBytes()));
            } else if (metodo.equals("POST") && ruta.equals("salir")) {
                api.exigirAdministrador();
                responder(intercambio, 200, Presentador.mapa("ok", true));
                new Thread(() -> {
                    detener();
                    alSalir.run();
                }).start();
            } else if (metodo.equals("GET") && ruta.startsWith("foto/")) {
                // Foto tipo cedula de un jugador o escudo de un equipo.
                String nombre = ruta.substring("foto/".length());
                byte[] foto = api.foto(nombre);
                if (foto == null) {
                    enviar(intercambio, 404, "text/plain; charset=utf-8", "No encontrado".getBytes(StandardCharsets.UTF_8));
                } else {
                    // cada foto nueva tiene un nombre nuevo: se puede guardar en cache sin miedo
                    intercambio.getResponseHeaders().set("Cache-Control", "private, max-age=86400, immutable");
                    enviar(intercambio, 200, nombre.endsWith(".png") ? "image/png" : "image/jpeg", foto);
                }
            } else if (metodo.equals("GET") && ruta.startsWith("imagen/")) {
                // Foto de una noticia: solo las de noticias publicadas (o todas para el administrador).
                String nombre = ruta.substring("imagen/".length());
                byte[] foto = api.imagenNoticia(nombre);
                if (foto == null) {
                    enviar(intercambio, 404, "text/plain; charset=utf-8", "No encontrado".getBytes(StandardCharsets.UTF_8));
                } else {
                    String extension = nombre.substring(nombre.lastIndexOf('.') + 1).toLowerCase();
                    intercambio.getResponseHeaders().set("Cache-Control", "private, max-age=3600");
                    enviar(intercambio, 200, TIPOS.getOrDefault(extension, "application/octet-stream"), foto);
                }
            } else if (metodo.equals("GET")) {
                responder(intercambio, 200, api.consultar(ruta));
            } else {
                String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                responder(intercambio, 200, api.ejecutar(ruta, Json.leerObjeto(cuerpo)));
            }
        } catch (ReglaNegocioException e) {
            responder(intercambio, 200, Presentador.mapa("ok", false, "codigo", e.getCodigo(), "error", e.getMessage()));
        } catch (PersistenciaException e) {
            responder(intercambio, 500, Presentador.mapa("ok", false, "codigo", "ARCHIVO", "error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            responder(intercambio, 500, Presentador.mapa("ok", false, "codigo", "ERROR",
                    "error", "Error inesperado: " + e.getMessage()));
        }
    }

    private void atenderArchivo(HttpExchange intercambio) throws IOException {
        String ruta = intercambio.getRequestURI().getPath();
        Path archivo = carpetaWeb.resolve(ruta.equals("/") ? "index.html" : ruta.substring(1)).normalize();
        if (!archivo.startsWith(carpetaWeb) || !Files.isRegularFile(archivo)) {
            enviar(intercambio, 404, "text/plain; charset=utf-8", "No encontrado".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String nombre = archivo.getFileName().toString();
        String extension = nombre.substring(nombre.lastIndexOf('.') + 1).toLowerCase();
        intercambio.getResponseHeaders().set("Cache-Control", "no-store");
        enviar(intercambio, 200, TIPOS.getOrDefault(extension, "application/octet-stream"), Files.readAllBytes(archivo));
    }

    /* ---------------------------------------------------------------- sesion y origen */

    private static final String ATRIBUTO_CLIENTE = "alphasport.cliente";

    /** Cliente de la peticion y el token con el que llego. */
    private record Cliente(ApiControlador api, String tokenRecibido) {
    }

    /** Token de la cookie de sesion; null si no la trae. */
    static String tokenDeCookie(HttpExchange intercambio) {
        for (String cabecera : intercambio.getRequestHeaders().getOrDefault("Cookie", List.of())) {
            for (String parte : cabecera.split(";")) {
                String[] par = parte.trim().split("=", 2);
                if (par.length == 2 && par[0].equals(COOKIE) && !par[1].isBlank()) {
                    return par[1].trim();
                }
            }
        }
        return null;
    }

    /** Cookie para guardar el token (o borrarla si es null). */
    static String cookie(String token) {
        return COOKIE + "=" + (token == null ? "; Max-Age=0" : token) + "; Path=/; HttpOnly; SameSite=Strict";
    }

    /** Direcciones con las que se puede abrir este servidor. */
    private Set<String> anfitriones() {
        return Set.of("127.0.0.1:" + puerto, "localhost:" + puerto, "[::1]:" + puerto);
    }

    /**
     * null si la peticion se puede atender; si no, por que se rechaza. El Host tiene que ser esta maquina y, en
     * las acciones, el Origin (o Referer) esta misma pagina: asi otra pagina abierta en el navegador no puede
     * enviar acciones con la sesion del administrador.
     */
    private String origenInvalido(HttpExchange intercambio) {
        String host = intercambio.getRequestHeaders().getFirst("Host");
        if (host == null || !anfitriones().contains(host.toLowerCase())) {
            return "Abra AlphaSport con la dirección que muestra el programa (http://127.0.0.1:" + puerto + ").";
        }
        if (intercambio.getRequestMethod().equals("GET")) {
            return null;
        }
        String origen = intercambio.getRequestHeaders().getFirst("Origin");
        if (origen == null) {
            String referer = intercambio.getRequestHeaders().getFirst("Referer");
            origen = referer == null ? null : referer.replaceFirst("^(https?://[^/]+).*$", "$1");
        }
        if (origen == null || !anfitriones().contains(origen.toLowerCase().replaceFirst("^http://", ""))) {
            return "Acción rechazada: no viene de la página de AlphaSport.";
        }
        return null;
    }

    private void responder(HttpExchange intercambio, int codigo, Map<String, Object> datos) throws IOException {
        enviar(intercambio, codigo, TIPOS.get("json"), Json.escribir(datos).getBytes(StandardCharsets.UTF_8));
    }

    private void descargar(HttpExchange intercambio, ApiControlador.Descarga descarga) throws IOException {
        intercambio.getResponseHeaders().set("Content-Disposition",
                "attachment; filename*=UTF-8''" + URLEncoder.encode(descarga.nombre(), StandardCharsets.UTF_8).replace("+", "%20"));
        enviar(intercambio, 200, descarga.tipo(), descarga.datos());
    }

    private void enviar(HttpExchange intercambio, int codigo, String tipo, byte[] datos) throws IOException {
        if (intercambio.getAttribute(ATRIBUTO_CLIENTE) instanceof Cliente c && !Objects.equals(c.api().getToken(), c.tokenRecibido())) {
            intercambio.getResponseHeaders().set("Set-Cookie", cookie(c.api().getToken())); // entro, cambio de usuario o salio
        }
        intercambio.getResponseHeaders().set("Content-Type", tipo);
        intercambio.sendResponseHeaders(codigo, datos.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(datos);
        }
    }
}
