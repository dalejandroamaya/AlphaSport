package alphasport.model;

import java.io.Serializable;

/**
 * Comentario de una noticia publicada. El texto se guarda ya filtrado (las palabras
 * prohibidas quedan como «***»), asi nunca se muestra el original.
 */
public class Comentario implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private String autor;
    private String texto;
    private String fecha;

    public Comentario() {
    }

    public Comentario(int id, String autor, String texto, String fecha) {
        this.id = id;
        this.autor = autor;
        this.texto = texto;
        this.fecha = fecha;
    }

    public int getId() {
        return id;
    }

    public String getAutor() {
        return autor;
    }

    public String getTexto() {
        return texto;
    }

    public String getFecha() {
        return fecha;
    }
}
