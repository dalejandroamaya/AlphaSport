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
 * Noticias en datos/noticias.dat (las fotos estan en datos/imagenes/: {@link ImagenesEnDisco}). Es el formato anterior a PostgreSQL: se conserva
 * para poder pasar esos datos a la base de datos.
 */
public class ArchivoRepositorioNoticias extends RepositorioNoticiasEnMemoria {

    private final Path archivo;

    public ArchivoRepositorioNoticias(Path carpetaDatos) throws PersistenciaException {
        this.archivo = carpetaDatos.resolve("noticias.dat");
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
        try (ObjectInputStream entrada = new LectorConRolesAnteriores(new ByteArrayInputStream(Files.readAllBytes(archivo)))) {
            return (List<Noticia>) entrada.readObject();
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            throw new PersistenciaException("No se pudo leer " + archivo, e);
        }
    }

    /** Todos los nombres que el rol tuvo en un archivo: los de antes del registro de cuentas y los de ahora. */
    enum RolGuardado {
        ADMINISTRADOR, VISITANTE, ORGANIZADOR, AFICIONADO
    }

    /**
     * Lee el rol de las noticias como {@link RolGuardado}: asi un archivo viejo con ADMINISTRADOR o VISITANTE
     * no falla al no existir ya esas constantes en {@link alphasport.model.Rol}. {@code Noticia.readObject} lo
     * convierte al rol actual por su nombre.
     */
    private static final class LectorConRolesAnteriores extends ObjectInputStream {

        LectorConRolesAnteriores(java.io.InputStream entrada) throws IOException {
            super(entrada);
        }

        @Override
        protected java.io.ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
            java.io.ObjectStreamClass leida = super.readClassDescriptor();
            return leida.getName().equals(alphasport.model.Rol.class.getName())
                    ? java.io.ObjectStreamClass.lookup(RolGuardado.class) : leida;
        }
    }
}
