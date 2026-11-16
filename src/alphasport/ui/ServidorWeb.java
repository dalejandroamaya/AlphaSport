package alphasport.ui;

import alphasport.persistence.PersistenciaException;
import alphasport.service.CampeonatoService;
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
import java.util.Map;

/**
 * Servidor local: entrega la interfaz web (HTML, CSS, JS) y la API JSON en /api/.
 * Solo escucha en 127.0.0.1, asi que no es accesible desde otros equipos.
 */
public class ServidorWeb {

    private static final Map<String, String> TIPOS = Map.of(
            "html", "text/html; charset=utf-8", "css", "text/css; charset=utf-8",
            "js", "text/javascript; charset=utf-8", "json", "application/json; charset=utf-8",
            "png", "image/png", "svg", "image/svg+xml", "ico", "image/x-icon", "webp", "image/webp",
            "jpg", "image/jpeg", "gif", "image/gif");

    private final Path carpetaWeb;
    private final CampeonatoService servicio;
    private final ApiControlador api;
    private HttpServer servidor;

    public ServidorWeb(Path carpetaWeb, CampeonatoService servicio, ApiControlador api) {
        this.carpetaWeb = carpetaWeb.toAbsolutePath().normalize();
        this.servicio = servicio;
        this.api = api;
    }

    public int iniciar(int puertoInicial) throws IOException {
        for (int puerto = puertoInicial; puerto < puertoInicial + 20; puerto++) {
            try {
                servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), puerto), 0);
                servidor.createContext("/api/", this::atenderApi);
                servidor.createContext("/", this::atenderArchivo);
                servidor.start();
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
        try {
            // Rutas que no pasan por api.ejecutar(): se validan aqui.
            if (ruta.equals("respaldo") || ruta.equals("exportar/historial")) {
                api.exigirSesion();
            }
            if (metodo.equals("POST") && (ruta.equals("respaldo") || ruta.equals("salir"))) {
                api.exigirAdministrador();
            }
            if (metodo.equals("GET") && ruta.equals("respaldo")) {
                descargar(intercambio, servicio.respaldo(), "application/octet-stream",
                        nombreArchivo(servicio.getCampeonato().getNombreCampeonato()) + ".dat");
            } else if (metodo.equals("GET") && ruta.equals("exportar/historial")) {
                descargar(intercambio, servicio.textoHistorial().getBytes(StandardCharsets.UTF_8), "text/plain; charset=utf-8",
                        nombreArchivo(servicio.getCampeonato().getNombreCampeonato()) + ".txt");
            } else if (metodo.equals("POST") && ruta.equals("respaldo")) {
                servicio.abrirRespaldo(intercambio.getRequestBody().readAllBytes());
                responder(intercambio, 200, api.respuesta("Se abrió «" + servicio.getCampeonato().getNombreCampeonato() + "»."));
            } else if (metodo.equals("POST") && ruta.equals("salir")) {
                responder(intercambio, 200, Presentador.mapa("ok", true));
                new Thread(() -> {
                    detener();
                    System.exit(0);
                }).start();
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

    private void responder(HttpExchange intercambio, int codigo, Map<String, Object> datos) throws IOException {
        enviar(intercambio, codigo, TIPOS.get("json"), Json.escribir(datos).getBytes(StandardCharsets.UTF_8));
    }

    private void descargar(HttpExchange intercambio, byte[] datos, String tipo, String nombre) throws IOException {
        intercambio.getResponseHeaders().set("Content-Disposition",
                "attachment; filename*=UTF-8''" + URLEncoder.encode(nombre, StandardCharsets.UTF_8).replace("+", "%20"));
        enviar(intercambio, 200, tipo, datos);
    }

    private void enviar(HttpExchange intercambio, int codigo, String tipo, byte[] datos) throws IOException {
        intercambio.getResponseHeaders().set("Content-Type", tipo);
        intercambio.sendResponseHeaders(codigo, datos.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(datos);
        }
    }

    private static String nombreArchivo(String nombre) {
        String limpio = nombre.trim().replaceAll("[^a-zA-Z0-9-_]+", "_");
        return limpio.isEmpty() ? "campeonato" : limpio;
    }
}
