package alphasport.model;

import java.io.Serializable;

/**
 * Voto de un visitante a un jugador en un partido: nota entera del 1 al 10.
 * El dispositivo es un identificador anonimo (no un dato personal) que evita votar
 * dos veces al mismo jugador en el mismo partido desde el mismo navegador o equipo.
 */
public class VotoJugador implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int MINIMO = 1;
    public static final int MAXIMO = 10;

    private final int idJugador;
    private final int valor;
    private final String dispositivo;
    private final long fecha; // milisegundos desde 1970 (marca de tiempo del voto)

    public VotoJugador(int idJugador, int valor, String dispositivo, long fecha) {
        this.idJugador = idJugador;
        this.valor = valor;
        this.dispositivo = dispositivo;
        this.fecha = fecha;
    }

    public int getIdJugador() {
        return idJugador;
    }

    public int getValor() {
        return valor;
    }

    public String getDispositivo() {
        return dispositivo;
    }

    public long getFecha() {
        return fecha;
    }

    /** Copia independiente (la usa Partido.copiarDatosDe al editar el partido). */
    public VotoJugador copia() {
        return new VotoJugador(idJugador, valor, dispositivo, fecha);
    }
}