package alphasport.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Fotos de jugadores y escudos en la carpeta datos/imagenes (la misma de las fotos de noticias).
 * Al quitar una foto no se borra el archivo: puede seguir en uso por un campeonato del historial
 * que copio el mismo equipo.
 */
public class ArchivoRepositorioFotos implements RepositorioFotos {

    private final Path carpeta;

    public ArchivoRepositorioFotos(Path carpetaDatos) {
        this.carpeta = carpetaDatos.resolve("imagenes");
    }

    @Override
    public String guardar(byte[] datos, String prefijo, String extension) throws PersistenciaException {
        String nombre = prefijo + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "." + extension;
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
        if (nombre == null || nombre.isBlank()) {
            return null;
        }
        Path ruta = carpeta.resolve(nombre).normalize();
        if (!ruta.startsWith(carpeta.normalize()) || !Files.isRegularFile(ruta)) {
            return null; // nada de «../» ni archivos que no existen
        }
        try {
            return Files.readAllBytes(ruta);
        } catch (IOException e) {
            throw new PersistenciaException("No se pudo leer la foto", e);
        }
    }
}
