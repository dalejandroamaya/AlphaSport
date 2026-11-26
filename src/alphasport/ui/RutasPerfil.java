package alphasport.ui;

import alphasport.model.Permiso;
import alphasport.service.PerfilService;
import alphasport.service.SesionService;
import java.util.Map;

/**
 * «Mi perfil» (Organizador y Aficionado): ver y editar datos, cambiar la contraseña y verificar el correo. La
 * cuenta sale SIEMPRE de la sesion; un «usuario» que venga en el cuerpo de la peticion se ignora.
 */
final class RutasPerfil implements ModuloRutas {

    private final SesionService sesion;

    RutasPerfil(SesionService sesion) {
        this.sesion = sesion;
    }

    @Override
    public void registrar(Rutas<Rutas.Consulta> consultas, Rutas<Rutas.Accion> acciones) {
        PerfilService perfil = sesion.perfil();
        consultas.con(Permiso.VER, "perfil", p -> {
            try {
                return Presentador.mapa("ok", true, "perfil", perfil.ver(usuario(p)));
            } catch (alphasport.persistence.PersistenciaException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        });
        acciones.con(Permiso.VER, "perfil/datos", p -> {
            String mensaje = perfil.actualizarDatos(usuario(p), p.texto("nombre"), p.texto("correo"), p.texto("claveActual"));
            return respuesta(perfil, p, mensaje);
        }).con(Permiso.VER, "perfil/clave", p -> {
            perfil.cambiarClave(usuario(p), p.texto("claveActual"), p.texto("clave"), p.texto("confirmacion"));
            return respuesta(perfil, p, "Contraseña cambiada.");
        }).con(Permiso.VER, "perfil/codigo", p -> respuesta(perfil, p, perfil.enviarCodigo(usuario(p))))
          .con(Permiso.VER, "perfil/verificar", p -> {
              perfil.verificarCodigo(usuario(p), p.texto("codigo"));
              return respuesta(perfil, p, "¡Correo verificado!");
          });
    }

    /** El usuario de la sesion abierta (nunca el del cuerpo). */
    private static String usuario(Peticion p) {
        return p.api().getUsuarioActual().getNombre();
    }

    private static Map<String, Object> respuesta(PerfilService perfil, Peticion p, String mensaje) throws Exception {
        return Presentador.mapa("ok", true, "mensaje", mensaje, "perfil", perfil.ver(usuario(p)));
    }
}
