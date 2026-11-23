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
