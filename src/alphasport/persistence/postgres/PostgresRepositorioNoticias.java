package alphasport.persistence.postgres;

import alphasport.model.Comentario;
import alphasport.model.Noticia;
import alphasport.model.Rol;
import alphasport.persistence.PersistenciaException;
import alphasport.persistence.RepositorioNoticias;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;

/**
 * Noticias y comentarios en PostgreSQL. La tabla guarda el nombre de cada foto; los archivos los guarda un
 * {@link alphasport.persistence.RepositorioImagenes}.
 */
public class PostgresRepositorioNoticias implements RepositorioNoticias {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** Con el codigo del campeonato de cada noticia (LEFT JOIN: las generales no tienen). */
    private static final String COLUMNAS = """
            SELECT n.id, n.titulo, n.cuerpo, n.autor, n.rol_autor, n.fecha, n.imagen_ruta, n.aprobado, t.codigo AS campeonato
              FROM noticias n LEFT JOIN torneos t ON t.id = n.torneo_id
            """;

    private final ConexionDB conexion;

    public PostgresRepositorioNoticias(ConexionDB conexion) {
        this.conexion = conexion;
    }

    @Override
    public List<Noticia> publicadas(String campeonato) throws PersistenciaException {
        return conexion.ejecutar(con -> listar(con, true, campeonato));
    }

    @Override
    public List<Noticia> pendientes(String campeonato) throws PersistenciaException {
        return conexion.ejecutar(con -> listar(con, false, campeonato));
    }

    /**
     * Dos consultas: las noticias y, de una vez, los comentarios de todas. Con un campeonato: las suyas (de su
     * torneo en curso) y las generales.
     */
    private static List<Noticia> listar(Connection con, boolean aprobadas, String campeonato) throws SQLException {
        Map<Integer, Noticia> porId = new HashMap<>();
        List<Noticia> lista = campeonato == null
                ? Sql.listar(con, COLUMNAS + " WHERE n.aprobado = ? ORDER BY n.fecha DESC, n.id DESC", PostgresRepositorioNoticias::leer, aprobadas)
                : Sql.listar(con, COLUMNAS + """
                         WHERE n.aprobado = ?
                           AND (n.torneo_id IS NULL OR n.torneo_id = (SELECT id FROM torneos WHERE codigo = ? AND situacion = 'ACTUAL'))
                         ORDER BY n.fecha DESC, n.id DESC""", PostgresRepositorioNoticias::leer, aprobadas, campeonato);
        lista.forEach(n -> porId.put(n.getId(), n));
        Sql.listar(con, """
                SELECT c.noticia_id, c.id, c.autor, c.texto, c.fecha FROM comentarios c JOIN noticias n ON n.id = c.noticia_id
                 WHERE n.aprobado = ? ORDER BY c.id""", rs -> {
            Noticia n = porId.get(rs.getInt(1)); // las de otros campeonatos no estan en la lista
            if (n != null) {
                n.getComentarios().add(leerComentario(rs));
            }
            return n;
        }, aprobadas);
        return lista;
    }

    @Override
    public Optional<Noticia> buscar(int id) throws PersistenciaException {
        return Optional.ofNullable(conexion.ejecutar(con -> {
            Noticia n = Sql.uno(con, COLUMNAS + " WHERE n.id = ?", PostgresRepositorioNoticias::leer, id);
            if (n != null) {
                n.getComentarios().addAll(Sql.listar(con, "SELECT noticia_id, id, autor, texto, fecha FROM comentarios WHERE noticia_id = ? ORDER BY id",
                        PostgresRepositorioNoticias::leerComentario, id));
            }
            return n;
        }));
    }

