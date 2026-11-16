package alphasport.model;

public class DirectorTecnico extends Persona {

    private static final long serialVersionUID = 1L;

    private String nacionalidad;

    public DirectorTecnico() {
    }

    public DirectorTecnico(String nombre, String documento, String nacionalidad) {
        super(nombre, documento);
        this.nacionalidad = nacionalidad;
    }

    public String getNacionalidad() {
        return nacionalidad;
    }

    public void setNacionalidad(String nacionalidad) {
        this.nacionalidad = nacionalidad;
    }

    @Override
    public String getRol() {
        return "Director tecnico";
    }
}
