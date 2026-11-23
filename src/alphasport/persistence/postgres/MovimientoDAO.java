package alphasport.persistence.postgres;

import alphasport.model.MovimientoDivision;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Tabla movimientos_division: los ascensos y descensos con los que empezo cada temporada. Apunta al equipo por su
 * id de la base, asi la constancia sigue al equipo aunque cambie de nombre.
 */
final class MovimientoDAO {

    /** Reemplaza los movimientos del torneo (son pocos y casi nunca cambian). */
    void guardar(Connection con, int idTorneo, List<MovimientoDivision> movimientos, Map<Integer, Integer> idsEquipos) throws SQLException {
        Sql.cambiar(con, "DELETE FROM movimientos_division WHERE torneo_id = ?", idTorneo);
        for (MovimientoDivision m : movimientos) {
            Integer idEquipo = idsEquipos.get(m.idEquipo());
            if (idEquipo != null) {
                Sql.cambiar(con, """
                        INSERT INTO movimientos_division (torneo_id, equipo_id, division_anterior, division_nueva, puesto, temporada_anterior)
                        VALUES (?, ?, ?, ?, ?, ?)""",
                        idTorneo, idEquipo, m.divisionAnterior(), m.divisionNueva(), m.puesto(), m.temporadaAnterior());
            }
        }
    }

    List<MovimientoDivision> listar(Connection con, int idTorneo) throws SQLException {
        return Sql.listar(con, """
                SELECT e.numero, e.nombre, m.division_anterior, m.division_nueva, m.puesto, m.temporada_anterior
                  FROM movimientos_division m JOIN equipos e ON e.id = m.equipo_id
                 WHERE m.torneo_id = ? ORDER BY m.division_nueva, m.id""",
                rs -> new MovimientoDivision(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getString(6)), idTorneo);
    }
}
