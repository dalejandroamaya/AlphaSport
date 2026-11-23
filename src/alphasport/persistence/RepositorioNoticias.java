package alphasport.persistence;

import alphasport.model.Comentario;
import alphasport.model.Noticia;
import java.util.List;

/**
 * Noticias y sus comentarios (las fotos van en un {@link RepositorioImagenes}). El programa usa PostgreSQL
 * ({@link alphasport.persistence.postgres.PostgresRepositorioNoticias}); las versiones en memoria y en archivo
 * quedan para las pruebas y para pasar los datos anteriores a la base de datos.
 */
public interface RepositorioNoticias {

    /** Las aprobadas, la mas reciente primero, con sus comentarios. */
    List<Noticia> publicadas() throws PersistenciaException;

    /** Las que esperan moderacion, la mas reciente primero. */
    List<Noticia> pendientes() throws PersistenciaException;

    /** La noticia con sus comentarios, o null si no existe. */
    Noticia buscar(int id) throws PersistenciaException;

    /** Guarda una noticia nueva y le pone su id. */
    void insertar(Noticia noticia) throws PersistenciaException;

    /** Guarda el titulo, el cuerpo, la foto y la aprobacion de una noticia que ya existe. */
    void actualizar(Noticia noticia) throws PersistenciaException;

    /** Borra la noticia con sus comentarios. */
    void eliminar(int id) throws PersistenciaException;

    /** Guarda un comentario nuevo y le pone su id. */
    void insertarComentario(int idNoticia, Comentario comentario) throws PersistenciaException;

    /** false si ese comentario no existe en la noticia. */
    boolean eliminarComentario(int idNoticia, int idComentario) throws PersistenciaException;

    /** true si la foto es de una noticia publicada (o de cualquiera, con {@code incluirPendientes}). */
    boolean imagenVisible(String nombre, boolean incluirPendientes) throws PersistenciaException;
}
