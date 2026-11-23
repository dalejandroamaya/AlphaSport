package alphasport.service;

import alphasport.model.Partido;
import alphasport.persistence.PersistenciaException;

/**
 * Partidos: guardar la previa o el resultado (con las reglas de ReglasPartido), el W.O. y el detalle. Al guardar
 * un resultado, las notas se actualizan en la misma transaccion (VotacionService).
 */
public class PartidosService {

    private final ContextoCampeonato ctx;
    private final VotacionService votacion;

    public PartidosService(ContextoCampeonato ctx, VotacionService votacion) {
        this.ctx = ctx;
        this.votacion = votacion;
    }

    /**
     * Aplica el resultado editado. Si cambia rondas ya generadas y no se confirmo,
     * lanza una excepcion con codigo CONFIRMAR para que la interfaz pregunte.
     */
    public void guardarPartido(int idPartido, Partido datos, boolean confirmado) throws PersistenciaException {
        ctx.exigirCampeonato();
        Partido partido = ctx.buscarPartido(idPartido);
        // Si al editar un W.O. se registra lo que paso en la cancha, deja de ser W.O.
        datos.setWalkover(datos.isWalkover() && datos.tieneResultadoDeWalkover());
        ReglasPartido.validar(ctx.campeonato(), partido, datos);
        if (datos.isJugado()) {
            boolean necesitaPenales = ctx.motor().requierePenales(partido, datos.getGolesLocal(), datos.getGolesVisitante());
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
        boolean afecta = ctx.motor().afectaRondasPosteriores(partido, datos);
        if (afecta && !confirmado) {
            throw new ReglaNegocioException("CONFIRMAR", "Este cambio modifica quién clasifica o avanza.\n"
                    + "Las rondas y fases generadas después de este partido se borrarán y tendrá que volver a pulsar "
                    + "«Avanzar» para generarlas de nuevo.\n\n¿Desea continuar?");
        }
        ctx.motor().aplicarEdicion(partido, datos, afecta);
        ReglasVotacion.marcarFinal(partido, ctx.ahora()); // [REQ-07] abre (o descarta) la votacion del publico
        ctx.enUnaTransaccion(() -> {
            ctx.guardar();
            votacion.guardarNotas(idPartido);
            return null;
        });
    }

    public void darWalkover(int idPartido, Partido datos, int idGanador, boolean confirmado) throws PersistenciaException {
        ctx.exigirCampeonato();
        Partido original = ctx.buscarPartido(idPartido); // «datos» es una copia sin ids: el local se toma del partido real
        if (!original.involucra(idGanador)) {
            throw new ReglaNegocioException("El ganador del W.O. debe ser uno de los dos equipos del partido.");
        }
        MotorCampeonato.darWalkover(datos, original.getIdLocal(), idGanador, "W.O. a favor de " + ctx.campeonato().nombreDe(idGanador));
        guardarPartido(idPartido, datos, confirmado);
    }

    /** Detalle de un partido (cara a cara, racha y alineaciones). Solo lectura. */
    public DetallePartido detallePartido(int idPartido) {
        ctx.exigirCampeonato();
        return new DetallePartido(ctx.campeonato(), ctx.historialGuardado(), ctx.buscarPartido(idPartido));
    }
}
