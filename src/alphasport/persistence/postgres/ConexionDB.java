package alphasport.persistence.postgres;

import alphasport.persistence.PersistenciaException;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Conexion con PostgreSQL. Lee db.properties (Git lo ignora) y, si falta algun dato, usa localhost:5432,
 * alphasport_db y el usuario postgres. La contrasena nunca va en el codigo: sale de db.properties o de la
 * variable de entorno ALPHASPORT_DB_PASSWORD.
 *
 * Mantiene una sola conexion abierta y la reutiliza (se vuelve a abrir si se cae). Todo el trabajo se hace
 * dentro de una transaccion: {@link #ejecutar} confirma si todo salio bien y deshace todo si algo falla.
 */
public final class ConexionDB implements AutoCloseable {

    public static final Path ARCHIVO = Path.of("db.properties");
    /** Version del esquema que necesita el programa (la ultima de database/migraciones). */
    public static final int VERSION_ESQUEMA = 1;

    /** Datos de conexion. */
    public record Configuracion(String host, int puerto, String base, String usuario, String clave) {

        public String url() {
            return "jdbc:postgresql://" + host + ":" + puerto + "/" + base;
        }

        /** Sin la contrasena: para mostrar en mensajes. */
        @Override
        public String toString() {
            return usuario + "@" + host + ":" + puerto + "/" + base;
        }
    }

    /** Trabajo con la base de datos que devuelve un resultado. */
    public interface Trabajo<T> {
        T hacer(Connection con) throws SQLException, PersistenciaException;
    }

    private final Configuracion configuracion;
    private final String esquema;
    private Connection conexion;

    public ConexionDB(Configuracion configuracion) {
        this(configuracion, null);
    }

    /** @param esquema esquema donde trabajar (las pruebas usan uno temporal); null = el normal */
    public ConexionDB(Configuracion configuracion, String esquema) {
        this.configuracion = configuracion;
        this.esquema = esquema;
    }

    /** Configuracion de db.properties con los valores por defecto para lo que falte. */
    public static Configuracion leerConfiguracion(Path archivo) throws PersistenciaException {
        Properties p = new Properties();
        if (Files.isRegularFile(archivo)) {
            try (Reader lector = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
                p.load(lector);
            } catch (IOException | IllegalArgumentException e) {
                throw new PersistenciaException("No se pudo leer " + archivo, e);
            }
        }
        String clave = p.getProperty("db.password");
        if (clave == null) {
            String entorno = System.getenv("ALPHASPORT_DB_PASSWORD");
            clave = entorno == null ? "" : entorno;
        }
        int puerto;
        try {
            puerto = Integer.parseInt(p.getProperty("db.port", "5432").trim());
        } catch (NumberFormatException e) {
            throw new PersistenciaException("El puerto (db.port) de " + archivo + " no es un número", e);
        }
        return new Configuracion(p.getProperty("db.host", "localhost").trim(), puerto,
                p.getProperty("db.name", "alphasport_db").trim(), p.getProperty("db.user", "postgres").trim(), clave.trim());
    }

    public Configuracion getConfiguracion() {
        return configuracion;
    }

    /** Hace el trabajo en una transaccion: confirma al final o deshace todo si hay un error. */
    public synchronized <T> T ejecutar(Trabajo<T> trabajo) throws PersistenciaException {
        Connection con = abierta();
        try {
            T resultado = trabajo.hacer(con);
            con.commit();
            return resultado;
        } catch (SQLException e) {
            deshacer(con);
            throw traducir(e);
        } catch (PersistenciaException | RuntimeException e) {
            deshacer(con);
            throw e;
        }
    }

    /** Comprueba que se puede conectar, que ya se ejecuto schema.sql y que no faltan migraciones. */
    public void verificar() throws PersistenciaException {
        int version = ejecutar(con -> {
            if (Sql.uno(con, "SELECT to_regclass('torneos') IS NOT NULL", rs -> rs.getBoolean(1))) {
                return Sql.uno(con, "SELECT to_regclass('esquema_version') IS NOT NULL", rs -> rs.getBoolean(1))
                        ? Sql.uno(con, "SELECT COALESCE(MAX(version), 0) FROM esquema_version", rs -> rs.getInt(1)) : 0;
            }
            return -1;
        });
        if (version < 0) {
            throw new PersistenciaException("La base de datos " + configuracion.base()
                    + " no tiene las tablas de AlphaSport. Ejecute database/schema.sql (ver README_DB.md).", null);
        }
        if (version < VERSION_ESQUEMA) {
            throw new PersistenciaException("La base de datos " + configuracion.base() + " está en la versión " + version + " y el programa necesita la "
                    + VERSION_ESQUEMA + ". Ejecute en orden los archivos de database/migraciones que falten (ver README_DB.md).", null);
        }
    }

    private Connection abierta() throws PersistenciaException {
        try {
            if (conexion == null || conexion.isClosed() || !conexion.isValid(3)) {
                close();
                Properties p = new Properties();
                p.setProperty("user", configuracion.usuario());
                p.setProperty("password", configuracion.clave());
                p.setProperty("connectTimeout", "5");
                p.setProperty("ApplicationName", "AlphaSport");
                if (esquema != null) {
                    p.setProperty("currentSchema", esquema);
                }
                conexion = DriverManager.getConnection(configuracion.url(), p);
                conexion.setAutoCommit(false);
            }
            return conexion;
        } catch (SQLException e) {
            throw traducir(e);
        }
    }

    private void deshacer(Connection con) {
        try {
            con.rollback();
        } catch (SQLException e) {
            close(); // la conexion se cayo: la siguiente operacion abre otra
        }
    }

    /** Mensaje entendible segun el tipo de error de PostgreSQL. */
    private PersistenciaException traducir(SQLException e) {
        String estado = e.getSQLState() == null ? "" : e.getSQLState();
        String donde = configuracion.host() + ":" + configuracion.puerto();
        if (estado.startsWith("08")) {
            return new PersistenciaException("No hay conexión con PostgreSQL en " + donde + ". ¿Está encendido el servicio?", e);
        }
        return switch (estado) {
            case "28P01", "28000" -> new PersistenciaException("PostgreSQL rechazó el usuario o la contraseña de " + configuracion.usuario()
                    + ". Revise db.properties.", e);
            case "3D000" -> new PersistenciaException("La base de datos " + configuracion.base() + " no existe. Créela en pgAdmin (ver README_DB.md).", e);
            case "42P01" -> new PersistenciaException("Faltan tablas en " + configuracion.base() + ". Ejecute database/schema.sql (ver README_DB.md).", e);
            default -> new PersistenciaException("Error en la base de datos: " + e.getMessage(), e);
        };
    }

    @Override
    public synchronized void close() {
        if (conexion != null) {
            try {
                conexion.close();
            } catch (SQLException e) {
                // ya estaba cerrada
            }
            conexion = null;
        }
    }
}
