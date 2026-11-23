package alphasport.service;

import alphasport.model.Campeonato;
import alphasport.model.ConfiguracionTorneo;
import alphasport.model.Equipo;
import alphasport.model.MovimientoDivision;
import alphasport.model.Partido;
import alphasport.model.TipoFormato;
import alphasport.persistence.PersistenciaException;
import alphasport.persistence.RepositorioCampeonato;
import alphasport.persistence.RepositorioHistorial;
import alphasport.persistence.Serializacion;
import alphasport.persistence.UnidadDeTrabajo;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Ciclo de vida del campeonato en curso: crearlo, sus reglas, avanzar, la siguiente temporada y las divisiones.
 * Es tambien la entrada a los demas servicios del campeonato, que comparten con el el mismo
 * {@link ContextoCampeonato}: {@link #equipos()}, {@link #partidos()}, {@link #votacion()} y {@link #historial()}.
 * Un servicio nuevo (por ejemplo, el Fantasy) recibe ese mismo contexto con {@link #contexto()}.
 */
public class CampeonatoService {

    private final ContextoCampeonato ctx;
    private final EquiposService equipos;
    private final VotacionService votacion;
    private final PartidosService partidos;
    private final HistorialService historial;

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
        this.ctx = new ContextoCampeonato(repositorio, historial, reloj, unidad);
        this.equipos = new EquiposService(ctx);
        this.votacion = new VotacionService(ctx);
        this.partidos = new PartidosService(ctx, votacion);
        this.historial = new HistorialService(ctx);
    }

    /* ---------------------------------------------------------------- servicios del mismo campeonato */

    public EquiposService equipos() {
        return equipos;
    }

    public PartidosService partidos() {
        return partidos;
    }

    public VotacionService votacion() {
        return votacion;
    }

    public HistorialService historial() {
        return historial;
    }

    /** El estado compartido, para un servicio nuevo del mismo campeonato. */
    public ContextoCampeonato contexto() {
        return ctx;
    }

    /* ---------------------------------------------------------------- estado */

    public void usarFotos(FotosService fotos) {
        ctx.usarFotos(fotos);
    }

    public FotosService getFotos() {
        return ctx.fotos();
    }

    /** Si no se puede leer se lanza el error: nunca se empieza un campeonato nuevo encima de uno que no se pudo cargar. */
    public void cargarGuardado() throws PersistenciaException {
        ctx.cargarGuardado();
    }

    /**
     * Vuelve a leer el campeonato si otro programa (el escritorio o la web) lo cambio en la base de datos.
     * Se llama al empezar cada consulta y cada accion.
     */
    public void sincronizar() {
        ctx.sincronizar();
    }

    public boolean hayCampeonato() {
        return ctx.campeonato() != null;
    }

    public Campeonato getCampeonato() {
        return ctx.campeonato();
    }

    public MotorCampeonato getMotor() {
        return ctx.motor();
    }

    public Equipo buscarEquipo(int id) {
        return ctx.buscarEquipo(id);
    }

    public Partido buscarPartido(int id) {
        return ctx.buscarPartido(id);
    }

    /* ---------------------------------------------------------------- ciclo de vida */

    public Sanciones sanciones() {
        return new Sanciones(ctx.campeonato());
    }

    /** Estadisticas globales: goleadores, asistidores, valla menos vencida y juego limpio. */
    public EstadisticasCampeonato estadisticas() {
        return new EstadisticasCampeonato(ctx.campeonato());
    }

    /**
     * Crea un campeonato nuevo. Con {@code mismosEquipos}, si el actual es una liga con divisiones que ya
     * termino y el nuevo es una liga con las mismas divisiones, se aplican los ascensos y descensos igual que en
     * {@link #siguienteTemporada}: devuelve el resumen ("" si no hubo).
     */
    public String crear(String nombre, TipoFormato formato, ConfiguracionTorneo configuracion, boolean mismosEquipos)
            throws PersistenciaException {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("Escriba el nombre del campeonato.");
        }
        if (configuracion.getCriterios().isEmpty()) {
            throw new ReglaNegocioException("Escoja al menos un criterio de desempate.");
        }
        Campeonato nuevo = new Campeonato(nombre.trim(), formato, configuracion);
        List<MovimientoDivision> movimientos = new ArrayList<>();
        if (mismosEquipos && ctx.campeonato() != null) {
            Campeonato anterior = ctx.campeonato();
            boolean mismaLiga = formato == TipoFormato.LIGA && configuracion.getDivisiones() == anterior.getConfiguracion().getDivisiones();
            List<MovimientoDivision> calculados = mismaLiga && conAscensos(anterior) && anterior.isTerminado() ? movimientos() : List.of();
            nuevo.importarEquipos(anterior.getEquipos());
            // importarEquipos da ids nuevos: el movimiento se pasa al equipo del mismo nombre.
            for (MovimientoDivision m : calculados) {
                Equipo e = nuevo.buscarEquipoPorNombre(m.nombreEquipo());
                if (e != null) {
                    e.setDivision(m.divisionNueva());
                    movimientos.add(m.conEquipo(e.getId()));
                }
            }
        }
        nuevo.setMovimientos(movimientos);
        ctx.reemplazarPor(nuevo);
        return MovimientoDivision.resumen(movimientos);
    }

    private static boolean conAscensos(Campeonato c) {
        return c.getFormato() == TipoFormato.LIGA && c.tieneDivisiones() && c.getConfiguracion().getAscensos() > 0;
    }

    /**
     * Descarta el campeonato en curso sin guardarlo en el historial (por ejemplo, si se creo por error).
     */
    public void cancelar() throws PersistenciaException {
        ctx.exigirCampeonato();
        ctx.repositorio().eliminar();
        ctx.usar(null);
    }

    /**
     * Rehace el campeonato con otro formato y otras reglas conservando los equipos y sus plantillas.
     * El fixture y los resultados anteriores se descartan.
     */
    public void cambiarFormato(String nombre, TipoFormato formato, ConfiguracionTorneo configuracion)
            throws PersistenciaException {
        ctx.exigirCampeonato();
        if (configuracion.getCriterios().isEmpty()) {
            throw new ReglaNegocioException("Escoja al menos un criterio de desempate.");
        }
        String limpio = nombre == null || nombre.isBlank() ? ctx.campeonato().getNombreCampeonato() : nombre.trim();
        Campeonato nuevo = new Campeonato(limpio, formato, configuracion);
        nuevo.importarEquipos(ctx.campeonato().getEquipos());
        ctx.usar(nuevo);
        ctx.guardar();
    }

    /**
     * Siguiente torneo con los mismos equipos, plantillas y reglas: el Clausura de un torneo
     * corto o la siguiente temporada de una liga. En una liga con divisiones, al terminar la
     * temporada ascienden los primeros y descienden los ultimos de cada division.
     */
    public String siguienteTemporada(String nombre) throws PersistenciaException {
        ctx.exigirCampeonato();
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("Escriba el nombre del nuevo torneo.");
        }
        if (ctx.campeonato().getFormato() != TipoFormato.TORNEO_CORTO && ctx.campeonato().getFormato() != TipoFormato.LIGA) {
            throw new ReglaNegocioException("La siguiente temporada solo existe en los formatos Liga y Apertura/Clausura.");
        }
        List<MovimientoDivision> movimientos = List.of();
        if (conAscensos(ctx.campeonato())) {
            if (!ctx.campeonato().isTerminado()) {
                throw new ReglaNegocioException("Termine la temporada (hasta tener campeón) para saber quién asciende y quién desciende.");
            }
            movimientos = movimientos();
        }
        List<MovimientoDivision> aplicar = movimientos;
        return ctx.enUnaTransaccion(() -> {
            siguienteTorneoCorto(nombre);
            for (MovimientoDivision m : aplicar) { // la copia conserva los ids de los equipos
                Equipo e = ctx.campeonato().buscarEquipo(m.idEquipo());
                if (e != null) {
                    e.setDivision(m.divisionNueva());
                }
            }
            ctx.campeonato().setMovimientos(aplicar); // constancia de por que cada equipo esta en su division
            ctx.guardar();
            return MovimientoDivision.resumen(aplicar);
        });
    }

    /**
     * Ascensos y descensos de la liga terminada: los «ascensos» ultimos de cada division bajan y los primeros de la
     * division de abajo suben. Nunca se mueve a todos: cada division conserva al menos un equipo.
     */
    private List<MovimientoDivision> movimientos() {
        int indiceFase = 0;
        int divisiones = ctx.campeonato().getFases().get(indiceFase).getGrupos().size();
        String temporada = ctx.campeonato().getNombreCampeonato();
        List<MovimientoDivision> lista = new ArrayList<>();
        for (int d = 0; d + 1 < divisiones; d++) {
            List<Integer> arriba = MotorCampeonato.ids(ctx.motor().tablaDeGrupo(indiceFase, d));
            List<Integer> abajo = MotorCampeonato.ids(ctx.motor().tablaDeGrupo(indiceFase, d + 1));
            int k = Math.min(ctx.campeonato().getConfiguracion().getAscensos(), Math.min(arriba.size(), abajo.size()) - 1);
            for (int i = 0; i < Math.max(0, k); i++) {
                int sube = abajo.get(i);
                lista.add(new MovimientoDivision(sube, ctx.campeonato().nombreDe(sube), d + 1, d, i + 1, temporada));
            }
            for (int i = arriba.size() - Math.max(0, k); i < arriba.size(); i++) {
                int baja = arriba.get(i);
                lista.add(new MovimientoDivision(baja, ctx.campeonato().nombreDe(baja), d, d + 1, i + 1, temporada));
            }
        }
        return lista;
    }

    private void siguienteTorneoCorto(String nombre) throws PersistenciaException {
        ctx.exigirCampeonato();
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("Escriba el nombre del nuevo torneo.");
        }
        Campeonato copia = Serializacion.copiaProfunda(ctx.campeonato());
        copia.setNombreCampeonato(nombre.trim());
        copia.getPartidos().clear();
        copia.getFases().clear();
        copia.setPasoActual(0);
        copia.setIdCampeon(Campeonato.SIN_CAMPEON);
        copia.setSemilla(System.currentTimeMillis());
        copia.setCodigo(null);
        copia.setFechaArchivo(null);
        copia.getEquipos().removeIf(Equipo::isRetirado);
        copia.setMovimientos(List.of()); // los de la temporada anterior no son de esta
        ctx.reemplazarPor(copia);
    }

    public String avanzar() throws PersistenciaException {
        ctx.exigirCampeonato();
        String mensaje;
        try {
            mensaje = ctx.campeonato().isIniciado() ? ctx.motor().avanzar() : ctx.motor().iniciar();
        } catch (IllegalStateException e) {
            throw new ReglaNegocioException(e.getMessage());
        }
        return ctx.enUnaTransaccion(() -> {
            ctx.guardar();
            if (ctx.campeonato().isTerminado()) {
                ctx.archivar();
            }
            return mensaje;
        });
    }

    /** Agrega una division al final (Liga B, Liga C...). */
    public String agregarDivision() throws PersistenciaException {
        ctx.exigirLigaSinIniciar();
        ConfiguracionTorneo cfg = ctx.campeonato().getConfiguracion();
        if (cfg.getDivisiones() >= ConfiguracionTorneo.MAXIMO_DIVISIONES) {
            throw new ReglaNegocioException("Se permiten como máximo " + ConfiguracionTorneo.MAXIMO_DIVISIONES + " divisiones.");
        }
        cfg.setDivisiones(cfg.getDivisiones() + 1);
        ctx.guardar();
        return "Se creó la " + ConfiguracionTorneo.nombreDivision(cfg.getDivisiones() - 1) + ". Asígnele equipos.";
    }

    /** Quita la ultima division; sus equipos pasan a la division anterior. */
    public String quitarDivision() throws PersistenciaException {
        ctx.exigirLigaSinIniciar();
        ConfiguracionTorneo cfg = ctx.campeonato().getConfiguracion();
        if (cfg.getDivisiones() <= 1) {
            throw new ReglaNegocioException("La liga ya tiene una sola división.");
        }
        int ultima = cfg.getDivisiones() - 1;
        List<Equipo> movidos = ctx.campeonato().equiposDeDivision(ultima);
        for (Equipo e : movidos) {
            e.setDivision(ultima - 1);
        }
        cfg.setDivisiones(ultima);
        ctx.guardar();
        return "Se quitó la " + ConfiguracionTorneo.nombreDivision(ultima) + "."
                + (movidos.isEmpty() ? "" : " Sus " + movidos.size() + " equipos pasaron a la " + ConfiguracionTorneo.nombreDivision(ultima - 1) + ".");
    }

    public void cambiarCriterios(List<alphasport.model.CriterioDesempate> criterios) throws PersistenciaException {
        ctx.exigirCampeonato();
        if (criterios.isEmpty()) {
            throw new ReglaNegocioException("Debe haber al menos un criterio de desempate.");
        }
        ctx.campeonato().getConfiguracion().setCriterios(new ArrayList<>(criterios));
        ctx.guardar();
    }
}
