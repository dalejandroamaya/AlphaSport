package alphasport.persistence.postgres;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ayudas para los DAO: consultas con parametros (nunca se arma SQL pegando textos del usuario) y conversiones.
 */
final class Sql {

    /** Lee una fila. */
    interface Lector<T> {
        T leer(ResultSet rs) throws SQLException;
    }

    private Sql() {
    }

    private static PreparedStatement preparar(Connection con, String sql, Object... parametros) throws SQLException {
        PreparedStatement ps = con.prepareStatement(sql);
        try {
            poner(ps, parametros);
        } catch (SQLException e) {
            ps.close();
            throw e;
        }
        return ps;
    }

    /** Pone los parametros en orden; null se envia como NULL. */
    static void poner(PreparedStatement ps, Object... parametros) throws SQLException {
        for (int i = 0; i < parametros.length; i++) {
            if (parametros[i] == null) {
                ps.setNull(i + 1, Types.NULL);
            } else {
                ps.setObject(i + 1, parametros[i]);
            }
        }
    }

    /** Ejecuta un INSERT, UPDATE o DELETE y devuelve cuantas filas cambio. */
    static int cambiar(Connection con, String sql, Object... parametros) throws SQLException {
        try (PreparedStatement ps = preparar(con, sql, parametros)) {
            return ps.executeUpdate();
        }
    }

    static <T> List<T> listar(Connection con, String sql, Lector<T> lector, Object... parametros) throws SQLException {
        try (PreparedStatement ps = preparar(con, sql, parametros); ResultSet rs = ps.executeQuery()) {
            List<T> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(lector.leer(rs));
            }
            return lista;
        }
    }

    /** La primera fila, o null si no hay ninguna. */
    static <T> T uno(Connection con, String sql, Lector<T> lector, Object... parametros) throws SQLException {
        List<T> lista = listar(con, sql, lector, parametros);
        return lista.isEmpty() ? null : lista.get(0);
    }

    /** INSERT ... RETURNING id. */
    static int insertar(Connection con, String sql, Object... parametros) throws SQLException {
        return uno(con, sql, rs -> rs.getInt(1), parametros);
    }

    /** Mapa numero -> id con las dos primeras columnas. */
    static Map<Integer, Integer> mapa(Connection con, String sql, Object... parametros) throws SQLException {
        Map<Integer, Integer> m = new HashMap<>();
        listar(con, sql, rs -> m.put(rs.getInt(1), rs.getInt(2)), parametros);
        return m;
    }

    /* ---------------------------------------------------------------- conversiones */

    /** Texto vacio -> NULL. */
    static String nulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto;
    }

    /** NULL -> texto vacio. */
    static String texto(String valor) {
        return valor == null ? "" : valor;
    }

    /** Entero que puede ser NULL. */
    static Integer entero(ResultSet rs, String columna) throws SQLException {
        int v = rs.getInt(columna);
        return rs.wasNull() ? null : v;
    }

    /** Milisegundos -> fecha y hora; 0 -> NULL. */
    static Timestamp momento(long ms) {
        return ms <= 0 ? null : new Timestamp(ms);
    }

    /** Fecha y hora -> milisegundos; NULL -> 0. */
    static long ms(ResultSet rs, String columna) throws SQLException {
        Timestamp t = rs.getTimestamp(columna);
        return t == null ? 0 : t.getTime();
    }

    /** «2026-11-22» -> fecha; cualquier otra cosa («Por definir») -> NULL. */
    static Date fecha(String texto) {
        try {
            return texto == null ? null : Date.valueOf(java.time.LocalDate.parse(texto.trim()));
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    /** «18:00» -> hora; vacia o mal escrita -> NULL. */
    static java.time.LocalTime hora(String texto) {
        if (texto == null || !texto.trim().matches("\\d{1,2}:\\d{2}")) {
            return null;
        }
        String t = texto.trim();
        try {
            return java.time.LocalTime.parse(t.length() == 4 ? "0" + t : t);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }
}
