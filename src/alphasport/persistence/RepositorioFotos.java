package alphasport.persistence;

/**
 * Donde se guardan las fotos de los jugadores (tipo cedula) y los escudos de los equipos.
 * El campeonato solo anota el nombre de cada archivo; los bytes viven aqui. Hay dos
 * implementaciones: {@link alphasport.persistence.postgres.PostgresImagenes} (tabla imagenes, la que usa el programa),
 * {@link ArchivoRepositorioFotos} (carpeta datos/imagenes) y
 * {@link RepositorioFotosEnMemoria} (pruebas).
 */
public interface RepositorioFotos {

    /** Guarda los bytes con un nombre nuevo que empieza por {@code prefijo} y devuelve ese nombre. */
    String guardar(byte[] datos, String prefijo, String extension) throws PersistenciaException;

    /** Bytes de una foto guardada, o null si no existe. */
    byte[] leer(String nombre) throws PersistenciaException;
}
