package alphasport.service;

import alphasport.model.Comentario;
import alphasport.model.Noticia;
import alphasport.model.Rol;
import alphasport.persistence.PersistenciaException;
import alphasport.persistence.RepositorioNoticias;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

/**
 * Casos de uso del modulo de noticias:
 * - el administrador crea, edita, aprueba y elimina noticias y comentarios;
 * - el visitante propone noticias (quedan pendientes) y comenta las publicadas.
 *
 * Las noticias viven en una lista en memoria; el repositorio solo las conserva
 * (en memoria o en un archivo, segun la implementacion que se le entregue).
 * Los permisos (quien puede llamar cada metodo) los aplica ApiControlador.
 */
public class NoticiasService {

    public static final int MAXIMO_TITULO = 120;
    public static final int MAXIMO_CUERPO = 4000;
    public static final int MAXIMO_COMENTARIO = 500;
    public static final int MAXIMO_NOMBRE = 40;
    /** Tamaño maximo de una foto (la interfaz la reduce antes de enviarla). */
    public static final int MAXIMO_IMAGEN = 5 * 1024 * 1024;

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Foto enviada desde la interfaz, ya validada. */
    public record Imagen(byte[] datos, String extension) {
    }

    private final RepositorioNoticias repositorio;
    private final ArrayList<Noticia> noticias = new ArrayList<>();
    private int siguienteNoticia = 1;
    private int siguienteComentario = 1;

    public NoticiasService(RepositorioNoticias repositorio) {
        this.repositorio = repositorio;
        try {
            noticias.addAll(repositorio.cargar());
        } catch (PersistenciaException e) {
            System.err.println("No se pudieron cargar las noticias: " + e.getMessage());
        }
        for (Noticia n : noticias) {
            siguienteNoticia = Math.max(siguienteNoticia, n.getId() + 1);
            for (Comentario c : n.getComentarios()) {
                siguienteComentario = Math.max(siguienteComentario, c.getId() + 1);
            }
        }
    }

    /* ---------------------------------------------------------------- consultas */

    /** Feed publico: solo las aprobadas, la mas reciente primero. */
    public List<Noticia> publicadas() {
        return noticias.stream().filter(Noticia::isAprobado).sorted(masRecientePrimero()).toList();
    }

    /** Propuestas de visitantes que esperan moderacion (solo las ve el administrador). */
    public List<Noticia> pendientes() {
        return noticias.stream().filter(n -> !n.isAprobado()).sorted(masRecientePrimero()).toList();
    }

    private static Comparator<Noticia> masRecientePrimero() {
        return Comparator.comparing(Noticia::getFecha).thenComparingInt(Noticia::getId).reversed();
    }

    public Noticia buscar(int id) {
        return noticias.stream().filter(n -> n.getId() == id).findFirst()
                .orElseThrow(() -> new ReglaNegocioException("La noticia no existe (quizá ya fue eliminada)."));
    }

    /**
     * Bytes de una foto. Solo se entregan fotos de noticias publicadas, o de cualquiera si
     * {@code incluirPendientes} (administrador): nadie puede pedir archivos sueltos.
     */
    public byte[] imagen(String nombre, boolean incluirPendientes) throws PersistenciaException {
        boolean permitida = noticias.stream().anyMatch(n -> n.getImagenRuta().equals(nombre) && (n.isAprobado() || incluirPendientes));
        return permitida ? repositorio.leerImagen(nombre) : null;
    }

    /* ---------------------------------------------------------------- visitante */

    /** El visitante propone una noticia: queda pendiente (aprobado = false) hasta que el administrador la apruebe. */
    public Noticia proponer(String titulo, String cuerpo, String autor, Imagen imagen) throws PersistenciaException {
        String nombre = limpiarNombre(autor, "Visitante");
        Noticia n = new Noticia(siguienteNoticia++, validarTitulo(titulo), validarCuerpo(cuerpo), nombre, Rol.VISITANTE, ahora());
        if (imagen != null) {
            n.setImagenRuta(repositorio.guardarImagen(imagen.datos(), imagen.extension()));
        }
        noticias.add(n);
        guardar();
        return n;
    }

    /** Comentario en una noticia publicada. El texto y el nombre pasan por el filtro anti-obscenidades. */
    public Comentario comentar(int idNoticia, String autor, String texto) throws PersistenciaException {
        Noticia n = buscar(idNoticia);
        if (!n.isAprobado()) {
            throw new ReglaNegocioException("Solo se puede comentar una noticia publicada.");
        }
        String limpio = texto == null ? "" : texto.trim();
        if (limpio.isEmpty()) {
            throw new ReglaNegocioException("Escriba el comentario.");
        }
        if (limpio.length() > MAXIMO_COMENTARIO) {
            throw new ReglaNegocioException("El comentario puede tener hasta " + MAXIMO_COMENTARIO + " caracteres.");
        }
        Comentario c = new Comentario(siguienteComentario++, FiltroPalabras.sanear(limpiarNombre(autor, "Anónimo")),
                FiltroPalabras.sanear(limpio), ahora());
        n.getComentarios().add(c);
        guardar();
        return c;
    }

