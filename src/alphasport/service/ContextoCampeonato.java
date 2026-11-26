package alphasport.service;

import alphasport.model.Campeonato;
import alphasport.model.Equipo;
import alphasport.model.Partido;
import alphasport.model.TipoFormato;
import alphasport.persistence.PersistenciaException;
import alphasport.persistence.RepositorioCampeonato;
import alphasport.persistence.RepositorioFotosEnMemoria;
import alphasport.persistence.RepositorioHistorial;
import alphasport.persistence.UnidadDeTrabajo;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * El campeonato en curso que comparten los servicios ({@link CampeonatoService}, {@link EquiposService},
 * {@link PartidosService}, {@link VotacionService}, {@link HistorialService} y, mas adelante, el Fantasy): lo
 * tiene en memoria, lo guarda, agrupa las escrituras de una accion en una transaccion y, si algo falla, vuelve a
 * lo que esta guardado. Asi cada servicio se ocupa solo de sus reglas.
 */
public final class ContextoCampeonato {

    private final RepositorioCampeonato repositorio;
    private final RepositorioHistorial historial;
    /** Agrupa las escrituras de una accion (partido y notas, historial y campeonato nuevo) en una transaccion. */
    private final UnidadDeTrabajo unidad;
    /** [REQ-07] Reloj para la hora de cierre de los partidos y la ventana de votacion (las pruebas lo adelantan). */
    private final Clock reloj;
    /** > 0 mientras se hace una accion dentro de la unidad de trabajo. */
    private int enCurso;
    /** true si la accion en curso ya escribio algo (si falla, hay que volver a lo guardado). */
    private boolean escribio;
    /** Fotos de jugadores y escudos (por defecto en memoria; FabricaAplicacion pone la carpeta datos/imagenes). */
    private FotosService fotos = new FotosService(new RepositorioFotosEnMemoria());
    private Campeonato campeonato;
    private MotorCampeonato motor;
    /** Id del Organizador dueno (0 = sin dueno). */
    private int propietario;

    public ContextoCampeonato(RepositorioCampeonato repositorio, RepositorioHistorial historial, Clock reloj, UnidadDeTrabajo unidad) {
        this.repositorio = repositorio;
        this.historial = historial;
        this.reloj = reloj;
        this.unidad = unidad;
    }

    /* ---------------------------------------------------------------- estado */

    /** El campeonato en curso; null si no hay. */
    public Campeonato campeonato() {
        return campeonato;
    }

    public MotorCampeonato motor() {
        return motor;
    }

    public FotosService fotos() {
        return fotos;
    }

    void usarFotos(FotosService fotos) {
        this.fotos = fotos;
    }

    /** Hora actual en ms, del reloj del servicio (las pruebas lo adelantan). */
    public long ahora() {
        return reloj.millis();
    }

    /** Fecha de hoy según el reloj del servicio. */
    public java.time.LocalDate hoy() {
        return java.time.LocalDate.now(reloj);
    }

    RepositorioCampeonato repositorio() {
        return repositorio;
    }

    RepositorioHistorial repositorioHistorial() {
        return historial;
    }

    /** Id del Organizador dueno de este campeonato (0 = sin dueno, o modo de un solo campeonato). */
    public int propietario() {
        return propietario;
    }

    /**
     * Fija el dueno: lo usa {@link GestorCampeonatos} al crear un campeonato o cuando un Organizador toma uno sin
     * dueno. Desde ahi, todo campeonato que pase por este contexto (otro formato, un respaldo, la siguiente
     * temporada) queda con ese dueno: un respaldo de otro organizador no cambia de quien es.
     */
    void asignarPropietario(int id) {
        propietario = id;
        if (campeonato != null) {
            campeonato.setPropietario(id);
        }
    }

    void usar(Campeonato c) {
        if (c != null) {
            if (propietario > 0) {
                c.setPropietario(propietario);
            } else {
                propietario = c.getPropietario();
            }
        }
        campeonato = c;
        motor = c == null ? null : new MotorCampeonato(c);
    }