    @Override
    public void insertar(Noticia n) throws PersistenciaException {
        conexion.ejecutar(con -> {
            // Primero el id del torneo en curso de su campeonato (SELECT), y luego el INSERT.
            Integer torneo = n.getCampeonato() == null ? null
                    : Sql.uno(con, "SELECT id FROM torneos WHERE codigo = ? AND situacion = 'ACTUAL'", rs -> rs.getInt(1), n.getCampeonato());
            if (n.getCampeonato() != null && torneo == null) {
                throw new PersistenciaException("El campeonato de la noticia ya no está en curso.", null);
            }
            n.setId(Sql.insertar(con, """
                    INSERT INTO noticias (torneo_id, titulo, cuerpo, autor, rol_autor, fecha, imagen_ruta, aprobado)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""",
                    torneo, n.getTitulo(), n.getCuerpo(), n.getAutor(), n.getRolAutor() == Rol.ORGANIZADOR ? "ADMIN" : "VISITANTE", // nombres de la columna
                    momento(n.getFecha()), Sql.nulo(n.getImagenRuta()), n.isAprobado()));
            return null;
        });
    }

    @Override
    public void actualizar(Noticia n) throws PersistenciaException {
        conexion.ejecutar(con -> Sql.cambiar(con, "UPDATE noticias SET titulo = ?, cuerpo = ?, imagen_ruta = ?, aprobado = ? WHERE id = ?",
                n.getTitulo(), n.getCuerpo(), Sql.nulo(n.getImagenRuta()), n.isAprobado(), n.getId()));
    }

    @Override
    public void eliminar(int id) throws PersistenciaException {
        conexion.ejecutar(con -> Sql.cambiar(con, "DELETE FROM noticias WHERE id = ?", id)); // los comentarios se van en cascada
    }

    @Override
    public void insertarComentario(int idNoticia, Comentario c) throws PersistenciaException {
        conexion.ejecutar(con -> {
            c.setId(Sql.insertar(con, "INSERT INTO comentarios (noticia_id, autor, texto, fecha) VALUES (?, ?, ?, ?) RETURNING id",
                    idNoticia, c.getAutor(), c.getTexto(), momento(c.getFecha())));
            return null;
        });
    }

    @Override
    public boolean eliminarComentario(int idNoticia, int idComentario) throws PersistenciaException {
        return conexion.ejecutar(con -> Sql.cambiar(con, "DELETE FROM comentarios WHERE id = ? AND noticia_id = ?", idComentario, idNoticia) == 1);
    }

    @Override
    public boolean imagenVisible(String nombre, boolean incluirPendientes) throws PersistenciaException {
        return conexion.ejecutar(con -> Sql.uno(con, "SELECT EXISTS (SELECT 1 FROM noticias WHERE imagen_ruta = ? AND (aprobado OR ?))",
                rs -> rs.getBoolean(1), nombre, incluirPendientes));
    }

    /* ---------------------------------------------------------------- filas */

    private static Noticia leer(java.sql.ResultSet rs) throws SQLException {
        Noticia n = new Noticia(rs.getInt("id"), rs.getString("titulo"), rs.getString("cuerpo"), rs.getString("autor"),
                Rol.desdeGuardado(rs.getString("rol_autor")), texto(rs.getTimestamp("fecha")));
        n.setImagenRuta(Sql.texto(rs.getString("imagen_ruta")));
        n.setAprobado(rs.getBoolean("aprobado"));
        n.setCampeonato(rs.getString("campeonato"));
        return n;
    }

    private static Comentario leerComentario(java.sql.ResultSet rs) throws SQLException {
        return new Comentario(rs.getInt("id"), rs.getString("autor"), rs.getString("texto"), texto(rs.getTimestamp("fecha")));
    }

    private static String texto(Timestamp fecha) {
        return fecha.toLocalDateTime().format(FORMATO);
    }

    private static Timestamp momento(String fecha) {
        try {
            return Timestamp.valueOf(LocalDateTime.parse(fecha, FORMATO));
        } catch (DateTimeParseException | NullPointerException e) {
            return Timestamp.valueOf(LocalDateTime.now());
        }
    }
}
