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
    private Campeonato campeonato;
    private MotorCampeonato motor;

    public CampeonatoService(RepositorioCampeonato repositorio, RepositorioHistorial historial) {
        this.repositorio = repositorio;
        this.historial = historial;
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

    public void siguienteTorneoCorto(String nombre) throws PersistenciaException {
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
        if (campeonato.agregarEquipo(nombre.trim()) == null) {
            throw new ReglaNegocioException("El equipo «" + nombre.trim() + "» ya existe.");
        }
        guardar();
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
        guardar();
    }

    public void darWalkover(int idPartido, Partido datos, int idGanador, boolean confirmado) throws PersistenciaException {
        exigirCampeonato();
        MotorCampeonato.darWalkover(datos, idGanador, "W.O. a favor de " + campeonato.nombreDe(idGanador));
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