    /** Si no se puede leer se lanza el error: nunca se empieza un campeonato nuevo encima de uno que no se pudo cargar. */
    void cargarGuardado() throws PersistenciaException {
        repositorio.cargar().ifPresent(this::usar);
    }

    /** Vuelve a leer el campeonato si otro programa (el escritorio o la web) lo cambio en la base de datos. */
    void sincronizar() {
        try {
            if (repositorio.desactualizado()) {
                usar(repositorio.cargar().orElse(null));
            }
        } catch (PersistenciaException e) {
            throw new ReglaNegocioException("CONEXION", e.getMessage());
        }
    }

    /** Los campeonatos del historial (la conexion caida se informa como CONEXION). */
    List<Campeonato> historialGuardado() {
        try {
            return historial.listar();
        } catch (PersistenciaException e) {
            throw new ReglaNegocioException("CONEXION", e.getMessage());
        }
    }

    /* ---------------------------------------------------------------- consultas con regla */

    public void exigirCampeonato() {
        if (campeonato == null) {
            throw new ReglaNegocioException("Primero cree un campeonato.");
        }
    }

    void exigirNoIniciado() {
        if (campeonato.isIniciado()) {
            throw new ReglaNegocioException("El campeonato ya inició: no se pueden agregar equipos nuevos.");
        }
    }

    void exigirLigaSinIniciar() {
        exigirCampeonato();
        if (campeonato.getFormato() != TipoFormato.LIGA) {
            throw new ReglaNegocioException("Las divisiones solo existen en el formato Liga.");
        }
        if (campeonato.isIniciado()) {
            throw new ReglaNegocioException("El campeonato ya inició: las divisiones se cambian antes de iniciar o en la siguiente temporada.");
        }
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

    /* ---------------------------------------------------------------- guardar */

    void guardar() throws PersistenciaException {
        persistir(() -> repositorio.guardar(campeonato));
    }

    @FunctionalInterface
    interface Escritura {
        void ejecutar() throws PersistenciaException;
    }

    /** Si no se pudo guardar, se descarta el cambio y se vuelve a lo que esta guardado. */
    void persistir(Escritura escritura) throws PersistenciaException {
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
    <T> T enUnaTransaccion(UnidadDeTrabajo.Paso<T> paso) throws PersistenciaException {
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

    /* ---------------------------------------------------------------- historial */

    /** Guarda en el historial el campeonato en curso si ya tuvo partidos jugados (true si lo hizo). */
    boolean archivar() throws PersistenciaException {
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
     * Termina el campeonato: pasa al historial (con sus ids, notas y votos) y deja de estar en curso. Solo si ya
     * tuvo partidos jugados; uno sin partidos se elimina en vez de archivarse.
     */
    void terminar() throws PersistenciaException {
        exigirCampeonato();
        if (campeonato.getPartidos().stream().noneMatch(Partido::isJugado)) {
            throw new ReglaNegocioException("Este campeonato todavía no tiene partidos jugados: no hay nada que guardar en el historial. "
                    + "Si ya no lo necesita, elimínelo.");
        }
        if (campeonato.getFechaArchivo() == null) {
            campeonato.setFechaArchivo(LocalDate.now().toString());
        }
        enUnaTransaccion(() -> {
            persistir(() -> repositorio.archivarEn(historial, campeonato));
            return null;
        });
        usar(null);
    }

    /**
     * {@code nuevo} pasa a ser el campeonato en curso. Si el actual ya tuvo partidos, queda en el historial con sus
     * ids: lo que apunta a sus jugadores y partidos (notas, votos y, mas adelante, el Fantasy) sigue valiendo.
     */
    void reemplazarPor(Campeonato nuevo) throws PersistenciaException {
        enUnaTransaccion(() -> {
            boolean archivado = archivar();
            usar(nuevo);
            persistir(() -> repositorio.reemplazar(nuevo, archivado));
            return null;
        });
    }
}
