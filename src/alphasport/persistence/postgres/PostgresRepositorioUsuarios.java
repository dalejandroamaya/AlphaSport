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
