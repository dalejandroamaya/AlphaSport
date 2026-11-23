package alphasport.ui;

import alphasport.persistence.ArchivoRepositorioFotos;
import alphasport.persistence.PersistenciaException;
import alphasport.persistence.postgres.ConexionDB;
import alphasport.persistence.postgres.PostgresRepositorioCampeonato;
import alphasport.persistence.postgres.PostgresRepositorioHistorial;
import alphasport.persistence.postgres.PostgresRepositorioNoticias;
import alphasport.service.CampeonatoService;
import alphasport.service.FotosService;
import alphasport.service.NoticiasService;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Arma las capas inferiores (persistence y service) para cualquiera de las dos interfaces. Los datos estan en
 * PostgreSQL (ver db.properties); en la carpeta datos/ solo quedan los archivos de imagen.
 */
public final class Arranque {

    public static final Path CARPETA_WEB = Path.of("web");
    public static final Path CARPETA_DATOS = Path.of("datos");

    private static ConexionDB conexion;

    private Arranque() {
    }

    /** Conexion que comparten todos los repositorios; falla con un mensaje claro si la base no esta lista. */
    private static synchronized ConexionDB conexion() throws PersistenciaException {
        if (conexion == null) {
            ConexionDB nueva = new ConexionDB(ConexionDB.leerConfiguracion(ConexionDB.ARCHIVO));
            nueva.verificar();
            conexion = nueva;
        }
        return conexion;
    }

    public static NoticiasService noticias() throws PersistenciaException {
        return new NoticiasService(new PostgresRepositorioNoticias(conexion(), CARPETA_DATOS.resolve("imagenes")));
    }

    public static CampeonatoService servicio() throws PersistenciaException {
        if (!Files.isRegularFile(CARPETA_WEB.resolve("index.html"))) {
            System.err.println("No se encontro la carpeta web/. Ejecute el programa desde la carpeta del proyecto.");
            System.exit(1);
        }
        CampeonatoService servicio = new CampeonatoService(
                new PostgresRepositorioCampeonato(conexion()), new PostgresRepositorioHistorial(conexion()));
        servicio.usarFotos(new FotosService(new ArchivoRepositorioFotos(CARPETA_DATOS)));
        servicio.cargarGuardado();
        return servicio;
    }
}
