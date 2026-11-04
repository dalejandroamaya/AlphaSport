package alphasport.model;

/**
 * Formatos de campeonato que el usuario puede escoger al crear un torneo.
 */
public enum TipoFormato {

    LIGA("Liga",
            "Todos juegan contra todos. Gana el que sume más puntos al final (3 por ganar, 1 por empatar). "
            + "Se puede jugar a una vuelta o a ida y vuelta."),
    ELIMINACION_DIRECTA("Copa (eliminación directa)",
            "Partidos de llave: el que pierde queda eliminado. Se juegan octavos, cuartos, semifinal y final. "
            + "Si hay empate, se define por penales."),
    GRUPOS_Y_ELIMINATORIA("Tipo Mundial (grupos y eliminatorias)",
            "Primero se juega en grupos pequeños. Los mejores de cada grupo pasan a una llave de eliminación "
            + "hasta la final, como en el Mundial."),
    TORNEO_CORTO("Apertura y Clausura (con liguilla)",
            "Todos contra todos en la fase regular; los mejores de la tabla pasan a la liguilla o a cuadrangulares "
            + "para definir al campeón, como en la Liga BetPlay."),
    SUIZO("Tipo Champions (sistema suizo)",
            "Todos en una sola tabla, pero cada equipo juega solo algunos partidos contra rivales distintos. "
            + "Los primeros pasan directo a la llave y los del medio juegan un repechaje."),
    DOBLE_ELIMINACION("Doble oportunidad (doble eliminación)",
            "Nadie se va al primer tropiezo: quien pierde una vez pasa a la llave de perdedores y solo queda "
            + "eliminado si pierde dos veces.");

    private final String nombre;
    private final String descripcion;

    TipoFormato(String nombre, String descripcion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    @Override
    public String toString() {
        return nombre;
    }
}
