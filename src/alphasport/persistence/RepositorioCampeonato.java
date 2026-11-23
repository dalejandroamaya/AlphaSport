package alphasport.persistence;

import alphasport.model.Campeonato;
import java.util.List;

/**
 * Acceso al campeonato en curso. El programa usa PostgreSQL
 * ({@link alphasport.persistence.postgres.PostgresRepositorioCampeonato}); la version con archivos queda para
 * las pruebas y para pasar los datos anteriores a la base de datos.
 */
public interface RepositorioCampeonato {

    void guardar(Campeonato campeonato) throws PersistenciaException;

    /**
     * Guarda {@code nuevo} en lugar del campeonato en curso. Con {@code archivarAnterior} el que estaba pasa al
     * historial tal como esta guardado, con sus ids (la copia que ya hizo el historial se cambia por el original):
     * lo que apunta a sus jugadores y partidos sigue valiendo. Sin el, el anterior se descarta.
     */
    default void reemplazar(Campeonato nuevo, boolean archivarAnterior) throws PersistenciaException {
        guardar(nuevo); // con archivos el historial guarda su propia copia
    }

    /** El campeonato guardado, o null si no hay ninguno. */
    Campeonato cargar() throws PersistenciaException;

    boolean existe() throws PersistenciaException;

    void eliminar() throws PersistenciaException;

    /** true si otro programa guardo cambios despues de la ultima vez que este leyo o guardo. */
    default boolean desactualizado() throws PersistenciaException {
        return false;
    }

    /** Guarda las notas calculadas de un partido (lista vacia = ya no tiene). */
    default void guardarNotas(int idPartido, List<NotaJugador> notas) throws PersistenciaException {
        // sin base de datos las notas se calculan al consultarlas
    }
}
