package alphasport.persistence;

/**
 * Archivos de imagen (las fotos de las noticias). Va aparte de {@link RepositorioNoticias}: las noticias estan en
 * la base de datos y las fotos en una carpeta, y quien solo lee noticias no tiene por que saber guardar archivos.
 */
public interface RepositorioImagenes {

    /** Guarda los bytes y devuelve el nombre del archivo (lo que se anota en Noticia.imagenRuta). */
    String guardar(byte[] datos, String extension) throws PersistenciaException;

    /** Bytes de la imagen, o null si no existe. */
    byte[] leer(String nombre) throws PersistenciaException;

    /** Borra la imagen; si no existe no hace nada. */
    void eliminar(String nombre) throws PersistenciaException;
}
