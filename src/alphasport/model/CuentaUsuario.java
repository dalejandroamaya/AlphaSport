package alphasport.model;

/**
 * Cuenta con la que se inicia sesion: lo que guarda un {@link alphasport.persistence.RepositorioUsuarios}
 * (en PostgreSQL, una fila de la tabla usuarios).
 *
 * @param usuario   nombre para entrar (username), en minusculas
 * @param nombre    nombre que se muestra
 * @param correo    correo en minusculas (null en cuentas creadas antes del registro)
 * @param claveHash hash de la clave (nunca la clave): ver alphasport.service.VerificadorClaves
 */
public record CuentaUsuario(String usuario, String nombre, String correo, Rol rol, String claveHash) {

    /** Cuenta sin correo (las de demostracion y las de las pruebas). */
    public CuentaUsuario(String usuario, String nombre, Rol rol, String claveHash) {
        this(usuario, nombre, null, rol, claveHash);
    }

    /** El usuario con su rol, para la sesion. */
    public Usuario comoUsuario() {
        return new Usuario(usuario, rol);
    }

    /** No muestra el hash de la clave si la cuenta se imprime en un mensaje o en un log. */
    @Override
    public String toString() {
        return "CuentaUsuario[usuario=" + usuario + ", rol=" + rol + "]";
    }
}
