package alphasport.persistence.postgres;

import alphasport.persistence.NotaJugador;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Tabla calificaciones_partido: la ultima nota calculada de cada jugador.
 */
final class CalificacionDAO {

    /** Reemplaza las notas de un partido (lista vacia = deja de tener notas). */
    void reemplazar(Connection con, int idTorneo, int numeroPartido, List<NotaJugador> notas) throws SQLException {
        Integer idPartido = Sql.uno(con, "SELECT id FROM partidos WHERE torneo_id = ? AND numero = ?", rs -> rs.getInt(1), idTorneo, numeroPartido);
        if (idPartido == null) {
            return;
        }
        Sql.cambiar(con, "DELETE FROM calificaciones_partido WHERE partido_id = ?", idPartido);
        try (PreparedStatement ps = con.prepareStatement("""
                INSERT INTO calificaciones_partido (partido_id, jugador_id, nota_sistema, votos, promedio_publico, nota_final, es_mvp)
                SELECT ?, j.id, ?, ?, ?, ?, ? FROM jugadores j WHERE j.torneo_id = ? AND j.numero = ?""")) {
            for (NotaJugador n : notas) {
                ps.setInt(1, idPartido);
                ps.setBigDecimal(2, BigDecimal.valueOf(n.notaSistema()));
                ps.setInt(3, n.votos());
                ps.setBigDecimal(4, n.votos() == 0 ? null : BigDecimal.valueOf(n.promedioPublico()));
                ps.setBigDecimal(5, BigDecimal.valueOf(n.notaFinal()));
                ps.setBoolean(6, n.mvp());
                ps.setInt(7, idTorneo);
                ps.setInt(8, n.idJugador());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
