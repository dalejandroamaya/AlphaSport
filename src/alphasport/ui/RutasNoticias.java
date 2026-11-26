package alphasport.ui;

import alphasport.model.Permiso;
import alphasport.service.NoticiasService;
import java.util.List;
import java.util.Map;

/**
 * Rutas de noticias y comentarios del campeonato elegido. Cualquiera con sesion propone y comenta; publicar,
 * aprobar y eliminar es de quien modera ese campeonato, su Organizador (y solo el ve las propuestas pendientes).
 * Que noticias se ven y quien modera lo decide {@link ApiControlador} con el gestor de campeonatos.
 */
final class RutasNoticias implements ModuloRutas {

    @Override
    public void registrar(Rutas<Rutas.Consulta> consultas, Rutas<Rutas.Accion> acciones) {
        consultas.con(Permiso.VER, "noticias", p -> Presentador.mapa("ok", true, "noticias", noticias(p)));
        acciones.con(Permiso.PROPONER_NOTICIA, "noticia/proponer", p -> {
            p.api().noticiasParaParticipar().proponer(p.texto("titulo"), p.texto("cuerpo"), p.texto("autor"),
                    NoticiasService.imagenDesdeDataUrl(p.texto("imagen")));
            return respuesta(p, "¡Gracias! Su noticia quedó pendiente: cuando el organizador del campeonato la apruebe, la verán todas las personas que lo siguen.");
        }).con(Permiso.COMENTAR, "noticia/comentar", p -> {
            p.api().noticiasParaParticipar().comentar(p.entero("id"), p.texto("autor"), p.texto("texto"));
            return respuesta(p, "Comentario publicado.");
        }).con(Permiso.MODERAR_NOTICIAS, "noticia/guardar", p -> {
            Integer id = p.cuerpo().get("id") == null ? null : p.entero("id");
            p.api().noticiasParaModerar().publicar(id, p.texto("titulo"), p.texto("cuerpo"), NoticiasService.imagenDesdeDataUrl(p.texto("imagen")),
                    p.si("quitarImagen"));
            return respuesta(p, id == null ? "Noticia publicada: ya la ven todas las personas que siguen este campeonato." : "Noticia guardada.");
        }).con(Permiso.MODERAR_NOTICIAS, "noticia/aprobar", p -> {
            p.api().noticiasParaModerar().aprobar(p.entero("id"));
            return respuesta(p, "Noticia aprobada: ya la ven todas las personas que siguen este campeonato.");
        }).con(Permiso.MODERAR_NOTICIAS, "noticia/eliminar", p -> {
            p.api().noticiasParaModerar().eliminar(p.entero("id"));
            return respuesta(p, p.si("rechazo") ? "Propuesta rechazada." : "Noticia eliminada.");
        }).con(Permiso.MODERAR_NOTICIAS, "comentario/eliminar", p -> {
            p.api().noticiasParaModerar().eliminarComentario(p.entero("noticia"), p.entero("id"));
            return respuesta(p, "Comentario eliminado.");
        });
    }

    /** La respuesta normal mas las noticias actualizadas. */
    private Map<String, Object> respuesta(Peticion p, String mensaje) {
        Map<String, Object> r = p.listo(mensaje);
        r.put("noticias", noticias(p));
        return r;
    }

    /** El feed del campeonato para todos y las pendientes solo para quien lo modera. */
    private Map<String, Object> noticias(Peticion p) {
        NoticiasService noticias = p.api().noticiasParaLeer();
        boolean moderador = p.api().moderaNoticias();
        return Presentador.noticias(noticias.publicadas(), moderador ? noticias.pendientes() : List.of(), moderador);
    }
}
