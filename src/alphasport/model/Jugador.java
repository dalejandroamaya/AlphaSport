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

    /** Linea de una posicion que no esta en el catalogo: va al final de la plantilla. */
    public static final int SIN_LINEA = GRUPOS_POSICIONES.length;

    /**
     * Orden natural de una plantilla: Porteria, Defensa, Mediocampo y Ataque (el orden de GRUPOS_POSICIONES);
     * dentro de cada linea, por dorsal y luego por nombre.
     */
    public static final java.util.Comparator<Jugador> POR_LINEA = java.util.Comparator.comparingInt(Jugador::getLinea)
            .thenComparingInt(Jugador::getDorsal)
            .thenComparing(j -> j.getNombre() == null ? "" : j.getNombre(), String.CASE_INSENSITIVE_ORDER);

    /**
     * Linea de una posicion segun GRUPOS_POSICIONES: 0 Porteria, 1 Defensa, 2 Mediocampo, 3 Ataque; SIN_LINEA si
     * no esta en el catalogo (sin distinguir mayusculas ni espacios alrededor).
     */
    public static int lineaDe(String posicion) {
        String buscada = posicion == null ? "" : posicion.trim();
        for (int g = 0; g < GRUPOS_POSICIONES.length; g++) {
            for (int k = 1; k < GRUPOS_POSICIONES[g].length; k++) {
                if (GRUPOS_POSICIONES[g][k].equalsIgnoreCase(buscada)) {
                    return g;
                }
            }
        }
        return SIN_LINEA;
    }

    private int id; // no cambia aunque se edite el dorsal (lo usan las tarjetas)
    private int dorsal;
    private String posicion;
    private String foto; // nombre de la foto tipo cedula (datos/imagenes); null o "" = sin foto (se ve «NN»)
    // La misma persona en todas las temporadas y copias del historial. Es un UUID para que un respaldo de otra
    // base nunca se confunda con alguien de esta.
    private String codigoPersona = nuevoCodigo();

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

    /** Linea de su posicion (ver {@link #lineaDe(String)}). */
    public int getLinea() {
        return lineaDe(posicion);
    }

    /** Foto tipo cedula del jugador ("" si no tiene). */
    public String getFoto() {
        return foto == null ? "" : foto;
    }

    public void setFoto(String foto) {
        this.foto = foto == null ? "" : foto;
    }

    /** Codigo de la persona (UUID): no cambia aunque el jugador pase a otra temporada o se edite. */
    public String getCodigoPersona() {
        return codigoPersona;
    }

    /** null o vacio = una persona nueva. */
    public void setCodigoPersona(String codigoPersona) {
        this.codigoPersona = codigoPersona == null || codigoPersona.isBlank() ? nuevoCodigo() : codigoPersona;
    }

    public static String nuevoCodigo() {
        return java.util.UUID.randomUUID().toString();
    }

    /** Los archivos anteriores no traen el codigo: cada jugador queda como una persona nueva. */
    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (codigoPersona == null || codigoPersona.isBlank()) {
            codigoPersona = nuevoCodigo();
        }
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
