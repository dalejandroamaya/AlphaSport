package alphasport.model;

/**
 * Usuario que inicio sesion. Es inmutable: el rol no cambia durante la sesion.
 */
public final class Usuario {

    /** Id de la cuenta (usuarios.id); 0 si no viene de una cuenta guardada. Identifica al dueno de un campeonato. */
    private final int id;
    private final String nombre;
    private final Rol rol;

    public Usuario(int id, String nombre, Rol rol) {
        this.id = id;
        this.nombre = nombre;
        this.rol = rol;
    }

    public Usuario(String nombre, Rol rol) {
        this(0, nombre, rol);
    }

    public int getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public Rol getRol() {
        return rol;
    }

    public boolean esOrganizador() {
        return rol == Rol.ORGANIZADOR;
    }

    public boolean puedeEditar() {
        return rol.puedeEditar();
    }

    public boolean puede(Permiso permiso) {
        return rol.puede(permiso);
    }
}
