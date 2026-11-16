package alphasport.model;

import java.io.Serializable;

/**
 * Sustitucion hecha durante un partido: en el minuto indicado sale un jugador
 * y entra otro del mismo equipo. Guarda copia del nombre y del dorsal por si
 * el jugador se edita o se borra despues.
 */
public class Cambio implements Serializable {

    private static final long serialVersionUID = 1L;

    private int idEquipo;
    private int minuto;
    private int idSale;
    private String nombreSale;
    private int dorsalSale;
    private int idEntra;
    private String nombreEntra;
    private int dorsalEntra;

    public Cambio() {
    }

    public Cambio(int idEquipo, int minuto, Jugador sale, Jugador entra) {
        this.idEquipo = idEquipo;
        this.minuto = minuto;
        this.idSale = sale.getId();
        this.nombreSale = sale.getNombre();
        this.dorsalSale = sale.getDorsal();
        this.idEntra = entra.getId();
        this.nombreEntra = entra.getNombre();
        this.dorsalEntra = entra.getDorsal();
    }

    public Cambio copia() {
        Cambio c = new Cambio();
        c.idEquipo = idEquipo;
        c.minuto = minuto;
        c.idSale = idSale;
        c.nombreSale = nombreSale;
        c.dorsalSale = dorsalSale;
        c.idEntra = idEntra;
        c.nombreEntra = nombreEntra;
        c.dorsalEntra = dorsalEntra;
        return c;
    }

    public int getIdEquipo() {
        return idEquipo;
    }

    public int getMinuto() {
        return minuto;
    }

    public int getIdSale() {
        return idSale;
    }

    public String getNombreSale() {
        return nombreSale;
    }

    public int getDorsalSale() {
        return dorsalSale;
    }

    public int getIdEntra() {
        return idEntra;
    }

    public String getNombreEntra() {
        return nombreEntra;
    }

    public int getDorsalEntra() {
        return dorsalEntra;
    }

    @Override
    public String toString() {
        return minuto + "' sale #" + dorsalSale + " " + nombreSale + ", entra #" + dorsalEntra + " " + nombreEntra;
    }
}
