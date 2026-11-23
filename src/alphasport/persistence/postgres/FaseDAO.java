package alphasport.persistence.postgres;

import alphasport.model.Fase;
import alphasport.model.TipoFase;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tablas fases, fase_participantes, fase_grupos y fase_cuadro.
 */
final class FaseDAO {

    /** Indice de la fase -> id. */
    Map<Integer, Integer> ids(Connection con, int idTorneo) throws SQLException {
        return Sql.mapa(con, "SELECT indice, id FROM fases WHERE torneo_id = ?", idTorneo);
    }

    /** Borra las fases desde ese indice (las que se deshicieron). */
    void eliminarDesde(Connection con, int idTorneo, int indice) throws SQLException {
        Sql.cambiar(con, "DELETE FROM fases WHERE torneo_id = ? AND indice >= ?", idTorneo, indice);
    }

    void guardar(Connection con, int idTorneo, int indice, Fase f, Map<Integer, Integer> equipos) throws SQLException {
        int id = Sql.insertar(con, """
                INSERT INTO fases (torneo_id, indice, nombre, tipo, paso_inicio, ida_y_vuelta, final_unica, ventaja_deportiva,
                                   max_rondas, rondas_suizo, con_reinicio, es_divisiones, cantidad_grupos)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (torneo_id, indice) DO UPDATE SET nombre = EXCLUDED.nombre, tipo = EXCLUDED.tipo, paso_inicio = EXCLUDED.paso_inicio,
                    ida_y_vuelta = EXCLUDED.ida_y_vuelta, final_unica = EXCLUDED.final_unica, ventaja_deportiva = EXCLUDED.ventaja_deportiva,
                    max_rondas = EXCLUDED.max_rondas, rondas_suizo = EXCLUDED.rondas_suizo, con_reinicio = EXCLUDED.con_reinicio,
                    es_divisiones = EXCLUDED.es_divisiones, cantidad_grupos = EXCLUDED.cantidad_grupos
                RETURNING id""",
                idTorneo, indice, f.getNombre(), f.getTipo().name(), f.getPasoInicio(), f.isIdaYVuelta(), f.isFinalUnica(), f.isVentajaDeportiva(),
                f.getMaxRondas(), f.getRondasSuizo(), f.isConReinicio(), f.isDivisiones(), f.getGrupos().size());
        Sql.cambiar(con, "DELETE FROM fase_participantes WHERE fase_id = ?", id);
        Sql.cambiar(con, "DELETE FROM fase_grupos WHERE fase_id = ?", id);
        Sql.cambiar(con, "DELETE FROM fase_cuadro WHERE fase_id = ?", id);
        int orden = 0;
        for (int equipo : f.getParticipantes()) {
            Sql.cambiar(con, "INSERT INTO fase_participantes (fase_id, orden, equipo_id) VALUES (?, ?, ?)", id, orden++, equipos.get(equipo));
        }
        for (int g = 0; g < f.getGrupos().size(); g++) {
            orden = 0;
            for (int equipo : f.getGrupos().get(g)) {
                Sql.cambiar(con, "INSERT INTO fase_grupos (fase_id, grupo, orden, equipo_id) VALUES (?, ?, ?, ?)", id, g, orden++, equipos.get(equipo));
            }
        }
        orden = 0;
        for (int equipo : f.getCuadro()) { // LIBRE (pasa directo) se guarda sin equipo
            Sql.cambiar(con, "INSERT INTO fase_cuadro (fase_id, orden, equipo_id) VALUES (?, ?, ?)", id, orden++,
                    equipo == Fase.LIBRE ? null : equipos.get(equipo));
        }
    }

    List<Fase> listar(Connection con, int idTorneo) throws SQLException {
        Map<Integer, Fase> porId = new HashMap<>();
        List<Fase> fases = Sql.listar(con, "SELECT * FROM fases WHERE torneo_id = ? ORDER BY indice", rs -> {
            Fase f = new Fase(rs.getString("nombre"), TipoFase.valueOf(rs.getString("tipo")));
            f.setPasoInicio(rs.getInt("paso_inicio"));
            f.setIdaYVuelta(rs.getBoolean("ida_y_vuelta"));
            f.setFinalUnica(rs.getBoolean("final_unica"));
            f.setVentajaDeportiva(rs.getBoolean("ventaja_deportiva"));
            f.setMaxRondas(rs.getInt("max_rondas"));
            f.setRondasSuizo(rs.getInt("rondas_suizo"));
            f.setConReinicio(rs.getBoolean("con_reinicio"));
            f.setDivisiones(rs.getBoolean("es_divisiones"));
            for (int g = rs.getInt("cantidad_grupos"); g > 0; g--) {
                f.getGrupos().add(new ArrayList<>());
            }
            porId.put(rs.getInt("id"), f);
            return f;
        }, idTorneo);
        Sql.listar(con, """
                SELECT x.fase_id, e.numero FROM fase_participantes x JOIN fases f ON f.id = x.fase_id JOIN equipos e ON e.id = x.equipo_id
                 WHERE f.torneo_id = ? ORDER BY x.fase_id, x.orden""", rs -> porId.get(rs.getInt(1)).getParticipantes().add(rs.getInt(2)), idTorneo);
        Sql.listar(con, """
                SELECT x.fase_id, x.grupo, e.numero FROM fase_grupos x JOIN fases f ON f.id = x.fase_id JOIN equipos e ON e.id = x.equipo_id
                 WHERE f.torneo_id = ? ORDER BY x.fase_id, x.grupo, x.orden""",
                rs -> porId.get(rs.getInt(1)).getGrupos().get(rs.getInt(2)).add(rs.getInt(3)), idTorneo);
        Sql.listar(con, """
                SELECT x.fase_id, COALESCE(e.numero, ?) FROM fase_cuadro x JOIN fases f ON f.id = x.fase_id LEFT JOIN equipos e ON e.id = x.equipo_id
                 WHERE f.torneo_id = ? ORDER BY x.fase_id, x.orden""", rs -> porId.get(rs.getInt(1)).getCuadro().add(rs.getInt(2)), Fase.LIBRE, idTorneo);
        return fases;
    }
}
