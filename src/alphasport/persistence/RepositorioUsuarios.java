package alphasport.persistence;

import alphasport.model.CuentaUsuario;
import java.util.Optional;

/**
 * Cuentas de usuario para iniciar sesion. Hoy son las de demostracion ({@link RepositorioUsuariosEnMemoria});
 * con el login del siguiente sprint, una implementacion de PostgreSQL leera la tabla usuarios sin cambiar
 * {@link alphasport.service.AutenticacionService}.
 */
public interface RepositorioUsuarios {

    /** La cuenta con ese nombre de usuario (sin distinguir mayusculas ni espacios alrededor), o vacio si no existe. */
    Optional<CuentaUsuario> buscar(String usuario) throws PersistenciaException;
}
