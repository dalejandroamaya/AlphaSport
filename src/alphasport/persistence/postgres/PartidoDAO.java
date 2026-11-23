package alphasport.persistence.postgres;

import alphasport.model.Cambio;
import alphasport.model.Equipo;
import alphasport.model.EstadoPartido;
import alphasport.model.Gol;
import alphasport.model.Jugador;
import alphasport.model.Partido;
import alphasport.model.Tarjeta;
import alphasport.model.VotoJugador;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tablas partidos, alineaciones, eventos_partido y votos_publico.
 */
final class PartidoDAO {

    private final EquipoDAO equipoDAO;

    PartidoDAO(EquipoDAO equipoDAO) {
        this.equipoDAO = equipoDAO;
    }

    /** Numero del partido -> id. */
    Map<Integer, Integer> ids(Connection con, int idTorneo) throws SQLException {
        return Sql.mapa(con, "SELECT numero, id FROM partidos WHERE torneo_id = ?", idTorneo);
    }

    void eliminar(Connection con, int idPartido) throws SQLException {
        Sql.cambiar(con, "DELETE FROM partidos WHERE id = ?", idPartido);
    }

    /** Ids que hacen falta para guardar los partidos de un torneo. */
    record Claves(int idTorneo, Map<Integer, Integer> fases, Map<Integer, Integer> equipos, Map<Integer, Integer> jugadores,
            Map<Integer, Equipo> plantillas) {
    }

    /** Crea o actualiza el partido con su alineacion, sus eventos y sus votos. */
    void guardar(Connection con, Partido p, Claves k) throws SQLException {
        int local = k.equipos().get(p.getIdLocal()), visitante = k.equipos().get(p.getIdVisitante());
        int id = Sql.insertar(con, """
                INSERT INTO partidos (torneo_id, numero, fase_id, local_id, visitante_id, paso, grupo, ronda, nombre_ronda, serie, pierna, llave, eliminatorio,
                                      fecha, hora, sede, observaciones, formacion_local, formacion_visitante, estado, walkover, goles_local, goles_visitante,
                                      penales_local, penales_visitante, amarillas_local, rojas_local, amarillas_visitante, rojas_visitante,
                                      finalizado_en, horas_votacion, votacion_cerrada_en)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (torneo_id, numero) DO UPDATE SET fase_id = EXCLUDED.fase_id, local_id = EXCLUDED.local_id, visitante_id = EXCLUDED.visitante_id,
                    paso = EXCLUDED.paso, grupo = EXCLUDED.grupo, ronda = EXCLUDED.ronda, nombre_ronda = EXCLUDED.nombre_ronda, serie = EXCLUDED.serie,
                    pierna = EXCLUDED.pierna, llave = EXCLUDED.llave, eliminatorio = EXCLUDED.eliminatorio, fecha = EXCLUDED.fecha, hora = EXCLUDED.hora,
                    sede = EXCLUDED.sede, observaciones = EXCLUDED.observaciones, formacion_local = EXCLUDED.formacion_local,
                    formacion_visitante = EXCLUDED.formacion_visitante, estado = EXCLUDED.estado, walkover = EXCLUDED.walkover, goles_local = EXCLUDED.goles_local,
                    goles_visitante = EXCLUDED.goles_visitante, penales_local = EXCLUDED.penales_local, penales_visitante = EXCLUDED.penales_visitante,
                    amarillas_local = EXCLUDED.amarillas_local, rojas_local = EXCLUDED.rojas_local, amarillas_visitante = EXCLUDED.amarillas_visitante,
                    rojas_visitante = EXCLUDED.rojas_visitante, finalizado_en = EXCLUDED.finalizado_en, horas_votacion = EXCLUDED.horas_votacion,
                    votacion_cerrada_en = EXCLUDED.votacion_cerrada_en
                RETURNING id""",
                k.idTorneo(), p.getId(), k.fases().get(p.getIndiceFase()), local, visitante, p.getPaso(), Sql.texto(p.getGrupo()), p.getRonda(),
                Sql.texto(p.getNombreRonda()), Sql.texto(p.getSerie()), p.getPierna(), Sql.texto(p.getLlave()), p.isEliminatorio(),
                Sql.fecha(p.getFecha()), Sql.hora(p.getHora()), Sql.texto(p.getSede()), Sql.texto(p.getObservaciones()),
                Sql.nulo(p.getFormacionLocal()), Sql.nulo(p.getFormacionVisitante()), p.getEstado().name(), p.isWalkover(), p.getGolesLocal(), p.getGolesVisitante(),
                penales(p.getPenalesLocal()), penales(p.getPenalesVisitante()), p.getAmarillasLocal(), p.getRojasLocal(), p.getAmarillasVisitante(),
                p.getRojasVisitante(), Sql.momento(p.getFinalizadoEn()), p.getHorasVotacion(), Sql.momento(p.getVotacionCerradaEn()));
        guardarAlineaciones(con, id, p, k);
        guardarEventos(con, id, p, k);
        guardarVotos(con, id, p, k);
    }

