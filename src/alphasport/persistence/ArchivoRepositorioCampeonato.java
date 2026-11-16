package alphasport.persistence;

import alphasport.model.Campeonato;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Guarda el campeonato en curso en un archivo binario (datos/campeonato.dat).
 */
public class ArchivoRepositorioCampeonato implements RepositorioCampeonato {

    private final Path archivo;

    public ArchivoRepositorioCampeonato(Path carpetaDatos) {
        this.archivo = carpetaDatos.resolve("campeonato.dat");
    }

    @Override
    public void guardar(Campeonato campeonato) throws PersistenciaException {
        try {
            Files.createDirectories(archivo.getParent());
            Files.write(archivo, Serializacion.aBytes(campeonato));
        } catch (IOException e) {
            throw new PersistenciaException("No se pudo guardar " + archivo, e);
        }
    }

    @Override
    public Campeonato cargar() throws PersistenciaException {
        try {
            return Serializacion.desdeBytes(Files.readAllBytes(archivo));
        } catch (IOException e) {
            throw new PersistenciaException("No se pudo leer " + archivo, e);
        }
    }

    @Override
    public boolean existe() {
        return Files.exists(archivo);
    }

    @Override
    public void eliminar() throws PersistenciaException {
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException e) {
            throw new PersistenciaException("No se pudo borrar " + archivo, e);
        }
    }
}
