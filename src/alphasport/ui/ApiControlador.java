package alphasport.ui;

import alphasport.model.Permiso;
import alphasport.model.Usuario;
import alphasport.service.AutenticacionService;
import alphasport.service.CampeonatoService;
import alphasport.service.NoticiasService;
import alphasport.service.ReglaNegocioException;
import alphasport.service.SesionService;
import java.util.List;
import java.util.Map;

/**
 * Punto de entrada de las dos interfaces: busca la ruta pedida, exige su {@link Permiso}, sincroniza con la base
 * de datos y deja el trabajo al modulo de esa ruta ({@link RutasSesion}, {@link RutasCampeonato},
 * {@link RutasEquipos}, {@link RutasPartidos}, {@link RutasVotacion}, {@link RutasNoticias}).
 * <p>
 * Cada instancia es un cliente con su propia sesion (su token): el escritorio usa una, y el servidor web una
 * por navegador con {@link #cliente(String)}. Los servicios y las rutas se comparten.
 */
public class ApiControlador {

    private final CampeonatoService servicio;
    private final SesionService sesion;
    private final Presentador presentador;
    private final NoticiasService noticias;
    private final Rutas<Rutas.Consulta> consultas;
    private final Rutas<Rutas.Accion> acciones;
    /** Token de la sesion de este cliente (null = sin sesion). */
    private String token;

    public ApiControlador(CampeonatoService servicio, AutenticacionService autenticacion, NoticiasService noticias) {
        this(servicio, new SesionService(autenticacion), noticias);
    }

    /** Sin servicio de noticias propio: noticias solo en memoria (pruebas). */
    public ApiControlador(CampeonatoService servicio, AutenticacionService autenticacion) {
        this(servicio, autenticacion, new NoticiasService(new alphasport.persistence.RepositorioNoticiasEnMemoria()));
    }

    public ApiControlador(CampeonatoService servicio, SesionService sesion, NoticiasService noticias) {
        this.servicio = servicio;
        this.sesion = sesion;
        this.noticias = noticias;
        this.presentador = new Presentador(servicio);
        this.consultas = new Rutas<>();
        this.acciones = new Rutas<>();
        for (ModuloRutas modulo : List.of(new RutasSesion(sesion), new RutasCampeonato(servicio, presentador),
                new RutasEquipos(servicio), new RutasPartidos(servicio, presentador), new RutasVotacion(servicio, presentador),
                new RutasNoticias(noticias))) {
            modulo.registrar(consultas, acciones);
        }
    }

    private ApiControlador(ApiControlador base, String token) {
        this.servicio = base.servicio;
        this.sesion = base.sesion;
        this.noticias = base.noticias;
        this.presentador = base.presentador;
        this.consultas = base.consultas;
        this.acciones = base.acciones;
        this.token = token;
    }

    /** Otro cliente (por ejemplo, un navegador) con su propia sesion; comparte los servicios. */
    public ApiControlador cliente(String token) {
        return new ApiControlador(this, token);
    }

    /** Token de la sesion de este cliente; cambia al entrar y vuelve a null al salir. */
    public String getToken() {
        return token;
    }

    public NoticiasService getNoticias() {
        return noticias;
    }

    /* ---------------------------------------------------------------- rutas */

    /** Consulta (solo lectura). Las que exigen un permiso leen antes los cambios de otro programa. */
    public Map<String, Object> consultar(String ruta) {
        Rutas.Encontrada<Rutas.Consulta> e = buscar(consultas, ruta, Permiso.VER);
        if (e.ruta().permiso() != null) {
            exigir(e.ruta().permiso());
            servicio.sincronizar();
        }
        return e.ruta().manejador().atender(new Peticion(this, e.resto(), Map.of()));
    }

    /** Accion: exige el permiso de la ruta y trabaja sobre los datos al dia. */
    public Map<String, Object> ejecutar(String ruta, Map<String, Object> cuerpo) throws Exception {
        Rutas.Encontrada<Rutas.Accion> e = buscar(acciones, ruta, Permiso.GESTIONAR_CAMPEONATO);
        if (e.ruta().permiso() != null) {
            exigir(e.ruta().permiso()); // toda accion que modifica datos pasa por aqui
        }
        servicio.sincronizar(); // otro programa pudo cambiar la base de datos
        return e.ruta().manejador().atender(new Peticion(this, e.resto(), cuerpo));
    }

