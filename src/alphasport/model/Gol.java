package alphasport.model;

import java.io.Serializable;

/**
 * Gol anotado durante un partido: quien lo marco, en que minuto y quien dio la
 * asistencia (opcional). Un autogol lo marca un jugador del rival y suma para
 * {@code idEquipo}. Guarda copia del nombre y del dorsal por si el jugador se
 * edita o se borra despues (igual que {@link Tarjeta} y {@link Cambio}).
 *
 * Los partidos guardados antes de existir esta clase simplemente no tienen goles
 * por jugador: el archivo .dat sigue siendo compatible.
 */
public class Gol implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Valor de {@link #getIdAsistente()} cuando el gol no tuvo asistencia. */
    public static final int SIN_ASISTENCIA = 0;

    private int idEquipo;          // equipo al que se le suma el gol
    private int minuto;            // 0 = sin minuto
    private boolean enContra;      // autogol: el autor es del equipo rival
    private int idAutor;
    private String nombreAutor;
    private int dorsalAutor;
    private int idAsistente = SIN_ASISTENCIA;
    private String nombreAsistente = "";
    private int dorsalAsistente;

    public Gol() {
    }

    public Gol(int idEquipo, int minuto, Jugador autor, boolean enContra, Jugador asistente) {
        this.idEquipo = idEquipo;
        this.minuto = minuto;
        this.enContra = enContra;
        this.idAutor = autor.getId();
        this.nombreAutor = autor.getNombre();
        this.dorsalAutor = autor.getDorsal();
        if (asistente != null && !enContra) {
            this.idAsistente = asistente.getId();
            this.nombreAsistente = asistente.getNombre();
            this.dorsalAsistente = asistente.getDorsal();
        }
    }

    public Gol copia() {
        Gol g = new Gol();
        g.idEquipo = idEquipo;
        g.minuto = minuto;
        g.enContra = enContra;
        g.idAutor = idAutor;
        g.nombreAutor = nombreAutor;
        g.dorsalAutor = dorsalAutor;
        g.idAsistente = idAsistente;
        g.nombreAsistente = nombreAsistente;
        g.dorsalAsistente = dorsalAsistente;
        return g;
    }

    public int getIdEquipo() {
        return idEquipo;
    }

    public int getMinuto() {
        return minuto;
    }

    public boolean isEnContra() {
        return enContra;
    }

    public int getIdAutor() {
        return idAutor;
    }

    public String getNombreAutor() {
        return nombreAutor;
    }

    public int getDorsalAutor() {
        return dorsalAutor;
    }

    public boolean tieneAsistencia() {
        return idAsistente != SIN_ASISTENCIA;
    }

    public int getIdAsistente() {
        return idAsistente;
    }

    public String getNombreAsistente() {
        return nombreAsistente == null ? "" : nombreAsistente;
    }

    public int getDorsalAsistente() {
        return dorsalAsistente;
    }

    @Override
    public String toString() {
        return (minuto > 0 ? minuto + "' " : "") + "gol de #" + dorsalAutor + " " + nombreAutor
                + (enContra ? " (en contra)" : "") + (tieneAsistencia() ? ", asistencia de " + nombreAsistente : "");
    }
}
