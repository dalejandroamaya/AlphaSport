package alphasport.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Roles del sistema. Cada rol es un conjunto de {@link Permiso}: la regla de quien puede hacer que vive aqui.
 */
public enum Rol {
    ADMINISTRADOR("Administrador", EnumSet.of(Permiso.VER, Permiso.GESTIONAR_CAMPEONATO, Permiso.MODERAR_NOTICIAS,
            Permiso.PROPONER_NOTICIA, Permiso.COMENTAR)),
    VISITANTE("Visitante", EnumSet.of(Permiso.VER, Permiso.PROPONER_NOTICIA, Permiso.COMENTAR, Permiso.VOTAR_JUGADORES));

    private final String nombre;
    private final Set<Permiso> permisos;

    Rol(String nombre, Set<Permiso> permisos) {
        this.nombre = nombre;
        this.permisos = Collections.unmodifiableSet(permisos);
    }

    public String getNombre() {
        return nombre;
    }

    public Set<Permiso> getPermisos() {
        return permisos;
    }

    public boolean puede(Permiso permiso) {
        return permisos.contains(permiso);
    }

    /** true si el rol puede crear, editar o eliminar; false si es de solo lectura. */
    public boolean puedeEditar() {
        return puede(Permiso.GESTIONAR_CAMPEONATO);
    }
}
