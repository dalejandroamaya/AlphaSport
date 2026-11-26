package alphasport.ui;

import alphasport.persistence.PersistenciaException;
import alphasport.service.ReglaNegocioException;

/**
 * Mensajes de error para el usuario. Los de las reglas del campeonato y los de la base de datos ya están escritos
 * para él (dicen qué pasó y qué hacer). Cualquier otro es un fallo del programa: su detalle técnico se queda en la
 * consola y al usuario se le explica qué pasó con palabras sencillas.
 */
public final class MensajeUsuario {

    /** Lo que ve el usuario cuando falla algo que no es una regla ni la base de datos. */
    public static final String INESPERADO = "No se pudo completar la acción por un problema del programa. "
            + "Intente de nuevo; si vuelve a pasar, avise al administrador del sistema.";

    private MensajeUsuario() {
    }

    /** Texto para mostrar: el de la regla o la base de datos, o el genérico si es un fallo del programa. */
    public static String de(Throwable e) {
        if ((e instanceof ReglaNegocioException || e instanceof PersistenciaException) && e.getMessage() != null && !e.getMessage().isBlank()) {
            return e.getMessage();
        }
        return INESPERADO;
    }
}
