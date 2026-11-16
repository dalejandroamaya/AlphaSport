package alphasport.model;

/**
 * Criterios para ordenar a los equipos que quedan empatados en puntos.
 * El orden en que se aplican se configura en cada campeonato.
 */
public enum CriterioDesempate {
    DIFERENCIA_GOLES("Diferencia de goles"),
    GOLES_A_FAVOR("Mayor numero de goles a favor"),
    ENFRENTAMIENTO_DIRECTO("Enfrentamiento directo entre los empatados"),
    PARTIDOS_GANADOS("Mayor numero de partidos ganados"),
    FAIR_PLAY("Juego limpio (amarilla = 1 pt, roja = 3 pts; gana el que tenga menos)"),
    SORTEO("Sorteo");

    private final String descripcion;

    CriterioDesempate(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    @Override
    public String toString() {
        return descripcion;
    }
}
