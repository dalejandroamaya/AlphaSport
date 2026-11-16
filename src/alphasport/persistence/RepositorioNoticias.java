package alphasport.persistence;

import alphasport.model.Noticia;
import java.util.List;

/**
 * Donde se guardan las noticias y sus fotos. El servicio trabaja siempre con listas en
 * memoria y le pide a este repositorio que las conserve. Hay dos implementaciones:
 * {@link RepositorioNoticiasEnMemoria} (solo memoria: se pierden al cerrar) y
 * {@link ArchivoRepositorioNoticias} (archivo .dat como el campeonato). En la Fase 3 se
 * agrega una con base de datos sin tocar el servicio.
 */
public interface RepositorioNoticias {

    void guardar(List<Noticia> noticias) throws PersistenciaException;

    /** Las noticias guardadas (lista vacia si todavia no hay ninguna). */
    List<Noticia> cargar() throws PersistenciaException;

    /** Guarda los bytes de una foto y devuelve su nombre (lo que se anota en Noticia.imagenRuta). */
    String guardarImagen(byte[] datos, String extension) throws PersistenciaException;

    /** Bytes de una foto guardada, o null si no existe. */
    byte[] leerImagen(String nombre) throws PersistenciaException;

    void eliminarImagen(String nombre) throws PersistenciaException;
}
