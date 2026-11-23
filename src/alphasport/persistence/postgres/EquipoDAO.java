package alphasport.persistence.postgres;

import alphasport.model.DirectorTecnico;
import alphasport.model.Equipo;
import alphasport.model.Jugador;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tablas equipos, directores_tecnicos y jugadores.
 */
final class EquipoDAO {

    /** Numero del equipo -> id. */
    Map<Integer, Integer> ids(Connection con, int idTorneo) throws SQLException {
        return Sql.mapa(con, "SELECT numero, id FROM equipos WHERE torneo_id = ?", idTorneo);
    }

    /** Numero del jugador (activo o no) -> id. */
    Map<Integer, Integer> idsJugadores(Connection con, int idTorneo) throws SQLException {
        return Sql.mapa(con, "SELECT numero, id FROM jugadores WHERE torneo_id = ?", idTorneo);
    }

    void eliminar(Connection con, int idEquipo) throws SQLException {
        Sql.cambiar(con, "DELETE FROM equipos WHERE id = ?", idEquipo);
    }

    /** Crea o actualiza el equipo con su tecnico y su plantilla. */
    void guardar(Connection con, int idTorneo, Equipo e, int orden, int idDivision) throws SQLException {
        int id = Sql.insertar(con, """
                INSERT INTO equipos (torneo_id, numero, nombre, estadio, escudo_ruta, division_id, retirado, orden)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (torneo_id, numero) DO UPDATE SET nombre = EXCLUDED.nombre, estadio = EXCLUDED.estadio,
                    escudo_ruta = EXCLUDED.escudo_ruta, division_id = EXCLUDED.division_id, retirado = EXCLUDED.retirado, orden = EXCLUDED.orden
                RETURNING id""",
                idTorneo, e.getId(), e.getNombreEquipo(), Sql.texto(e.getEstadio()), Sql.nulo(e.getEscudo()), idDivision, e.isRetirado(), orden);
        guardarTecnico(con, id, e.getDirectorTecnico());
        // Quien ya no esta en la plantilla queda inactivo: sus goles, tarjetas y votos lo siguen usando.
        Sql.cambiar(con, "UPDATE jugadores SET activo = FALSE WHERE equipo_id = ? AND activo", id);
        int posicion = 0;
        for (Jugador j : e.getJugadores()) {
            guardarJugador(con, idTorneo, id, j, posicion++);
        }
    }

    private void guardarTecnico(Connection con, int idEquipo, DirectorTecnico dt) throws SQLException {
        if (dt == null) {
            Sql.cambiar(con, "DELETE FROM directores_tecnicos WHERE equipo_id = ?", idEquipo);
            return;
        }
        Sql.cambiar(con, """
                INSERT INTO directores_tecnicos (equipo_id, nombre, documento, nacionalidad) VALUES (?, ?, ?, ?)
                ON CONFLICT (equipo_id) DO UPDATE SET nombre = EXCLUDED.nombre, documento = EXCLUDED.documento, nacionalidad = EXCLUDED.nacionalidad""",
                idEquipo, Sql.texto(dt.getNombre()), Sql.texto(dt.getDocumento()), Sql.texto(dt.getNacionalidad()));
    }

    /** es_capitan no se toca: todavia no se edita en el programa. */
    private void guardarJugador(Connection con, int idTorneo, int idEquipo, Jugador j, int orden) throws SQLException {
        Sql.cambiar(con, """
                INSERT INTO jugadores (torneo_id, equipo_id, numero, nombre, documento, dorsal, posicion, foto_ruta, activo, orden)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?)
                ON CONFLICT (torneo_id, numero) DO UPDATE SET equipo_id = EXCLUDED.equipo_id, nombre = EXCLUDED.nombre,
                    documento = EXCLUDED.documento, dorsal = EXCLUDED.dorsal, posicion = EXCLUDED.posicion,
                    foto_ruta = EXCLUDED.foto_ruta, activo = TRUE, orden = EXCLUDED.orden""",
                idTorneo, idEquipo, j.getId(), Sql.texto(j.getNombre()), Sql.texto(j.getDocumento()), j.getDorsal(),
                Sql.texto(j.getPosicion()), Sql.nulo(j.getFoto()), orden);
    }

    /**
     * Jugador que aparece en un partido pero ya no esta en la plantilla (archivos anteriores): se crea inactivo
     * con los datos que guardo el partido.
     */
    int crearInactivo(Connection con, int idTorneo, int idEquipo, int numero, String nombre, int dorsal) throws SQLException {
        return Sql.insertar(con, """
                INSERT INTO jugadores (torneo_id, equipo_id, numero, nombre, dorsal, activo) VALUES (?, ?, ?, ?, ?, FALSE) RETURNING id""",
                idTorneo, idEquipo, numero, nombre == null || nombre.isBlank() ? "Jugador retirado" : nombre, Math.max(0, Math.min(99, dorsal)));
    }

    /** Equipos del torneo con su tecnico y su plantilla activa. */
    List<Equipo> listar(Connection con, int idTorneo) throws SQLException {
        Map<Integer, Equipo> porId = new HashMap<>();
        List<Equipo> equipos = Sql.listar(con, """
                SELECT e.id, e.numero, e.nombre, e.estadio, e.escudo_ruta, e.retirado, d.indice AS division,
                       dt.equipo_id IS NOT NULL AS tiene_dt, dt.nombre AS dt_nombre, dt.documento AS dt_documento, dt.nacionalidad AS dt_nacionalidad
                  FROM equipos e
                  JOIN divisiones d ON d.id = e.division_id
                  LEFT JOIN directores_tecnicos dt ON dt.equipo_id = e.id
                 WHERE e.torneo_id = ? ORDER BY e.orden, e.numero""", rs -> {
            Equipo e = new Equipo(rs.getInt("numero"), rs.getString("nombre"));
            e.setEstadio(rs.getString("estadio"));
            e.setEscudo(Sql.texto(rs.getString("escudo_ruta")));
            e.setRetirado(rs.getBoolean("retirado"));
            e.setDivision(rs.getInt("division"));
            if (rs.getBoolean("tiene_dt")) {
                e.setDirectorTecnico(new DirectorTecnico(rs.getString("dt_nombre"), rs.getString("dt_documento"), rs.getString("dt_nacionalidad")));
            }
            porId.put(rs.getInt("id"), e);
            return e;
        }, idTorneo);
        Sql.listar(con, """
                SELECT equipo_id, numero, nombre, documento, dorsal, posicion, foto_ruta FROM jugadores
                 WHERE torneo_id = ? AND activo ORDER BY equipo_id, orden, numero""", rs -> {
            Jugador j = new Jugador(rs.getString("nombre"), rs.getString("documento"), rs.getInt("dorsal"), rs.getString("posicion"));
            j.setId(rs.getInt("numero"));
            j.setFoto(Sql.texto(rs.getString("foto_ruta")));
            return porId.get(rs.getInt("equipo_id")).getJugadores().add(j);
        }, idTorneo);
        return new ArrayList<>(equipos);
    }
}
