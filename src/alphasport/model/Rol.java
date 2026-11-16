package alphasport.model;

/**
 * Roles del sistema. Cada rol sabe que puede hacer, asi la regla vive en un solo lugar.
 */
public enum Rol {
    ADMINISTRADOR("Administrador", true),
    VISITANTE("Visitante", false);

    private final String nombre;
    private final boolean puedeEditar;

    Rol(String nombre, boolean puedeEditar) {
        this.nombre = nombre;
        this.puedeEditar = puedeEditar;
    }

    public String getNombre() {
        return nombre;
    }

    /** true si el rol puede crear, editar o eliminar; false si es de solo lectura. */
    public boolean puedeEditar() {
        return puedeEditar;
    }
}