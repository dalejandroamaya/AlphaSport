package alphasport.model;

public class Jugador extends Persona {

    private static final long serialVersionUID = 1L;

    /** Posiciones especificas agrupadas por linea (las que se usan en todo el futbol). */
    public static final String[][] GRUPOS_POSICIONES = {
        {"Portería", "Portero"},
        {"Defensa", "Defensa central", "Líbero", "Lateral derecho", "Lateral izquierdo", "Carrilero derecho", "Carrilero izquierdo"},
        {"Mediocampo", "Mediocampista defensivo", "Mediocampista central", "Interior derecho", "Interior izquierdo",
            "Volante derecho", "Volante izquierdo", "Mediocampista ofensivo"},
        {"Ataque", "Mediapunta", "Extremo derecho", "Extremo izquierdo", "Segundo delantero", "Delantero centro"}
    };

    public static final String[] POSICIONES = java.util.Arrays.stream(GRUPOS_POSICIONES)
            .flatMap(g -> java.util.Arrays.stream(g).skip(1)).toArray(String[]::new);

    private int id; // no cambia aunque se edite el dorsal (lo usan las tarjetas)
    private int dorsal;
    private String posicion;
    private String foto; // nombre de la foto tipo cedula (datos/imagenes); null o "" = sin foto (se ve «NN»)

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

    /** Foto tipo cedula del jugador ("" si no tiene). */
    public String getFoto() {
        return foto == null ? "" : foto;
    }

    public void setFoto(String foto) {
        this.foto = foto == null ? "" : foto;
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
