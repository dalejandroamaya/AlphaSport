package alphasport.ui;

import alphasport.model.Cambio;
import alphasport.model.ConfiguracionTorneo;
import alphasport.model.CriterioDesempate;
import alphasport.model.DirectorTecnico;
import alphasport.model.EstadoPartido;
import alphasport.model.Formacion;
import alphasport.model.Jugador;
import alphasport.model.Partido;
import alphasport.model.Tarjeta;
import alphasport.model.TipoFormato;
import alphasport.model.Usuario;
import alphasport.service.AutenticacionService;
import alphasport.service.CampeonatoService;
import alphasport.service.ReglaNegocioException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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

    public ApiControlador(CampeonatoService servicio, AutenticacionService autenticacion) {
        this.servicio = servicio;
        this.autenticacion = autenticacion;
        this.presentador = new Presentador(servicio);
    }

    /* ---------------------------------------------------------------- sesion y permisos */

    /** Usuario con sesion abierta; null si nadie ha iniciado sesion. */
    private Usuario usuarioActual;

    /** Rutas de ejecutar() que no exigen ser administrador. Todo lo demas si (lista blanca). */
    private static final Set<String> RUTAS_PUBLICAS = Set.of("login", "login/visitante", "logout");

    public Usuario getUsuarioActual() {
        return usuarioActual;
    }

    /** Lanza LOGIN si no hay sesion. */
    public void exigirSesion() {
        if (usuarioActual == null) {
            throw new ReglaNegocioException("LOGIN", "Inicie sesión para continuar.");
        }
    }

    /** Lanza PERMISO si el usuario no puede modificar datos. */
    public void exigirAdministrador() {
        exigirSesion();
        if (!usuarioActual.puedeEditar()) {
            throw new ReglaNegocioException("PERMISO", "Su rol (" + usuarioActual.getRol().getNombre()
                    + ") es de solo lectura: no puede crear, editar ni eliminar.");
        }
    }

    /** Datos de la sesion para la interfaz: {usuario, rol, puedeEditar}. */
    public Map<String, Object> sesion() {
        return usuarioActual == null ? null
                : Presentador.mapa("usuario", usuarioActual.getNombre(), "rol", usuarioActual.getRol().name(),
                        "rolNombre", usuarioActual.getRol().getNombre(), "puedeEditar", usuarioActual.puedeEditar());
    }

    public Map<String, Object> consultar(String ruta) {
        if (!ruta.equals("catalogo") && !ruta.equals("sesion")) {
            exigirSesion(); // consultar es solo lectura: basta con haber iniciado sesion
        }
        if (ruta.startsWith("partido/")) { // detalle tipo Flashscore: partido/{id}
            return presentador.detallePartido(servicio.detallePartido(entero(ruta.substring("partido/".length()))));
        }
        return switch (ruta) {
            case "catalogo" -> presentador.catalogo();
            case "sesion" -> Presentador.mapa("ok", true, "sesion", sesion());
            case "estado" -> presentador.estado();
            case "historial" -> presentador.historial(servicio.historial());
            default -> throw new ReglaNegocioException("Ruta desconocida: " + ruta);
        };
    }

    public Map<String, Object> ejecutar(String ruta, Map<String, Object> cuerpo) throws Exception {
        if (!RUTAS_PUBLICAS.contains(ruta)) {
            exigirAdministrador(); // toda accion que modifica datos pasa por aqui
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
            case "siguiente" -> servicio.siguienteTorneoCorto(texto(cuerpo, "nombre"));
            case "cancelar" -> servicio.cancelar();
            case "cambiarFormato" -> servicio.cambiarFormato(texto(cuerpo, "nombre"),
                    TipoFormato.valueOf(texto(cuerpo, "formato")), configuracion(objeto(cuerpo, "config")));
            case "avanzar" -> mensaje = servicio.avanzar();
            case "equipo/rapido" -> servicio.agregarEquipo(texto(cuerpo, "nombre"));
            case "equipo" -> guardarEquipo(cuerpo);
            case "semilla" -> mensaje = servicio.cargarDatosPrueba(entero(cuerpo.get("semilla")),
                    entero(cuerpo.get("cantidad")), !Boolean.FALSE.equals(cuerpo.get("completar")));
            case "equipo/quitar" -> mensaje = servicio.quitarEquipo(entero(cuerpo.get("id")));
            case "partido" -> guardarPartido(cuerpo);
            case "criterios" -> servicio.cambiarCriterios(criterios(lista(cuerpo, "criterios")));
            case "archivar" -> servicio.archivarActual();
            case "historial/eliminar" -> servicio.eliminarDelHistorial(texto(cuerpo, "codigo"));
            default -> throw new ReglaNegocioException("Ruta desconocida: " + ruta);
        }
        return respuesta(mensaje);
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
            jugadores.add(jugador);
        }
        Object id = cuerpo.get("id");
        servicio.guardarEquipo(id == null ? null : entero(id), texto(cuerpo, "nombre"), texto(cuerpo, "estadio"),
                director, jugadores);
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
            if (d.containsKey("titularesLocal")) { // la carga rapida no envia el XI ni los cambios: se conservan
                datos.setTitulares(titulares(lista(d, "titularesLocal"), original.getIdLocal()),
                        titulares(lista(d, "titularesVisitante"), original.getIdVisitante()));
            }
            if (d.containsKey("cambios")) {
                datos.setCambios(cambios(lista(d, "cambios"), original, datos));
            }
        } else {
            datos.reiniciarResultado();
            datos.setEstado(estado);
        }
        // La formacion se elige antes del partido: se guarda en cualquier estado.
        if (d.containsKey("formacionLocal")) {
            datos.setFormaciones(formacion(texto(d, "formacionLocal")), formacion(texto(d, "formacionVisitante")));
        }
        String fecha = texto(d, "fecha");
        datos.setFecha(fecha.isBlank() ? "Por definir" : fecha);
        datos.setHora(texto(d, "hora"));
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
            default -> servicio.guardarPartido(id, datos, confirmado);
        }
    }

    private ArrayList<Tarjeta> tarjetas(List<Object> lista) {
        ArrayList<Tarjeta> tarjetas = new ArrayList<>();
        for (Object o : lista) {
            Map<String, Object> t = mapa(o);
            int idEquipo = entero(t.get("equipo"));
            Jugador jugador = servicio.getCampeonato().buscarJugador(idEquipo, entero(t.get("jugador")));
            if (jugador == null) {
                throw new ReglaNegocioException("Una de las tarjetas es de un jugador que ya no existe.");
            }
            boolean roja = "ROJA".equals(texto(t, "tipo"));
            Tarjeta tarjeta = new Tarjeta(idEquipo, jugador, roja ? Tarjeta.ROJA : Tarjeta.AMARILLA,
                    roja ? Math.max(1, entero(t.get("fechas"))) : 0);
            int minuto = t.get("minuto") == null ? 0 : entero(t.get("minuto"));
            if (minuto < 0 || minuto > MINUTO_MAXIMO) {
                throw new ReglaNegocioException("El minuto de la tarjeta debe estar entre 1 y " + MINUTO_MAXIMO + ".");
            }
            tarjeta.setMinuto(minuto);
            tarjetas.add(tarjeta);
        }
        return tarjetas;
    }

    /** Minuto mas alto que se acepta (90 + prorroga + descuento). */
    public static final int MINUTO_MAXIMO = 130;

    /**
     * Lee los cambios y revisa que tengan sentido en orden de minuto: un
     * jugador que salio no puede volver a entrar y nadie entra dos veces.
     */
    /** Jugadores que inician el partido. */
    public static final int TITULARES = 11;

    /**
     * Lee el XI inicial de un equipo: jugadores de su plantilla, sin repetir,
     * y exactamente 11 (o toda la plantilla si tiene menos de 11).
     */
    private ArrayList<Integer> titulares(List<Object> lista, int idEquipo) {
        ArrayList<Integer> ids = new ArrayList<>();
        if (lista.isEmpty()) {
            return ids;
        }
        alphasport.model.Equipo equipo = servicio.buscarEquipo(idEquipo);
        for (Object o : lista) {
            int id = entero(o);
            if (servicio.getCampeonato().buscarJugador(idEquipo, id) == null) {
                throw new ReglaNegocioException("Uno de los titulares de " + equipo.getNombreEquipo() + " no está en su plantilla.");
            }
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        int necesarios = Math.min(TITULARES, equipo.getJugadores().size());
        if (ids.size() != necesarios) {
            throw new ReglaNegocioException("El XI inicial de " + equipo.getNombreEquipo() + " debe tener " + necesarios
                    + " jugadores (tiene " + ids.size() + ").");
        }
        return ids;
    }

    private ArrayList<Cambio> cambios(List<Object> lista, Partido partido, Partido datos) {
        ArrayList<Cambio> cambios = new ArrayList<>();
        for (Object o : lista) {
            Map<String, Object> c = mapa(o);
            int idEquipo = entero(c.get("equipo"));
            if (!partido.involucra(idEquipo)) {
                throw new ReglaNegocioException("Uno de los cambios es de un equipo que no juega este partido.");
            }
            int minuto = entero(c.get("minuto"));
            if (minuto < 1 || minuto > MINUTO_MAXIMO) {
                throw new ReglaNegocioException("El minuto del cambio debe estar entre 1 y " + MINUTO_MAXIMO + ".");
            }
            Jugador sale = servicio.getCampeonato().buscarJugador(idEquipo, entero(c.get("sale")));
            Jugador entra = servicio.getCampeonato().buscarJugador(idEquipo, entero(c.get("entra")));
            if (sale == null || entra == null) {
                throw new ReglaNegocioException("Uno de los cambios es de un jugador que ya no existe.");
            }
            if (sale.getId() == entra.getId()) {
                throw new ReglaNegocioException("En un cambio el jugador que sale y el que entra deben ser distintos.");
            }
            cambios.add(new Cambio(idEquipo, minuto, sale, entra));
        }
        cambios.sort(Comparator.comparingInt(Cambio::getMinuto));
        Set<Integer> salieron = new HashSet<>();
        Set<Integer> entraron = new HashSet<>();
        // Con el XI inicial definido: sale alguien que esta en la cancha y entra un suplente.
        java.util.Map<Integer, Set<Integer>> enCancha = new java.util.HashMap<>();
        // («datos» es una copia sin los ids de los equipos: el XI se busca por el partido original)
        java.util.Map<Integer, List<Integer>> xi = java.util.Map.of(partido.getIdLocal(), datos.getTitularesLocal(),
                partido.getIdVisitante(), datos.getTitularesVisitante());
        xi.forEach((idEquipo, ids) -> {
            if (!ids.isEmpty()) {
                enCancha.put(idEquipo, new HashSet<>(ids));
            }
        });
        for (Cambio c : cambios) {
            if (!enCancha.containsKey(c.getIdEquipo()) && !lista.isEmpty()) {
                throw new ReglaNegocioException("Antes de registrar cambios escoja el XI inicial de "
                        + servicio.getCampeonato().nombreDe(c.getIdEquipo()) + ".");
            }
            Set<Integer> cancha = enCancha.get(c.getIdEquipo());
            if (!cancha.contains(c.getIdSale()) && !salieron.contains(c.getIdSale())) {
                throw new ReglaNegocioException("#" + c.getDorsalSale() + " " + c.getNombreSale() + " no está en la cancha en el minuto "
                        + c.getMinuto() + ": solo puede salir un titular o alguien que ya entró.");
            }
            if (xi.get(c.getIdEquipo()).contains(c.getIdEntra())) {
                throw new ReglaNegocioException("#" + c.getDorsalEntra() + " " + c.getNombreEntra() + " es titular: solo pueden entrar suplentes.");
            }
            if (salieron.contains(c.getIdSale())) {
                throw new ReglaNegocioException("#" + c.getDorsalSale() + " " + c.getNombreSale() + " ya había salido antes del minuto " + c.getMinuto() + ".");
            }
            if (salieron.contains(c.getIdEntra()) || entraron.contains(c.getIdEntra())) {
                throw new ReglaNegocioException("#" + c.getDorsalEntra() + " " + c.getNombreEntra()
                        + (salieron.contains(c.getIdEntra()) ? " ya fue sustituido y no puede volver a entrar." : " ya había entrado en otro cambio."));
            }
            salieron.add(c.getIdSale());
            entraron.add(c.getIdEntra());
            cancha.remove(c.getIdSale());
            cancha.add(c.getIdEntra());
        }
        return cambios;
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
