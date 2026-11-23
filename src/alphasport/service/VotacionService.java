package alphasport.service;

import alphasport.model.Partido;
import alphasport.persistence.NotaJugador;
import alphasport.persistence.PersistenciaException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * [REQ-07] Calificaciones de los jugadores y votacion del publico: votar, abrir y cerrar la votacion y guardar las
 * notas calculadas.
 */
public class VotacionService {

    private final ContextoCampeonato ctx;

    public VotacionService(ContextoCampeonato ctx) {
        this.ctx = ctx;
    }

    /** Deja en la base de datos las notas del partido tal como las calcula CalculadoraCalificaciones. */
    void guardarNotas(int idPartido) throws PersistenciaException {
        CalificacionesPartido c = calificaciones(idPartido, null);
        List<NotaJugador> notas = new ArrayList<>();
        for (List<CalificacionesPartido.Calificacion> lado : List.of(c.local(), c.visitante())) {
            for (CalificacionesPartido.Calificacion n : lado) {
                notas.add(new NotaJugador(n.idJugador(), n.notaSistema(), n.votos(), n.promedioPublico(), n.notaFinal(), n.mvp()));
            }
        }
        ctx.persistir(() -> ctx.repositorio().guardarNotas(idPartido, notas));
    }

    /** [REQ-07] Calificaciones de un partido; {@code dispositivo} sirve para mostrar el voto propio (puede ser null). */
    public CalificacionesPartido calificaciones(int idPartido, String dispositivo) {
        ctx.exigirCampeonato();
        return CalculadoraCalificaciones.calcular(ctx.campeonato(), ctx.buscarPartido(idPartido), ctx.ahora(), dispositivo);
    }

    /** [REQ-07] Voto de un visitante (1 a 10) a un jugador; las reglas estan en ReglasVotacion.votar. */
    public CalificacionesPartido votarJugador(int idPartido, int idJugador, int valor, String dispositivo) throws PersistenciaException {
        return votarJugadores(idPartido, Map.of(idJugador, valor), dispositivo);
    }

    /** [REQ-07] Votos de un visitante a varios jugadores (id -> nota): se guardan todos o ninguno. */
    public CalificacionesPartido votarJugadores(int idPartido, Map<Integer, Integer> notas, String dispositivo) throws PersistenciaException {
        ctx.exigirCampeonato();
        ReglasVotacion.votar(ctx.campeonato(), ctx.buscarPartido(idPartido), notas, dispositivo, ctx.ahora());
        ctx.enUnaTransaccion(() -> {
            ctx.guardar();
            guardarNotas(idPartido);
            return null;
        });
        return calificaciones(idPartido, dispositivo);
    }

    /** [REQ-07] Partidos que el publico puede votar ahora, primero los que cierran antes. */
    public List<Partido> votacionesAbiertas() {
        return ctx.campeonato() == null ? List.of() : ReglasVotacion.abiertas(ctx.campeonato(), ctx.ahora());
    }

    /** [REQ-07] true si el publico puede votar ese partido ahora. */
    public boolean votacionAbierta(Partido partido) {
        return ReglasVotacion.abierta(ctx.campeonato(), partido, ctx.ahora());
    }

    /** [REQ-07] Programa en la copia que se esta editando cuantas horas dura la votacion (la guarda guardarPartido). */
    public void programarVotacion(Partido datos, int horas) {
        ReglasVotacion.programar(datos, horas, ctx.ahora());
    }

    /** [REQ-07] El administrador cierra la votacion antes de tiempo. */
    public void cerrarVotacion(int idPartido) throws PersistenciaException {
        ctx.exigirCampeonato();
        ReglasVotacion.cerrar(ctx.buscarPartido(idPartido), ctx.ahora());
        ctx.guardar();
    }
}
