package alphasport.ui;

import alphasport.service.AutenticacionService;
import alphasport.service.SesionService;

/** Rutas publicas de la sesion: registrarse, entrar, salir y consultar quien esta. Sin sesion no se entra. */
final class RutasSesion implements ModuloRutas {

    private final SesionService sesion;

    RutasSesion(SesionService sesion) {
        this.sesion = sesion;
    }

    @Override
    public void registrar(Rutas<Rutas.Consulta> consultas, Rutas<Rutas.Accion> acciones) {
        consultas.publica("sesion", p -> Presentador.mapa("ok", true, "sesion", p.api().sesion()));
        acciones.publica("login", p -> {
            p.api().entrar(sesion.iniciar(p.texto("usuario"), p.texto("clave")));
            return p.listo(null);
        }).publica("registro", p -> {
            // El tipo de cuenta llega tal cual: el servicio solo acepta ORGANIZADOR o AFICIONADO.
            p.api().entrar(sesion.registrar(new AutenticacionService.SolicitudRegistro(p.texto("usuario"), p.texto("nombre"),
                    p.texto("correo"), p.texto("clave"), p.texto("confirmacion"), p.texto("tipo"))));
            return p.listo("Cuenta creada. ¡Le damos la bienvenida a AlphaSport!");
        }).publica("logout", p -> {
            p.api().salir();
            return Presentador.mapa("ok", true);
        });
    }
}
