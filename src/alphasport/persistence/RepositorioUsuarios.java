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

    /* ---------------------------------------------------------------- Mi perfil */

    /**
     * Estado de la verificacion del correo de una cuenta.
     *
     * @param codigoHash hash del codigo de 6 digitos pendiente, o null si no hay ninguno
     * @param venceEn milisegundos desde 1970 en que vence el codigo (0 si no hay)
     * @param intentos intentos fallidos con el codigo actual
     */
    record Verificacion(boolean correoVerificado, String codigoHash, long venceEn, int intentos) {

        public static final Verificacion NINGUNA = new Verificacion(false, null, 0, 0);
    }

    /**
     * Cambia el nombre y el correo de la cuenta (el usuario no cambia). Si el correo es otro, la cuenta queda
     * con el correo sin verificar y sin codigo pendiente.
     *
     * @return CORREO_EN_USO si otra cuenta ya tiene ese correo (no cambia nada); CREADA si se guardo
     */
    Alta actualizarDatos(String usuario, String nombre, String correo) throws PersistenciaException;

    /** Guarda el hash de la clave nueva. */
    void cambiarClave(String usuario, String claveHash) throws PersistenciaException;

    /** Guarda (o, con codigoHash null, borra) el codigo de verificacion, su vencimiento y los intentos fallidos. */
    void guardarCodigo(String usuario, String codigoHash, long venceEn, int intentos) throws PersistenciaException;

    /** El estado de la verificacion del correo; NINGUNA si la cuenta no existe. */
    Verificacion verificacion(String usuario) throws PersistenciaException;

    /** Marca el correo como verificado y borra el codigo. */
    void marcarVerificado(String usuario) throws PersistenciaException;

    /** Usuario o correo como se guardan: sin espacios alrededor y en minusculas ("" si es null). */
    static String normalizar(String texto) {
        return texto == null ? "" : texto.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
