package alphasport.model;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;

/**
 * Noticia del torneo. La crea el administrador (publicada de una vez) o la propone un
 * visitante (queda pendiente hasta que el administrador la aprueba).
 */
public class Noticia implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private String titulo;
    private String cuerpo;
    private String autor;                // nombre que se muestra («Administración», «Juan»...)
    private Rol rolAutor;                // quien la envio: ORGANIZADOR o AFICIONADO
    private String fecha;                // «2026-11-15 18:40»
    private String imagenRuta = "";      // nombre del archivo de la foto ("" = sin foto)
    private boolean aprobado;            // false = pendiente de moderacion
    private ArrayList<Comentario> comentarios = new ArrayList<>();

    public Noticia() {
    }

    public Noticia(int id, String titulo, String cuerpo, String autor, Rol rolAutor, String fecha) {
        this.id = id;
        this.titulo = titulo;
        this.cuerpo = cuerpo;
        this.autor = autor;
        this.rolAutor = rolAutor;
        this.fecha = fecha;
        // Lo que crea el organizador sale publicado; lo que envia un aficionado espera aprobacion.
        this.aprobado = rolAutor == Rol.ORGANIZADOR;
    }

    public int getId() {
        return id;
    }

    /** Lo pone el repositorio al guardar. */
    public void setId(int id) {
        this.id = id;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getCuerpo() {
        return cuerpo;
    }

    public void setCuerpo(String cuerpo) {
        this.cuerpo = cuerpo;
    }

    public String getAutor() {
        return autor;
    }

    public Rol getRolAutor() {
        return rolAutor;
    }

    public String getFecha() {
        return fecha;
    }

    public String getImagenRuta() {
        return imagenRuta == null ? "" : imagenRuta;
    }

    public void setImagenRuta(String imagenRuta) {
        this.imagenRuta = imagenRuta == null ? "" : imagenRuta;
    }

    public boolean tieneImagen() {
        return !getImagenRuta().isEmpty();
    }

    public boolean isAprobado() {
        return aprobado;
    }

    public void setAprobado(boolean aprobado) {
        this.aprobado = aprobado;
    }

    public ArrayList<Comentario> getComentarios() {
        if (comentarios == null) {
            comentarios = new ArrayList<>();
        }
        return comentarios;
    }

    /**
     * Lee una noticia de un archivo. Los archivos anteriores al registro de cuentas guardan el rol con su
     * nombre viejo (ADMINISTRADOR o VISITANTE); quien lee el archivo lo entrega como otro enum y aqui se
     * convierte por su nombre con {@link Rol#desdeGuardado}.
     */
    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream entrada) throws IOException, ClassNotFoundException {
        ObjectInputStream.GetField campos = entrada.readFields();
        id = campos.get("id", 0);
        titulo = (String) campos.get("titulo", null);
        cuerpo = (String) campos.get("cuerpo", null);
        autor = (String) campos.get("autor", null);
        Object rol = campos.get("rolAutor", null);
        rolAutor = rol instanceof Enum<?> e ? Rol.desdeGuardado(e.name()) : null;
        fecha = (String) campos.get("fecha", null);
        imagenRuta = (String) campos.get("imagenRuta", "");
        aprobado = campos.get("aprobado", false);
        comentarios = (ArrayList<Comentario>) campos.get("comentarios", null);
    }
}
