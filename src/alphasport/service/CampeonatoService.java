package alphasport.service;

import alphasport.model.Campeonato;
import alphasport.model.ConfiguracionTorneo;
import alphasport.model.DirectorTecnico;
import alphasport.model.Equipo;
import alphasport.model.Jugador;
import alphasport.model.Partido;
import alphasport.model.TipoFormato;
import alphasport.persistence.NotaJugador;
import alphasport.persistence.PersistenciaException;
import alphasport.persistence.RepositorioCampeonato;
import alphasport.persistence.RepositorioFotosEnMemoria;
import alphasport.persistence.RepositorioHistorial;
import alphasport.persistence.Serializacion;
import alphasport.persistence.UnidadDeTrabajo;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Casos de uso del campeonato en curso: es la unica puerta de entrada de la
 * interfaz a las reglas del torneo y al almacenamiento.
 */
public class CampeonatoService {

    private final RepositorioCampeonato repositorio;
    private final RepositorioHistorial historial;
    /** Agrupa las escrituras de una accion (partido y notas, historial y campeonato nuevo) en una transaccion. */
    private final UnidadDeTrabajo unidad;
    /** > 0 mientras se hace una accion dentro de la unidad de trabajo. */
    private int enCurso;
    /** true si la accion en curso ya escribio algo (si falla, hay que volver a lo guardado). */
    private boolean escribio;
    /** [REQ-07] Reloj para la hora de cierre de los partidos y la ventana de votacion (las pruebas lo adelantan). */
    private final Clock reloj;
    /** Fotos de jugadores y escudos (por defecto en memoria; FabricaAplicacion pone la carpeta datos/imagenes). */
    private FotosService fotos = new FotosService(new RepositorioFotosEnMemoria());
    private Campeonato campeonato;
    private MotorCampeonato motor;

    public CampeonatoService(RepositorioCampeonato repositorio, RepositorioHistorial historial) {
        this(repositorio, historial, Clock.systemDefaultZone());
    }

    public CampeonatoService(RepositorioCampeonato repositorio, RepositorioHistorial historial, Clock reloj) {
        this(repositorio, historial, reloj, UnidadDeTrabajo.DIRECTA);
    }

    /** Con PostgreSQL, {@code unidad} es la misma ConexionDB de los repositorios. */
    public CampeonatoService(RepositorioCampeonato repositorio, RepositorioHistorial historial, UnidadDeTrabajo unidad) {
        this(repositorio, historial, Clock.systemDefaultZone(), unidad);
    }

    public CampeonatoService(RepositorioCampeonato repositorio, RepositorioHistorial historial, Clock reloj, UnidadDeTrabajo unidad) {
        this.repositorio = repositorio;
        this.historial = historial;
        this.reloj = reloj;
        this.unidad = unidad;
    }

    public void usarFotos(FotosService fotos) {
        this.fotos = fotos;
    }

    public FotosService getFotos() {
        return fotos;
    }

    /** Si no se puede leer se lanza el error: nunca se empieza un campeonato nuevo encima de uno que no se pudo cargar. */
    public void cargarGuardado() throws PersistenciaException {
        repositorio.cargar().ifPresent(this::usar);
    }

    /**
     * Vuelve a leer el campeonato si otro programa (el escritorio o la web) lo cambio en la base de datos.
     * Se llama al empezar cada consulta y cada accion.
     */
    public void sincronizar() {
        try {
            if (repositorio.desactualizado()) {
                usar(repositorio.cargar().orElse(null));
            }
        } catch (PersistenciaException e) {
            throw new ReglaNegocioException("CONEXION", e.getMessage());
        }
    }

    public boolean hayCampeonato() {
        return campeonato != null;
    }

    public Campeonato getCampeonato() {
        return campeonato;
    }

    public MotorCampeonato getMotor() {
        return motor;
    }

    public Sanciones sanciones() {
        return new Sanciones(campeonato);
    }

