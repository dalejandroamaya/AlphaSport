package alphasport.service;

import alphasport.model.Campeonato;
import alphasport.model.Partido;
import alphasport.model.VotoJugador;
import alphasport.service.CalificacionesPartido.Votacion;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * [REQ-07] Reglas de la votacion del publico: cuanto dura (la programa el administrador en cada partido),
 * cuando esta abierta, como se cierra antes de tiempo y que votos se aceptan. La nota se calcula aparte, en
 * {@link CalculadoraCalificaciones}. Solo tiene metodos estaticos; la version web tiene su gemela en
 * web/js/service/reglasVotacion.js con las mismas reglas.
 */
public final class ReglasVotacion {

    /** Duracion si el administrador no elige otra. */
    public static final int HORAS_POR_DEFECTO = Partido.HORAS_VOTACION_POR_DEFECTO;
    public static final int HORAS_MINIMO = 1;
    /** 30 dias. */
    public static final int HORAS_MAXIMO = 720;
    private static final long HORA_MS = 60L * 60 * 1000;

    private ReglasVotacion() {
    }

    /* ---------------------------------------------------------------- tiempo */

    /**
     * Hora (ms) en que cierra la votacion: la hora del final mas las horas programadas o, si el administrador
     * la cerro antes, esa hora. 0 si el partido no tiene hora de cierre.
     */
    public static long cierraEn(Partido p) {
        if (p.getFinalizadoEn() <= 0) {
            return 0;
        }
        long programado = p.getFinalizadoEn() + p.getHorasVotacion() * HORA_MS;
        return cerradaAntes(p) ? Math.min(p.getVotacionCerradaEn(), programado) : programado;
    }

    /** true si el administrador la cerro a mano. */
    public static boolean cerradaAntes(Partido p) {
        return p.getVotacionCerradaEn() > 0;
    }

    /** Estado de la votacion a la hora indicada. */
    public static Votacion estado(Partido p, long ahora) {
        if (!p.isJugado()) {
            return Votacion.SIN_RESULTADO;
        }
        if (p.getFinalizadoEn() <= 0) {
            return Votacion.SIN_REGISTRO;
        }
        return ahora >= p.getFinalizadoEn() && ahora < cierraEn(p) ? Votacion.ABIERTA : Votacion.CERRADA;
    }

    /** true si el visitante puede votar ahora: abierta, sin W.O. y con jugadores que calificar. */
    public static boolean abierta(Campeonato c, Partido p, long ahora) {
        return estado(p, ahora) == Votacion.ABIERTA && !CalculadoraCalificaciones.esWalkover(p)
                && (!CalculadoraCalificaciones.participantes(c, p, p.getIdLocal()).isEmpty()
                || !CalculadoraCalificaciones.participantes(c, p, p.getIdVisitante()).isEmpty());
    }

    /** Partidos con la votacion abierta, primero los que cierran antes. */
    public static List<Partido> abiertas(Campeonato c, long ahora) {
        return c.getPartidos().stream().filter(p -> abierta(c, p, ahora))
                .sorted(Comparator.comparingLong(ReglasVotacion::cierraEn)).toList();
    }

    /**
     * La primera vez que el partido queda JUGADO se guarda la hora del final: abre la votacion. Corregir
     * despues el resultado no la mueve; si deja de estar jugado, se descarta junto con los votos.
     */
    public static void marcarFinal(Partido p, long ahora) {
        if (p.isJugado()) {
            if (p.getFinalizadoEn() <= 0) {
                p.setFinalizadoEn(ahora);
            }
        } else {
            p.setFinalizadoEn(0);
            p.setVotacionCerradaEn(0);
            p.getVotos().clear();
        }
    }

    /* ---------------------------------------------------------------- administrador */

