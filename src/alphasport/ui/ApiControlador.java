package alphasport.ui;

import alphasport.model.Cambio;
import alphasport.model.ConfiguracionTorneo;
import alphasport.model.CriterioDesempate;
import alphasport.model.DirectorTecnico;
import alphasport.model.EstadoPartido;
import alphasport.model.Formacion;
import alphasport.model.Gol;
import alphasport.model.Jugador;
import alphasport.model.Partido;
import alphasport.model.Permiso;
import alphasport.model.Tarjeta;
import alphasport.model.TipoFormato;
import alphasport.model.Usuario;
import alphasport.service.AutenticacionService;
import alphasport.service.CalificacionesPartido;
import alphasport.service.CampeonatoService;
import alphasport.service.NoticiasService;
import alphasport.service.ReglaNegocioException;
import alphasport.service.ReglasPartido;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Atiende las acciones de la interfaz web: convierte el JSON recibido en
 * llamadas al servicio y devuelve el estado actualizado.
 */
public class ApiControlador {

    private final CampeonatoService servicio;
    private final AutenticacionService autenticacion;
    private final Presentador presentador;
    private final NoticiasService noticias;

    public ApiControlador(CampeonatoService servicio, AutenticacionService autenticacion, NoticiasService noticias) {
        this.servicio = servicio;
        this.autenticacion = autenticacion;
        this.noticias = noticias;
        this.presentador = new Presentador(servicio);
    }

    /** Sin servicio de noticias propio: noticias solo en memoria (pruebas). */
    public ApiControlador(CampeonatoService servicio, AutenticacionService autenticacion) {
        this(servicio, autenticacion, new NoticiasService(new alphasport.persistence.RepositorioNoticiasEnMemoria()));
    }

    public NoticiasService getNoticias() {
        return noticias;
    }

    /* ---------------------------------------------------------------- sesion y permisos */

    /** Usuario con sesion abierta; null si nadie ha iniciado sesion. */
    private Usuario usuarioActual;

    /** Rutas de ejecutar() que no exigen sesion. */
    private static final Set<String> RUTAS_PUBLICAS = Set.of("login", "login/visitante", "logout");

    /**
     * Permiso que exige cada ruta de ejecutar(). Lo que no esta en la lista exige gestionar el campeonato
     * (lista blanca: una ruta nueva queda protegida aunque se olvide agregarla aqui).
     */
    private static Permiso permisoDe(String ruta) {
        return switch (ruta) {
            case "noticia/proponer" -> Permiso.PROPONER_NOTICIA;
            case "noticia/comentar" -> Permiso.COMENTAR;
            case "calificacion/votar" -> Permiso.VOTAR_JUGADORES; // [REQ-07] solo los visitantes
            default -> ruta.startsWith("noticia/") || ruta.startsWith("comentario/") ? Permiso.MODERAR_NOTICIAS
                    : Permiso.GESTIONAR_CAMPEONATO;
        };
    }

    public Usuario getUsuarioActual() {
        return usuarioActual;
    }

    /** Lanza LOGIN si no hay sesion. */
    public void exigirSesion() {
        if (usuarioActual == null) {
            throw new ReglaNegocioException("LOGIN", "Inicie sesión para continuar.");
        }
    }

    /** Lanza LOGIN si no hay sesion y PERMISO si el rol no tiene ese permiso. */
    public void exigir(Permiso permiso) {
        exigirSesion();
        if (!usuarioActual.puede(permiso)) {
            throw new ReglaNegocioException("PERMISO", "Su rol (" + usuarioActual.getRol().getNombre()
                    + ") no puede " + permiso.getAccion() + ".");
        }
    }

    /** Lanza PERMISO si el usuario no puede modificar datos. */
    public void exigirAdministrador() {
        exigir(Permiso.GESTIONAR_CAMPEONATO);
    }

    private boolean puede(Permiso permiso) {
        return usuarioActual != null && usuarioActual.puede(permiso);
    }

    /** Datos de la sesion para la interfaz: {usuario, rol, puedeEditar, permisos}. */
    public Map<String, Object> sesion() {
        return usuarioActual == null ? null
                : Presentador.mapa("usuario", usuarioActual.getNombre(), "rol", usuarioActual.getRol().name(),
                        "rolNombre", usuarioActual.getRol().getNombre(), "puedeEditar", usuarioActual.puedeEditar(),
                        "permisos", usuarioActual.getRol().getPermisos().stream().map(Permiso::name).sorted().toList());
    }

