package alphasport.persistence;

import alphasport.model.Campeonato;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Guarda cada campeonato archivado completo en datos/historial/campeonato_codigo.dat.
 */
public class ArchivoRepositorioHistorial implements RepositorioHistorial {

    private final Path carpeta;

    public ArchivoRepositorioHistorial(Path carpetaDatos) {
        this.carpeta = carpetaDatos.resolve("historial");
    }

    @Override
    public void archivar(Campeonato campeonato) throws PersistenciaException {
        try {
            Files.createDirectories(carpeta);
            Files.write(archivoDe(campeonato.getCodigo()), Serializacion.aBytes(campeonato));
        } catch (IOException e) {
            throw new PersistenciaException("No se pudo archivar el campeonato", e);
        }
    }

    @Override
    public List<Campeonato> listar() {
        List<Campeonato> lista = new ArrayList<>();
        if (!Files.isDirectory(carpeta)) {
            return lista;
        }
        try (Stream<Path> archivos = Files.list(carpeta)) {
            for (Path archivo : (Iterable<Path>) archivos.filter(p -> p.toString().endsWith(".dat"))::iterator) {
                try {
                    lista.add(Serializacion.desdeBytes(Files.readAllBytes(archivo)));
                } catch (IOException | PersistenciaException e) {
                    System.err.println("Se omite " + archivo.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("No se pudo leer el historial: " + e.getMessage());
        }
        lista.sort(Comparator.comparing((Campeonato c) -> String.valueOf(c.getFechaArchivo())).reversed());
        return lista;
    }

    @Override
    public boolean eliminar(String codigo) {
        try {
            return Files.deleteIfExists(archivoDe(codigo));
        } catch (IOException e) {
            return false;
        }
    }

    private Path archivoDe(String codigo) {
        return carpeta.resolve("campeonato_" + codigo + ".dat");
    }
}
