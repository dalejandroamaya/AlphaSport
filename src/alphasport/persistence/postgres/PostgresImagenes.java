package alphasport.persistence.postgres;

import alphasport.persistence.PersistenciaException;
import alphasport.persistence.RepositorioFotos;
import alphasport.persistence.RepositorioImagenes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Fotos de jugadores, escudos y fotos de noticias dentro de la base (tabla {@code imagenes}). Así las ven todos
 * los computadores que usan la misma base (por ejemplo, una base en internet como Neon); antes vivían en la
 * carpeta datos/imagenes de cada computador y los demás veían «NN» o el escudo de iniciales.
 *
 * <p>Las imágenes que quedaron en datos/imagenes de antes se suben solas: todas al abrir el programa
 * ({@link #subirArchivosAnteriores()}) y, por si acaso, cualquiera que se pida y todavía no esté en la base.
 */
public class PostgresImagenes implements RepositorioFotos, RepositorioImagenes {

    /** Nombres que genera el programa: jugador-…, escudo-… o noticia-…, con una extensión de imagen. */
    private static final Pattern NOMBRE = Pattern.compile("[a-z]+-[a-z0-9]{6,32}\\.(jpg|jpeg|png|gif|webp)");

    private final ConexionDB conexion;
    private final Path carpetaAnterior;

    /** @param carpetaAnterior datos/imagenes: de ahí se suben las imágenes guardadas antes como archivos (puede no existir) */
    public PostgresImagenes(ConexionDB conexion, Path carpetaAnterior) {
        this.conexion = conexion;
        this.carpetaAnterior = carpetaAnterior;
    }

    // ---------------------------------------------------------------- fotos de jugadores y escudos

    @Override
    public String guardar(byte[] datos, String prefijo, String extension) throws PersistenciaException {
        String nombre = prefijo + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "." + extension;
        insertar(nombre, datos);
        return nombre;
    }

    // ---------------------------------------------------------------- fotos de noticias

    @Override
    public String guardar(byte[] datos, String extension) throws PersistenciaException {
        String nombre = "noticia-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "." + extension;
        insertar(nombre, datos);
        return nombre;
    }

    @Override
    public void eliminar(String nombre) throws PersistenciaException {
        if (valido(nombre)) {
            conexion.ejecutar(con -> Sql.cambiar(con, "DELETE FROM imagenes WHERE nombre = ?", nombre));
        }
    }

    // ---------------------------------------------------------------- lectura (las dos)

    @Override
    public byte[] leer(String nombre) throws PersistenciaException {
        if (!valido(nombre)) {
            return null; // nada de «../» ni nombres inventados
        }
        byte[] datos = conexion.ejecutar(con -> Sql.uno(con, "SELECT datos FROM imagenes WHERE nombre = ?", rs -> rs.getBytes(1), nombre));
        if (datos != null) {
            return datos;
        }
        // Una imagen de antes que todavía estaba solo en este computador: se sube ahora para que la vean todos.
        Path archivo = archivoAnterior(nombre);
        if (archivo == null) {
            return null;
        }
        try {
            datos = Files.readAllBytes(archivo);
        } catch (IOException e) {
            return null;
        }
        insertar(nombre, datos);
        return datos;
    }

    /**
     * Sube a la base las imágenes de datos/imagenes que todavía no están (no borra los archivos).
     *
     * @return cuántas se subieron
     */
    public int subirArchivosAnteriores() throws PersistenciaException {
        if (carpetaAnterior == null || !Files.isDirectory(carpetaAnterior)) {
            return 0;
        }
        List<Path> archivos;
        try (Stream<Path> lista = Files.list(carpetaAnterior)) {
            archivos = lista.filter(Files::isRegularFile).filter(p -> valido(p.getFileName().toString())).toList();
        } catch (IOException e) {
            return 0;
        }
        if (archivos.isEmpty()) {
            return 0;
        }
        Set<String> enBase = new HashSet<>(conexion.ejecutar(con -> Sql.listar(con, "SELECT nombre FROM imagenes", rs -> rs.getString(1))));
        int subidas = 0;
        for (Path p : archivos) {
            String nombre = p.getFileName().toString();
            if (enBase.contains(nombre)) {
                continue;
            }
            try {
                insertar(nombre, Files.readAllBytes(p));
                subidas++;
            } catch (IOException e) {
                // un archivo que no se puede leer se salta; los demás siguen
            }
        }
        return subidas;
    }

    private void insertar(String nombre, byte[] datos) throws PersistenciaException {
        conexion.ejecutar(con -> Sql.cambiar(con, "INSERT INTO imagenes (nombre, datos) VALUES (?, ?) ON CONFLICT (nombre) DO NOTHING", nombre, datos));
    }

    private Path archivoAnterior(String nombre) {
        if (carpetaAnterior == null) {
            return null;
        }
        Path ruta = carpetaAnterior.resolve(nombre).normalize();
        return ruta.startsWith(carpetaAnterior.normalize()) && Files.isRegularFile(ruta) ? ruta : null;
    }

    private static boolean valido(String nombre) {
        return nombre != null && NOMBRE.matcher(nombre).matches();
    }
}