    public Map<String, Object> consultar(String ruta) {
        if (!ruta.equals("catalogo") && !ruta.equals("sesion")) {
            exigir(Permiso.VER); // consultar es solo lectura
            servicio.sincronizar();
        }
        if (ruta.startsWith("partido/")) {
            // Detalle del partido: partido/{id} o partido/{id}/{dispositivo}. El dispositivo (anonimo) solo
            // sirve para mostrar en las calificaciones [REQ-07] lo que ya voto este navegador o equipo.
            String[] partes = ruta.substring("partido/".length()).split("/");
            int id = entero(partes[0]);
            String dispositivo = partes.length > 1 ? partes[1] : null;
            return presentador.detallePartido(servicio.detallePartido(id), servicio.calificaciones(id, dispositivo));
        }
        if (ruta.startsWith("votaciones/")) { // [REQ-07] votaciones abiertas: votaciones/{dispositivo}
            return Presentador.mapa("ok", true, "votaciones", presentador.votaciones(ruta.substring("votaciones/".length())));
        }
        return switch (ruta) {
            case "catalogo" -> presentador.catalogo();
            case "sesion" -> Presentador.mapa("ok", true, "sesion", sesion());
            case "estado" -> presentador.estado();
            case "historial" -> presentador.historial(servicio.historial());
            case "noticias" -> Presentador.mapa("ok", true, "noticias", noticiasJson());
            default -> throw new ReglaNegocioException("Ruta desconocida: " + ruta);
        };
    }

    public Map<String, Object> ejecutar(String ruta, Map<String, Object> cuerpo) throws Exception {
        if (!RUTAS_PUBLICAS.contains(ruta)) {
            exigir(permisoDe(ruta)); // toda accion que modifica datos pasa por aqui
        }
        servicio.sincronizar(); // otro programa pudo cambiar la base de datos
        if (ruta.startsWith("noticia/") || ruta.startsWith("comentario/")) {
            Map<String, Object> r = respuesta(ejecutarNoticia(ruta, cuerpo));
            r.put("noticias", noticiasJson());
            return r;
        }
        if (ruta.equals("calificacion/votar")) {
            return votar(cuerpo);
        }
        String mensaje = null;
        switch (ruta) {
            case "login" -> usuarioActual = autenticacion.iniciarSesion(texto(cuerpo, "usuario"), texto(cuerpo, "clave"));
            case "login/visitante" -> usuarioActual = autenticacion.entrarComoVisitante();
            case "logout" -> {
                usuarioActual = null;
                return Presentador.mapa("ok", true);
            }
            case "nuevo" -> servicio.crear(texto(cuerpo, "nombre"), TipoFormato.valueOf(texto(cuerpo, "formato")),
                    configuracion(objeto(cuerpo, "config")), Boolean.TRUE.equals(cuerpo.get("mismosEquipos")));
            case "siguiente" -> mensaje = servicio.siguienteTemporada(texto(cuerpo, "nombre"));
            case "equipo/division" -> servicio.cambiarDivision(entero(cuerpo.get("id")), entero(cuerpo.get("division")));
            case "division/agregar" -> mensaje = servicio.agregarDivision();
            case "division/quitar" -> mensaje = servicio.quitarDivision();
            case "cancelar" -> servicio.cancelar();
            case "cambiarFormato" -> servicio.cambiarFormato(texto(cuerpo, "nombre"),
                    TipoFormato.valueOf(texto(cuerpo, "formato")), configuracion(objeto(cuerpo, "config")));
            case "avanzar" -> mensaje = servicio.avanzar();
            case "equipo/rapido" -> servicio.agregarEquipo(texto(cuerpo, "nombre"));
            case "equipo" -> guardarEquipo(cuerpo);
            case "semilla" -> mensaje = servicio.cargarDatosPrueba(entero(cuerpo.get("semilla")),
                    entero(cuerpo.get("cantidad")), !Boolean.FALSE.equals(cuerpo.get("completar")));
            case "jugador/foto" -> mensaje = servicio.cambiarFotoJugador(entero(cuerpo.get("equipo")), entero(cuerpo.get("jugador")),
                    texto(cuerpo, "imagen"));
            case "equipo/escudo" -> mensaje = servicio.cambiarEscudo(entero(cuerpo.get("id")), texto(cuerpo, "imagen"));
            case "equipo/quitar" -> mensaje = servicio.quitarEquipo(entero(cuerpo.get("id")));
            case "partido" -> guardarPartido(cuerpo);
            case "votacion/cerrar" -> {
                servicio.cerrarVotacion(entero(cuerpo.get("partido")));
                mensaje = "Votación cerrada: las notas finales quedaron definitivas.";
            }
            case "criterios" -> servicio.cambiarCriterios(criterios(lista(cuerpo, "criterios")));
            case "archivar" -> servicio.archivarActual();
            case "historial/eliminar" -> servicio.eliminarDelHistorial(texto(cuerpo, "codigo"));
            default -> throw new ReglaNegocioException("Ruta desconocida: " + ruta);
        }
        return respuesta(mensaje);
    }

