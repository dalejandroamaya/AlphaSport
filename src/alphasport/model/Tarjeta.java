package alphasport.model;

import java.io.Serializable;

/**
 * Tarjeta mostrada a un jugador en un partido. Si es roja, guarda cuantas
 * fechas (partidos de su equipo) queda suspendido el jugador.
 */
public class Tarjeta implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String AMARILLA = "Amarilla";
    public static final String ROJA = "Roja";

    private int idEquipo;
    private int idJugador;
    private String nombreJugador; // copia por si el jugador se borra despues
    private int dorsal;
    private String tipo;
    private int fechasSuspension;

    public Tarjeta() {
    }

    public Tarjeta(int idEquipo, Jugador jugador, String tipo, int fechasSuspension) {
        this.idEquipo = idEquipo;
        this.idJugador = jugador.getId();
        this.nombreJugador = jugador.getNombre();
        this.dorsal = jugador.getDorsal();
        this.tipo = tipo;
        this.fechasSuspension = ROJA.equals(tipo) ? fechasSuspension : 0;
    }

    public boolean isRoja() {
        return ROJA.equals(tipo);
    }

    public Tarjeta copia() {
        Tarjeta t = new Tarjeta();
        t.idEquipo = idEquipo;
        t.idJugador = idJugador;
        t.nombreJugador = nombreJugador;
        t.dorsal = dorsal;
        t.tipo = tipo;
        t.fechasSuspension = fechasSuspension;
        return t;
    }

    public int getIdEquipo() {
        return idEquipo;
    }

    public int getIdJugador() {
        return idJugador;
    }

    public String getNombreJugador() {
        return nombreJugador;
    }

    public int getDorsal() {
        return dorsal;
    }

    public String getTipo() {
        return tipo;
    }

    public int getFechasSuspension() {
        return fechasSuspension;
    }

    @Override
    public String toString() {
        return tipo + " - #" + dorsal + " " + nombreJugador
                + (isRoja() ? " (" + fechasSuspension + " fecha" + (fechasSuspension == 1 ? "" : "s") + " de suspension)" : "");
    }
}
