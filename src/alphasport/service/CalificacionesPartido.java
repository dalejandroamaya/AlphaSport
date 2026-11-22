package alphasport.service;

import java.util.List;

/**
 * [REQ-07] Resultado de calificar a los jugadores de un partido: lo que devuelve
 * {@link CalculadoraCalificaciones#calcular} y lo que el Presentador convierte en JSON.
 *
 * No es un modelo: nunca se guarda. Se calcula de nuevo cada vez que se consulta, a partir
 * de los goles, tarjetas, XI y votos del partido, asi siempre coincide con lo registrado.
 * Al ser un record, sus datos no se pueden cambiar despues del calculo.
 *
 * @param votacion                 estado de la votacion del publico
 * @param cierraEn                 hora (ms) en que cierra la votacion; 0 si el partido no tiene hora de cierre
 * @param motivoSinCalificaciones  por que no hay calificaciones ("" si las hay)
 * @param local                    jugadores del equipo local (titulares primero, por dorsal)
 * @param visitante                jugadores del equipo visitante
 * @param idMvp                    id del MVP del partido (0 si no hay calificaciones)
 */
public record CalificacionesPartido(Votacion votacion, long cierraEn, String motivoSinCalificaciones,
        List<Calificacion> local, List<Calificacion> visitante, int idMvp) {

    /** Estado de la votacion del publico. */
    public enum Votacion {
        /** El partido no tiene resultado final: todavia no hay calificaciones. */
        SIN_RESULTADO,
        /** Dentro del tiempo programado: los visitantes pueden votar. */
        ABIERTA,
        /** Vencio el tiempo o la cerro el administrador: la nota final es definitiva. */
        CERRADA,
        /** Partido jugado antes de existir las calificaciones (sin hora de cierre): solo nota del sistema. */
        SIN_REGISTRO
    }

    /**
     * Calificacion de un jugador. {@code promedioPublico} vale 0 si nadie lo ha votado;
     * {@code miVoto} es el voto de ESTE dispositivo (0 = no ha votado).
     */
    public record Calificacion(int idJugador, int idEquipo, String nombre, int dorsal, String posicion, String foto,
            boolean portero, boolean titular, int goles, int asistencias, int amarillas, int rojas,
            int golesRecibidos, double notaSistema, List<String> desglose, int votos, double promedioPublico,
            double notaFinal, boolean mvp, int miVoto) {
    }

    /** true si hay al menos un jugador calificado. */
    public boolean disponible() {
        return !local.isEmpty() || !visitante.isEmpty();
    }
}