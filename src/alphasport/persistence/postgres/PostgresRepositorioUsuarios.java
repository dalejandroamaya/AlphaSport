package alphasport.persistence.postgres;

import alphasport.model.CuentaUsuario;
import alphasport.model.Rol;
import alphasport.persistence.PersistenciaException;
import alphasport.persistence.RepositorioUsuarios;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Cuentas en la tabla usuarios, con JDBC y SQL basico: SELECT para leer o saber si algo existe, e INSERT para
 * crear. Cada dato viaja como parametro (?) de un PreparedStatement, nunca pegado al texto del SQL. El
 * usuario y el correo se guardan en minusculas, asi la llave UNIQUE no distingue mayusculas.
 */
public class PostgresRepositorioUsuarios implements RepositorioUsuarios {

    private final ConexionDB conexion;

    public PostgresRepositorioUsuarios(ConexionDB conexion) {
        this.conexion = conexion;
    }

    @Override
    public Optional<CuentaUsuario> buscar(String usuario) throws PersistenciaException {
        String sql = "SELECT id, username, nombre, email, rol, password_hash FROM usuarios WHERE username = ?";
        return conexion.ejecutar(con -> {
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, RepositorioUsuarios.normalizar(usuario));
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(cuenta(rs)) : Optional.<CuentaUsuario>empty();
                }
            }
        });
    }

    @Override
    public boolean existeUsuario(String usuario) throws PersistenciaException {
        return conexion.ejecutar(con -> existe(con, "SELECT COUNT(*) FROM usuarios WHERE username = ?", usuario));
    }

    @Override
    public boolean existeCorreo(String correo) throws PersistenciaException {
        return conexion.ejecutar(con -> existe(con, "SELECT COUNT(*) FROM usuarios WHERE email = ?", correo));
    }

    /**
     * En una sola transaccion: SELECT para saber si el usuario o el correo ya estan, y si no, INSERT. Si otro
     * programa registra el mismo usuario en ese instante, la llave UNIQUE rechaza el INSERT y se deshace todo.
     */
    @Override
    public Alta crear(CuentaUsuario cuenta) throws PersistenciaException {
        return conexion.ejecutar(con -> {
            if (existe(con, "SELECT COUNT(*) FROM usuarios WHERE username = ?", cuenta.usuario())) {
                return Alta.USUARIO_EN_USO;
            }
            if (cuenta.correo() != null && existe(con, "SELECT COUNT(*) FROM usuarios WHERE email = ?", cuenta.correo())) {
                return Alta.CORREO_EN_USO;
            }
            String sql = "INSERT INTO usuarios (nombre, username, email, password_hash, rol) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, cuenta.nombre());
                ps.setString(2, RepositorioUsuarios.normalizar(cuenta.usuario()));
                ps.setString(3, cuenta.correo() == null ? null : RepositorioUsuarios.normalizar(cuenta.correo()));
                ps.setString(4, cuenta.claveHash());
                ps.setString(5, cuenta.rol().name());
                ps.executeUpdate();
            }
            return Alta.CREADA;
        });
    }

    /* ---------------------------------------------------------------- Mi perfil (SQL basico: SELECT y UPDATE) */

    @Override
    public Alta actualizarDatos(String usuario, String nombre, String correo) throws PersistenciaException {
        String u = RepositorioUsuarios.normalizar(usuario), nuevo = RepositorioUsuarios.normalizar(correo);
        return conexion.ejecutar(con -> {
            String actual = null;
            try (PreparedStatement ps = con.prepareStatement("SELECT email FROM usuarios WHERE username = ?")) {
                ps.setString(1, u);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Alta.CREADA;
                    }
                    actual = rs.getString(1);
                }
            }
            boolean otroCorreo = !nuevo.equals(RepositorioUsuarios.normalizar(actual));
            if (otroCorreo && existe(con, "SELECT COUNT(*) FROM usuarios WHERE email = ?", nuevo)) {
                return Alta.CORREO_EN_USO;
            }
            // Otro correo: queda sin verificar y sin codigo pendiente.
            String sql = otroCorreo
                    ? "UPDATE usuarios SET nombre = ?, email = ?, correo_verificado = FALSE, codigo_verificacion = NULL, codigo_vence = NULL, codigo_intentos = 0 WHERE username = ?"
                    : "UPDATE usuarios SET nombre = ?, email = ? WHERE username = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, nombre);
                ps.setString(2, nuevo);
                ps.setString(3, u);
                ps.executeUpdate();
            }
            return Alta.CREADA;
        });
    }

    @Override
    public void cambiarClave(String usuario, String claveHash) throws PersistenciaException {
        actualizar("UPDATE usuarios SET password_hash = ? WHERE username = ?", claveHash, usuario);
    }

    @Override
    public void guardarCodigo(String usuario, String codigoHash, long venceEn, int intentos) throws PersistenciaException {
        conexion.ejecutar(con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE usuarios SET codigo_verificacion = ?, codigo_vence = ?, codigo_intentos = ? WHERE username = ?")) {
                ps.setString(1, codigoHash);
                ps.setTimestamp(2, codigoHash == null ? null : new java.sql.Timestamp(venceEn));
                ps.setInt(3, codigoHash == null ? 0 : intentos);
                ps.setString(4, RepositorioUsuarios.normalizar(usuario));
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Verificacion verificacion(String usuario) throws PersistenciaException {
        String sql = "SELECT correo_verificado, codigo_verificacion, codigo_vence, codigo_intentos FROM usuarios WHERE username = ?";
        return conexion.ejecutar(con -> {
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, RepositorioUsuarios.normalizar(usuario));
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Verificacion.NINGUNA;
                    }
                    java.sql.Timestamp vence = rs.getTimestamp(3);
                    return new Verificacion(rs.getBoolean(1), rs.getString(2), vence == null ? 0 : vence.getTime(), rs.getInt(4));
                }
            }
        });
    }

    @Override
    public void marcarVerificado(String usuario) throws PersistenciaException {
        actualizar("UPDATE usuarios SET correo_verificado = TRUE, codigo_verificacion = NULL, codigo_vence = NULL, codigo_intentos = 0"
                + " WHERE username = ?", usuario);
    }

    /** UPDATE cuyos parametros son los valores dados, en orden (el ultimo, el usuario). */
    private void actualizar(String sql, String... valores) throws PersistenciaException {
        conexion.ejecutar(con -> {
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                for (int i = 0; i < valores.length; i++) {
                    ps.setString(i + 1, i == valores.length - 1 ? RepositorioUsuarios.normalizar(valores[i]) : valores[i]);
                }
                ps.executeUpdate();
            }
            return null;
        });
    }

    private static boolean existe(Connection con, String sql, String valor) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, RepositorioUsuarios.normalizar(valor));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    /** De la fila al objeto. Un rol desconocido no deja entrar (no se adivina un rol). */
    private static CuentaUsuario cuenta(ResultSet rs) throws SQLException {
        Rol rol = Rol.desdeGuardado(rs.getString("rol"));
        if (rol == null) {
            throw new SQLException("La cuenta " + rs.getString("username") + " tiene un rol desconocido: " + rs.getString("rol"));
        }
        return new CuentaUsuario(rs.getString("username"), rs.getString("nombre"), rs.getString("email"), rol, rs.getString("password_hash"),
                rs.getInt("id"));
    }
}
