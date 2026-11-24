package alphasport.model;

/**
 * Cuenta con la que se inicia sesion: lo que guarda un {@link alphasport.persistence.RepositorioUsuarios}
 * (en PostgreSQL, una fila de la tabla usuarios).
 *
 * @param usuario   nombre para entrar (username), en minusculas
 * @param nombre    nombre que se muestra
 * @param correo    correo en minusculas (null en cuentas creadas antes del registro)
 * @param claveHash hash de la clave (nunca la clave): ver alphasport.service.VerificadorClaves
 * @param id        usuarios.id (0 mientras la cuenta no esta guardada)
 */
public record CuentaUsuario(String usuario, String nombre, String correo, Rol rol, String claveHash, int id) {

    /** Cuenta nueva, todavia sin id: el repositorio se lo da al guardarla. */
    public CuentaUsuario(String usuario, String nombre, String correo, Rol rol, String claveHash) {
        this(usuario, nombre, correo, rol, claveHash, 0);
    }

    /** Cuenta sin correo (las de las pruebas). */
    public CuentaUsuario(String usuario, String nombre, Rol rol, String claveHash) {
        this(usuario, nombre, null, rol, claveHash, 0);
    }

    /** La misma cuenta con su id. */
    public CuentaUsuario conId(int nuevoId) {
        return new CuentaUsuario(usuario, nombre, correo, rol, claveHash, nuevoId);
    }

    /** El usuario con su id y su rol, para la sesion. */
    public Usuario comoUsuario() {
        return new Usuario(id, usuario, rol);
    }

    /** No muestra el hash de la clave si la cuenta se imprime en un mensaje o en un log. */
    @Override
    public String toString() {
        return "CuentaUsuario[id=" + id + ", usuario=" + usuario + ", rol=" + rol + "]";
    }
}
