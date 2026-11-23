package alphasport.ui;

import alphasport.persistence.ArchivoRepositorioFotos;
import alphasport.persistence.PersistenciaException;
import alphasport.persistence.RepositorioUsuariosEnMemoria;
import alphasport.persistence.postgres.ConexionDB;
import alphasport.persistence.postgres.PostgresRepositorioCampeonato;
import alphasport.persistence.postgres.PostgresRepositorioHistorial;
import alphasport.persistence.postgres.PostgresRepositorioNoticias;
import alphasport.service.AutenticacionService;
import alphasport.service.CampeonatoService;
import alphasport.service.ClavesPbkdf2;
import alphasport.service.FotosService;
import alphasport.service.NoticiasService;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Raiz de composicion: el unico lugar que arma la aplicacion (conexion, repositorios, servicios y controlador)
 * para el escritorio y para la web. Es un objeto, no estado estatico: cada fabrica tiene su conexion y la cierra
 * con {@link #close()}. No termina el programa: si algo falla lanza la excepcion y quien la usa decide.
 */
public final class FabricaAplicacion implements AutoCloseable {

    /** Carpeta de la interfaz web (tambien tiene las imagenes y fuentes del escritorio). */
    public static final Path CARPETA_WEB = Path.of("web");
    /** Carpeta de los archivos de imagen; los datos estan en PostgreSQL. */
    public static final Path CARPETA_DATOS = Path.of("datos");

    private final Path archivoConexion;
    private ConexionDB conexion;
    private CampeonatoService servicio;
    private NoticiasService noticias;

    /** Con la conexion de db.properties. */
    public FabricaAplicacion() {
        this(ConexionDB.ARCHIVO);
    }

    public FabricaAplicacion(Path archivoConexion) {
        this.archivoConexion = archivoConexion;
    }

    /** Conexion que comparten todos los repositorios; falla con un mensaje claro si la base no esta lista. */
    public ConexionDB conexion() throws PersistenciaException {
        if (conexion == null) {
            ConexionDB nueva = new ConexionDB(ConexionDB.leerConfiguracion(archivoConexion));
            nueva.verificar();
            conexion = nueva;
        }
        return conexion;
    }

    /**
     * Cuentas para iniciar sesion: por ahora las de demostracion. Con el login, aqui se pasara el repositorio
     * de la tabla usuarios; nada mas cambia.
     */
    public AutenticacionService autenticacion() {
        return new AutenticacionService(RepositorioUsuariosEnMemoria.demostracion(), new ClavesPbkdf2());
    }

    public NoticiasService noticias() throws PersistenciaException {
        if (noticias == null) {
            noticias = new NoticiasService(new PostgresRepositorioNoticias(conexion(), CARPETA_DATOS.resolve("imagenes")));
        }
        return noticias;
    }

    /** El servicio del campeonato con el campeonato guardado ya cargado. */
    public CampeonatoService servicio() throws PersistenciaException {
        if (servicio == null) {
            if (!Files.isRegularFile(CARPETA_WEB.resolve("index.html"))) {
                throw new PersistenciaException("No se encontró la carpeta web/. Ejecute el programa desde la carpeta del proyecto.", null);
            }
            CampeonatoService nuevo = new CampeonatoService(
                    new PostgresRepositorioCampeonato(conexion()), new PostgresRepositorioHistorial(conexion()), conexion());
            nuevo.usarFotos(new FotosService(new ArchivoRepositorioFotos(CARPETA_DATOS)));
            nuevo.cargarGuardado();
            servicio = nuevo;
        }
        return servicio;
    }

    /** El controlador que usan las dos interfaces (el escritorio lo usa directo; la web, por ServidorWeb). */
    public ApiControlador controlador() throws PersistenciaException {
        return new ApiControlador(servicio(), autenticacion(), noticias());
    }

    /** El servidor local de la version web. */
    public ServidorWeb servidorWeb() throws PersistenciaException {
        return new ServidorWeb(CARPETA_WEB, servicio(), controlador());
    }

    @Override
    public void close() {
        if (conexion != null) {
            conexion.close();
            conexion = null;
        }
    }
}
