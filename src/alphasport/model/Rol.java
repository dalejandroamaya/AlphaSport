package alphasport.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Tipos de cuenta. Cada rol es un conjunto de {@link Permiso}: la regla de quien puede hacer que vive aqui.
 * Al registrarse, la persona elige uno de los dos.
 */
public enum Rol {
    /** Crea y gestiona torneos (antes «Administrador»). */
    ORGANIZADOR("Organizador", EnumSet.of(Permiso.VER, Permiso.GESTIONAR_CAMPEONATO, Permiso.MODERAR_NOTICIAS,
            Permiso.PROPONER_NOTICIA, Permiso.COMENTAR)),
    /** Ve resultados y noticias, comenta, propone noticias y vota (antes «Visitante»). */
    AFICIONADO("Aficionado", EnumSet.of(Permiso.VER, Permiso.PROPONER_NOTICIA, Permiso.COMENTAR, Permiso.VOTAR_JUGADORES));

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

    /**
     * El rol guardado con ese nombre, aceptando tambien los nombres anteriores (ADMINISTRADOR/ADMIN y VISITANTE) que
     * pueden venir de datos guardados antes del cambio; null si no es ninguno.
     */
    public static Rol desdeGuardado(String texto) {
        if (texto == null) {
            return null;
        }
        return switch (texto.trim().toUpperCase()) {
            case "ORGANIZADOR", "ADMINISTRADOR", "ADMIN" -> ORGANIZADOR;
            case "AFICIONADO", "VISITANTE" -> AFICIONADO;
            default -> null;
        };
    }
}
