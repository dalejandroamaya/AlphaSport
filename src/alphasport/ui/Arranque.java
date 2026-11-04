package alphasport.ui;

import alphasport.persistence.ArchivoRepositorioCampeonato;
import alphasport.persistence.ArchivoRepositorioHistorial;
import alphasport.service.CampeonatoService;
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

    public static CampeonatoService servicio() {
        if (!Files.isRegularFile(CARPETA_WEB.resolve("index.html"))) {
            System.err.println("No se encontro la carpeta web/. Ejecute el programa desde la carpeta del proyecto.");
            System.exit(1);
        }
        CampeonatoService servicio = new CampeonatoService(
                new ArchivoRepositorioCampeonato(CARPETA_DATOS), new ArchivoRepositorioHistorial(CARPETA_DATOS));
        servicio.cargarGuardado();
        return servicio;
    }
}
