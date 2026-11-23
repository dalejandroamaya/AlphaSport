package alphasport.persistence.postgres;

import alphasport.model.Campeonato;
import alphasport.model.Equipo;
import alphasport.model.Jugador;
import alphasport.model.Partido;
import alphasport.persistence.PersistenciaException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Un campeonato completo (torneo, equipos, fases y partidos) repartido en sus tablas. Conserva los ids de las
 * filas y solo escribe lo que cambio: compara la huella de cada equipo, fase y partido con la ultima guardada.
 */
final class CampeonatoDAO {

    /** Huella de cada parte tal como esta en la base de datos («P12» = partido 12). */
    static final class Huellas {
        private final Map<String, String> porParte = new HashMap<>();
    }

    private final TorneoDAO torneos = new TorneoDAO();
    private final EquipoDAO equipos = new EquipoDAO();
    private final FaseDAO fases = new FaseDAO();
    private final PartidoDAO partidos = new PartidoDAO(equipos);

    TorneoDAO torneos() {
        return torneos;
    }

    Campeonato leer(Connection con, int idTorneo) throws SQLException {
        Campeonato c = torneos.leer(con, idTorneo);
        c.getEquipos().addAll(equipos.listar(con, idTorneo));
        c.getFases().addAll(fases.listar(con, idTorneo));
        c.getPartidos().addAll(partidos.listar(con, idTorneo));
        return c;
    }

    /** Escribe el campeonato en el torneo indicado y devuelve sus huellas nuevas. */
    Huellas escribir(Connection con, int idTorneo, Campeonato c, Huellas antes) throws SQLException, PersistenciaException {
        numerarJugadores(c);
        Huellas ahora = huellas(c);
        quitarSobrantes(con, idTorneo, c);
        torneos.actualizar(con, idTorneo, c);

        Map<Integer, Integer> divisiones = torneos.guardarDivisiones(con, idTorneo, c);
        int orden = 0;
        for (Equipo e : c.getEquipos()) {
            if (cambio("E" + e.getId(), antes, ahora)) {
                equipos.guardar(con, idTorneo, e, orden, divisiones.get(e.getDivision()));
            }
            orden++;
        }
        Map<Integer, Integer> idsEquipos = equipos.ids(con, idTorneo);
        for (int i = 0; i < c.getFases().size(); i++) {
            if (cambio("F" + i, antes, ahora)) {
                fases.guardar(con, idTorneo, i, c.getFases().get(i), idsEquipos);
            }
        }
        Map<Integer, Equipo> plantillas = new HashMap<>();
        c.getEquipos().forEach(e -> plantillas.put(e.getId(), e));
        PartidoDAO.Claves claves = new PartidoDAO.Claves(idTorneo, fases.ids(con, idTorneo), idsEquipos, equipos.idsJugadores(con, idTorneo), plantillas);
        for (Partido p : c.getPartidos()) {
            if (cambio("P" + p.getId(), antes, ahora)) {
                partidos.guardar(con, p, claves);
            }
        }
        torneos.limpiarDivisiones(con, idTorneo);
        torneos.guardarCampeon(con, idTorneo, c.isTerminado() ? idsEquipos.get(c.getIdCampeon()) : null);
        return ahora;
    }

    /** Borra lo que ya no existe: primero los partidos, luego las fases y al final los equipos. */
    private void quitarSobrantes(Connection con, int idTorneo, Campeonato c) throws SQLException {
        for (Map.Entry<Integer, Integer> p : partidos.ids(con, idTorneo).entrySet()) {
            if (c.buscarPartido(p.getKey()) == null) {
                partidos.eliminar(con, p.getValue());
            }
        }
        fases.eliminarDesde(con, idTorneo, c.getFases().size());
        for (Map.Entry<Integer, Integer> e : equipos.ids(con, idTorneo).entrySet()) {
            if (c.buscarEquipo(e.getKey()) == null) {
                equipos.eliminar(con, e.getValue());
            }
        }
    }

    /**
     * Los archivos muy viejos traen jugadores sin id: en la base de datos todos lo necesitan. Una persona
     * no puede estar dos veces en el mismo torneo: si un codigo se repite, el segundo pasa a ser otra persona.
     */
    private static void numerarJugadores(Campeonato c) {
        Set<String> personas = new HashSet<>();
        for (Equipo e : c.getEquipos()) {
            for (Jugador j : new ArrayList<>(e.getJugadores())) {
                if (j.getId() <= 0) {
                    j.setId(c.nuevoIdJugador());
                }
                if (!personas.add(j.getCodigoPersona())) {
                    j.setCodigoPersona(null);
                    personas.add(j.getCodigoPersona());
                }
            }
        }
    }
    /* ---------------------------------------------------------------- huellas */

    Huellas huellas(Campeonato c) throws PersistenciaException {
        Huellas h = new Huellas();
        for (int i = 0; i < c.getEquipos().size(); i++) {
            Equipo e = c.getEquipos().get(i);
            h.porParte.put("E" + e.getId(), i + ":" + huella(e));
        }
        for (int i = 0; i < c.getFases().size(); i++) {
            h.porParte.put("F" + i, huella(c.getFases().get(i)));
        }
        for (Partido p : c.getPartidos()) {
            h.porParte.put("P" + p.getId(), huella(p));
        }
        return h;
    }

    private static boolean cambio(String parte, Huellas antes, Huellas ahora) {
        return !ahora.porParte.get(parte).equals(antes.porParte.get(parte));
    }

    private static String huella(Serializable parte) throws PersistenciaException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream salida = new ObjectOutputStream(bytes)) {
            salida.writeObject(parte);
            salida.flush();
            return new BigInteger(1, MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())).toString(36);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new PersistenciaException("No se pudo revisar qué cambió", e);
        }
    }
}