    /* ---------------------------------------------------------------- [REQ-07] calificaciones */

    /**
     * Voto del publico: {partido, dispositivo, votos: [{jugador, valor (1 a 10)}]} o, para un solo jugador,
     * {partido, jugador, valor, dispositivo}. Solo votan los visitantes: el administrador ya influye en la nota
     * del sistema al registrar goles y tarjetas. Devuelve las calificaciones actualizadas.
     */
    private Map<String, Object> votar(Map<String, Object> cuerpo) throws Exception {
        int idPartido = entero(cuerpo.get("partido"));
        String dispositivo = texto(cuerpo, "dispositivo");
        Map<Integer, Integer> notas = new LinkedHashMap<>();
        for (Object o : cuerpo.containsKey("votos") ? lista(cuerpo, "votos") : List.<Object>of(cuerpo)) {
            notas.put(entero(mapa(o).get("jugador")), entero(mapa(o).get("valor")));
        }
        CalificacionesPartido calificaciones = servicio.votarJugadores(idPartido, notas, dispositivo);
        Map<String, Object> r = respuesta(notas.size() == 1 ? "¡Gracias! Su calificación quedó registrada."
                : "¡Gracias! Sus " + notas.size() + " calificaciones quedaron registradas.");
        r.put("calificaciones", Presentador.calificaciones(calificaciones));
        return r;
    }

    /* ---------------------------------------------------------------- noticias y comentarios */

    /** Noticias para la interfaz: el feed publico para todos y las pendientes solo para el administrador. */
    private Map<String, Object> noticiasJson() {
        boolean moderador = puede(Permiso.MODERAR_NOTICIAS);
        return Presentador.noticias(noticias.publicadas(), moderador ? noticias.pendientes() : List.of(), moderador);
    }

    private String ejecutarNoticia(String ruta, Map<String, Object> cuerpo) throws Exception {
        switch (ruta) {
            case "noticia/proponer" -> {
                noticias.proponer(texto(cuerpo, "titulo"), texto(cuerpo, "cuerpo"), texto(cuerpo, "autor"),
                        NoticiasService.imagenDesdeDataUrl(texto(cuerpo, "imagen")));
                return "¡Gracias! Su noticia quedó pendiente: se publicará cuando el administrador la apruebe.";
            }
            case "noticia/comentar" -> {
                noticias.comentar(entero(cuerpo.get("id")), texto(cuerpo, "autor"), texto(cuerpo, "texto"));
                return "Comentario publicado.";
            }
            case "noticia/guardar" -> {
                Object id = cuerpo.get("id");
                noticias.publicar(id == null ? null : entero(id), texto(cuerpo, "titulo"), texto(cuerpo, "cuerpo"),
                        NoticiasService.imagenDesdeDataUrl(texto(cuerpo, "imagen")), Boolean.TRUE.equals(cuerpo.get("quitarImagen")));
                return id == null ? "Noticia publicada." : "Noticia guardada.";
            }
            case "noticia/aprobar" -> {
                noticias.aprobar(entero(cuerpo.get("id")));
                return "Noticia aprobada: ya aparece en el feed.";
            }
            case "noticia/eliminar" -> {
                noticias.eliminar(entero(cuerpo.get("id")));
                return Boolean.TRUE.equals(cuerpo.get("rechazo")) ? "Propuesta rechazada." : "Noticia eliminada.";
            }
            case "comentario/eliminar" -> {
                noticias.eliminarComentario(entero(cuerpo.get("noticia")), entero(cuerpo.get("id")));
                return "Comentario eliminado.";
            }
            default -> throw new ReglaNegocioException("Ruta desconocida: " + ruta);
        }
    }