    /* ---------------------------------------------------------------- administrador */

    /**
     * Crea (id null) o edita una noticia oficial; queda publicada (aprobado = true).
     * {@code imagen} null y {@code quitarImagen} false = deja la foto que tenia.
     */
    public Noticia publicar(Integer id, String titulo, String cuerpo, Imagen imagen, boolean quitarImagen) throws PersistenciaException {
        String t = validarTitulo(titulo), c = validarCuerpo(cuerpo);
        Noticia n;
        if (id == null) {
            n = new Noticia(siguienteNoticia++, t, c, "Administración", Rol.ADMINISTRADOR, ahora());
            noticias.add(n);
        } else {
            n = buscar(id);
            n.setTitulo(t);
            n.setCuerpo(c);
            n.setAprobado(true); // editar una propuesta pendiente tambien la publica
        }
        if (imagen != null || quitarImagen) {
            if (n.tieneImagen()) {
                repositorio.eliminarImagen(n.getImagenRuta());
            }
            n.setImagenRuta(imagen == null ? "" : repositorio.guardarImagen(imagen.datos(), imagen.extension()));
        }
        guardar();
        return n;
    }

    /** Aprueba una propuesta: pasa al feed publico. */
    public Noticia aprobar(int id) throws PersistenciaException {
        Noticia n = buscar(id);
        n.setAprobado(true);
        guardar();
        return n;
    }

    /** Rechaza una propuesta o elimina una noticia publicada (con su foto y sus comentarios). */
    public void eliminar(int id) throws PersistenciaException {
        Noticia n = buscar(id);
        noticias.remove(n);
        if (n.tieneImagen()) {
            repositorio.eliminarImagen(n.getImagenRuta());
        }
        guardar();
    }

    public void eliminarComentario(int idNoticia, int idComentario) throws PersistenciaException {
        if (!buscar(idNoticia).getComentarios().removeIf(c -> c.getId() == idComentario)) {
            throw new ReglaNegocioException("El comentario no existe.");
        }
        guardar();
    }

    /* ---------------------------------------------------------------- fotos y validaciones */

    /**
     * Lee una foto enviada como «data URL» (data:image/png;base64,...): es como la envian la
     * web y el escritorio. Solo se aceptan JPG, PNG, GIF y WEBP de verdad (se revisan los bytes).
     */
    public static Imagen imagenDesdeDataUrl(String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) {
            return null;
        }
        int coma = dataUrl.indexOf(',');
        if (!dataUrl.startsWith("data:image/") || coma < 0 || !dataUrl.substring(0, coma).endsWith(";base64")) {
            throw new ReglaNegocioException("La foto no tiene un formato válido.");
        }
        byte[] datos;
        try {
            datos = Base64.getDecoder().decode(dataUrl.substring(coma + 1));
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException("La foto está dañada.");
        }
        if (datos.length > MAXIMO_IMAGEN) {
            throw new ReglaNegocioException("La foto es muy pesada (máximo 5 MB).");
        }
        String extension = extensionReal(datos);
        if (extension == null) {
            throw new ReglaNegocioException("El archivo no es una imagen JPG, PNG, GIF o WEBP.");
        }
        return new Imagen(datos, extension);
    }

    /** Tipo de imagen segun sus primeros bytes (no se confia en el nombre del archivo). */
    static String extensionReal(byte[] d) {
        if (d.length > 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8) {
            return "jpg";
        }
        if (d.length > 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
            return "png";
        }
        if (d.length > 6 && d[0] == 'G' && d[1] == 'I' && d[2] == 'F') {
            return "gif";
        }
        if (d.length > 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "webp";
        }
        return null;
    }

    private static String validarTitulo(String titulo) {
        String t = titulo == null ? "" : titulo.trim();
        if (t.length() < 3) {
            throw new ReglaNegocioException("Escriba un título (al menos 3 caracteres).");
        }
        if (t.length() > MAXIMO_TITULO) {
            throw new ReglaNegocioException("El título puede tener hasta " + MAXIMO_TITULO + " caracteres.");
        }
        return t;
    }

    private static String validarCuerpo(String cuerpo) {
        String c = cuerpo == null ? "" : cuerpo.trim();
        if (c.length() < 10) {
            throw new ReglaNegocioException("Escriba el texto de la noticia (al menos 10 caracteres).");
        }
        if (c.length() > MAXIMO_CUERPO) {
            throw new ReglaNegocioException("La noticia puede tener hasta " + MAXIMO_CUERPO + " caracteres.");
        }
        return c;
    }

    private static String limpiarNombre(String nombre, String porDefecto) {
        String n = nombre == null ? "" : nombre.trim();
        if (n.isEmpty()) {
            return porDefecto;
        }
        return n.length() > MAXIMO_NOMBRE ? n.substring(0, MAXIMO_NOMBRE) : n;
    }

    private static String ahora() {
        return LocalDateTime.now().format(FORMATO_FECHA);
    }

    private void guardar() throws PersistenciaException {
        repositorio.guardar(noticias);
    }
}