    /** Estadisticas globales: goleadores, asistidores, valla menos vencida y juego limpio. */
    public EstadisticasCampeonato estadisticas() {
        return new EstadisticasCampeonato(campeonato);
    }

    public void crear(String nombre, TipoFormato formato, ConfiguracionTorneo configuracion, boolean mismosEquipos)
            throws PersistenciaException {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("Escriba el nombre del campeonato.");
        }
        if (configuracion.getCriterios().isEmpty()) {
            throw new ReglaNegocioException("Escoja al menos un criterio de desempate.");
        }
        Campeonato nuevo = new Campeonato(nombre.trim(), formato, configuracion);
        if (mismosEquipos && campeonato != null) {
            nuevo.importarEquipos(campeonato.getEquipos());
        }
        reemplazarPor(nuevo);
    }

    /**
     * Descarta el campeonato en curso sin guardarlo en el historial (por ejemplo, si se creo por error).
     */
    public void cancelar() throws PersistenciaException {
        exigirCampeonato();
        repositorio.eliminar();
        usar(null);
    }

    /**
     * Rehace el campeonato con otro formato y otras reglas conservando los equipos y sus plantillas.
     * El fixture y los resultados anteriores se descartan.
     */
    public void cambiarFormato(String nombre, TipoFormato formato, ConfiguracionTorneo configuracion)
            throws PersistenciaException {
        exigirCampeonato();
        if (configuracion.getCriterios().isEmpty()) {
            throw new ReglaNegocioException("Escoja al menos un criterio de desempate.");
        }
        String limpio = nombre == null || nombre.isBlank() ? campeonato.getNombreCampeonato() : nombre.trim();
        Campeonato nuevo = new Campeonato(limpio, formato, configuracion);
        nuevo.importarEquipos(campeonato.getEquipos());
        usar(nuevo);
        guardar();
    }

    /**
     * Siguiente torneo con los mismos equipos, plantillas y reglas: el Clausura de un torneo
     * corto o la siguiente temporada de una liga. En una liga con divisiones, al terminar la
     * temporada ascienden los primeros y descienden los ultimos de cada division.
     */
    public String siguienteTemporada(String nombre) throws PersistenciaException {
        exigirCampeonato();
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("Escriba el nombre del nuevo torneo.");
        }
        if (campeonato.getFormato() != TipoFormato.TORNEO_CORTO && campeonato.getFormato() != TipoFormato.LIGA) {
            throw new ReglaNegocioException("La siguiente temporada solo existe en los formatos Liga y Apertura/Clausura.");
        }
        java.util.Map<Integer, Integer> nuevaDivision = new java.util.HashMap<>();
        String movimientos = "";
        boolean conAscensos = campeonato.tieneDivisiones() && campeonato.getConfiguracion().getAscensos() > 0;
        if (conAscensos) {
            if (!campeonato.isTerminado()) {
                throw new ReglaNegocioException("Termine la temporada (hasta tener campeón) para saber quién asciende y quién desciende.");
            }
            movimientos = ascensosYDescensos(nuevaDivision);
        }
        String resumen = movimientos;
        return enUnaTransaccion(() -> {
            siguienteTorneoCorto(nombre);
            for (Equipo e : campeonato.getEquipos()) { // la copia conserva los ids de los equipos
                if (nuevaDivision.containsKey(e.getId())) {
                    e.setDivision(nuevaDivision.get(e.getId()));
                }
            }
            guardar();
            return resumen;
        });
    }

    /**
     * Calcula a que division va cada equipo: los «ascensos» ultimos de cada division bajan
     * y los primeros de la division de abajo suben. Devuelve el resumen para mostrar.
     */
    private String ascensosYDescensos(java.util.Map<Integer, Integer> nuevaDivision) {
        int indiceFase = 0;
        int divisiones = campeonato.getFases().get(indiceFase).getGrupos().size();
        StringBuilder texto = new StringBuilder();
        for (int d = 0; d + 1 < divisiones; d++) {
            List<Integer> arriba = MotorCampeonato.ids(motor.tablaDeGrupo(indiceFase, d));
            List<Integer> abajo = MotorCampeonato.ids(motor.tablaDeGrupo(indiceFase, d + 1));
            // Nunca se mueve a todos: cada division conserva al menos un equipo.
            int k = Math.min(campeonato.getConfiguracion().getAscensos(), Math.min(arriba.size(), abajo.size()) - 1);
            if (k <= 0) {
                continue;
            }
            List<Integer> bajan = arriba.subList(arriba.size() - k, arriba.size());
            List<Integer> suben = abajo.subList(0, k);
            for (int id : bajan) {
                nuevaDivision.put(id, d + 1);
            }
            for (int id : suben) {
                nuevaDivision.put(id, d);
            }
            texto.append(texto.length() == 0 ? "" : " ")
                    .append("Suben a la ").append(alphasport.model.ConfiguracionTorneo.nombreDivision(d)).append(": ")
                    .append(String.join(", ", suben.stream().map(campeonato::nombreDe).toList()))
                    .append(". Bajan a la ").append(alphasport.model.ConfiguracionTorneo.nombreDivision(d + 1)).append(": ")
                    .append(String.join(", ", bajan.stream().map(campeonato::nombreDe).toList())).append(".");
        }
        return texto.toString();
    }

    private void siguienteTorneoCorto(String nombre) throws PersistenciaException {
        exigirCampeonato();
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("Escriba el nombre del nuevo torneo.");
        }
        Campeonato copia = Serializacion.copiaProfunda(campeonato);
        copia.setNombreCampeonato(nombre.trim());
        copia.getPartidos().clear();
        copia.getFases().clear();
        copia.setPasoActual(0);
        copia.setIdCampeon(Campeonato.SIN_CAMPEON);
        copia.setSemilla(System.currentTimeMillis());
        copia.setCodigo(null);
        copia.setFechaArchivo(null);
        copia.getEquipos().removeIf(Equipo::isRetirado);
        reemplazarPor(copia);
    }

    public String avanzar() throws PersistenciaException {
        exigirCampeonato();
        String mensaje;
        try {
            mensaje = campeonato.isIniciado() ? motor.avanzar() : motor.iniciar();
        } catch (IllegalStateException e) {
            throw new ReglaNegocioException(e.getMessage());
        }
        return enUnaTransaccion(() -> {
            guardar();
            if (campeonato.isTerminado()) {
                archivar();
            }
            return mensaje;
        });
    }

    public void agregarEquipo(String nombre) throws PersistenciaException {
        exigirCampeonato();
        exigirNoIniciado();
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("Escriba el nombre del equipo.");
        }
        int division = campeonato.divisionConMenosEquipos();
        Equipo nuevo = campeonato.agregarEquipo(nombre.trim());
        if (nuevo == null) {
            throw new ReglaNegocioException("El equipo «" + nombre.trim() + "» ya existe.");
        }
        nuevo.setDivision(division);
        guardar();
    }

    /* ---------------------------------------------------------------- liga con divisiones */

    private void exigirLigaSinIniciar() {
        exigirCampeonato();
        if (campeonato.getFormato() != TipoFormato.LIGA) {
            throw new ReglaNegocioException("Las divisiones solo existen en el formato Liga.");
        }
        if (campeonato.isIniciado()) {
            throw new ReglaNegocioException("El campeonato ya inició: las divisiones se cambian antes de iniciar o en la siguiente temporada.");
        }
    }

    /** Pasa un equipo a otra division (Liga A = 0). */
    public void cambiarDivision(int idEquipo, int division) throws PersistenciaException {
        exigirLigaSinIniciar();
        Equipo equipo = buscarEquipo(idEquipo);
        if (division < 0 || division >= campeonato.getConfiguracion().getDivisiones()) {
            throw new ReglaNegocioException("Esa división no existe.");
        }
        equipo.setDivision(division);
        guardar();
    }

    /** Agrega una division al final (Liga B, Liga C...). */
    public String agregarDivision() throws PersistenciaException {
        exigirLigaSinIniciar();
        ConfiguracionTorneo cfg = campeonato.getConfiguracion();
        if (cfg.getDivisiones() >= ConfiguracionTorneo.MAXIMO_DIVISIONES) {
            throw new ReglaNegocioException("Se permiten como máximo " + ConfiguracionTorneo.MAXIMO_DIVISIONES + " divisiones.");
        }
        cfg.setDivisiones(cfg.getDivisiones() + 1);
        guardar();
        return "Se creó la " + ConfiguracionTorneo.nombreDivision(cfg.getDivisiones() - 1) + ". Asígnele equipos.";
    }

    /** Quita la ultima division; sus equipos pasan a la division anterior. */
    public String quitarDivision() throws PersistenciaException {
        exigirLigaSinIniciar();
        ConfiguracionTorneo cfg = campeonato.getConfiguracion();
        if (cfg.getDivisiones() <= 1) {
            throw new ReglaNegocioException("La liga ya tiene una sola división.");
        }
        int ultima = cfg.getDivisiones() - 1;
        List<Equipo> movidos = campeonato.equiposDeDivision(ultima);
        for (Equipo e : movidos) {
            e.setDivision(ultima - 1);
        }
        cfg.setDivisiones(ultima);
        guardar();
        return "Se quitó la " + ConfiguracionTorneo.nombreDivision(ultima) + "."
                + (movidos.isEmpty() ? "" : " Sus " + movidos.size() + " equipos pasaron a la " + ConfiguracionTorneo.nombreDivision(ultima - 1) + ".");
    }

    public void guardarEquipo(Integer idEquipo, String nombre, String estadio, DirectorTecnico dt, List<Jugador> jugadores)
            throws PersistenciaException {
        guardarEquipo(idEquipo, nombre, estadio, dt, jugadores, null);
    }

    /**
     * Crea o edita un equipo. La foto de cada jugador y el {@code escudo} pueden ser una foto nueva
     * (data URL, se normaliza y se guarda), el nombre de la que ya tenia o "" (sin foto).
     * {@code escudo} null deja el escudo como estaba.
     */
    public void guardarEquipo(Integer idEquipo, String nombre, String estadio, DirectorTecnico dt, List<Jugador> jugadores,
            String escudo) throws PersistenciaException {
        exigirCampeonato();
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty()) {
            throw new ReglaNegocioException("Debe ingresar un nombre.");
        }
        Equipo existente = idEquipo == null ? null : buscarEquipo(idEquipo);
        Equipo homonimo = campeonato.buscarEquipoPorNombre(limpio);
        if (homonimo != null && homonimo != existente) {
            throw new ReglaNegocioException("El equipo " + limpio + " ya existe.");
        }
        validarDorsales(jugadores);
        Equipo equipo = existente;
        if (equipo == null) {
            exigirNoIniciado();
            equipo = campeonato.agregarEquipo(limpio);
        }
        equipo.setNombreEquipo(limpio);
        equipo.setEstadio(estadio == null ? "" : estadio.trim());
        equipo.setDirectorTecnico(dt);
        for (Jugador j : jugadores) {
            if (j.getId() == 0) {
                j.setId(campeonato.nuevoIdJugador());
            } else if (existente != null && campeonato.buscarJugador(existente.getId(), j.getId()) != null) {
                // La pantalla reconstruye el jugador: sigue siendo la misma persona.
                j.setCodigoPersona(campeonato.buscarJugador(existente.getId(), j.getId()).getCodigoPersona());
            }
            j.setFoto(fotos.resolver(j.getFoto(), false));
        }
        if (escudo != null) {
            equipo.setEscudo(fotos.resolver(escudo, true));
        }
        equipo.setJugadores(new ArrayList<>(jugadores));
        guardar();
    }

    /** Cambia (data URL) o quita ("" o null) la foto tipo cedula de un jugador. */
    public String cambiarFotoJugador(int idEquipo, int idJugador, String dataUrl) throws PersistenciaException {
        exigirCampeonato();
        Jugador j = campeonato.buscarJugador(idEquipo, idJugador);
        if (j == null) {
            throw new ReglaNegocioException("Ese jugador ya no está en la plantilla.");
        }
        boolean quitar = dataUrl == null || dataUrl.isBlank();
        j.setFoto(quitar ? "" : fotos.guardarJugador(dataUrl));
        guardar();
        return quitar ? "Foto de " + j.getNombre() + " eliminada." : "Foto de " + j.getNombre() + " actualizada.";
    }

    /** Cambia (data URL) o quita ("" o null) el escudo de un equipo. */
    public String cambiarEscudo(int idEquipo, String dataUrl) throws PersistenciaException {
        exigirCampeonato();
        Equipo e = buscarEquipo(idEquipo);
        boolean quitar = dataUrl == null || dataUrl.isBlank();
        e.setEscudo(quitar ? "" : fotos.guardarEscudo(dataUrl));
        guardar();
        return quitar ? "Escudo de " + e.getNombreEquipo() + " eliminado." : "Escudo de " + e.getNombreEquipo() + " actualizado.";
    }

    /**
     * Carga equipos de prueba con plantilla completa generados con una semilla
     * (la misma semilla produce siempre los mismos datos). Si se pide, tambien
     * llena la plantilla de los equipos que todavia no tienen jugadores.
     */
    public String cargarDatosPrueba(int semilla, int cantidad, boolean completarExistentes) throws PersistenciaException {
        exigirCampeonato();
        int pedidos = Math.max(0, Math.min(DatosPrueba.MAXIMO_EQUIPOS, cantidad));
        if (pedidos > 0) {
            exigirNoIniciado();
        }
        List<Equipo> sinPlantilla = new ArrayList<>();
        List<String> usados = new ArrayList<>();
        for (Equipo e : campeonato.getEquipos()) {
            usados.add(e.getNombreEquipo());
            if (completarExistentes && !e.isRetirado() && e.getJugadores().isEmpty()) {
                sinPlantilla.add(e);
            }
        }
        if (pedidos == 0 && sinPlantilla.isEmpty()) {
            throw new ReglaNegocioException(completarExistentes ? "Todos los equipos ya tienen plantilla. Indique cuántos equipos nuevos crear."
                    : "Indique cuántos equipos de prueba crear.");
        }
        DatosPrueba.Resultado r = DatosPrueba.generar(semilla, pedidos, usados, sinPlantilla.size());
        for (DatosPrueba.EquipoPrueba e : r.equipos()) {
            int division = campeonato.divisionConMenosEquipos(); // reparte los equipos entre las divisiones
            Equipo equipo = campeonato.agregarEquipo(e.nombre());
            equipo.setDivision(division);
            equipo.setEstadio(e.estadio());
            equipo.setDirectorTecnico(e.dt());
            asignarIds(e.jugadores());
            equipo.setJugadores(e.jugadores());
        }
        for (int i = 0; i < sinPlantilla.size(); i++) {
            ArrayList<Jugador> jugadores = r.plantillas().get(i);
            asignarIds(jugadores);
            sinPlantilla.get(i).setJugadores(jugadores);
        }
        guardar();
        int creados = r.equipos().size();
        String texto = creados == 0 ? "" : creados + (creados == 1 ? " equipo de prueba creado" : " equipos de prueba creados");
        if (!sinPlantilla.isEmpty()) {
            texto += (texto.isEmpty() ? "Plantilla completada en " : "; plantilla completada en ") + sinPlantilla.size()
                    + (sinPlantilla.size() == 1 ? " equipo" : " equipos");
        }
        if (creados < pedidos) {
            texto += " (no hay más nombres de prueba libres)";
        }
        return texto + ". Semilla " + semilla + ".";
    }

    private void asignarIds(List<Jugador> jugadores) {
        for (Jugador j : jugadores) {
            j.setId(campeonato.nuevoIdJugador());
        }
    }

    public String quitarEquipo(int idEquipo) throws PersistenciaException {
        exigirCampeonato();
        String mensaje = motor.retirarEquipo(buscarEquipo(idEquipo));
        guardar();
        return mensaje;
    }

    /**
     * Aplica el resultado editado. Si cambia rondas ya generadas y no se confirmo,
     * lanza una excepcion con codigo CONFIRMAR para que la interfaz pregunte.
     */
    public void guardarPartido(int idPartido, Partido datos, boolean confirmado) throws PersistenciaException {
        exigirCampeonato();
        Partido partido = buscarPartido(idPartido);
        // Si al editar un W.O. se registra lo que paso en la cancha, deja de ser W.O.
        datos.setWalkover(datos.isWalkover() && datos.tieneResultadoDeWalkover());
        ReglasPartido.validar(campeonato, partido, datos);
        if (datos.isJugado()) {
            boolean necesitaPenales = motor.requierePenales(partido, datos.getGolesLocal(), datos.getGolesVisitante());
            if (necesitaPenales && (!datos.tienePenales() || datos.getPenalesLocal() == datos.getPenalesVisitante())) {
                throw new ReglaNegocioException("PENALES", "Este partido de eliminación terminó empatado"
                        + (partido.getPierna() == 2 ? " en el global" : "")
                        + ": marque «Se definió por penales» y escriba un resultado sin empate.");
            }
            if (!necesitaPenales) {
                datos.setPenalesLocal(Partido.SIN_PENALES);
                datos.setPenalesVisitante(Partido.SIN_PENALES);
            }
        }
        boolean afecta = motor.afectaRondasPosteriores(partido, datos);
        if (afecta && !confirmado) {
            throw new ReglaNegocioException("CONFIRMAR", "Este cambio modifica quién clasifica o avanza.\n"
                    + "Las rondas y fases generadas después de este partido se borrarán y tendrá que volver a pulsar "
                    + "«Avanzar» para generarlas de nuevo.\n\n¿Desea continuar?");
        }
        motor.aplicarEdicion(partido, datos, afecta);
        ReglasVotacion.marcarFinal(partido, reloj.millis()); // [REQ-07] abre (o descarta) la votacion del publico
        enUnaTransaccion(() -> {
            guardar();
            guardarNotas(idPartido);
            return null;
        });
    }

    /** Deja en la base de datos las notas del partido tal como las calcula CalculadoraCalificaciones. */
    private void guardarNotas(int idPartido) throws PersistenciaException {
        CalificacionesPartido c = calificaciones(idPartido, null);
        List<NotaJugador> notas = new ArrayList<>();
        for (List<CalificacionesPartido.Calificacion> lado : List.of(c.local(), c.visitante())) {
            for (CalificacionesPartido.Calificacion n : lado) {
                notas.add(new NotaJugador(n.idJugador(), n.notaSistema(), n.votos(), n.promedioPublico(), n.notaFinal(), n.mvp()));
            }
        }
        persistir(() -> repositorio.guardarNotas(idPartido, notas));
    }

    /** [REQ-07] Calificaciones de un partido; {@code dispositivo} sirve para mostrar el voto propio (puede ser null). */
    public CalificacionesPartido calificaciones(int idPartido, String dispositivo) {
        exigirCampeonato();
        return CalculadoraCalificaciones.calcular(campeonato, buscarPartido(idPartido), reloj.millis(), dispositivo);
    }

    /** [REQ-07] Voto de un visitante (1 a 10) a un jugador; las reglas estan en ReglasVotacion.votar. */
    public CalificacionesPartido votarJugador(int idPartido, int idJugador, int valor, String dispositivo) throws PersistenciaException {
        return votarJugadores(idPartido, Map.of(idJugador, valor), dispositivo);
    }

    /** [REQ-07] Votos de un visitante a varios jugadores (id -> nota): se guardan todos o ninguno. */
    public CalificacionesPartido votarJugadores(int idPartido, Map<Integer, Integer> notas, String dispositivo) throws PersistenciaException {
        exigirCampeonato();
        ReglasVotacion.votar(campeonato, buscarPartido(idPartido), notas, dispositivo, reloj.millis());
        enUnaTransaccion(() -> {
            guardar();
            guardarNotas(idPartido);
            return null;
        });
        return calificaciones(idPartido, dispositivo);
    }

    /** [REQ-07] Partidos que el publico puede votar ahora, primero los que cierran antes. */
    public List<Partido> votacionesAbiertas() {
        return campeonato == null ? List.of() : ReglasVotacion.abiertas(campeonato, reloj.millis());
    }

    /** [REQ-07] true si el publico puede votar ese partido ahora. */
    public boolean votacionAbierta(Partido partido) {
        return ReglasVotacion.abierta(campeonato, partido, reloj.millis());
    }

    /** [REQ-07] Programa en la copia que se esta editando cuantas horas dura la votacion (la guarda guardarPartido). */
    public void programarVotacion(Partido datos, int horas) {
        ReglasVotacion.programar(datos, horas, reloj.millis());
    }

    /** [REQ-07] El administrador cierra la votacion antes de tiempo. */
    public void cerrarVotacion(int idPartido) throws PersistenciaException {
        exigirCampeonato();
        ReglasVotacion.cerrar(buscarPartido(idPartido), reloj.millis());
        guardar();
    }

    public void darWalkover(int idPartido, Partido datos, int idGanador, boolean confirmado) throws PersistenciaException {
        exigirCampeonato();
        Partido original = buscarPartido(idPartido); // «datos» es una copia sin ids: el local se toma del partido real
        if (!original.involucra(idGanador)) {
            throw new ReglaNegocioException("El ganador del W.O. debe ser uno de los dos equipos del partido.");
        }
        MotorCampeonato.darWalkover(datos, original.getIdLocal(), idGanador, "W.O. a favor de " + campeonato.nombreDe(idGanador));
        guardarPartido(idPartido, datos, confirmado);
    }

    public void cambiarCriterios(List<alphasport.model.CriterioDesempate> criterios) throws PersistenciaException {
        exigirCampeonato();
        if (criterios.isEmpty()) {
            throw new ReglaNegocioException("Debe haber al menos un criterio de desempate.");
        }
        campeonato.getConfiguracion().setCriterios(new ArrayList<>(criterios));
        guardar();
    }

    public void archivarActual() throws PersistenciaException {
        archivar();
    }

    /** Guarda en el historial el campeonato en curso si ya tuvo partidos jugados (true si lo hizo). */
    private boolean archivar() throws PersistenciaException {
        if (campeonato == null || campeonato.getPartidos().stream().noneMatch(Partido::isJugado)) {
            return false;
        }
        if (campeonato.getFechaArchivo() == null || campeonato.isTerminado()) {
            campeonato.setFechaArchivo(LocalDate.now().toString());
        }
        return enUnaTransaccion(() -> {
            persistir(() -> historial.archivar(campeonato));
            guardar();
            return true;
        });
    }

    /**
     * {@code nuevo} pasa a ser el campeonato en curso. Si el actual ya tuvo partidos, queda en el historial con sus
     * ids: lo que apunta a sus jugadores y partidos (notas, votos y, mas adelante, el Fantasy) sigue valiendo.
     */
    private void reemplazarPor(Campeonato nuevo) throws PersistenciaException {
        enUnaTransaccion(() -> {
            boolean archivado = archivar();
            usar(nuevo);
            persistir(() -> repositorio.reemplazar(nuevo, archivado));
            return null;
        });
    }

    public List<Campeonato> historial() {
        try {
            return historial.listar();
        } catch (PersistenciaException e) {
            throw new ReglaNegocioException("CONEXION", e.getMessage());
        }
    }

    public void eliminarDelHistorial(String codigo) throws PersistenciaException {
        if (!historial.eliminar(codigo)) {
            throw new ReglaNegocioException("No se encontró ese campeonato en el historial.");
        }
    }

    public byte[] respaldo() throws PersistenciaException {
        exigirCampeonato();
        return Serializacion.aBytes(campeonato);
    }

    public void abrirRespaldo(byte[] datos) throws PersistenciaException {
        Campeonato abierto = Serializacion.desdeBytes(datos);
        reemplazarPor(abierto);
    }

    public String textoHistorial() {
        exigirCampeonato();
        return new ReporteService().historial(motor);
    }

    public Equipo buscarEquipo(int id) {
        Equipo equipo = campeonato.buscarEquipo(id);
        if (equipo == null) {
            throw new ReglaNegocioException("El equipo no existe.");
        }
        return equipo;
    }

    public Partido buscarPartido(int id) {
        Partido partido = campeonato.buscarPartido(id);
        if (partido == null) {
            throw new ReglaNegocioException("El partido no existe.");
        }
        return partido;
    }

    /** Detalle de un partido (cara a cara, racha y alineaciones). Solo lectura. */
    public DetallePartido detallePartido(int idPartido) {
        exigirCampeonato();
        return new DetallePartido(campeonato, historial(), buscarPartido(idPartido));
    }

    private void validarDorsales(List<Jugador> jugadores) {
        Set<Integer> usados = new HashSet<>();
        for (Jugador j : jugadores) {
            if (j.getNombre() == null || j.getNombre().isBlank()) {
                throw new ReglaNegocioException("Escriba el nombre del jugador.");
            }
            if (j.getDorsal() < 1 || j.getDorsal() > 99) {
                throw new ReglaNegocioException("El dorsal debe estar entre 1 y 99.");
            }
            if (!usados.add(j.getDorsal())) {
                throw new ReglaNegocioException("El dorsal " + j.getDorsal() + " ya está en uso.");
            }
        }
    }

    private void usar(Campeonato c) {
        campeonato = c;
        motor = c == null ? null : new MotorCampeonato(c);
    }

    private void guardar() throws PersistenciaException {
        persistir(() -> repositorio.guardar(campeonato));
    }

    @FunctionalInterface
    private interface Escritura {
        void ejecutar() throws PersistenciaException;
    }

    /** Si no se pudo guardar, se descarta el cambio y se vuelve a lo que esta guardado. */
    private void persistir(Escritura escritura) throws PersistenciaException {
        if (enCurso > 0) {
            escribio = true; // si falla, lo deshace y lo recupera enUnaTransaccion
            escritura.ejecutar();
            return;
        }
        try {
            escritura.ejecutar();
        } catch (PersistenciaException e) {
            volverALoGuardado(e);
            throw e;
        }
    }

    /**
     * Hace las escrituras de una accion en una sola transaccion: se guardan todas o ninguna. Si algo falla
     * despues de escribir, la base de datos queda como estaba y el campeonato se vuelve a leer, porque el
     * que esta en memoria (y lo que el repositorio sabe de el) ya tenia el cambio.
     */
    private <T> T enUnaTransaccion(UnidadDeTrabajo.Paso<T> paso) throws PersistenciaException {
        if (enCurso > 0) {
            return paso.hacer(); // ya dentro de una: se une a ella
        }
        enCurso++;
        escribio = false;
        try {
            return unidad.enTransaccion(paso);
        } catch (PersistenciaException | RuntimeException e) {
            if (escribio) {
                volverALoGuardado(e);
            }
            throw e;
        } finally {
            enCurso--;
        }
    }

    private void volverALoGuardado(Exception error) {
        try {
            usar(repositorio.cargar().orElse(null));
        } catch (PersistenciaException sinConexion) {
            error.addSuppressed(sinConexion); // sin conexion: se sincroniza en la siguiente accion
        }
    }

    private void exigirCampeonato() {
        if (campeonato == null) {
            throw new ReglaNegocioException("Primero cree un campeonato.");
        }
    }

    private void exigirNoIniciado() {
        if (campeonato.isIniciado()) {
            throw new ReglaNegocioException("El campeonato ya inició: no se pueden agregar equipos nuevos.");
        }
    }
}