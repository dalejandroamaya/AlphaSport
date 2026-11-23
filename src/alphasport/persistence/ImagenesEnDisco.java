package alphasport.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Fotos de las noticias en una carpeta. La base de datos solo guarda el nombre de cada archivo.
 */
public class ImagenesEnDisco implements RepositorioImagenes {

    private final Path carpeta;

    public ImagenesEnDisco(Path carpeta) {
        this.carpeta = carpeta;
    }

    @Override
    public String guardar(byte[] datos, String extension) throws PersistenciaException {
        String nombre = "noticia-" + UUID.randomUUID().toString().substring(0, 8) + "." + extension;
        try {
            Files.createDirectories(carpeta);
            Files.write(carpeta.resolve(nombre), datos);
        } catch (IOException e) {
            throw new PersistenciaException("No se pudo guardar la foto", e);
        }
        return nombre;
    }

    @Override
    public byte[] leer(String nombre) throws PersistenciaException {
        Path ruta = rutaSegura(nombre);
        try {
            return ruta != null && Files.isRegularFile(ruta) ? Files.readAllBytes(ruta) : null;
        } catch (IOException e) {
            throw new PersistenciaException("No se pudo leer la foto", e);
        }
    }

    @Override
    public void eliminar(String nombre) throws PersistenciaException {
        Path ruta = rutaSegura(nombre);
        try {
            if (ruta != null) {
                Files.deleteIfExists(ruta);
            }
        } catch (IOException e) {
            throw new PersistenciaException("No se pudo borrar la foto", e);
        }
    }

    /** Solo nombres de la carpeta (nada de «../»). */
    private Path rutaSegura(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return null;
        }
        Path ruta = carpeta.resolve(nombre).normalize();
        return ruta.startsWith(carpeta.normalize()) ? ruta : null;
    }
}
