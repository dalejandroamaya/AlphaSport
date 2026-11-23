package alphasport.service;

import alphasport.model.Permiso;
import alphasport.model.Usuario;

/**
 * Sesion del usuario: quien entro, con que rol, y si puede hacer cada accion. El controlador solo le pregunta;
 * la autenticacion (validar la clave) la hace {@link AutenticacionService}.
 */
public class SesionService {

    private final AutenticacionService autenticacion;
    /** Usuario con sesion abierta; null si nadie ha iniciado sesion. */
    private Usuario usuario;

    public SesionService(AutenticacionService autenticacion) {
        this.autenticacion = autenticacion;
    }

    /** @throws ReglaNegocioException con codigo LOGIN si el usuario o la clave no coinciden. */
    public Usuario iniciar(String nombre, String clave) {
        usuario = autenticacion.iniciarSesion(nombre, clave);
        return usuario;
    }

    /** Acceso de solo lectura sin contraseña (boton «Entrar como visitante»). */
    public Usuario entrarComoVisitante() {
        usuario = autenticacion.entrarComoVisitante();
        return usuario;
    }

    public void cerrar() {
        usuario = null;
    }

    /** null si no hay sesion. */
    public Usuario usuario() {
        return usuario;
    }

    public boolean puede(Permiso permiso) {
        return usuario != null && usuario.puede(permiso);
    }

    /** Lanza LOGIN si no hay sesion. */
    public Usuario exigirSesion() {
        if (usuario == null) {
            throw new ReglaNegocioException("LOGIN", "Inicie sesión para continuar.");
        }
        return usuario;
    }

    /** Lanza LOGIN si no hay sesion y PERMISO si el rol no tiene ese permiso. */
    public Usuario exigir(Permiso permiso) {
        Usuario u = exigirSesion();
        if (!u.puede(permiso)) {
            throw new ReglaNegocioException("PERMISO", "Su rol (" + u.getRol().getNombre() + ") no puede " + permiso.getAccion() + ".");
        }
        return u;
    }
}
