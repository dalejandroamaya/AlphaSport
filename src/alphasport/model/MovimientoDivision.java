package alphasport.model;

import java.io.Serializable;
import java.util.List;

/**
 * Ascenso o descenso de un equipo al pasar de temporada en una liga con divisiones. Lo guarda la temporada nueva:
 * es la constancia de por que el equipo esta en su division (de donde venia, a donde paso y en que puesto termino).
 *
 * @param idEquipo            id del equipo en la temporada nueva
 * @param divisionAnterior    0 = Liga A, 1 = Liga B...
 * @param puesto              puesto con el que termino en su division la temporada anterior
 * @param temporadaAnterior   nombre de la temporada que se cerro
 */
public record MovimientoDivision(int idEquipo, String nombreEquipo, int divisionAnterior, int divisionNueva, int puesto,
        String temporadaAnterior) implements Serializable {

    private static final long serialVersionUID = 1L;

    /** true si subio de division (un numero de division menor es una division mas alta). */
    public boolean isAscenso() {
        return divisionNueva < divisionAnterior;
    }

    /** El mismo movimiento con el id que tiene el equipo en otro campeonato (al copiar los equipos). */
    public MovimientoDivision conEquipo(int otroId) {
        return new MovimientoDivision(otroId, nombreEquipo, divisionAnterior, divisionNueva, puesto, temporadaAnterior);
    }

    /** «Suben a la Liga A: X. Bajan a la Liga B: Y.» por cada par de divisiones; "" si no hay movimientos. */
    public static String resumen(List<MovimientoDivision> movimientos) {
        StringBuilder texto = new StringBuilder();
        int ultima = movimientos.stream().mapToInt(m -> Math.max(m.divisionAnterior(), m.divisionNueva())).max().orElse(0);
        for (int d = 0; d < ultima; d++) {
            int arriba = d;
            List<String> suben = movimientos.stream().filter(m -> m.isAscenso() && m.divisionNueva() == arriba)
                    .map(MovimientoDivision::nombreEquipo).toList();
            List<String> bajan = movimientos.stream().filter(m -> !m.isAscenso() && m.divisionAnterior() == arriba)
                    .map(MovimientoDivision::nombreEquipo).toList();
            if (suben.isEmpty() && bajan.isEmpty()) {
                continue;
            }
            texto.append(texto.length() == 0 ? "" : " ")
                    .append("Suben a la ").append(ConfiguracionTorneo.nombreDivision(d)).append(": ").append(String.join(", ", suben))
                    .append(". Bajan a la ").append(ConfiguracionTorneo.nombreDivision(d + 1)).append(": ").append(String.join(", ", bajan)).append(".");
        }
        return texto.toString();
    }
}
