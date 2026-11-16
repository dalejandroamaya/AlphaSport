package alphasport.persistence;

import alphasport.model.Noticia;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Guarda las noticias en datos/noticias.dat y las fotos en datos/imagenes/ (sin base de
 * datos, igual que el campeonato). Las fotos se copian a esa carpeta con un nombre propio,
 * asi la noticia no se rompe si la foto original se mueve o se borra.
 */
public class ArchivoRepositorioNoticias implements RepositorioNoticias {

    private final Path archivo;
    private final Path carpetaImagenes;

    public ArchivoRepositorioNoticias(Path carpetaDatos) {
        this.archivo = carpetaDatos.resolve("noticias.dat");
        this.carpetaImagenes = carpetaDatos.resolve("imagenes");
    }

    @Override
    public void guardar(List<Noticia> noticias) throws PersistenciaException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream salida = new ObjectOutputStream(bytes)) {
            salida.writeObject(new ArrayList<>(noticias));
        } catch (IOException e) {
            throw new PersistenciaException("No se pudieron convertir las noticias", e);
        }
        try {
            Files.createDirectories(archivo.getParent());
            Files.write(archivo, bytes.toByteArray());
        } catch (IOException e) {
            throw new PersistenciaException("No se pudo guardar " + archivo, e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Noticia> cargar() throws PersistenciaException {
        if (!Files.exists(archivo)) {
            return new ArrayList<>();
        }
        try (ObjectInputStream entrada = new ObjectInputStream(new ByteArrayInputStream(Files.readAllBytes(archivo)))) {
            return (List<Noticia>) entrada.readObject();
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            throw new PersistenciaException("No se pudo leer " + archivo, e);
        }
    }

    @Override
    public String guardarImagen(byte[] datos, String extension) throws PersistenciaException {
        String nombre = "noticia-" + UUID.randomUUID().toString().substring(0, 8) + "." + extension;
        try {
            Files.createDirectories(carpetaImagenes);
            Files.write(carpetaImagenes.resolve(nombre), datos);
        } catch (IOException e) {
            throw new PersistenciaException("No se pudo guardar la foto", e);
        }
        return nombre;
    }

    @Override
    public byte[] leerImagen(String nombre) throws PersistenciaException {
        Path ruta = rutaSegura(nombre);
        try {
            return ruta != null && Files.isRegularFile(ruta) ? Files.readAllBytes(ruta) : null;
        } catch (IOException e) {
            throw new PersistenciaException("No se pudo leer la foto", e);
        }
    }

    @Override
    public void eliminarImagen(String nombre) throws PersistenciaException {
        Path ruta = rutaSegura(nombre);
        try {
            if (ruta != null) {
                Files.deleteIfExists(ruta);
            }
        } catch (IOException e) {
            throw new PersistenciaException("No se pudo borrar la foto", e);
        }
    }

    /** Solo nombres de la carpeta de imagenes (nada de «../»). */
    private Path rutaSegura(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return null;
        }
        Path ruta = carpetaImagenes.resolve(nombre).normalize();
        return ruta.startsWith(carpetaImagenes.normalize()) ? ruta : null;
    }
}
