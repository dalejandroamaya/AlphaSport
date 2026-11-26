package alphasport.ui;

import alphasport.model.Campeonato;
import alphasport.model.Permiso;
import alphasport.model.Usuario;
import alphasport.persistence.PersistenciaException;
import alphasport.persistence.RepositorioCampeonatosEnMemoria;
import alphasport.persistence.RepositorioHistorial;
import alphasport.service.AutenticacionService;
import alphasport.service.CampeonatoService;
import alphasport.service.FotosService;
import alphasport.service.GestorCampeonatos;
import alphasport.service.NoticiasService;
import alphasport.service.ReglaNegocioException;
import alphasport.service.SesionService;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Punto de entrada de las dos interfaces: busca la ruta pedida, exige su {@link Permiso}, sincroniza con la base
 * de datos y deja el trabajo al modulo de esa ruta.
 * <p>
 * Hay rutas generales ({@link RutasSesion}, {@link RutasCampeonatos}, {@link RutasNoticias}) y rutas de UN
 * campeonato ({@link RutasCampeonato}, {@link RutasEquipos}, {@link RutasPartidos}, {@link RutasVotacion}), que
 * trabajan sobre el campeonato elegido. El campeonato de cada peticion se decide asi:
 * <ol>
 * <li>el que pide la peticion (la web lo manda en la cabecera {@value ServidorWeb#CABECERA_CAMPEONATO});</li>
 * <li>si no, el ultimo que eligio esta sesion ({@code campeonato/elegir}, o el que acaba de crear);</li>
 * <li>si no, el unico que el usuario puede ver, si ve uno solo.</li>
 * </ol>
 * Si no queda ninguno, «estado» responde {@code hay: false} (con {@code elegir: true} si hay campeonatos para
 * elegir) y las demas rutas de un campeonato responden el codigo CAMPEONATO. Nunca se usa «el primero» entre varios.
 * Quien puede ver o cambiar cada campeonato lo decide {@link GestorCampeonatos} (en el servicio).
 * <p>
 * Cada instancia es un cliente con su propia sesion (su token): el escritorio usa una, y el servidor web una
 * por peticion con {@link #cliente(String, String)}. Los servicios y las rutas se comparten.
 */
public class ApiControlador {

    /** Lo de un campeonato: su servicio, su presentador y sus rutas. */
    private record Espacio(CampeonatoService servicio, Presentador presentador, Rutas<Rutas.Consulta> consultas, Rutas<Rutas.Accion> acciones) {
    }

    /** Lo que comparten todos los clientes. */
    private static final class Compartido {
        final GestorCampeonatos gestor;
        final SesionService sesion;
        final NoticiasService noticias;
        final Supplier<FotosService> fotos;
        final Rutas<Rutas.Consulta> consultas = new Rutas<>();
        final Rutas<Rutas.Accion> acciones = new Rutas<>();
        /** Rutas de un campeonato vacio: dicen que rutas hay, su permiso, el catalogo y el estado sin campeonato. */
        final Espacio plantilla;
        private final Map<CampeonatoService, Espacio> espacios = new IdentityHashMap<>();

        Compartido(GestorCampeonatos gestor, SesionService sesion, NoticiasService noticias, Supplier<FotosService> fotos) {
            this.gestor = gestor;
            this.sesion = sesion;
            this.noticias = noticias;
            this.fotos = fotos;
            this.plantilla = crearEspacio(new CampeonatoService(new RepositorioCampeonatosEnMemoria().nuevo(), HISTORIAL_VACIO));
            for (ModuloRutas modulo : List.of(new RutasSesion(sesion), new RutasPerfil(sesion), new RutasCampeonatos(gestor, plantilla.presentador()), new RutasNoticias())) {
                modulo.registrar(consultas, acciones);
            }
        }

        /** El espacio de un campeonato (lo crea la primera vez); suelta los de campeonatos que ya no estan. */
        synchronized Espacio espacio(CampeonatoService servicio) {
            espacios.keySet().removeIf(s -> s != servicio && !s.hayCampeonato());
            return espacios.computeIfAbsent(servicio, Compartido::crearEspacio);
        }

        private static Espacio crearEspacio(CampeonatoService s) {
            Presentador presentador = new Presentador(s);
            Rutas<Rutas.Consulta> consultas = new Rutas<>();
            Rutas<Rutas.Accion> acciones = new Rutas<>();
            for (ModuloRutas modulo : List.of(new RutasCampeonato(s, presentador), new RutasEquipos(s.equipos()),
                    new RutasPartidos(s, presentador), new RutasVotacion(s.votacion(), presentador))) {
                modulo.registrar(consultas, acciones);
            }
            return new Espacio(s, presentador, consultas, acciones);
        }
    }

    /** Historial vacio para la plantilla (nunca se guarda nada en ella). */
    private static final RepositorioHistorial HISTORIAL_VACIO = new RepositorioHistorial() {
        @Override
        public void archivar(Campeonato campeonato) throws PersistenciaException {
            throw new PersistenciaException("Sin campeonato elegido.", null);
        }

        @Override
        public List<Campeonato> listar() {
            return List.of();
        }

        @Override
        public boolean eliminar(String codigo) {
            return false;
        }
    };

    private final Compartido c;
    /** Token de la sesion de este cliente (null = sin sesion). */
    private String token;
    /** Codigo del campeonato que pide este cliente (null = el elegido en la sesion). */
    private String pedido;

    public ApiControlador(CampeonatoService servicio, AutenticacionService autenticacion, NoticiasService noticias) {
        this(servicio, new SesionService(autenticacion), noticias);
    }

    /** Sin servicio de noticias propio: noticias solo en memoria (pruebas). */
    public ApiControlador(CampeonatoService servicio, AutenticacionService autenticacion) {
        this(servicio, autenticacion, new NoticiasService(new alphasport.persistence.RepositorioNoticiasEnMemoria()));
    }

    /** Un solo campeonato, como antes de que hubiera varios (pruebas y version con archivos). */
    public ApiControlador(CampeonatoService servicio, SesionService sesion, NoticiasService noticias) {
        this(new Compartido(GestorCampeonatos.unico(servicio), sesion, noticias, servicio::getFotos));
    }

    /** Varios campeonatos en curso, cada uno con su dueno (la aplicacion con PostgreSQL). */
    public ApiControlador(GestorCampeonatos gestor, SesionService sesion, NoticiasService noticias, FotosService fotos) {
        this(new Compartido(gestor, sesion, noticias, () -> fotos));
    }

    private ApiControlador(Compartido compartido) {
        this.c = compartido;
    }

    /** Otro cliente (por ejemplo, un navegador) con su propia sesion; comparte los servicios. */
    public ApiControlador cliente(String token) {
        return cliente(token, null);
    }

    /** Otro cliente con su sesion y el campeonato que pide (null = el elegido en su sesion). */
    public ApiControlador cliente(String token, String campeonato) {
        ApiControlador otro = new ApiControlador(c);
        otro.token = token;
        otro.pedido = campeonato == null || campeonato.isBlank() ? null : campeonato;
        return otro;
    }

    /** Token de la sesion de este cliente; cambia al entrar y vuelve a null al salir. */
    public String getToken() {
        return token;
    }

    /** Todas las noticias, sin separar por campeonato (pruebas). */
    public NoticiasService getNoticias() {
        return c.noticias;
    }

    /* ---------------------------------------------------------------- rutas */

    /** Consulta (solo lectura). Las que exigen un permiso leen antes los cambios de otro programa. */
    public Map<String, Object> consultar(String ruta) {
        var general = c.consultas.buscar(ruta);
        if (general.isPresent()) {
            Rutas.Encontrada<Rutas.Consulta> e = general.get();
            if (e.ruta().permiso() != null) {
                exigir(e.ruta().permiso());
            }
            return e.ruta().manejador().atender(new Peticion(this, e.resto(), Map.of()));
        }
        Rutas.Encontrada<Rutas.Consulta> e = buscar(c.plantilla.consultas(), ruta, Permiso.VER);
        if (e.ruta().permiso() == null) {
            return e.ruta().manejador().atender(new Peticion(this, e.resto(), Map.of())); // el catalogo: no es de un campeonato
        }
        exigir(e.ruta().permiso());
        Espacio espacio = ruta.equals("estado") ? null : espacio(e.ruta().permiso(), true);
        Rutas.Encontrada<Rutas.Consulta> propia = espacio == null ? e : espacio.consultas().buscar(ruta).orElseThrow();
        return propia.ruta().manejador().atender(new Peticion(this, propia.resto(), Map.of()));
    }

    /** Accion: exige el permiso de la ruta (y, si cambia un campeonato, ser su dueno) y trabaja sobre los datos al dia. */
    public Map<String, Object> ejecutar(String ruta, Map<String, Object> cuerpo) throws Exception {
        var general = c.acciones.buscar(ruta);
        if (general.isPresent()) {
            Rutas.Encontrada<Rutas.Accion> e = general.get();
            if (e.ruta().permiso() != null) {
                exigir(e.ruta().permiso());
            }
            return e.ruta().manejador().atender(new Peticion(this, e.resto(), cuerpo));
        }
        Rutas.Encontrada<Rutas.Accion> e = buscar(c.plantilla.acciones(), ruta, Permiso.GESTIONAR_CAMPEONATO);
        exigir(e.ruta().permiso()); // toda accion que modifica datos pasa por aqui
        Espacio espacio = espacio(e.ruta().permiso(), true);
        Rutas.Encontrada<Rutas.Accion> propia = espacio.acciones().buscar(ruta).orElseThrow();
        try {
            return propia.ruta().manejador().atender(new Peticion(this, propia.resto(), cuerpo));
        } finally {
            recordar(espacio.servicio());
        }
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
        Map<String, Permiso> todas = new LinkedHashMap<>(c.acciones.permisos());
        todas.putAll(c.plantilla.acciones().permisos());
        return todas;
    }

    public Map<String, Permiso> permisosDeConsultas() {
        Map<String, Permiso> todas = new LinkedHashMap<>(c.consultas.permisos());
        todas.putAll(c.plantilla.consultas().permisos());
        return todas;
    }

    /* ---------------------------------------------------------------- campeonato elegido */

    /**
     * Codigo del campeonato de esta peticion (ver la descripcion de la clase); null si no hay ninguno. No revisa
     * permisos: eso lo hace el gestor al abrirlo.
     */
    public String codigoElegido() {
        if (pedido != null) {
            return pedido;
        }
        Usuario u = getUsuarioActual();
        if (u == null) {
            return null;
        }
        String enSesion = c.sesion.elegido(token);
        if (enSesion != null) {
            return enSesion;
        }
        return c.gestor.porDefecto(u).filter(CampeonatoService::hayCampeonato).map(s -> s.getCampeonato().getCodigo()).orElse(null);
    }

    /** Este cliente (y su sesion) pasa a trabajar con ese campeonato. */
    void elegir(String codigo) {
        pedido = codigo;
        c.sesion.elegir(token, codigo);
    }

    /** Olvida ese campeonato si era el elegido (se termino, se elimino o ya no esta). */
    void olvidarElegido(String codigo) {
        if (codigo != null && codigo.equals(pedido)) {
            pedido = null;
        }
        if (codigo != null && codigo.equals(c.sesion.elegido(token))) {
            c.sesion.elegir(token, null);
        }
    }

    /**
     * El espacio del campeonato de esta peticion, abierto con el permiso de la ruta: para cambiarlo hay que ser
     * su dueno; para leerlo basta con poder verlo. Con {@code obligatorio}, sin campeonato responde CAMPEONATO.
     */
    private Espacio espacio(Permiso permiso, boolean obligatorio) {
        Usuario u = getUsuarioActual();
        if (c.gestor.esUnico()) {
            CampeonatoService unico = c.gestor.porDefecto(u).orElseThrow();
            unico.sincronizar(); // otro programa pudo cambiar la base de datos
            return c.espacio(unico);
        }
        String codigo = codigoElegido();
        if (codigo == null) {
            if (obligatorio) {
                throw new ReglaNegocioException("CAMPEONATO", "Elija un campeonato.");
            }
            return null;
        }
        boolean cambia = permiso == Permiso.GESTIONAR_CAMPEONATO || permiso == Permiso.MODERAR_NOTICIAS;
        try {
            return c.espacio(cambia ? c.gestor.paraGestionar(codigo, u) : c.gestor.paraLeer(codigo, u));
        } catch (ReglaNegocioException e) {
            if ("CAMPEONATO".equals(e.getCodigo())) {
                olvidarElegido(codigo); // ya no esta en curso: la proxima vez se elige otro
            }
            throw e;
        }
    }

    /** Despues de una accion: se recuerda el campeonato (pudo cambiar de codigo) o se olvida si ya no esta. */
    private void recordar(CampeonatoService servicio) {
        c.gestor.olvidarVacios();
        if (c.gestor.esUnico()) {
            return;
        }
        if (servicio.hayCampeonato()) {
            String codigo = servicio.getCampeonato().getCodigo();
            if (!codigo.equals(codigoElegido())) {
                elegir(codigo); // otro formato o la siguiente temporada: el mismo campeonato con otro codigo
            }
        } else {
            olvidarElegido(codigoElegido());
        }
    }

    /**
     * Estado del campeonato elegido para la interfaz. Sin campeonato: {@code hay: false}, y {@code elegir: true}
     * si el usuario tiene campeonatos para elegir.
     */
    public Map<String, Object> estado() {
        Espacio espacio;
        try {
            espacio = espacio(Permiso.VER, false);
        } catch (ReglaNegocioException e) {
            if (!"CAMPEONATO".equals(e.getCodigo()) && !"PERMISO".equals(e.getCodigo())) {
                throw e;
            }
            // El elegido ya no esta, o no es de este usuario (por ejemplo, otra cuenta en la misma pestaña): se
            // olvida y se busca otro. Solo para mostrar el estado: una accion sobre el sigue respondiendo el error.
            olvidarElegido(codigoElegido());
            espacio = espacio(Permiso.VER, false);
        }
        Map<String, Object> estado = new LinkedHashMap<>((espacio == null ? c.plantilla : espacio).presentador().estado());
        if (!c.gestor.esUnico()) {
            estado.put("elegir", espacio == null && getUsuarioActual() != null && !c.gestor.activos(getUsuarioActual()).isEmpty());
        }
        return estado;
    }

    /* ---------------------------------------------------------------- noticias del campeonato */

    /** Las noticias que se ven: las del campeonato elegido y las generales (sin campeonato, solo las generales). */
    NoticiasService noticiasParaLeer() {
        if (c.gestor.esUnico()) {
            return c.noticias;
        }
        try {
            Espacio espacio = espacio(Permiso.VER, false);
            return c.noticias.de(espacio == null ? "" : espacio.servicio().getCampeonato().getCodigo());
        } catch (ReglaNegocioException e) {
            if (!"CAMPEONATO".equals(e.getCodigo()) && !"PERMISO".equals(e.getCodigo())) {
                throw e;
            }
            return c.noticias.de(""); // "" no es ningun campeonato: solo las generales
        }
    }

    /** Para proponer y comentar: el campeonato elegido, que el usuario pueda ver. */
    NoticiasService noticiasParaParticipar() {
        return c.gestor.esUnico() ? c.noticias : c.noticias.de(espacio(Permiso.VER, true).servicio().getCampeonato().getCodigo());
    }

    /** Para moderar: el campeonato elegido, y solo su Organizador. */
    NoticiasService noticiasParaModerar() {
        return c.gestor.esUnico() ? c.noticias : c.noticias.de(espacio(Permiso.MODERAR_NOTICIAS, true).servicio().getCampeonato().getCodigo());
    }

    /** true si el usuario modera las noticias del campeonato elegido (ve las pendientes). */
    boolean moderaNoticias() {
        if (!puede(Permiso.MODERAR_NOTICIAS)) {
            return false;
        }
        if (c.gestor.esUnico()) {
            return true;
        }
        try {
            return espacio(Permiso.MODERAR_NOTICIAS, false) != null;
        } catch (ReglaNegocioException e) {
            return false;
        }
    }

    /* ---------------------------------------------------------------- sesion y permisos */

    /** Usuario con sesion abierta; null si nadie ha iniciado sesion. */
    public Usuario getUsuarioActual() {
        return c.sesion.usuario(token);
    }

    /** Lanza LOGIN si no hay sesion. */
    public void exigirSesion() {
        c.sesion.exigirSesion(token);
    }

    /** Lanza LOGIN si no hay sesion y PERMISO si el rol no tiene ese permiso. */
    public void exigir(Permiso permiso) {
        c.sesion.exigir(token, permiso);
    }

    /** Lanza PERMISO si el usuario no puede modificar datos. */
    public void exigirAdministrador() {
        exigir(Permiso.GESTIONAR_CAMPEONATO);
    }

    boolean puede(Permiso permiso) {
        return c.sesion.puede(token, permiso);
    }

    /** La sesion nueva reemplaza a la que tuviera este cliente. */
    void entrar(String nuevo) {
        c.sesion.cerrar(token);
        token = nuevo;
    }

    void salir() {
        c.sesion.cerrar(token);
        token = null;
        pedido = null;
    }

    /** Datos de la sesion para la interfaz: {usuario, rol, puedeEditar, permisos}. */
    public Map<String, Object> sesion() {
        Usuario u = c.sesion.usuario(token);
        return u == null ? null
                : Presentador.mapa("usuario", u.getNombre(), "rol", u.getRol().name(),
                        "rolNombre", u.getRol().getNombre(), "puedeEditar", u.puedeEditar(),
                        "permisos", u.getRol().getPermisos().stream().map(Permiso::name).sorted().toList());
    }

    public Map<String, Object> respuesta(String mensaje) {
        return Presentador.mapa("ok", true, "mensaje", mensaje, "estado", estado(), "sesion", sesion());
    }

    /* ---------------------------------------------------------------- archivos */

    /** Archivo para descargar: su contenido, su tipo y el nombre sugerido. */
    public record Descarga(byte[] datos, String tipo, String nombre) {
    }

    /**
     * Respaldo del campeonato elegido (.dat): solo el Organizador dueño (GestorCampeonatos.paraGestionar). Un
     * Aficionado u otro Organizador recibe PERMISO aunque llame la ruta directamente. El archivo es el campeonato
     * (equipos, partidos y votos anónimos por dispositivo): no lleva cuentas, correos ni contraseñas.
     */
    public Descarga respaldo() throws Exception {
        exigir(Permiso.GESTIONAR_CAMPEONATO);
        CampeonatoService servicio = espacio(Permiso.GESTIONAR_CAMPEONATO, true).servicio();
        return new Descarga(servicio.historial().respaldo(), "application/octet-stream", nombreArchivo(servicio) + ".dat");
    }

    /** Historial del campeonato elegido como texto (.txt). */
    public Descarga historialEnTexto() {
        exigir(Permiso.VER);
        CampeonatoService servicio = espacio(Permiso.VER, true).servicio();
        return new Descarga(servicio.historial().textoHistorial().getBytes(java.nio.charset.StandardCharsets.UTF_8), "text/plain; charset=utf-8",
                nombreArchivo(servicio) + ".txt");
    }

    /**
     * Abre un respaldo. Con varios campeonatos entra como uno mas del Organizador (no reemplaza a ninguno); con
     * uno solo, reemplaza al que estaba (el anterior queda en el historial si tuvo partidos).
     */
    public Map<String, Object> abrirRespaldo(byte[] datos) throws Exception {
        exigir(Permiso.GESTIONAR_CAMPEONATO);
        CampeonatoService servicio = c.gestor.abrirRespaldo(getUsuarioActual(), datos);
        if (!c.gestor.esUnico()) {
            elegir(servicio.getCampeonato().getCodigo());
        }
        return respuesta("Se abrió «" + servicio.getCampeonato().getNombreCampeonato() + "».");
    }

    /** Nombre de archivo a partir del nombre del campeonato (solo letras, numeros, - y _). */
    private static String nombreArchivo(CampeonatoService servicio) {
        String limpio = servicio.hayCampeonato() ? servicio.getCampeonato().getNombreCampeonato().trim().replaceAll("[^a-zA-Z0-9-_]+", "_") : "";
        return limpio.isEmpty() ? "campeonato" : limpio;
    }

    /** Foto de un jugador o escudo (la pide ServidorWeb para /api/foto/{nombre}); basta con tener sesion. */
    public byte[] foto(String nombre) throws Exception {
        exigirSesion();
        return c.fotos.get().leerGuardada(nombre);
    }

    /** Foto de una noticia (/api/imagen/{nombre}). Las de propuestas pendientes solo las ve quien modera. */
    public byte[] imagenNoticia(String nombre) throws Exception {
        exigirSesion();
        return c.noticias.imagen(nombre, puede(Permiso.MODERAR_NOTICIAS));
    }
}
