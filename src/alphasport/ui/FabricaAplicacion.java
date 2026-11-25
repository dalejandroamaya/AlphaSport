package alphasport.ui;

import alphasport.persistence.PersistenciaException;
import alphasport.persistence.postgres.ConexionDB;
import alphasport.persistence.postgres.PostgresImagenes;
import alphasport.persistence.postgres.PostgresRepositorioCampeonatos;
import alphasport.persistence.postgres.PostgresRepositorioHistorial;
import alphasport.persistence.postgres.PostgresRepositorioNoticias;
import alphasport.persistence.postgres.PostgresRepositorioUsuarios;
import alphasport.service.AutenticacionService;
import alphasport.service.CampeonatoService;
import alphasport.service.ClavesPbkdf2;
import alphasport.service.FotosService;
import alphasport.service.GestorCampeonatos;
import alphasport.service.NoticiasService;
import alphasport.service.SesionService;
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
    private GestorCampeonatos gestor;
    private FotosService fotos;
    private PostgresImagenes imagenes;
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
     * Cuentas para registrarse e iniciar sesion: las de la tabla usuarios de PostgreSQL, con las claves en hash
     * PBKDF2. Ya no hay cuentas de demostracion.
     */
    public AutenticacionService autenticacion() throws PersistenciaException {
        return new AutenticacionService(new PostgresRepositorioUsuarios(conexion()), new ClavesPbkdf2());
    }

    public NoticiasService noticias() throws PersistenciaException {
        if (noticias == null) {
            noticias = new NoticiasService(new PostgresRepositorioNoticias(conexion()), imagenes());
        }
        return noticias;
    }

    /**
     * Los campeonatos en curso (pueden ser varios, cada uno con su dueno). Cada uno se abre con su propio
     * servicio, que comparte la conexion, el historial y la carpeta de fotos.
     */
    public GestorCampeonatos gestor() throws PersistenciaException {
        if (gestor == null) {
            if (!Files.isRegularFile(CARPETA_WEB.resolve("index.html"))) {
                throw new PersistenciaException("No se encontró la carpeta web/. Ejecute el programa desde la carpeta del proyecto.", null);
            }
            ConexionDB db = conexion();
            PostgresRepositorioHistorial historial = new PostgresRepositorioHistorial(db);
            FotosService fotos = fotos();
            gestor = new GestorCampeonatos(new PostgresRepositorioCampeonatos(db), historial, repositorio -> {
                CampeonatoService s = new CampeonatoService(repositorio, historial, db);
                s.usarFotos(fotos);
                return s;
            });
        }
        return gestor;
    }

    /** Fotos de jugadores y escudos, en la base (las comparten todos los campeonatos y todos los computadores). */
    private FotosService fotos() throws PersistenciaException {
        if (fotos == null) {
            fotos = new FotosService(imagenes());
        }
        return fotos;
    }

    /**
     * Fotos, escudos y fotos de noticias en la tabla imagenes. La primera vez sube las que quedaron de antes en
     * datos/imagenes (no las borra), para que también las vean los demás computadores que usan la misma base.
     */
    private PostgresImagenes imagenes() throws PersistenciaException {
        if (imagenes == null) {
            imagenes = new PostgresImagenes(conexion(), CARPETA_DATOS.resolve("imagenes"));
            imagenes.subirArchivosAnteriores();
        }
        return imagenes;
    }

    /** El controlador que usan las dos interfaces (el escritorio lo usa directo; la web, por ServidorWeb). */
    public ApiControlador controlador() throws PersistenciaException {
        return new ApiControlador(gestor(), new SesionService(autenticacion()), noticias(), fotos());
    }

    /** El servidor local de la version web. */
    public ServidorWeb servidorWeb() throws PersistenciaException {
        return new ServidorWeb(CARPETA_WEB, controlador());
    }

    @Override
    public void close() {
        if (conexion != null) {
            conexion.close();
            conexion = null;
        }
    }
}
