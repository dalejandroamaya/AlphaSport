package alphasport.persistence.postgres;

import alphasport.model.Campeonato;
import alphasport.persistence.NotaJugador;
import alphasport.persistence.PersistenciaException;
import alphasport.persistence.RepositorioCampeonato;
import alphasport.persistence.RepositorioHistorial;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Un campeonato en curso en PostgreSQL. Cada guardado es una transaccion y sube la version del torneo: si otro
 * programa (el escritorio o la web) guardo antes, no se pisa su cambio y se avisa.
 * <p>
 * Hay dos formas de usarlo:
 * <ul>
 * <li>{@link #de(ConexionDB, String)} y {@link #nuevo(ConexionDB)}: atado a UN torneo (por su codigo). Es lo que
 * usa {@link PostgresRepositorioCampeonatos} con varios campeonatos en curso: guardar, archivar o eliminar solo
 * toca ese torneo, nunca otro.</li>
 * <li>{@link #PostgresRepositorioCampeonato(ConexionDB)}: «el» campeonato en curso, como antes de que hubiera
 * varios (pruebas y {@link MigradorArchivos}). Si hay mas de uno en curso, avisa en vez de elegir uno.</li>
 * </ul>
 */
public class PostgresRepositorioCampeonato implements RepositorioCampeonato {

    private final ConexionDB conexion;
    private final CampeonatoDAO campeonatos = new CampeonatoDAO();
    private final CalificacionDAO calificaciones = new CalificacionDAO();
    /** true = «el unico en curso»; false = atado al torneo de {@link #codigo}. */
    private final boolean unico;
    /** Torneo al que esta atado (null = todavia no se creo). Cambia si el campeonato se rehace con otro codigo. */
    private String codigo;
    /** Lo que este programa leyo o guardo por ultima vez (null = no habia campeonato). */
    private TorneoDAO.Cabecera conocido;
    private CampeonatoDAO.Huellas huellas = new CampeonatoDAO.Huellas();

    /** El unico campeonato en curso (como antes de que hubiera varios). */
    public PostgresRepositorioCampeonato(ConexionDB conexion) {
        this(conexion, true, null);
    }

    private PostgresRepositorioCampeonato(ConexionDB conexion, boolean unico, String codigo) {
        this.conexion = conexion;
        this.unico = unico;
        this.codigo = codigo;
    }

    /** Atado al torneo en curso con ese codigo. */
    public static PostgresRepositorioCampeonato de(ConexionDB conexion, String codigo) {
        return new PostgresRepositorioCampeonato(conexion, false, codigo);
    }

    /** Para un campeonato que todavia no existe: el primer guardado lo crea y desde ahi queda atado a el. */
    public static PostgresRepositorioCampeonato nuevo(ConexionDB conexion) {
        return new PostgresRepositorioCampeonato(conexion, false, null);
    }

    /** La fila del torneo de este repositorio como esta ahora en la base (null si no hay). */
    private TorneoDAO.Cabecera enLaBase(Connection con) throws SQLException, PersistenciaException {
        if (!unico) {
            return codigo == null ? null : campeonatos.torneos().actual(con, codigo);
        }
        List<TorneoDAO.Cabecera> actuales = campeonatos.torneos().actuales(con);
        if (actuales.size() > 1) {
            throw new PersistenciaException("Hay " + actuales.size() + " campeonatos en curso: elija uno (cada uno se abre por su código).", null);
        }
        return actuales.isEmpty() ? null : actuales.get(0);
    }

    @Override
    public boolean existe() throws PersistenciaException {
        return conexion.ejecutar(con -> enLaBase(con) != null);
    }

    @Override
    public Optional<Campeonato> cargar() throws PersistenciaException {
        return conexion.ejecutar(con -> {
            conocido = enLaBase(con);
            if (conocido == null) {
                huellas = new CampeonatoDAO.Huellas();
                return Optional.empty();
            }
            codigo = conocido.codigo();
            Campeonato c = campeonatos.leer(con, conocido.id());
            huellas = campeonatos.huellas(c);
            return Optional.of(c);
        });
    }

    @Override
    public boolean desactualizado() throws PersistenciaException {
        return conexion.ejecutar(con -> !java.util.Objects.equals(conocido, enLaBase(con)));
    }

    @Override
    public void guardar(Campeonato c) throws PersistenciaException {
        boolean otroTorneo = conocido == null || !conocido.codigo().equals(c.getCodigo());
        CampeonatoDAO.Huellas[] nuevas = new CampeonatoDAO.Huellas[1];
        conocido = conexion.ejecutar(con -> {
            int id = otroTorneo ? reemplazarActual(con, c) : avanzar(con);
            nuevas[0] = campeonatos.escribir(con, id, c, otroTorneo ? new CampeonatoDAO.Huellas() : huellas);
            codigo = c.getCodigo();
            return enLaBase(con);
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
            codigo = nuevo.getCodigo();
            return enLaBase(con);
        });
        huellas = nuevas[0];
    }

    /**
     * Termina este campeonato: pasa entero al historial (mismos ids, notas y votos) en lugar de la copia que
     * hubiera, y deja de estar en curso. Los demas campeonatos no se tocan.
     */
    @Override
    public void archivarEn(RepositorioHistorial historial, Campeonato c) throws PersistenciaException {
        guardar(c);
        conexion.ejecutar(con -> {
            avanzar(con);
            campeonatos.torneos().eliminarArchivado(con, conocido.codigo());
            campeonatos.torneos().archivar(con, conocido.id());
            return null;
        });
        conocido = null;
        codigo = null;
        huellas = new CampeonatoDAO.Huellas();
    }

    private int avanzar(Connection con) throws SQLException, PersistenciaException {
        if (!campeonatos.torneos().avanzarVersion(con, conocido.id(), conocido.version())) {
            throw conflicto();
        }
        return conocido.id();
    }

    /**
     * Un campeonato con otro codigo (otro formato, o un respaldo abierto) ocupa el lugar del de este repositorio,
     * que se descarta; sus noticias pasan al nuevo. Las copias del historial y los demas campeonatos no se tocan.
     */
    private int reemplazarActual(Connection con, Campeonato c) throws SQLException, PersistenciaException {
        if (conocido == null) {
            if (enLaBase(con) != null) {
                throw conflicto(); // otro programa ya lo creo
            }
            return campeonatos.torneos().insertar(con, c, TorneoDAO.ACTUAL);
        }
        avanzar(con);
        int nuevo = campeonatos.torneos().insertar(con, c, TorneoDAO.ACTUAL);
        campeonatos.torneos().moverNoticias(con, conocido.id(), nuevo);
        campeonatos.torneos().eliminar(con, conocido.id());
        return nuevo;
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
        codigo = null;
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
