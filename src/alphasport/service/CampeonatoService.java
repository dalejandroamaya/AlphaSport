package alphasport.service;

import alphasport.model.Campeonato;
import alphasport.model.ConfiguracionTorneo;
import alphasport.model.DirectorTecnico;
import alphasport.model.Equipo;
import alphasport.model.Jugador;
import alphasport.model.Partido;
import alphasport.model.TipoFormato;
import alphasport.persistence.PersistenciaException;
import alphasport.persistence.RepositorioCampeonato;
import alphasport.persistence.RepositorioHistorial;
import alphasport.persistence.Serializacion;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Casos de uso del campeonato en curso: es la unica puerta de entrada de la
 * interfaz a las reglas del torneo y al almacenamiento.
 */
public class CampeonatoService {

    private final RepositorioCampeonato repositorio;
    private final RepositorioHistorial historial;
    /** [REQ-07] Reloj para la hora de cierre de los partidos y la ventana de votacion (las pruebas lo adelantan). */
    private final Clock reloj;
    private Campeonato campeonato;
    private MotorCampeonato motor;

    public CampeonatoService(RepositorioCampeonato repositorio, RepositorioHistorial historial) {
        this(repositorio, historial, Clock.systemDefaultZone());
    }

    public CampeonatoService(RepositorioCampeonato repositorio, RepositorioHistorial historial, Clock reloj) {
        this.repositorio = repositorio;
        this.historial = historial;
        this.reloj = reloj;
    }

    public void cargarGuardado() {
        if (!repositorio.existe()) {
            return;
        }
        try {
            usar(repositorio.cargar());
        } catch (PersistenciaException e) {
            System.err.println(e.getMessage() + ". Se empezara un campeonato nuevo.");
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
        archivarActual();
        usar(nuevo);
        guardar();
    }

    /**
     * Descarta el campeonato en curso sin guardarlo en el historial (por ejemplo, si se creo por error).
     */
    public void cancelar() throws PersistenciaException {
        exigirCampeonato();
        campeonato = null;
        motor = null;
        repositorio.eliminar();
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
        siguienteTorneoCorto(nombre);
        for (Equipo e : campeonato.getEquipos()) { // la copia conserva los ids de los equipos
            if (nuevaDivision.containsKey(e.getId())) {
                e.setDivision(nuevaDivision.get(e.getId()));
            }
        }
        guardar();
        return movimientos;
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
        archivarActual();
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
        usar(copia);
        guardar();
    }

    public String avanzar() throws PersistenciaException {
        exigirCampeonato();
        String mensaje;
        try {
            mensaje = campeonato.isIniciado() ? motor.avanzar() : motor.iniciar();
        } catch (IllegalStateException e) {
            throw new ReglaNegocioException(e.getMessage());
        }
        guardar();
        if (campeonato.isTerminado()) {
            archivarActual();
        }
        return mensaje;
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
            }
        }
        equipo.setJugadores(new ArrayList<>(jugadores));
        guardar();
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
        marcarFinal(partido);
        guardar();
    }

    /**
     * [REQ-07] La primera vez que el partido queda JUGADO se guarda la hora de cierre: abre la votacion
     * de 24 horas. Corregir despues el resultado no la mueve; si deja de estar jugado, se descarta
     * junto con los votos.
     */
    private void marcarFinal(Partido partido) {
        if (partido.isJugado()) {
            if (partido.getFinalizadoEn() <= 0) {
                partido.setFinalizadoEn(reloj.millis());
            }
        } else {
            partido.setFinalizadoEn(0);
            partido.getVotos().clear();
        }
    }

    /** [REQ-07] Calificaciones de un partido; {@code dispositivo} sirve para mostrar el voto propio (puede ser null). */
    public CalificacionesPartido calificaciones(int idPartido, String dispositivo) {
        exigirCampeonato();
        return CalculadoraCalificaciones.calcular(campeonato, buscarPartido(idPartido), reloj.millis(), dispositivo);
    }

    /** [REQ-07] Voto de un visitante (1 a 10) a un jugador; las reglas estan en CalculadoraCalificaciones.votar. */
    public CalificacionesPartido votarJugador(int idPartido, int idJugador, int valor, String dispositivo) throws PersistenciaException {
        exigirCampeonato();
        Partido partido = buscarPartido(idPartido);
        CalculadoraCalificaciones.votar(campeonato, partido, idJugador, valor, dispositivo, reloj.millis());
        guardar();
        return calificaciones(idPartido, dispositivo);
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
        if (campeonato == null || campeonato.getPartidos().stream().noneMatch(Partido::isJugado)) {
            return;
        }
        if (campeonato.getFechaArchivo() == null || campeonato.isTerminado()) {
            campeonato.setFechaArchivo(LocalDate.now().toString());
        }
        historial.archivar(campeonato);
        guardar();
    }

    public List<Campeonato> historial() {
        return historial.listar();
    }

    public void eliminarDelHistorial(String codigo) {
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
        archivarActual();
        usar(abierto);
        guardar();
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

    /** Detalle tipo Flashscore de un partido (cara a cara, racha y alineaciones). Solo lectura. */
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
        motor = new MotorCampeonato(c);
    }

    private void guardar() throws PersistenciaException {
        repositorio.guardar(campeonato);
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