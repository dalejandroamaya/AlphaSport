package alphasport.persistence;

import alphasport.model.Comentario;
import alphasport.model.Noticia;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Noticias y fotos solo en memoria (se pierden al cerrar). La usan las pruebas automaticas.
 */
public class RepositorioNoticiasEnMemoria implements RepositorioNoticias {

    protected final List<Noticia> noticias = new ArrayList<>();
    private int siguienteNoticia = 1;
    private int siguienteComentario = 1;

    /** Se llama despues de cada cambio (la version en archivo guarda aqui). */
    protected void alCambiar() throws PersistenciaException {
        // en memoria no hay nada que guardar
    }

    private static Comparator<Noticia> masRecientePrimero() {
        return Comparator.comparing(Noticia::getFecha).thenComparingInt(Noticia::getId).reversed();
    }

    @Override
    public List<Noticia> publicadas() {
        return noticias.stream().filter(Noticia::isAprobado).sorted(masRecientePrimero()).toList();
    }

    @Override
    public List<Noticia> pendientes() {
        return noticias.stream().filter(n -> !n.isAprobado()).sorted(masRecientePrimero()).toList();
    }

    @Override
    public Optional<Noticia> buscar(int id) {
        return noticias.stream().filter(n -> n.getId() == id).findFirst();
    }

    @Override
    public void insertar(Noticia noticia) throws PersistenciaException {
        noticias.forEach(n -> siguienteNoticia = Math.max(siguienteNoticia, n.getId() + 1));
        noticia.setId(siguienteNoticia++);
        noticias.add(noticia);
        alCambiar();
    }

    @Override
    public void actualizar(Noticia noticia) throws PersistenciaException {
        noticias.replaceAll(n -> n.getId() == noticia.getId() ? noticia : n);
        alCambiar();
    }

    @Override
    public void eliminar(int id) throws PersistenciaException {
        noticias.removeIf(n -> n.getId() == id);
        alCambiar();
    }

    @Override
    public void insertarComentario(int idNoticia, Comentario comentario) throws PersistenciaException {
        noticias.forEach(n -> n.getComentarios().forEach(c -> siguienteComentario = Math.max(siguienteComentario, c.getId() + 1)));
        comentario.setId(siguienteComentario++);
        // Igual que en la base de datos (llave foranea): comentar una noticia que no existe es un error.
        buscar(idNoticia).orElseThrow(() -> new PersistenciaException("La noticia " + idNoticia + " no existe.", null)).getComentarios().add(comentario);
        alCambiar();
    }

    @Override
    public boolean eliminarComentario(int idNoticia, int idComentario) throws PersistenciaException {
        boolean quitado = buscar(idNoticia).map(n -> n.getComentarios().removeIf(c -> c.getId() == idComentario)).orElse(false);
        alCambiar();
        return quitado;
    }

    @Override
    public boolean imagenVisible(String nombre, boolean incluirPendientes) {
        return noticias.stream().anyMatch(n -> n.getImagenRuta().equals(nombre) && (n.isAprobado() || incluirPendientes));
    }
}
