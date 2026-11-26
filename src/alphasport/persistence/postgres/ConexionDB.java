package alphasport.persistence.postgres;

import alphasport.persistence.PersistenciaException;
import alphasport.persistence.UnidadDeTrabajo;
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
public final class ConexionDB implements AutoCloseable, UnidadDeTrabajo {

    public static final Path ARCHIVO = Path.of("db.properties");
    /** Version del esquema que necesita el programa (la ultima de database/migraciones). */
    public static final int VERSION_ESQUEMA = 9;

    /** Datos de conexion. */
    /**
     * @param ssl modo SSL del driver (db.ssl): vacío = como el servidor lo permita (base local);
     *            «require» = conexión cifrada obligatoria, como piden las bases en la nube (Neon).
     */
    public record Configuracion(String host, int puerto, String base, String usuario, String clave, String ssl) {

        /** Sin SSL configurado (base local o pruebas). */
        public Configuracion(String host, int puerto, String base, String usuario, String clave) {
            this(host, puerto, base, usuario, clave, "");
        }

        /** true si la conexión debe ir cifrada (una base en internet). */
        public boolean conSsl() {
            return ssl != null && !ssl.isBlank() && !ssl.equalsIgnoreCase("disable");
        }

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
    /** Conexion de la transaccion abierta (null = ninguna): un trabajo dentro de otro se une a ella. */
    private Connection enCurso;

    public ConexionDB(Configuracion configuracion) {
        this(configuracion, null);
    }

    /** @param esquema esquema donde trabajar (las pruebas usan uno temporal); null = el normal */
    public ConexionDB(Configuracion configuracion, String esquema) {
        this.configuracion = configuracion;
        this.esquema = esquema;
    }

    /**
     * Configuracion de db.properties con los valores por defecto para lo que falte. Si no hay db.properties y
     * existe la variable de entorno DATABASE_URL (asi se configura en Render), se usa esa cadena. En
     * db.properties, db.url (la cadena completa) reemplaza a db.host, db.user, db.password...
     */
    public static Configuracion leerConfiguracion(Path archivo) throws PersistenciaException {
        String cadena = System.getenv("DATABASE_URL");
        if (!Files.isRegularFile(archivo) && cadena != null && !cadena.isBlank()) {
            return desdeCadena(cadena.trim());
        }
        Properties p = new Properties();
        if (Files.isRegularFile(archivo)) {
            try (Reader lector = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
                p.load(lector);
            } catch (IOException | IllegalArgumentException e) {
                throw new PersistenciaException("No se pudo leer " + archivo, e);
            }
        }
        // db.url: la cadena completa de Neon (la escribe configurar_base.bat).
        String url = p.getProperty("db.url");
        if (url != null && !url.isBlank()) {
            return desdeCadena(url.trim());
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
        String ssl = p.getProperty("db.ssl", "").trim().toLowerCase();
        if (!ssl.isEmpty() && !java.util.Set.of("disable", "allow", "prefer", "require", "verify-ca", "verify-full").contains(ssl)) {
            throw new PersistenciaException("db.ssl de " + archivo + " debe ser require (bases en la nube como Neon) o quedar vacío: " + ssl, null);
        }
        return new Configuracion(p.getProperty("db.host", "localhost").trim(), puerto,
                p.getProperty("db.name", "alphasport_db").trim(), p.getProperty("db.user", "postgres").trim(), clave.trim(), ssl);
    }

    /**
     * Lee una cadena como la que da Neon: postgresql://usuario:clave@servidor/base?sslmode=require.
     * Usa la conexion directa (sin «-pooler») y, si no dice sslmode, conexion cifrada.
     */
    static Configuracion desdeCadena(String cadena) throws PersistenciaException {
        try {
            java.net.URI u = new java.net.URI(cadena.replaceFirst("^postgres(ql)?://", "http://"));
            String datos = u.getRawUserInfo();
            if (u.getHost() == null || datos == null || !datos.contains(":")) {
                throw new PersistenciaException("DATABASE_URL debe tener la forma postgresql://usuario:clave@servidor/base", null);
            }
            String usuario = java.net.URLDecoder.decode(datos.substring(0, datos.indexOf(':')), StandardCharsets.UTF_8);
            String clave = java.net.URLDecoder.decode(datos.substring(datos.indexOf(':') + 1), StandardCharsets.UTF_8);
            String base = u.getPath() == null || u.getPath().length() <= 1 ? "neondb" : u.getPath().substring(1);
            String ssl = "require";
            if (u.getRawQuery() != null) {
                for (String parte : u.getRawQuery().split("&")) {
                    if (parte.startsWith("sslmode=")) {
                        ssl = parte.substring("sslmode=".length()).toLowerCase();
                    }
                }
            }
            if (!java.util.Set.of("disable", "allow", "prefer", "require", "verify-ca", "verify-full").contains(ssl)) {
                ssl = "require";
            }
            return new Configuracion(u.getHost().replace("-pooler", ""), u.getPort() > 0 ? u.getPort() : 5432, base, usuario, clave, ssl);
        } catch (java.net.URISyntaxException | IllegalArgumentException e) {
            throw new PersistenciaException("DATABASE_URL no es una cadena de conexión válida (postgresql://…)", null);
        }
    }

    public Configuracion getConfiguracion() {
        return configuracion;
    }

    /**
     * Hace el trabajo en una transaccion: confirma al final o deshace todo si hay un error. Dentro de otra
     * transaccion de este mismo hilo (una unidad de trabajo) el trabajo se une a ella: se confirma o se deshace
     * con todo lo demas. Mientras dura, los demas hilos esperan.
     */
    public synchronized <T> T ejecutar(Trabajo<T> trabajo) throws PersistenciaException {
        if (enCurso != null) {
            try {
                return trabajo.hacer(enCurso);
            } catch (SQLException e) {
                throw traducir(e); // la transaccion de afuera lo deshace todo
            }
        }
        Connection con = abierta();
        enCurso = con;
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
        } finally {
            enCurso = null;
        }
    }

