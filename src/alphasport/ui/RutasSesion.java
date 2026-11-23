package alphasport.ui;

import alphasport.service.SesionService;

/** Rutas publicas de la sesion: entrar, entrar como visitante, salir y consultar quien esta. */
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
        }).publica("login/visitante", p -> {
            p.api().entrar(sesion.entrarComoVisitante());
            return p.listo(null);
        }).publica("logout", p -> {
            p.api().salir();
            return Presentador.mapa("ok", true);
        });
    }
}