    /**
     * Cambia cuantas horas dura la votacion (se cuentan desde el final del partido). Se puede programar antes
     * de jugar. En un partido terminado la vuelve a abrir (aunque hubiera vencido o se hubiera cerrado a mano),
     * asi que el nuevo cierre tiene que ser futuro. Con las mismas horas no cambia nada.
     */
    public static void programar(Partido p, int horas, long ahora) {
        if (horas < HORAS_MINIMO || horas > HORAS_MAXIMO) {
            throw new ReglaNegocioException("El tiempo de votación debe estar entre " + HORAS_MINIMO + " y " + HORAS_MAXIMO + " horas.");
        }
        if (horas == p.getHorasVotacion()) {
            return;
        }
        if (p.isJugado() && p.getFinalizadoEn() > 0 && p.getFinalizadoEn() + horas * HORA_MS <= ahora) {
            throw new ReglaNegocioException("El partido terminó hace más de " + horas + (horas == 1 ? " hora" : " horas")
                    + ": elija un tiempo mayor o use «Cerrar votación ahora».");
        }
        p.setHorasVotacion(horas);
        p.setVotacionCerradaEn(0);
    }

    /** Cierra la votacion antes de tiempo: desde ahora no se aceptan votos y la nota final queda definitiva. */
    public static void cerrar(Partido p, long ahora) {
        if (estado(p, ahora) != Votacion.ABIERTA) {
            throw new ReglaNegocioException("La votación de este partido no está abierta.");
        }
        p.setVotacionCerradaEn(ahora);
    }

    /* ---------------------------------------------------------------- visitante */

    /**
     * Registra los votos de un visitante ({@code notas}: id del jugador -> nota). Primero se validan todos: si
     * uno no se acepta no se guarda ninguno. Valida que la votacion este abierta, que cada jugador haya jugado
     * ese partido, que la nota sea un entero del 1 al 10 y que ese dispositivo no lo haya votado antes.
     *
     * @throws ReglaNegocioException con un mensaje para mostrar en pantalla
     */
    public static void votar(Campeonato c, Partido p, Map<Integer, Integer> notas, String dispositivo, long ahora) {
        if (dispositivo == null || !dispositivo.matches("[A-Za-z0-9-]{8,64}")) {
            throw new ReglaNegocioException("No se pudo identificar este dispositivo para registrar el voto.");
        }
        if (CalculadoraCalificaciones.esWalkover(p)) {
            throw new ReglaNegocioException("Partido definido por W.O.: no hay jugadores que calificar.");
        }
        switch (estado(p, ahora)) {
            case SIN_RESULTADO -> throw new ReglaNegocioException("La votación se abre cuando el partido termina.");
            case SIN_REGISTRO -> throw new ReglaNegocioException("Este partido se jugó antes de existir las votaciones: solo tiene nota del sistema.");
            case CERRADA -> throw new ReglaNegocioException("La votación de este partido ya cerró.");
            case ABIERTA -> {
                // se puede votar
            }
        }
        if (notas.isEmpty()) {
            throw new ReglaNegocioException("Elija la nota de al menos un jugador.");
        }
        List<VotoJugador> nuevos = new ArrayList<>();
        notas.forEach((idJugador, nota) -> {
            validar(c, p, idJugador, nota, dispositivo);
            nuevos.add(new VotoJugador(idJugador, nota, dispositivo, ahora));
        });
        p.getVotos().addAll(nuevos);
    }

    private static void validar(Campeonato c, Partido p, int idJugador, int nota, String dispositivo) {
        if (nota < VotoJugador.MINIMO || nota > VotoJugador.MAXIMO) {
            throw new ReglaNegocioException("La calificación debe ser un número entero del 1 al 10.");
        }
        if (!CalculadoraCalificaciones.participantes(c, p, p.getIdLocal()).contains(idJugador)
                && !CalculadoraCalificaciones.participantes(c, p, p.getIdVisitante()).contains(idJugador)) {
            throw new ReglaNegocioException("Ese jugador no participó en este partido.");
        }
        if (p.getVotos().stream().anyMatch(v -> v.getIdJugador() == idJugador && dispositivo.equals(v.getDispositivo()))) {
            throw new ReglaNegocioException("Ya calificó a este jugador en este partido desde este dispositivo.");
        }
    }
}
