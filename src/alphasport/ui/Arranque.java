package alphasport.ui;

import alphasport.persistence.ArchivoRepositorioCampeonato;
import alphasport.persistence.ArchivoRepositorioFotos;
import alphasport.persistence.ArchivoRepositorioHistorial;
import alphasport.persistence.ArchivoRepositorioNoticias;
import alphasport.persistence.PersistenciaException;
import alphasport.service.CampeonatoService;
import alphasport.service.FotosService;
import alphasport.service.NoticiasService;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Arma las capas inferiores (persistence y service) para cualquiera de las dos interfaces.
 */
public final class Arranque {

    public static final Path CARPETA_WEB = Path.of("web");
    public static final Path CARPETA_DATOS = Path.of("datos");

    private Arranque() {
    }

    /**
     * Noticias: el servicio las maneja en listas en memoria y este repositorio las conserva en
     * datos/noticias.dat (y las fotos en datos/imagenes/). Para dejarlas SOLO en memoria (se
     * pierden al cerrar) basta con cambiar esta linea por: new RepositorioNoticiasEnMemoria().
     */
    public static NoticiasService noticias() {
        return new NoticiasService(new ArchivoRepositorioNoticias(CARPETA_DATOS));
    }

    public static CampeonatoService servicio() throws PersistenciaException {
        if (!Files.isRegularFile(CARPETA_WEB.resolve("index.html"))) {
            System.err.println("No se encontro la carpeta web/. Ejecute el programa desde la carpeta del proyecto.");
            System.exit(1);
        }
        CampeonatoService servicio = new CampeonatoService(
                new ArchivoRepositorioCampeonato(CARPETA_DATOS), new ArchivoRepositorioHistorial(CARPETA_DATOS));
        servicio.usarFotos(new FotosService(new ArchivoRepositorioFotos(CARPETA_DATOS)));
        servicio.cargarGuardado();
        return servicio;
    }
}