    @Override
    public <T> T enTransaccion(UnidadDeTrabajo.Paso<T> paso) throws PersistenciaException {
        return ejecutar(con -> paso.hacer());
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
                // Una base en internet (Neon) puede tardar unos segundos en «despertar» la primera vez.
                p.setProperty("connectTimeout", configuracion.conSsl() ? "30" : "5");
                p.setProperty("ApplicationName", "AlphaSport");
                if (configuracion.conSsl()) {
                    p.setProperty("sslmode", configuracion.ssl());
                    p.setProperty("tcpKeepAlive", "true"); // que el router no corte la conexión mientras no se usa
                }
                if (esquema != null) {
                    p.setProperty("currentSchema", esquema);
                }
                conexion = conectar(p);
                if (esquema != null) {
                    // Algunos servidores en internet (Neon) no aplican currentSchema al conectar: se fija aquí, antes de
                    // abrir la transacción, para que un ROLLBACK no lo deshaga.
                    try (java.sql.Statement st = conexion.createStatement()) {
                        st.execute("SET search_path TO \"" + esquema.replace("\"", "\"\"") + "\"");
                    }
                }
                conexion.setAutoCommit(false);
            }
            return conexion;
        } catch (SQLException e) {
            throw traducir(e);
        }
    }

    /** Abre la conexión. Con una base en internet, si falla la red lo intenta otra vez antes de rendirse. */
    private Connection conectar(Properties p) throws SQLException {
        int intentos = configuracion.conSsl() ? 3 : 1;
        for (int i = 1; ; i++) {
            try {
                return DriverManager.getConnection(configuracion.url(), p);
            } catch (SQLException e) {
                String estado = e.getSQLState() == null ? "" : e.getSQLState();
                if (i >= intentos || !estado.startsWith("08")) {
                    throw e;
                }
                try {
                    Thread.sleep(1000L * i);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
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
            return new PersistenciaException(configuracion.conSsl()
                    ? "No hay conexión con la base de datos en internet (" + configuracion.host() + "). Revise su conexión a internet e intente de nuevo."
                    : "No hay conexión con PostgreSQL en " + donde + ". ¿Está encendido el servicio?", e);
        }
        return switch (estado) {
            case "28P01", "28000" -> new PersistenciaException("PostgreSQL rechazó el usuario o la contraseña de " + configuracion.usuario()
                    + ". Revise db.properties.", e);
            case "3D000" -> new PersistenciaException("La base de datos " + configuracion.base() + " no existe. Créela en pgAdmin (ver README_DB.md).", e);
            case "42P01" -> new PersistenciaException("Faltan tablas en " + configuracion.base() + ". Ejecute database/schema.sql (ver README_DB.md).", e);
            case "23P01" -> new PersistenciaException(e.getMessage() != null && e.getMessage().contains("jugadores_documento_unico")
                    ? "Dos jugadores del campeonato tienen el mismo documento. Cada jugador debe tener el suyo: revise los documentos."
                    : e.getMessage() != null && e.getMessage().contains("jugadores_dorsal_unico")
                    ? "Dos jugadores del mismo equipo tienen el mismo dorsal." : "Error en la base de datos: " + e.getMessage(), e);
            // Dos registros con el mismo usuario o correo al mismo tiempo: la llave UNIQUE frena el segundo.
            case "23505" -> new PersistenciaException(e.getMessage() != null && e.getMessage().contains("usuarios_")
                    ? "Ese nombre de usuario o correo ya está registrado. Elija otro." : "Error en la base de datos: " + e.getMessage(), e);
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
