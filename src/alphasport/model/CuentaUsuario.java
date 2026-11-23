package alphasport.model;

/**
 * Cuenta con la que se inicia sesion: lo que guarda un {@link alphasport.persistence.RepositorioUsuarios}
 * (en PostgreSQL, una fila de la tabla usuarios).
 *
 * @param usuario nombre para entrar (username), en minusculas
 * @param nombre  nombre que se muestra
 * @param claveHash hash de la clave (nunca la clave): ver alphasport.service.VerificadorClaves
 */
public record CuentaUsuario(String usuario, String nombre, Rol rol, String claveHash) {

    /** El usuario con su rol, para la sesion. */
    public Usuario comoUsuario() {
        return new Usuario(usuario, rol);
    }
}
