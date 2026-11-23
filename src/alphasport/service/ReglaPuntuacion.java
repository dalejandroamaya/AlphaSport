package alphasport.service;

import java.util.List;

/**
 * Convierte lo que hizo un jugador en un partido en puntos. Cada conjunto de reglas es una implementacion:
 * la nota del partido ({@link PuntuacionNotaPartido}) y, mas adelante, los puntos del modo Fantasy, sin tocar
 * {@link CalculadoraCalificaciones}.
 */
public interface ReglaPuntuacion {

    /**
     * Puntos del jugador. En {@code desglose} se agrega la explicacion linea por linea
     * (por ejemplo «Gol ×2: +2.0»), la que ve el usuario.
     */
    double puntuar(DesempenoJugador desempeno, List<String> desglose);
}
