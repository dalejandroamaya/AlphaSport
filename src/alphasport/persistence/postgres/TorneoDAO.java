package alphasport.persistence.postgres;

import alphasport.model.Campeonato;
import alphasport.model.ConfiguracionTorneo;
import alphasport.model.CriterioDesempate;
import alphasport.model.Equipo;
import alphasport.model.TipoFormato;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tablas torneos, torneo_criterios y divisiones.
 */
final class TorneoDAO {

    static final String ACTUAL = "ACTUAL";
    static final String ARCHIVADO = "ARCHIVADO";

    /** Fila de torneos sin cargar el resto: para saber que hay y si cambio. */
    record Cabecera(int id, int version, String codigo) {
    }

    /** Campeonato en curso con su organizador (0 y null si no tiene dueno). */
    record Activo(String codigo, int propietario, String organizador) {
    }

    /** Todos los torneos en curso (puede haber varios), del mas antiguo al mas nuevo. */
    List<Cabecera> actuales(Connection con) throws SQLException {
        return Sql.listar(con, "SELECT id, version, codigo FROM torneos WHERE situacion = 'ACTUAL' ORDER BY id",
                rs -> new Cabecera(rs.getInt(1), rs.getInt(2), rs.getString(3)));
    }

    /** El torneo en curso con ese codigo, o null. */
    Cabecera actual(Connection con, String codigo) throws SQLException {
        return Sql.uno(con, "SELECT id, version, codigo FROM torneos WHERE codigo = ? AND situacion = 'ACTUAL'",
                rs -> new Cabecera(rs.getInt(1), rs.getInt(2), rs.getString(3)), codigo);
    }

    /** Los campeonatos en curso con el nombre de su organizador (LEFT JOIN: tambien los que no tienen dueno). */
    List<Activo> activos(Connection con) throws SQLException {
        return Sql.listar(con, """
                SELECT t.codigo, t.propietario_id, u.nombre
                  FROM torneos t LEFT JOIN usuarios u ON u.id = t.propietario_id
                 WHERE t.situacion = 'ACTUAL'
                 ORDER BY t.id""", rs -> new Activo(rs.getString(1), rs.getInt(2), rs.getString(3)));
    }

    /**
     * Un Organizador toma un campeonato sin dueno (y su copia del historial, si la hay). El WHERE pide que siga
     * sin dueno: si dos lo intentan a la vez, solo el primero lo consigue.
     *
     * @return true si lo tomo
     */
    boolean tomar(Connection con, String codigo, int propietario) throws SQLException {
        int tomados = Sql.cambiar(con, """
                UPDATE torneos SET propietario_id = ?, version = version + 1, actualizado_en = CURRENT_TIMESTAMP
                 WHERE codigo = ? AND situacion = 'ACTUAL' AND propietario_id IS NULL""", propietario, codigo);
        if (tomados == 1) {
            Sql.cambiar(con, "UPDATE torneos SET propietario_id = ? WHERE codigo = ? AND situacion = 'ARCHIVADO' AND propietario_id IS NULL",
                    propietario, codigo);
        }
        return tomados == 1;
    }

    /** Las noticias de un torneo pasan a otro (al cambiar de formato el torneo se rehace con otro id). */
    void moverNoticias(Connection con, int desde, int hacia) throws SQLException {
        Sql.cambiar(con, "UPDATE noticias SET torneo_id = ? WHERE torneo_id = ?", hacia, desde);
    }

    /** Archivados, el mas reciente primero. */
    List<Cabecera> archivados(Connection con) throws SQLException {
        return Sql.listar(con, "SELECT id, version, codigo FROM torneos WHERE situacion = 'ARCHIVADO' ORDER BY fecha_archivo DESC NULLS LAST, id DESC",
                rs -> new Cabecera(rs.getInt(1), rs.getInt(2), rs.getString(3)));
    }

    int insertar(Connection con, Campeonato c, String situacion) throws SQLException {
        return Sql.insertar(con, "INSERT INTO torneos (codigo, situacion, nombre, formato, propietario_id) VALUES (?, ?, ?, ?, ?) RETURNING id",
                c.getCodigo(), situacion, c.getNombreCampeonato(), c.getFormato().name(), c.getPropietario() > 0 ? c.getPropietario() : null);
    }

