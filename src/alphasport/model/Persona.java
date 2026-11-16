package alphasport.model;

import java.io.Serializable;

/**
 * Clase base para las personas que pertenecen a un equipo
 * (jugadores y director tecnico).
 */
public abstract class Persona implements Serializable {

    private static final long serialVersionUID = 1L;

    private String nombre;
    private String documento;

    public Persona() {
    }

    public Persona(String nombre, String documento) {
        this.nombre = nombre;
        this.documento = documento;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
    }

    /**
     * Cada tipo de persona indica su rol dentro del equipo.
     */
    public abstract String getRol();

    @Override
    public String toString() {
        return getRol() + ": " + nombre;
    }
}
