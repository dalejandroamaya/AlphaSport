package alphasport.model;

/**
 * Cuenta con la que se inicia sesion: lo que guarda un {@link alphasport.persistence.RepositorioUsuarios}
 * (en PostgreSQL, una fila de la tabla usuarios).
 *
 * @param usuario nombre para entrar (username), en minusculas
 * @param nombre  nombre que se muestra
 * @param clave   clave guardada de la cuenta
 */
public record CuentaUsuario(String usuario, String nombre, Rol rol, String clave) {

    /** El usuario con su rol, para la sesion. */
    public Usuario comoUsuario() {
        return new Usuario(usuario, rol);
    }
}
