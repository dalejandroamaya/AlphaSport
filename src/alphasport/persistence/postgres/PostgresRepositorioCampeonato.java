package alphasport.persistence.postgres;

import alphasport.model.Campeonato;
import alphasport.persistence.NotaJugador;
import alphasport.persistence.PersistenciaException;
import alphasport.persistence.RepositorioCampeonato;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Campeonato en curso en PostgreSQL. Cada guardado es una transaccion y sube la version del torneo: si otro
 * programa (el escritorio o la web) guardo antes, no se pisa su cambio y se avisa.
 */
public class PostgresRepositorioCampeonato implements RepositorioCampeonato {

    private final ConexionDB conexion;
    private final CampeonatoDAO campeonatos = new CampeonatoDAO();
    private final CalificacionDAO calificaciones = new CalificacionDAO();
    /** Lo que este programa leyo o guardo por ultima vez (null = no habia campeonato). */
    private TorneoDAO.Cabecera conocido;
    private CampeonatoDAO.Huellas huellas = new CampeonatoDAO.Huellas();

    public PostgresRepositorioCampeonato(ConexionDB conexion) {
        this.conexion = conexion;
    }

    @Override
    public boolean existe() throws PersistenciaException {
        return conexion.ejecutar(con -> campeonatos.torneos().actual(con) != null);
    }

    @Override
    public Campeonato cargar() throws PersistenciaException {
        return conexion.ejecutar(con -> {
            conocido = campeonatos.torneos().actual(con);
            if (conocido == null) {
                huellas = new CampeonatoDAO.Huellas();
                return null;
            }
            Campeonato c = campeonatos.leer(con, conocido.id());
            huellas = campeonatos.huellas(c);
            return c;
        });
    }

    @Override
    public boolean desactualizado() throws PersistenciaException {
        return conexion.ejecutar(con -> !java.util.Objects.equals(conocido, campeonatos.torneos().actual(con)));
    }

    @Override
    public void guardar(Campeonato c) throws PersistenciaException {
        boolean otroTorneo = conocido == null || !conocido.codigo().equals(c.getCodigo());
        CampeonatoDAO.Huellas[] nuevas = new CampeonatoDAO.Huellas[1];
        conocido = conexion.ejecutar(con -> {
            int id = otroTorneo ? reemplazarActual(con, c) : avanzar(con);
            nuevas[0] = campeonatos.escribir(con, id, c, otroTorneo ? new CampeonatoDAO.Huellas() : huellas);
            return campeonatos.torneos().actual(con);
        });
        huellas = nuevas[0];
    }

    @Override
    public void reemplazar(Campeonato nuevo, boolean archivarAnterior) throws PersistenciaException {
        if (!archivarAnterior || conocido == null || conocido.codigo().equals(nuevo.getCodigo())) {
            guardar(nuevo);
            return;
        }
        CampeonatoDAO.Huellas[] nuevas = new CampeonatoDAO.Huellas[1];
        conocido = conexion.ejecutar(con -> {
            avanzar(con);
            // El torneo en curso pasa entero al historial (mismos ids) en lugar de la copia que hizo el historial.
            campeonatos.torneos().eliminarArchivado(con, conocido.codigo());
            campeonatos.torneos().archivar(con, conocido.id());
            int id = campeonatos.torneos().insertar(con, nuevo, TorneoDAO.ACTUAL);
            nuevas[0] = campeonatos.escribir(con, id, nuevo, new CampeonatoDAO.Huellas());
            return campeonatos.torneos().actual(con);
        });
        huellas = nuevas[0];
    }

    private int avanzar(Connection con) throws SQLException, PersistenciaException {
        if (!campeonatos.torneos().avanzarVersion(con, conocido.id(), conocido.version())) {
            throw conflicto();
        }
        return conocido.id();
    }

    /** Un campeonato nuevo ocupa el lugar del que estaba en curso, que se descarta (las copias del historial no se tocan). */
    private int reemplazarActual(Connection con, Campeonato c) throws SQLException, PersistenciaException {
        if (conocido != null) {
            avanzar(con);
            campeonatos.torneos().eliminar(con, conocido.id());
        } else if (campeonatos.torneos().actual(con) != null) {
            throw conflicto();
        }
        return campeonatos.torneos().insertar(con, c, TorneoDAO.ACTUAL);
    }

    private static PersistenciaException conflicto() {
        return new PersistenciaException("Otro programa cambió el campeonato al mismo tiempo. Se cargaron sus datos: revise e intente de nuevo.", null);
    }

    @Override
    public void eliminar() throws PersistenciaException {
        if (conocido == null) {
            return;
        }
        conexion.ejecutar(con -> {
            avanzar(con);
            return campeonatos.torneos().eliminar(con, conocido.id());
        });
        conocido = null;
        huellas = new CampeonatoDAO.Huellas();
    }

    @Override
    public void guardarNotas(int idPartido, List<NotaJugador> notas) throws PersistenciaException {
        if (conocido != null) {
            conexion.ejecutar(con -> {
                calificaciones.reemplazar(con, conocido.id(), idPartido, notas);
                return null;
            });
        }
    }
}
