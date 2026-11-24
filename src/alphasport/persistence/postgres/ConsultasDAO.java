package alphasport.persistence.postgres;

import alphasport.persistence.PersistenciaException;
import java.util.List;

/**
 * Consultas de lectura sobre las vistas de schema.sql. Los equipos y partidos se piden por el numero que usan
 * dentro del campeonato en curso.
 */
public class ConsultasDAO {

    /** Fila de v_tabla_posiciones (puntos, diferencia y goles; sin los demas criterios de desempate). */
    public record Posicion(String grupo, int equipo, String nombre, int pj, int pg, int pe, int pp, int gf, int gc, int dg, int pts) {
    }

    /** Titular del ultimo partido del equipo. */
    public record Titular(int partido, int jugador, int dorsal, String nombre, String posicion, String formacion) {
    }

    /** Partido que el publico puede votar ahora. */
    public record VotacionActiva(int partido, String local, String visitante, long cierraEn, int votos) {
    }

    /** Nota de un jugador en un partido. */
    public record Nota(int jugador, double sistema, Double publico, int votos, double notaFinal, boolean mvp) {
    }

    /** El campeonato en curso con ese codigo (puede haber varios en curso). */
    private static final String DEL_CAMPEONATO = "torneo_id = (SELECT id FROM torneos WHERE codigo = ? AND situacion = 'ACTUAL')";

    private final ConexionDB conexion;

    public ConsultasDAO(ConexionDB conexion) {
        this.conexion = conexion;
    }

    public List<Posicion> tablaPosiciones(String campeonato, int fase) throws PersistenciaException {
        return conexion.ejecutar(con -> Sql.listar(con, "SELECT * FROM v_tabla_posiciones WHERE " + DEL_CAMPEONATO
                + " AND fase = ? ORDER BY grupo, pts DESC, dg DESC, gf DESC, equipo", rs -> new Posicion(rs.getString("grupo"), rs.getInt("equipo_numero"),
                rs.getString("equipo"), rs.getInt("pj"), rs.getInt("pg"), rs.getInt("pe"), rs.getInt("pp"), rs.getInt("gf"), rs.getInt("gc"),
                rs.getInt("dg"), rs.getInt("pts")), campeonato, fase));
    }

    /** XI del ultimo partido jugado del equipo, en el orden en que se eligio (vacia si no hay). */
    public List<Titular> ultimaAlineacion(String campeonato, int equipo) throws PersistenciaException {
        return conexion.ejecutar(con -> Sql.listar(con, """
                SELECT u.* FROM v_ultima_alineacion u
                 WHERE u.equipo_id = (SELECT e.id FROM equipos e JOIN torneos t ON t.id = e.torneo_id
                                       WHERE t.codigo = ? AND t.situacion = 'ACTUAL' AND e.numero = ?)
                 ORDER BY u.orden""", rs -> new Titular(rs.getInt("partido_numero"), rs.getInt("jugador_numero"), rs.getInt("dorsal"),
                rs.getString("nombre"), rs.getString("posicion"), Sql.texto(rs.getString("formacion"))), campeonato, equipo));
    }

    /** Primero las que cierran antes. */
    public List<VotacionActiva> votacionesActivas(String campeonato) throws PersistenciaException {
        return conexion.ejecutar(con -> Sql.listar(con, "SELECT * FROM v_votaciones_activas WHERE " + DEL_CAMPEONATO + " ORDER BY cierra_en, partido_numero",
                rs -> new VotacionActiva(rs.getInt("partido_numero"), rs.getString("local"), rs.getString("visitante"), Sql.ms(rs, "cierra_en"), rs.getInt("votos")),
                campeonato));
    }

    public List<Nota> notas(String campeonato, int partido) throws PersistenciaException {
        return conexion.ejecutar(con -> Sql.listar(con, "SELECT * FROM v_calificaciones WHERE " + DEL_CAMPEONATO + " AND partido_numero = ? ORDER BY nota_final DESC, dorsal",
                rs -> new Nota(rs.getInt("jugador_numero"), rs.getDouble("nota_sistema"),
                        rs.getBigDecimal("promedio_publico") == null ? null : rs.getDouble("promedio_publico"),
                        rs.getInt("votos"), rs.getDouble("nota_final"), rs.getBoolean("es_mvp")), campeonato, partido));
    }
}
