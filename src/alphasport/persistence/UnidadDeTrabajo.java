package alphasport.persistence;

/**
 * Agrupa varias escrituras (por ejemplo, el partido y sus notas) en una sola transaccion: se guardan todas
 * o ninguna. Con PostgreSQL la implementa {@link alphasport.persistence.postgres.ConexionDB}.
 */
public interface UnidadDeTrabajo {

    /** Sin base de datos (archivos y pruebas en memoria): cada escritura va por su cuenta. */
    UnidadDeTrabajo DIRECTA = new UnidadDeTrabajo() {
        @Override
        public <T> T enTransaccion(Paso<T> paso) throws PersistenciaException {
            return paso.hacer();
        }
    };

    /** Hace el paso completo en una transaccion: si algo falla, no queda nada guardado a medias. */
    <T> T enTransaccion(Paso<T> paso) throws PersistenciaException;

    @FunctionalInterface
    interface Paso<T> {
        T hacer() throws PersistenciaException;
    }
}
