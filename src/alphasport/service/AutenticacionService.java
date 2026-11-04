package alphasport.service;

/**
 * Valida el acceso a la aplicacion (usuario demostrativo del prototipo).
 */
public class AutenticacionService {

    private static final String USUARIO = "admin";
    private static final String CLAVE = "123";

    public boolean autenticar(String usuario, String clave) {
        return USUARIO.equals(usuario == null ? null : usuario.trim()) && CLAVE.equals(clave);
    }
}
