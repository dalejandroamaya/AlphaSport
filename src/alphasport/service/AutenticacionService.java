package alphasport.service;

import alphasport.model.Rol;
import alphasport.model.Usuario;
import java.util.Map;

/**
 * Valida el acceso a la aplicacion y devuelve el usuario con su rol.
 * Prototipo: las cuentas estan en memoria (sin base de datos).
 */
public class AutenticacionService {

    /** Cuenta demostrativa: clave y rol. */
    private record Cuenta(String clave, Rol rol) {
    }

    private static final Map<String, Cuenta> CUENTAS = Map.of(
            "admin", new Cuenta("123", Rol.ADMINISTRADOR),
            "visitante", new Cuenta("321", Rol.VISITANTE));

    /**
     * Inicia sesion con usuario y clave.
     *
     * @throws ReglaNegocioException con codigo LOGIN si los datos no coinciden.
     */
    public Usuario iniciarSesion(String usuario, String clave) {
        String nombre = usuario == null ? "" : usuario.trim().toLowerCase();
        Cuenta cuenta = CUENTAS.get(nombre);
        if (cuenta == null || !cuenta.clave().equals(clave)) {
            throw new ReglaNegocioException("LOGIN", "Usuario o contraseña incorrectos.");
        }
        return new Usuario(nombre, cuenta.rol());
    }

    /** Acceso de solo lectura sin contraseña (boton «Entrar como visitante»). */
    public Usuario entrarComoVisitante() {
        return new Usuario("visitante", Rol.VISITANTE);
    }
}