    /**
     * La ruta pedida. Una ruta que no existe exige igual el permiso mas alto antes de decir que no existe: asi no
     * se puede averiguar sin permiso que rutas hay.
     */
    private <A> Rutas.Encontrada<A> buscar(Rutas<A> rutas, String ruta, Permiso siNoExiste) {
        return rutas.buscar(ruta).orElseGet(() -> {
            exigir(siNoExiste);
            throw new ReglaNegocioException("Ruta desconocida: " + ruta);
        });
    }

    /** Rutas registradas con el permiso que exige cada una (null = publica). */
    public Map<String, Permiso> permisosDeAcciones() {
        return acciones.permisos();
    }

    public Map<String, Permiso> permisosDeConsultas() {
        return consultas.permisos();
    }

    /* ---------------------------------------------------------------- sesion y permisos */

    /** Usuario con sesion abierta; null si nadie ha iniciado sesion. */
    public Usuario getUsuarioActual() {
        return sesion.usuario(token);
    }

    /** Lanza LOGIN si no hay sesion. */
    public void exigirSesion() {
        sesion.exigirSesion(token);
    }

    /** Lanza LOGIN si no hay sesion y PERMISO si el rol no tiene ese permiso. */
    public void exigir(Permiso permiso) {
        sesion.exigir(token, permiso);
    }

    /** Lanza PERMISO si el usuario no puede modificar datos. */
    public void exigirAdministrador() {
        exigir(Permiso.GESTIONAR_CAMPEONATO);
    }

    boolean puede(Permiso permiso) {
        return sesion.puede(token, permiso);
    }

    /** La sesion nueva reemplaza a la que tuviera este cliente. */
    void entrar(String nuevo) {
        sesion.cerrar(token);
        token = nuevo;
    }

    void salir() {
        sesion.cerrar(token);
        token = null;
    }

    /** Datos de la sesion para la interfaz: {usuario, rol, puedeEditar, permisos}. */
    public Map<String, Object> sesion() {
        Usuario u = sesion.usuario(token);
        return u == null ? null
                : Presentador.mapa("usuario", u.getNombre(), "rol", u.getRol().name(),
                        "rolNombre", u.getRol().getNombre(), "puedeEditar", u.puedeEditar(),
                        "permisos", u.getRol().getPermisos().stream().map(Permiso::name).sorted().toList());
    }

    public Map<String, Object> respuesta(String mensaje) {
        return Presentador.mapa("ok", true, "mensaje", mensaje, "estado", presentador.estado(), "sesion", sesion());
    }

    /* ---------------------------------------------------------------- archivos */

    /** Archivo para descargar: su contenido, su tipo y el nombre sugerido. */
    public record Descarga(byte[] datos, String tipo, String nombre) {
    }

    /** Respaldo del campeonato en curso (.dat). Lo puede descargar cualquiera que lo vea. */
    public Descarga respaldo() throws Exception {
        exigir(Permiso.VER);
        servicio.sincronizar();
        return new Descarga(servicio.respaldo(), "application/octet-stream", nombreArchivo() + ".dat");
    }

    /** Historial del campeonato como texto (.txt). */
    public Descarga historialEnTexto() {
        exigir(Permiso.VER);
        servicio.sincronizar();
        return new Descarga(servicio.textoHistorial().getBytes(java.nio.charset.StandardCharsets.UTF_8), "text/plain; charset=utf-8",
                nombreArchivo() + ".txt");
    }

    /** Abre un respaldo: reemplaza el campeonato en curso (el anterior queda en el historial si tuvo partidos). */
    public Map<String, Object> abrirRespaldo(byte[] datos) throws Exception {
        exigir(Permiso.GESTIONAR_CAMPEONATO);
        servicio.sincronizar();
        servicio.abrirRespaldo(datos);
        return respuesta("Se abrió «" + servicio.getCampeonato().getNombreCampeonato() + "».");
    }

    /** Nombre de archivo a partir del nombre del campeonato (solo letras, numeros, - y _). */
    private String nombreArchivo() {
        String limpio = servicio.hayCampeonato() ? servicio.getCampeonato().getNombreCampeonato().trim().replaceAll("[^a-zA-Z0-9-_]+", "_") : "";
        return limpio.isEmpty() ? "campeonato" : limpio;
    }

    /** Foto de un jugador o escudo (la pide ServidorWeb para /api/foto/{nombre}); basta con tener sesion. */
    public byte[] foto(String nombre) throws Exception {
        exigirSesion();
        return servicio.getFotos().leerGuardada(nombre);
    }

    /** Foto de una noticia (/api/imagen/{nombre}). Las de propuestas pendientes solo las ve quien modera. */
    public byte[] imagenNoticia(String nombre) throws Exception {
        exigirSesion();
        return noticias.imagen(nombre, puede(Permiso.MODERAR_NOTICIAS));
    }
}
