package alphasport.persistence;

import alphasport.model.Comentario;
import alphasport.model.Noticia;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Noticias y fotos solo en memoria (se pierden al cerrar). La usan las pruebas automaticas.
 */
public class RepositorioNoticiasEnMemoria implements RepositorioNoticias {

    protected final List<Noticia> noticias = new ArrayList<>();
    private final Map<String, byte[]> imagenes = new HashMap<>();
    private int siguienteImagen = 1;
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
    public Noticia buscar(int id) {
        return noticias.stream().filter(n -> n.getId() == id).findFirst().orElse(null);
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
        buscar(idNoticia).getComentarios().add(comentario);
        alCambiar();
    }

    @Override
    public boolean eliminarComentario(int idNoticia, int idComentario) throws PersistenciaException {
        boolean quitado = buscar(idNoticia).getComentarios().removeIf(c -> c.getId() == idComentario);
        alCambiar();
        return quitado;
    }

    @Override
    public boolean imagenVisible(String nombre, boolean incluirPendientes) {
        return noticias.stream().anyMatch(n -> n.getImagenRuta().equals(nombre) && (n.isAprobado() || incluirPendientes));
    }

    @Override
    public String guardarImagen(byte[] datos, String extension) throws PersistenciaException {
        String nombre = "noticia-" + (siguienteImagen++) + "." + extension;
        imagenes.put(nombre, datos.clone());
        return nombre;
    }

    @Override
    public byte[] leerImagen(String nombre) throws PersistenciaException {
        byte[] datos = imagenes.get(nombre);
        return datos == null ? null : datos.clone();
    }

    @Override
    public void eliminarImagen(String nombre) throws PersistenciaException {
        imagenes.remove(nombre);
    }
}
