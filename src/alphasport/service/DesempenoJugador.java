package alphasport.service;

import alphasport.model.Partido;

/**
 * Lo que hizo un jugador en un partido, ya contado: es lo que puntua cualquier {@link ReglaPuntuacion}.
 *
 * @param golesRecibidos goles del rival (se usa para el portero)
 */
public record DesempenoJugador(boolean portero, boolean titular, int goles, int asistencias, int amarillas, int rojas,
        int golesRecibidos) {

    /** Cuenta los goles, asistencias y tarjetas del jugador en el partido. */
    public static DesempenoJugador de(Partido p, int idEquipo, int idJugador, boolean portero) {
        int goles = (int) p.getGoles().stream().filter(g -> !g.isEnContra() && g.getIdAutor() == idJugador).count();
        int asistencias = (int) p.getGoles().stream().filter(g -> g.getIdAsistente() == idJugador).count();
        int amarillas = (int) p.getTarjetas().stream().filter(t -> t.getIdJugador() == idJugador && !t.isRoja()).count();
        int rojas = (int) p.getTarjetas().stream().filter(t -> t.getIdJugador() == idJugador && t.isRoja()).count();
        return new DesempenoJugador(portero, p.getTitularesDe(idEquipo).contains(idJugador), goles, asistencias, amarillas, rojas,
                p.getGolesDe(p.getRival(idEquipo)));
    }
}
