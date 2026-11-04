package alphasport.model;

/**
 * Formatos de campeonato que el usuario puede escoger al crear un torneo.
 */
public enum TipoFormato {

    LIGA("Todos contra todos (Liga / Round-Robin)",
            "Todos los equipos se enfrentan entre si. Gana el que sume mas puntos "
            + "(3 por victoria, 1 por empate). Puede jugarse a una sola vuelta o a "
            + "ida y vuelta (doble round-robin, como LaLiga o la Premier League)."),
    ELIMINACION_DIRECTA("Eliminacion directa (Copa / Knockout)",
            "El que pierde queda eliminado. Se arma una llave (dieciseisavos, octavos, "
            + "cuartos...). A partido unico (empate = penales) o a ida y vuelta "
            + "(avanza el mejor marcador global)."),
    GRUPOS_Y_ELIMINATORIA("Mixto: fase de grupos + eliminatorias",
            "Los equipos se dividen en grupos (mini ligas, normalmente de 4). Los mejores "
            + "de cada grupo pasan a una llave de eliminacion directa hasta la final "
            + "(como el Mundial o la Copa America)."),
    TORNEO_CORTO("Torneo corto (Apertura/Clausura) + Liguilla",
            "Fase regular todos contra todos a una vuelta. Los mejores de la tabla "
            + "clasifican a una liguilla de eliminacion directa o a cuadrangulares "
            + "semifinales (como la Liga BetPlay o la Liga MX)."),
    SUIZO("Sistema suizo (Fase de liga UEFA)",
            "Todos en una sola tabla, pero cada equipo juega solo un numero limitado de "
            + "partidos contra rivales distintos. Los primeros pasan directo a la llave "
            + "y los de la zona media juegan un repechaje (como la Champions League)."),
    DOBLE_ELIMINACION("Doble eliminacion (Winner / Loser bracket)",
            "Perder una vez manda al equipo a la llave de perdedores; solo queda fuera "
            + "al perder dos veces. El campeon de ganadores juega la gran final contra "
            + "el campeon de perdedores. Ideal para torneos amateur o relampago.");

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