    private static Integer penales(int valor) {
        return valor == Partido.SIN_PENALES ? null : valor;
    }

    /** Id del jugador; si ya no esta en ninguna plantilla se crea inactivo. */
    private int jugador(Connection con, Claves k, int idEquipo, int numero, String nombre, int dorsal) throws SQLException {
        Integer id = k.jugadores().get(numero);
        if (id == null) {
            id = equipoDAO.crearInactivo(con, k.idTorneo(), k.equipos().get(idEquipo), numero, nombre, dorsal);
            k.jugadores().put(numero, id);
        }
        return id;
    }

    /** Titulares en el orden en que se eligieron y, con el XI definido, el resto de la plantilla como suplentes. */
    private void guardarAlineaciones(Connection con, int idPartido, Partido p, Claves k) throws SQLException {
        Sql.cambiar(con, "DELETE FROM alineaciones WHERE partido_id = ?", idPartido);
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO alineaciones (partido_id, jugador_id, equipo_id, titular, orden) VALUES (?, ?, ?, ?, ?)")) {
            for (int equipo : new int[]{p.getIdLocal(), p.getIdVisitante()}) {
                List<Integer> titulares = p.getTitularesDe(equipo);
                if (titulares.isEmpty()) {
                    continue;
                }
                int orden = 0;
                for (int numero : titulares) {
                    Sql.poner(ps, idPartido, jugador(con, k, equipo, numero, "", 0), k.equipos().get(equipo), true, orden++);
                    ps.addBatch();
                }
                for (Jugador j : k.plantillas().get(equipo).getJugadores()) {
                    if (!titulares.contains(j.getId())) {
                        Sql.poner(ps, idPartido, jugador(con, k, equipo, j.getId(), j.getNombre(), j.getDorsal()), k.equipos().get(equipo), false, orden++);
                        ps.addBatch();
                    }
                }
            }
            ps.executeBatch();
        }
    }

    private void guardarEventos(Connection con, int idPartido, Partido p, Claves k) throws SQLException {
        Sql.cambiar(con, "DELETE FROM eventos_partido WHERE partido_id = ?", idPartido);
        try (PreparedStatement ps = con.prepareStatement("""
                INSERT INTO eventos_partido (partido_id, tipo, orden, minuto, equipo_id, jugador_id, jugador_nombre, jugador_dorsal,
                                             segundo_jugador_id, segundo_jugador_nombre, segundo_jugador_dorsal, fechas_suspension)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")) {
            int orden = 0;
            for (Gol g : p.getGoles()) {
                int equipoAutor = g.isEnContra() ? p.getRival(g.getIdEquipo()) : g.getIdEquipo();
                Integer asistente = g.tieneAsistencia()
                        ? jugador(con, k, g.getIdEquipo(), g.getIdAsistente(), g.getNombreAsistente(), g.getDorsalAsistente()) : null;
                Sql.poner(ps, idPartido, g.isEnContra() ? "AUTOGOL" : "GOL", orden++, minuto(g.getMinuto()), k.equipos().get(g.getIdEquipo()),
                        jugador(con, k, equipoAutor, g.getIdAutor(), g.getNombreAutor(), g.getDorsalAutor()), Sql.texto(g.getNombreAutor()), g.getDorsalAutor(),
                        asistente, g.getNombreAsistente(), g.getDorsalAsistente(), 0);
                ps.addBatch();
            }
            for (Tarjeta t : p.getTarjetas()) {
                Sql.poner(ps, idPartido, t.isRoja() ? "ROJA" : "AMARILLA", orden++, minuto(t.getMinuto()), k.equipos().get(t.getIdEquipo()),
                        jugador(con, k, t.getIdEquipo(), t.getIdJugador(), t.getNombreJugador(), t.getDorsal()), Sql.texto(t.getNombreJugador()), t.getDorsal(),
                        null, "", 0, t.getFechasSuspension());
                ps.addBatch();
            }
            for (Cambio c : p.getCambios()) {
                Sql.poner(ps, idPartido, "CAMBIO", orden++, minuto(c.getMinuto()), k.equipos().get(c.getIdEquipo()),
                        jugador(con, k, c.getIdEquipo(), c.getIdSale(), c.getNombreSale(), c.getDorsalSale()), Sql.texto(c.getNombreSale()), c.getDorsalSale(),
                        jugador(con, k, c.getIdEquipo(), c.getIdEntra(), c.getNombreEntra(), c.getDorsalEntra()), Sql.texto(c.getNombreEntra()), c.getDorsalEntra(), 0);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static Integer minuto(int minuto) {
        return minuto <= 0 ? null : minuto;
    }

    /** Los votos nunca cambian: se agregan los que falten y, si la votacion se descarto, se borran. */
    private void guardarVotos(Connection con, int idPartido, Partido p, Claves k) throws SQLException {
        if (p.getVotos().isEmpty()) {
            Sql.cambiar(con, "DELETE FROM votos_publico WHERE partido_id = ?", idPartido);
            return;
        }
        try (PreparedStatement ps = con.prepareStatement("""
                INSERT INTO votos_publico (partido_id, jugador_id, dispositivo, valor, fecha) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (partido_id, jugador_id, dispositivo) DO NOTHING""")) {
            for (VotoJugador v : p.getVotos()) {
                Sql.poner(ps, idPartido, jugador(con, k, p.getIdLocal(), v.getIdJugador(), "", 0), v.getDispositivo(), v.getValor(), Sql.momento(v.getFecha()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /* ---------------------------------------------------------------- lectura */

    /** Todos los partidos del torneo con cuatro consultas, sin importar cuantos sean. */
    List<Partido> listar(Connection con, int idTorneo) throws SQLException {
        Map<Integer, Partido> porId = new HashMap<>();
        List<Partido> partidos = Sql.listar(con, """
                SELECT p.*, f.indice AS indice_fase, l.numero AS local, v.numero AS visitante FROM partidos p
                  JOIN fases f ON f.id = p.fase_id JOIN equipos l ON l.id = p.local_id JOIN equipos v ON v.id = p.visitante_id
                 WHERE p.torneo_id = ? ORDER BY p.numero""", rs -> {
            Partido p = leerPartido(rs);
            porId.put(rs.getInt("id"), p);
            return p;
        }, idTorneo);
        leerTitulares(con, idTorneo, porId);
        leerEventos(con, idTorneo, porId);
        Sql.listar(con, """
                SELECT x.partido_id, j.numero, x.valor, x.dispositivo, x.fecha FROM votos_publico x
                  JOIN partidos p ON p.id = x.partido_id JOIN jugadores j ON j.id = x.jugador_id
                 WHERE p.torneo_id = ? ORDER BY x.id""",
                rs -> porId.get(rs.getInt(1)).getVotos().add(new VotoJugador(rs.getInt(2), rs.getInt(3), rs.getString(4), Sql.ms(rs, "fecha"))), idTorneo);
        return partidos;
    }

    private static Partido leerPartido(ResultSet rs) throws SQLException {
        Partido p = new Partido(rs.getInt("numero"), rs.getInt("local"), rs.getInt("visitante"));
        p.setIndiceFase(rs.getInt("indice_fase"));
        p.setPaso(rs.getInt("paso"));
        p.setGrupo(rs.getString("grupo"));
        p.setRonda(rs.getInt("ronda"));
        p.setNombreRonda(rs.getString("nombre_ronda"));
        p.setSerie(rs.getString("serie"));
        p.setPierna(rs.getInt("pierna"));
        p.setLlave(rs.getString("llave"));
        p.setEliminatorio(rs.getBoolean("eliminatorio"));
        p.setFecha(rs.getDate("fecha") == null ? "Por definir" : rs.getDate("fecha").toLocalDate().toString());
        p.setHora(rs.getTime("hora") == null ? "" : rs.getString("hora").substring(0, 5));
        p.setSede(rs.getString("sede"));
        p.setObservaciones(rs.getString("observaciones"));
        p.setFormaciones(rs.getString("formacion_local"), rs.getString("formacion_visitante"));
        p.setEstado(EstadoPartido.valueOf(rs.getString("estado")));
        p.setWalkover(rs.getBoolean("walkover"));
        p.setGolesLocal(rs.getInt("goles_local"));
        p.setGolesVisitante(rs.getInt("goles_visitante"));
        Integer pl = Sql.entero(rs, "penales_local"), pv = Sql.entero(rs, "penales_visitante");
        p.setPenalesLocal(pl == null ? Partido.SIN_PENALES : pl);
        p.setPenalesVisitante(pv == null ? Partido.SIN_PENALES : pv);
        p.setAmarillasLocal(rs.getInt("amarillas_local"));
        p.setRojasLocal(rs.getInt("rojas_local"));
        p.setAmarillasVisitante(rs.getInt("amarillas_visitante"));
        p.setRojasVisitante(rs.getInt("rojas_visitante"));
        p.setFinalizadoEn(Sql.ms(rs, "finalizado_en"));
        p.setHorasVotacion(rs.getInt("horas_votacion"));
        p.setVotacionCerradaEn(Sql.ms(rs, "votacion_cerrada_en"));
        return p;
    }

    private void leerTitulares(Connection con, int idTorneo, Map<Integer, Partido> porId) throws SQLException {
        Sql.listar(con, """
                SELECT a.partido_id, e.numero, j.numero FROM alineaciones a JOIN partidos p ON p.id = a.partido_id
                  JOIN equipos e ON e.id = a.equipo_id JOIN jugadores j ON j.id = a.jugador_id
                 WHERE p.torneo_id = ? AND a.titular ORDER BY a.partido_id, a.equipo_id, a.orden""",
                rs -> porId.get(rs.getInt(1)).getTitularesDe(rs.getInt(2)).add(rs.getInt(3)), idTorneo);
    }

    private void leerEventos(Connection con, int idTorneo, Map<Integer, Partido> porId) throws SQLException {
        Map<Integer, ArrayList<Tarjeta>> tarjetas = new HashMap<>();
        Sql.listar(con, """
                SELECT x.*, e.numero AS equipo, j.numero AS jugador, s.numero AS segundo FROM eventos_partido x
                  JOIN partidos p ON p.id = x.partido_id JOIN equipos e ON e.id = x.equipo_id
                  JOIN jugadores j ON j.id = x.jugador_id LEFT JOIN jugadores s ON s.id = x.segundo_jugador_id
                 WHERE p.torneo_id = ? ORDER BY x.partido_id, x.orden""", rs -> {
            int idPartido = rs.getInt("partido_id"), equipo = rs.getInt("equipo"), minuto = rs.getInt("minuto");
            Partido p = porId.get(idPartido);
            Jugador jugador = deEseDia(rs.getInt("jugador"), rs.getString("jugador_nombre"), rs.getInt("jugador_dorsal"));
            Jugador segundo = Sql.entero(rs, "segundo") == null ? null
                    : deEseDia(rs.getInt("segundo"), rs.getString("segundo_jugador_nombre"), rs.getInt("segundo_jugador_dorsal"));
            String tipo = rs.getString("tipo");
            switch (tipo) {
                case "GOL", "AUTOGOL" -> p.getGoles().add(new Gol(equipo, minuto, jugador, tipo.equals("AUTOGOL"), segundo));
                case "CAMBIO" -> p.getCambios().add(new Cambio(equipo, minuto, jugador, segundo));
                default -> {
                    Tarjeta t = new Tarjeta(equipo, jugador, tipo.equals("ROJA") ? Tarjeta.ROJA : Tarjeta.AMARILLA, rs.getInt("fechas_suspension"));
                    t.setMinuto(minuto);
                    tarjetas.computeIfAbsent(idPartido, x -> new ArrayList<>()).add(t);
                }
            }
            return p;
        }, idTorneo);
        tarjetas.forEach((idPartido, lista) -> porId.get(idPartido).setTarjetas(lista, porId.get(idPartido).getIdLocal()));
    }

    /** Jugador con el nombre y el dorsal que tenia el dia del partido. */
    private static Jugador deEseDia(int numero, String nombre, int dorsal) {
        Jugador j = new Jugador(nombre, "", dorsal, "");
        j.setId(numero);
        return j;
    }
}