    /**
     * Sube la version si nadie mas guardo desde que se leyo.
     *
     * @return false si otro programa guardo antes (no se cambia nada)
     */
    boolean avanzarVersion(Connection con, int id, int version) throws SQLException {
        return Sql.cambiar(con, "UPDATE torneos SET version = version + 1, actualizado_en = CURRENT_TIMESTAMP WHERE id = ? AND version = ?", id, version) == 1;
    }

    /** El torneo en curso pasa al historial tal como esta: sus ids, notas y votos no cambian. */
    void archivar(Connection con, int id) throws SQLException {
        Sql.cambiar(con, """
                UPDATE torneos SET situacion = 'ARCHIVADO', fecha_archivo = COALESCE(fecha_archivo, CURRENT_DATE),
                       version = version + 1, actualizado_en = CURRENT_TIMESTAMP
                 WHERE id = ?""", id);
    }

    /** Borra la copia archivada de un torneo, si la hay. */
    void eliminarArchivado(Connection con, String codigo) throws SQLException {
        Integer id = Sql.uno(con, "SELECT id FROM torneos WHERE codigo = ? AND situacion = 'ARCHIVADO'", rs -> rs.getInt(1), codigo);
        if (id != null) {
            eliminar(con, id);
        }
    }

    boolean eliminar(Connection con, int id) throws SQLException {
        // Los eventos y los partidos apuntan a equipos y jugadores sin cascada: se borran antes.
        Sql.cambiar(con, "DELETE FROM partidos WHERE torneo_id = ?", id);
        return Sql.cambiar(con, "DELETE FROM torneos WHERE id = ?", id) == 1;
    }

    /** Datos y reglas del torneo (el campeon se guarda aparte, cuando ya existen los equipos). */
    void actualizar(Connection con, int id, Campeonato c) throws SQLException {
        ConfiguracionTorneo r = c.getConfiguracion();
        Sql.cambiar(con, """
                UPDATE torneos SET nombre = ?, formato = ?, paso_actual = ?, semilla = ?, fecha_archivo = ?,
                       siguiente_equipo = ?, siguiente_partido = ?, siguiente_jugador = ?,
                       puntos_victoria = ?, puntos_empate = ?, ida_y_vuelta = ?, eliminatoria_ida_y_vuelta = ?, final_unica = ?,
                       sorteo_aleatorio = ?, tamano_grupo = ?, clasificados_por_grupo = ?, clasificados_playoff = ?, tipo_playoff = ?,
                       ventaja_deportiva = ?, rondas_suizo = ?, directos_suizo = ?, repechaje_suizo = ?, gran_final_con_reinicio = ?,
                       cantidad_divisiones = ?, ascensos = ?
                 WHERE id = ?""",
                c.getNombreCampeonato(), c.getFormato().name(), c.getPasoActual(), c.getSemilla(), Sql.fecha(c.getFechaArchivo()),
                c.getSiguienteIdEquipo(), c.getSiguienteIdPartido(), c.getSiguienteIdJugador(),
                r.getPuntosVictoria(), r.getPuntosEmpate(), r.isIdaYVuelta(), r.isEliminatoriaIdaYVuelta(), r.isFinalUnica(),
                r.isSorteoAleatorio(), r.getTamanoGrupo(), r.getClasificadosPorGrupo(), r.getClasificadosPlayoff(), Sql.texto(r.getTipoPlayoff()),
                r.isVentajaDeportiva(), r.getRondasSuizo(), r.getDirectosSuizo(), r.getRepechajeSuizo(), r.isGranFinalConReinicio(),
                r.getDivisiones(), r.getAscensos(), id);
        Sql.cambiar(con, "DELETE FROM torneo_criterios WHERE torneo_id = ?", id);
        int orden = 0;
        for (CriterioDesempate criterio : r.getCriterios()) {
            Sql.cambiar(con, "INSERT INTO torneo_criterios (torneo_id, orden, criterio) VALUES (?, ?, ?)", id, orden++, criterio.name());
        }
    }

    void guardarCampeon(Connection con, int id, Integer idEquipo) throws SQLException {
        Sql.cambiar(con, "UPDATE torneos SET campeon_id = ? WHERE id = ?", idEquipo, id);
    }

