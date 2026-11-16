package alphasport.persistence;

/**
 * Error al guardar o leer datos, independiente del medio de almacenamiento.
 */
public class PersistenciaException extends Exception {

    public PersistenciaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
