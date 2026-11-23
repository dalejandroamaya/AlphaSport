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
 * {@link RepositorioUsuarios} (el servicio no sabe si estan en memoria o en la base de datos) y la clave se
 * compara con su hash con un {@link VerificadorClaves}: en ningun lado se guarda la clave.
 */
public class AutenticacionService {

    private final RepositorioUsuarios usuarios;
    private final VerificadorClaves verificador;
    /** Hash de una clave que nadie usa: con un usuario que no existe se hace el mismo trabajo (no se nota en el tiempo). */
    private final String hashFicticio;

    public AutenticacionService(RepositorioUsuarios usuarios, VerificadorClaves verificador) {
        this.usuarios = usuarios;
        this.verificador = verificador;
        this.hashFicticio = verificador.hash(java.util.UUID.randomUUID().toString());
    }

    /** Con las cuentas de demostracion (admin y visitante) y PBKDF2. */
    public AutenticacionService() {
        this(RepositorioUsuariosEnMemoria.demostracion(), new ClavesPbkdf2());
    }

    /**
     * Inicia sesion con usuario y clave.
     *
     * @throws ReglaNegocioException con codigo LOGIN si los datos no coinciden.
     */
    public Usuario iniciarSesion(String usuario, String clave) throws PersistenciaException {
        Optional<CuentaUsuario> cuenta = usuarios.buscar(usuario);
        boolean coincide = verificador.coincide(clave == null ? "" : clave, cuenta.map(CuentaUsuario::claveHash).orElse(hashFicticio));
        if (cuenta.isEmpty() || !coincide) {
            throw new ReglaNegocioException("LOGIN", "Usuario o contraseña incorrectos.");
        }
        return cuenta.get().comoUsuario();
    }

    /** Acceso de solo lectura sin contraseña (boton «Entrar como visitante»). */
    public Usuario entrarComoVisitante() {
        return new Usuario("visitante", Rol.AFICIONADO);
    }
}
