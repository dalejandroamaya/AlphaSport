package alphasport.service;

import alphasport.model.CuentaUsuario;
import alphasport.model.Rol;
import alphasport.model.Usuario;
import alphasport.persistence.PersistenciaException;
import alphasport.persistence.RepositorioUsuarios;
import alphasport.persistence.RepositorioUsuariosEnMemoria;
import java.util.Optional;

/**
 * Valida el acceso a la aplicacion y devuelve el usuario con su rol. Las cuentas las da un
 * {@link RepositorioUsuarios}: el servicio no sabe si estan en memoria o en la base de datos.
 */
public class AutenticacionService {

    private final RepositorioUsuarios usuarios;

    public AutenticacionService(RepositorioUsuarios usuarios) {
        this.usuarios = usuarios;
    }

    /** Con las cuentas de demostracion (admin y visitante). */
    public AutenticacionService() {
        this(RepositorioUsuariosEnMemoria.demostracion());
    }

    /**
     * Inicia sesion con usuario y clave.
     *
     * @throws ReglaNegocioException con codigo LOGIN si los datos no coinciden.
     */
    public Usuario iniciarSesion(String usuario, String clave) throws PersistenciaException {
        Optional<CuentaUsuario> cuenta = usuarios.buscar(usuario);
        if (cuenta.isEmpty() || !cuenta.get().clave().equals(clave)) {
            throw new ReglaNegocioException("LOGIN", "Usuario o contraseña incorrectos.");
        }
        return cuenta.get().comoUsuario();
    }

    /** Acceso de solo lectura sin contraseña (boton «Entrar como visitante»). */
    public Usuario entrarComoVisitante() {
        return new Usuario("visitante", Rol.VISITANTE);
    }
}
