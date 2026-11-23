package alphasport.model;

/**
 * Usuario que inicio sesion. Es inmutable: el rol no cambia durante la sesion.
 */
public final class Usuario {

    private final String nombre;
    private final Rol rol;

    public Usuario(String nombre, Rol rol) {
        this.nombre = nombre;
        this.rol = rol;
    }

    public String getNombre() {
        return nombre;
    }

    public Rol getRol() {
        return rol;
    }

    public boolean esAdministrador() {
        return rol == Rol.ADMINISTRADOR;
    }

    public boolean puedeEditar() {
        return rol.puedeEditar();
    }

    public boolean puede(Permiso permiso) {
        return rol.puede(permiso);
    }
}
