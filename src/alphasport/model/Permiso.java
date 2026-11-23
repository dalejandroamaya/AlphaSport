package alphasport.model;

/**
 * Lo que un rol puede hacer. Un rol nuevo (por ejemplo, el jugador del modo Fantasy) es solo otro conjunto
 * de permisos en {@link Rol}: las pantallas y el servidor preguntan por el permiso, no por el rol.
 */
public enum Permiso {
    /** Consultar el campeonato, las estadisticas, el historial y las noticias publicadas. */
    VER("ver el campeonato"),
    /** Crear, editar y eliminar: campeonato, equipos, partidos, votaciones e historial. */
    GESTIONAR_CAMPEONATO("crear, editar ni eliminar"),
    /** Publicar, aprobar y eliminar noticias y comentarios; ver las propuestas pendientes. */
    MODERAR_NOTICIAS("publicar ni moderar noticias"),
    /** Proponer una noticia (queda pendiente hasta que se apruebe). */
    PROPONER_NOTICIA("proponer noticias"),
    COMENTAR("comentar"),
    /** Calificar a los jugadores de un partido. El administrador no vota: ya influye al registrar el partido. */
    VOTAR_JUGADORES("calificar a los jugadores");

    private final String accion;

    Permiso(String accion) {
        this.accion = accion;
    }

    /** Lo que permite, para el mensaje de «no puede ...». */
    public String getAccion() {
        return accion;
    }
}
