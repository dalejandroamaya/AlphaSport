package alphasport.ui;

import alphasport.model.Permiso;
import alphasport.service.NoticiasService;
import java.util.List;
import java.util.Map;

/**
 * Rutas de noticias y comentarios. Cualquiera con sesion propone y comenta; publicar, aprobar y eliminar es de
 * quien modera (y solo el ve las propuestas pendientes).
 */
final class RutasNoticias implements ModuloRutas {

    private final NoticiasService noticias;

    RutasNoticias(NoticiasService noticias) {
        this.noticias = noticias;
    }

    @Override
    public void registrar(Rutas<Rutas.Consulta> consultas, Rutas<Rutas.Accion> acciones) {
        consultas.con(Permiso.VER, "noticias", p -> Presentador.mapa("ok", true, "noticias", noticias(p)));
        acciones.con(Permiso.PROPONER_NOTICIA, "noticia/proponer", p -> {
            noticias.proponer(p.texto("titulo"), p.texto("cuerpo"), p.texto("autor"), NoticiasService.imagenDesdeDataUrl(p.texto("imagen")));
            return respuesta(p, "¡Gracias! Su noticia quedó pendiente: se publicará cuando el administrador la apruebe.");
        }).con(Permiso.COMENTAR, "noticia/comentar", p -> {
            noticias.comentar(p.entero("id"), p.texto("autor"), p.texto("texto"));
            return respuesta(p, "Comentario publicado.");
        }).con(Permiso.MODERAR_NOTICIAS, "noticia/guardar", p -> {
            Integer id = p.cuerpo().get("id") == null ? null : p.entero("id");
            noticias.publicar(id, p.texto("titulo"), p.texto("cuerpo"), NoticiasService.imagenDesdeDataUrl(p.texto("imagen")), p.si("quitarImagen"));
            return respuesta(p, id == null ? "Noticia publicada." : "Noticia guardada.");
        }).con(Permiso.MODERAR_NOTICIAS, "noticia/aprobar", p -> {
            noticias.aprobar(p.entero("id"));
            return respuesta(p, "Noticia aprobada: ya aparece en el feed.");
        }).con(Permiso.MODERAR_NOTICIAS, "noticia/eliminar", p -> {
            noticias.eliminar(p.entero("id"));
            return respuesta(p, p.si("rechazo") ? "Propuesta rechazada." : "Noticia eliminada.");
        }).con(Permiso.MODERAR_NOTICIAS, "comentario/eliminar", p -> {
            noticias.eliminarComentario(p.entero("noticia"), p.entero("id"));
            return respuesta(p, "Comentario eliminado.");
        });
    }

    /** La respuesta normal mas las noticias actualizadas. */
    private Map<String, Object> respuesta(Peticion p, String mensaje) {
        Map<String, Object> r = p.listo(mensaje);
        r.put("noticias", noticias(p));
        return r;
    }

    /** El feed publico para todos y las pendientes solo para quien modera. */
    private Map<String, Object> noticias(Peticion p) {
        boolean moderador = p.api().puede(Permiso.MODERAR_NOTICIAS);
        return Presentador.noticias(noticias.publicadas(), moderador ? noticias.pendientes() : List.of(), moderador);
    }
}