    /**
     * Crea las divisiones que falten: las del torneo y la de cualquier equipo que venga de una division mayor.
     *
     * @return indice de la division -> id
     */
    Map<Integer, Integer> guardarDivisiones(Connection con, int id, Campeonato c) throws SQLException {
        int cuantas = c.getConfiguracion().getDivisiones();
        for (Equipo e : c.getEquipos()) {
            cuantas = Math.max(cuantas, e.getDivision() + 1);
        }
        for (int indice = 0; indice < cuantas; indice++) {
            Sql.cambiar(con, "INSERT INTO divisiones (torneo_id, indice, nombre) VALUES (?, ?, ?) ON CONFLICT (torneo_id, indice) DO NOTHING",
                    id, indice, ConfiguracionTorneo.nombreDivision(indice));
        }
        return Sql.mapa(con, "SELECT indice, id FROM divisiones WHERE torneo_id = ?", id);
    }

    /** Borra las divisiones que ya no usa el torneo ni ningun equipo. */
    void limpiarDivisiones(Connection con, int id) throws SQLException {
        Sql.cambiar(con, """
                DELETE FROM divisiones d USING torneos t
                 WHERE t.id = d.torneo_id AND d.torneo_id = ? AND d.indice >= t.cantidad_divisiones
                   AND NOT EXISTS (SELECT 1 FROM equipos e WHERE e.division_id = d.id)""", id);
    }

    /** Torneo con sus reglas, sin equipos, fases ni partidos. */
    Campeonato leer(Connection con, int id) throws SQLException {
        Campeonato campeonato = Sql.uno(con, "SELECT * FROM torneos WHERE id = ?", rs -> {
            ConfiguracionTorneo r = new ConfiguracionTorneo();
            r.setPuntosVictoria(rs.getInt("puntos_victoria"));
            r.setPuntosEmpate(rs.getInt("puntos_empate"));
            r.setIdaYVuelta(rs.getBoolean("ida_y_vuelta"));
            r.setEliminatoriaIdaYVuelta(rs.getBoolean("eliminatoria_ida_y_vuelta"));
            r.setFinalUnica(rs.getBoolean("final_unica"));
            r.setSorteoAleatorio(rs.getBoolean("sorteo_aleatorio"));
            r.setTamanoGrupo(rs.getInt("tamano_grupo"));
            r.setClasificadosPorGrupo(rs.getInt("clasificados_por_grupo"));
            r.setClasificadosPlayoff(rs.getInt("clasificados_playoff"));
            r.setTipoPlayoff(rs.getString("tipo_playoff"));
            r.setVentajaDeportiva(rs.getBoolean("ventaja_deportiva"));
            r.setRondasSuizo(rs.getInt("rondas_suizo"));
            r.setDirectosSuizo(rs.getInt("directos_suizo"));
            r.setRepechajeSuizo(rs.getInt("repechaje_suizo"));
            r.setGranFinalConReinicio(rs.getBoolean("gran_final_con_reinicio"));
            r.setDivisiones(rs.getInt("cantidad_divisiones"));
            r.setAscensos(rs.getInt("ascensos"));
            Campeonato c = new Campeonato(rs.getString("nombre"), TipoFormato.valueOf(rs.getString("formato")), r);
            c.setCodigo(rs.getString("codigo"));
            c.setPropietario(rs.getInt("propietario_id")); // 0 si es NULL (sin dueno)
            c.setPasoActual(rs.getInt("paso_actual"));
            c.setSemilla(rs.getLong("semilla"));
            c.setFechaArchivo(rs.getDate("fecha_archivo") == null ? null : rs.getDate("fecha_archivo").toLocalDate().toString());
            c.restaurarContadores(rs.getInt("siguiente_equipo"), rs.getInt("siguiente_partido"), rs.getInt("siguiente_jugador"));
            return c;
        }, id);
        campeonato.getConfiguracion().setCriterios(new ArrayList<>(Sql.listar(con,
                "SELECT criterio FROM torneo_criterios WHERE torneo_id = ? ORDER BY orden", rs -> CriterioDesempate.valueOf(rs.getString(1)), id)));
        Integer campeon = Sql.uno(con, "SELECT e.numero FROM torneos t JOIN equipos e ON e.id = t.campeon_id WHERE t.id = ?", rs -> rs.getInt(1), id);
        campeonato.setIdCampeon(campeon == null ? Campeonato.SIN_CAMPEON : campeon);
        return campeonato;
    }
}