    /** Foto de una noticia (la pide ServidorWeb para /api/imagen/{nombre}). Las de pendientes solo las ve el administrador. */
    /** Foto de un jugador o escudo (la pide ServidorWeb para /api/foto/{nombre}); basta con tener sesion. */
    public byte[] foto(String nombre) throws Exception {
        exigirSesion();
        return servicio.getFotos().leerGuardada(nombre);
    }

    public byte[] imagenNoticia(String nombre) throws Exception {
        exigirSesion();
        return noticias.imagen(nombre, usuarioActual.puede(Permiso.MODERAR_NOTICIAS));
    }

    public Map<String, Object> respuesta(String mensaje) {
        return Presentador.mapa("ok", true, "mensaje", mensaje, "estado", presentador.estado(), "sesion", sesion());
    }

    private void guardarEquipo(Map<String, Object> cuerpo) throws Exception {
        Map<String, Object> dt = objeto(cuerpo, "dt");
        DirectorTecnico director = dt == null || texto(dt, "nombre").isBlank() ? null
                : new DirectorTecnico(texto(dt, "nombre").trim(), texto(dt, "documento").trim(), texto(dt, "nacionalidad").trim());
        List<Jugador> jugadores = new ArrayList<>();
        for (Object o : lista(cuerpo, "jugadores")) {
            Map<String, Object> j = mapa(o);
            Jugador jugador = new Jugador(texto(j, "nombre").trim(), texto(j, "documento").trim(),
                    entero(j.get("dorsal")), texto(j, "posicion"));
            jugador.setId(j.get("id") == null ? 0 : entero(j.get("id")));
            jugador.setFoto(texto(j, "foto")); // foto nueva (data URL), la que tenia o ""
            jugadores.add(jugador);
        }
        Object id = cuerpo.get("id");
        servicio.guardarEquipo(id == null ? null : entero(id), texto(cuerpo, "nombre"), texto(cuerpo, "estadio"),
                director, jugadores, cuerpo.containsKey("escudo") ? texto(cuerpo, "escudo") : null);
    }

    private void guardarPartido(Map<String, Object> cuerpo) throws Exception {
        int id = entero(cuerpo.get("id"));
        Partido original = servicio.buscarPartido(id);
        Partido datos = new Partido();
        datos.copiarDatosDe(original);
        Map<String, Object> d = objeto(cuerpo, "datos");
        EstadoPartido estado = EstadoPartido.valueOf(texto(d, "estado"));
        if (estado == EstadoPartido.JUGADO || estado == EstadoPartido.EN_JUEGO) {
            // «En juego»: el marcador, las tarjetas y los cambios se registran en vivo (sin penales).
            boolean jugado = estado == EstadoPartido.JUGADO;
            datos.setEstado(estado);
            datos.setGolesLocal(Math.max(0, entero(d.get("gl"))));
            datos.setGolesVisitante(Math.max(0, entero(d.get("gv"))));
            datos.setPenalesLocal(jugado ? entero(d.get("pl")) : Partido.SIN_PENALES);
            datos.setPenalesVisitante(jugado ? entero(d.get("pv")) : Partido.SIN_PENALES);
            datos.setTarjetas(tarjetas(lista(d, "tarjetas")), original.getIdLocal());
            leerTitulares(d, datos);
            if (d.containsKey("cambios")) {
                datos.setCambios(cambios(lista(d, "cambios")));
            }
            if (d.containsKey("goles")) { // la carga rapida no envia los goles: se conservan
                datos.setGoles(goles(lista(d, "goles")));
            }
        } else {
            datos.reiniciarResultado();
            datos.setEstado(estado);
            leerTitulares(d, datos); // «Previa del partido»: el XI se elige antes de jugar
        }
        // La formacion se elige antes del partido: se guarda en cualquier estado.
        if (d.containsKey("formacionLocal")) {
            datos.setFormaciones(formacion(texto(d, "formacionLocal")), formacion(texto(d, "formacionVisitante")));
        }
        datos.setFecha(fecha(texto(d, "fecha")));
        datos.setHora(hora(texto(d, "hora")));
        datos.setSede(texto(d, "sede").trim());
        datos.setObservaciones(texto(d, "obs").trim());
        boolean confirmado = Boolean.TRUE.equals(cuerpo.get("confirmado"));
        switch (texto(cuerpo, "accion")) {
            case "woLocal" -> servicio.darWalkover(id, datos, original.getIdLocal(), confirmado);
            case "woVisitante" -> servicio.darWalkover(id, datos, original.getIdVisitante(), confirmado);
            case "borrar" -> {
                datos.reiniciarResultado();
                servicio.guardarPartido(id, datos, confirmado);
            }
            default -> {
                if (d.containsKey("horasVotacion")) { // [REQ-07] cuanto dura la votacion (la carga rapida no lo envia)
                    servicio.programarVotacion(datos, entero(d.get("horasVotacion")));
                }
                servicio.guardarPartido(id, datos, confirmado);
            }
        }
    }

