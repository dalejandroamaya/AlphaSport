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

/**
 * Noticias en datos/noticias.dat y fotos en datos/imagenes/. Es el formato anterior a PostgreSQL: se conserva
 * para poder pasar esos datos a la base de datos.
 */
public class ArchivoRepositorioNoticias extends RepositorioNoticiasEnMemoria {

    private final Path archivo;
    private final ImagenesEnDisco imagenes;

    public ArchivoRepositorioNoticias(Path carpetaDatos) throws PersistenciaException {
        this.archivo = carpetaDatos.resolve("noticias.dat");
        this.imagenes = new ImagenesEnDisco(carpetaDatos.resolve("imagenes"));
        noticias.addAll(leer());
    }

    /** Todas las noticias del archivo, aprobadas o no, en el orden en que se crearon. */
    public List<Noticia> todas() {
        return List.copyOf(noticias);
    }

    @Override
    protected void alCambiar() throws PersistenciaException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream salida = new ObjectOutputStream(bytes)) {
            salida.writeObject(new ArrayList<>(noticias));
            salida.flush();
            Files.createDirectories(archivo.getParent());
            Files.write(archivo, bytes.toByteArray());
        } catch (IOException e) {
            throw new PersistenciaException("No se pudo guardar " + archivo, e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Noticia> leer() throws PersistenciaException {
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
        return imagenes.guardar(datos, extension);
    }

    @Override
    public byte[] leerImagen(String nombre) throws PersistenciaException {
        return imagenes.leer(nombre);
    }

    @Override
    public void eliminarImagen(String nombre) throws PersistenciaException {
        imagenes.eliminar(nombre);
    }
}
