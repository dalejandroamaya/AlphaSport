package alphasport.model;

import java.util.Arrays;

/**
 * Formaciones tacticas que el entrenador puede elegir para un partido.
 * Cada formacion son las lineas de jugadores de campo, de atras hacia adelante
 * (el portero no se cuenta): 4-4-2 = 4 defensas, 4 mediocampistas, 2 delanteros.
 */
public enum Formacion {
    F_4_4_2("4-4-2"),
    F_4_3_3("4-3-3"),
    F_4_2_3_1("4-2-3-1"),
    F_4_5_1("4-5-1"),
    F_4_1_4_1("4-1-4-1"),
    F_3_5_2("3-5-2"),
    F_3_4_3("3-4-3"),
    F_5_3_2("5-3-2"),
    F_5_4_1("5-4-1");

   
    public static final Formacion POR_DEFECTO = F_4_4_2;

    private final String texto;
    private final int[] lineas;

    Formacion(String texto) {
        this.texto = texto;
        this.lineas = Arrays.stream(texto.split("-")).mapToInt(Integer::parseInt).toArray();
    }

    public String getTexto() {
        return texto;
    }

    
    public int[] getLineas() {
        return lineas.clone();
    }

   
    public static Formacion desdeTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        for (Formacion f : values()) {
            if (f.texto.equals(texto.trim())) {
                return f;
            }
        }
        return null;
    }
}
