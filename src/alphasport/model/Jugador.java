package alphasport.model;

public class Jugador extends Persona {

    private static final long serialVersionUID = 1L;

    public static final String[] POSICIONES = {"Portero", "Defensa", "Mediocampista", "Delantero"};

    private int id; // no cambia aunque se edite el dorsal (lo usan las tarjetas)
    private int dorsal;
    private String posicion;

    public Jugador() {
    }

    public Jugador(String nombre, String documento, int dorsal, String posicion) {
        super(nombre, documento);
        this.dorsal = dorsal;
        this.posicion = posicion;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getDorsal() {
        return dorsal;
    }

    public void setDorsal(int dorsal) {
        this.dorsal = dorsal;
    }

    public String getPosicion() {
        return posicion;
    }

    public void setPosicion(String posicion) {
        this.posicion = posicion;
    }

    @Override
    public String getRol() {
        return "Jugador";
    }

    @Override
    public String toString() {
        return "#" + dorsal + " " + getNombre() + " (" + posicion + ")";
    }
}