    /* Lectura del JSON del partido: solo convierte. Las reglas (XI, minutos, cambios, goles y tarjetas) las
       revisa ReglasPartido al guardar, igual para el escritorio y la web. */

    private ArrayList<Tarjeta> tarjetas(List<Object> lista) {
        ArrayList<Tarjeta> tarjetas = new ArrayList<>();
        for (Object o : lista) {
            Map<String, Object> t = mapa(o);
            boolean roja = "ROJA".equals(texto(t, "tipo"));
            Tarjeta tarjeta = new Tarjeta(entero(t.get("equipo")), jugador(t.get("jugador"), "Una de las tarjetas"),
                    roja ? Tarjeta.ROJA : Tarjeta.AMARILLA, roja ? Math.max(1, entero(t.get("fechas"))) : 0);
            tarjeta.setMinuto(t.get("minuto") == null ? 0 : entero(t.get("minuto")));
            tarjetas.add(tarjeta);
        }
        return tarjetas;
    }

    /** XI inicial de los dos equipos, si viene en la petición (la carga rápida no lo envía: se conserva). */
    private void leerTitulares(Map<String, Object> d, Partido datos) {
        if (d.containsKey("titularesLocal")) {
            datos.setTitulares(enteros(lista(d, "titularesLocal")), enteros(lista(d, "titularesVisitante")));
        }
    }

    private static ArrayList<Integer> enteros(List<Object> lista) {
        ArrayList<Integer> ids = new ArrayList<>();
        lista.forEach(o -> ids.add(entero(o)));
        return ids;
    }

    private ArrayList<Cambio> cambios(List<Object> lista) {
        ArrayList<Cambio> cambios = new ArrayList<>();
        for (Object o : lista) {
            Map<String, Object> c = mapa(o);
            cambios.add(new Cambio(entero(c.get("equipo")), entero(c.get("minuto")),
                    jugador(c.get("sale"), "Uno de los cambios"), jugador(c.get("entra"), "Uno de los cambios")));
        }
        cambios.sort(Comparator.comparingInt(Cambio::getMinuto));
        return cambios;
    }

    private ArrayList<Gol> goles(List<Object> lista) {
        ArrayList<Gol> goles = new ArrayList<>();
        for (Object o : lista) {
            Map<String, Object> g = mapa(o);
            boolean enContra = Boolean.TRUE.equals(g.get("enContra"));
            int idAsistente = g.get("asistente") == null || enContra ? Gol.SIN_ASISTENCIA : entero(g.get("asistente")); // un autogol no tiene
            goles.add(new Gol(entero(g.get("equipo")), g.get("minuto") == null ? 0 : entero(g.get("minuto")),
                    jugador(g.get("autor"), "Uno de los goles"), enContra,
                    idAsistente == Gol.SIN_ASISTENCIA ? null : jugador(idAsistente, "La asistencia de un gol")));
        }
        goles.sort(Comparator.comparingInt(x -> x.getMinuto() == 0 ? Integer.MAX_VALUE : x.getMinuto()));
        return goles;
    }

