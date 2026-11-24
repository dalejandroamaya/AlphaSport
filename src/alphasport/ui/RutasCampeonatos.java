package alphasport.ui;

import alphasport.model.Permiso;
import alphasport.model.TipoFormato;
import alphasport.model.Usuario;
import alphasport.service.GestorCampeonatos;
import java.util.List;
import java.util.Map;

/**
 * Rutas de la lista de campeonatos (no de uno en particular): ver los que estan en curso, elegir uno, crear uno
 * nuevo, terminarlo, tomar uno sin organizador y el historial. Los permisos de dueno los revisa
 * {@link GestorCampeonatos}.
 */
final class RutasCampeonatos implements ModuloRutas {

    private final GestorCampeonatos gestor;
    private final Presentador presentador;

    RutasCampeonatos(GestorCampeonatos gestor, Presentador presentador) {
        this.gestor = gestor;
        this.presentador = presentador;
    }

    @Override
    public void registrar(Rutas<Rutas.Consulta> consultas, Rutas<Rutas.Accion> acciones) {
        consultas.con(Permiso.VER, "campeonatos", p -> {
            Usuario u = p.api().getUsuarioActual();
            return Presentador.mapa("ok", true, "campeonatos", lista(gestor.activos(u)), "sinOrganizador", lista(gestor.sinOrganizador(u)),
                    "elegido", p.api().codigoElegido());
        }).con(Permiso.VER, "historial", p -> presentador.historial(gestor.historial(p.api().getUsuarioActual())));
        Permiso gestionar = Permiso.GESTIONAR_CAMPEONATO;
        acciones.con(Permiso.VER, "campeonato/elegir", p -> {
            String codigo = p.texto("codigo");
            gestor.paraLeer(codigo, p.api().getUsuarioActual()); // PERMISO si es de otro organizador
            p.api().elegir(codigo);
            return p.listo(null);
        }).con(gestionar, "nuevo", p -> {
            // Con «mismos equipos» se copian los del campeonato elegido (que tiene que ser suyo).
            GestorCampeonatos.Creado creado = gestor.crear(p.api().getUsuarioActual(), p.texto("nombre"), TipoFormato.valueOf(p.texto("formato")),
                    RutasCampeonato.configuracion(p.objeto("config")), p.si("mismosEquipos") ? p.api().codigoElegido() : null);
            p.api().elegir(creado.servicio().getCampeonato().getCodigo());
            return p.listo(creado.movimientos().isEmpty() ? null : creado.movimientos()); // ascensos y descensos aplicados, si hubo
        }).con(gestionar, "campeonato/terminar", p -> {
            String codigo = p.tiene("codigo") ? p.texto("codigo") : p.api().codigoElegido();
            gestor.terminar(codigo, p.api().getUsuarioActual());
            p.api().olvidarElegido(codigo);
            return p.listo("El campeonato terminó y quedó en el historial.");
        }).con(gestionar, "campeonato/tomar", p -> {
            String codigo = p.texto("codigo");
            gestor.tomar(codigo, p.api().getUsuarioActual());
            p.api().elegir(codigo);
            return p.listo("Ahora usted organiza este campeonato.");
        }).con(gestionar, "historial/eliminar", p -> {
            gestor.eliminarDelHistorial(p.texto("codigo"), p.api().getUsuarioActual());
            return p.listo(null);
        });
    }

    static List<Object> lista(List<GestorCampeonatos.Resumen> resumenes) {
        return resumenes.stream().map(r -> (Object) resumen(r)).toList();
    }

    static Map<String, Object> resumen(GestorCampeonatos.Resumen r) {
        return Presentador.mapa("codigo", r.codigo(), "nombre", r.nombre(), "formato", r.formato().name(), "formatoNombre", r.formato().getNombre(),
                "estado", r.estado(), "fase", r.fase(), "organizador", r.organizador(), "sinOrganizador", r.propietario() == 0,
                "equipos", r.equipos(), "partidosJugados", r.partidosJugados(), "partidos", r.partidos());
    }
}
