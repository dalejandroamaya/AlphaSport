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
    private static final String COLUMNAS = "SELECT id, titulo, cuerpo, autor, rol_autor, fecha, imagen_ruta, aprobado FROM noticias ";

    private final ConexionDB conexion;

    public PostgresRepositorioNoticias(ConexionDB conexion) {
        this.conexion = conexion;
    }

    @Override
    public List<Noticia> publicadas() throws PersistenciaException {
        return conexion.ejecutar(con -> listar(con, true));
    }

    @Override
    public List<Noticia> pendientes() throws PersistenciaException {
        return conexion.ejecutar(con -> listar(con, false));
    }

    /** Dos consultas: las noticias y, de una vez, los comentarios de todas. */
    private static List<Noticia> listar(Connection con, boolean aprobadas) throws SQLException {
        Map<Integer, Noticia> porId = new HashMap<>();
        List<Noticia> lista = Sql.listar(con, COLUMNAS + "WHERE aprobado = ? ORDER BY fecha DESC, id DESC", PostgresRepositorioNoticias::leer, aprobadas);
        lista.forEach(n -> porId.put(n.getId(), n));
        Sql.listar(con, """
                SELECT c.noticia_id, c.id, c.autor, c.texto, c.fecha FROM comentarios c JOIN noticias n ON n.id = c.noticia_id
                 WHERE n.aprobado = ? ORDER BY c.id""", rs -> porId.get(rs.getInt(1)).getComentarios().add(leerComentario(rs)), aprobadas);
        return lista;
    }

    @Override
    public Optional<Noticia> buscar(int id) throws PersistenciaException {
        return Optional.ofNullable(conexion.ejecutar(con -> {
            Noticia n = Sql.uno(con, COLUMNAS + "WHERE id = ?", PostgresRepositorioNoticias::leer, id);
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
            n.setId(Sql.insertar(con, """
                    INSERT INTO noticias (titulo, cuerpo, autor, rol_autor, fecha, imagen_ruta, aprobado) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id""",
                    n.getTitulo(), n.getCuerpo(), n.getAutor(), n.getRolAutor() == Rol.ADMINISTRADOR ? "ADMIN" : "VISITANTE",
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
                rs.getString("rol_autor").equals("ADMIN") ? Rol.ADMINISTRADOR : Rol.VISITANTE, texto(rs.getTimestamp("fecha")));
        n.setImagenRuta(Sql.texto(rs.getString("imagen_ruta")));
        n.setAprobado(rs.getBoolean("aprobado"));
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