    /** Jugador por id; si ya no esta en ninguna plantilla, el dato no se puede leer. */
    private Jugador jugador(Object id, String que) {
        Jugador j = ReglasPartido.buscarJugador(servicio.getCampeonato(), entero(id));
        if (j == null) {
            throw new ReglaNegocioException(que + " es de un jugador que ya no existe.");
        }
        return j;
    }

    /** La fecha se guarda como DATE: aaaa-mm-dd o «Por definir». */
    private static String fecha(String texto) {
        if (texto.isBlank() || texto.equals("Por definir")) {
            return "Por definir";
        }
        try {
            return java.time.LocalDate.parse(texto.trim()).toString();
        } catch (java.time.format.DateTimeParseException e) {
            throw new ReglaNegocioException("La fecha «" + texto + "» no existe. Use el formato año-mes-día.");
        }
    }

    /** La hora se guarda como TIME: hh:mm en 24 horas o vacia. */
    private static String hora(String texto) {
        String t = texto.trim();
        if (t.isEmpty()) {
            return "";
        }
        if (!t.matches("([01]?\\d|2[0-3]):[0-5]\\d")) {
            throw new ReglaNegocioException("Escriba la hora en formato de 24 horas, por ejemplo 15:30.");
        }
        return t.length() == 4 ? "0" + t : t;
    }

    /** Valida el texto de una formacion ("" = sin definir). */
    private static String formacion(String texto) {
        if (texto.isBlank()) {
            return "";
        }
        if (Formacion.desdeTexto(texto) == null) {
            throw new ReglaNegocioException("La formación «" + texto + "» no existe.");
        }
        return texto;
    }

    private ConfiguracionTorneo configuracion(Map<String, Object> c) {
        ConfiguracionTorneo cfg = new ConfiguracionTorneo();
        cfg.setPuntosVictoria(entero(c.get("puntosVictoria")));
        cfg.setPuntosEmpate(entero(c.get("puntosEmpate")));
        cfg.setIdaYVuelta(Boolean.TRUE.equals(c.get("idaYVuelta")));
        cfg.setEliminatoriaIdaYVuelta(Boolean.TRUE.equals(c.get("eliminatoriaIdaYVuelta")));
        cfg.setFinalUnica(Boolean.TRUE.equals(c.get("finalUnica")));
        cfg.setSorteoAleatorio(Boolean.TRUE.equals(c.get("sorteoAleatorio")));
        cfg.setTamanoGrupo(entero(c.get("tamanoGrupo")));
        cfg.setClasificadosPorGrupo(entero(c.get("clasificadosPorGrupo")));
        cfg.setClasificadosPlayoff(entero(c.get("clasificadosPlayoff")));
        cfg.setTipoPlayoff(texto(c, "tipoPlayoff"));
        cfg.setVentajaDeportiva(Boolean.TRUE.equals(c.get("ventajaDeportiva")));
        cfg.setRondasSuizo(entero(c.get("rondasSuizo")));
        cfg.setDirectosSuizo(entero(c.get("directosSuizo")));
        cfg.setRepechajeSuizo(entero(c.get("repechajeSuizo")));
        cfg.setGranFinalConReinicio(Boolean.TRUE.equals(c.get("granFinalConReinicio")));
        cfg.setDivisiones(c.get("divisiones") == null ? 1 : entero(c.get("divisiones")));
        cfg.setAscensos(c.get("ascensos") == null ? 2 : entero(c.get("ascensos")));
        cfg.setCriterios(criterios(lista(c, "criterios")));
        return cfg;
    }

    private ArrayList<CriterioDesempate> criterios(List<Object> claves) {
        ArrayList<CriterioDesempate> lista = new ArrayList<>();
        for (Object clave : claves) {
            lista.add(CriterioDesempate.valueOf(String.valueOf(clave)));
        }
        return lista;
    }

    private static String texto(Map<String, Object> m, String clave) {
        Object v = m.get(clave);
        return v == null ? "" : v.toString();
    }

    private static int entero(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException("Valor numérico inválido: " + v);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object o) {
        return (Map<String, Object>) o;
    }

    private static Map<String, Object> objeto(Map<String, Object> m, String clave) {
        Object v = m.get(clave);
        return v == null ? null : mapa(v);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> lista(Map<String, Object> m, String clave) {
        Object v = m.get(clave);
        return v == null ? List.of() : (List<Object>) v;
    }
}