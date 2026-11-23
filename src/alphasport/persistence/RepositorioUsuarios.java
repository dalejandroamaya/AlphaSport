package alphasport.persistence;

import alphasport.model.CuentaUsuario;
import java.util.Optional;

/**
 * Cuentas de usuario para iniciar sesion y registrarse. La version real es
 * {@link alphasport.persistence.postgres.PostgresRepositorioUsuarios} (tabla usuarios); la de memoria es para
 * las pruebas. {@link alphasport.service.AutenticacionService} no sabe cual usa.
 * El usuario y el correo se comparan sin distinguir mayusculas ni espacios alrededor.
 */
public interface RepositorioUsuarios {

    /** Resultado de {@link #crear}: la cuenta quedo guardada, o por que no. */
    enum Alta {
        CREADA, USUARIO_EN_USO, CORREO_EN_USO
    }

    /** La cuenta con ese nombre de usuario, o vacio si no existe. */
    Optional<CuentaUsuario> buscar(String usuario) throws PersistenciaException;

    /** true si ya hay una cuenta con ese nombre de usuario. */
    boolean existeUsuario(String usuario) throws PersistenciaException;

    /** true si ya hay una cuenta con ese correo. */
    boolean existeCorreo(String correo) throws PersistenciaException;

    /**
     * Guarda una cuenta nueva si el usuario y el correo estan libres (primero lo consulta y luego la inserta,
     * todo junto). Nunca cambia una cuenta que ya existe.
     */
    Alta crear(CuentaUsuario cuenta) throws PersistenciaException;

    /** Usuario o correo como se guardan: sin espacios alrededor y en minusculas ("" si es null). */
    static String normalizar(String texto) {
        return texto == null ? "" : texto.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
