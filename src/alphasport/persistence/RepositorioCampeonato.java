package alphasport.persistence;

import alphasport.model.Campeonato;
import java.util.List;
import java.util.Optional;

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

    /**
     * El campeonato guardado, o vacio si no hay ninguno. Todas las versiones (base de datos, archivo y memoria)
     * responden igual: no tener campeonato no es un error.
     */
    Optional<Campeonato> cargar() throws PersistenciaException;

    /** Lo mismo que {@code cargar().isPresent()}, sin leer todo el campeonato. */
    boolean existe() throws PersistenciaException;

    void eliminar() throws PersistenciaException;

    /**
     * Termina el campeonato: queda en el historial y deja de estar en curso. Por defecto se guarda una copia en
     * el historial y se elimina; PostgreSQL lo pasa al historial tal como esta (con sus ids).
     */
    default void archivarEn(RepositorioHistorial historial, Campeonato campeonato) throws PersistenciaException {
        historial.archivar(campeonato);
        eliminar();
    }

    /** true si otro programa guardo cambios despues de la ultima vez que este leyo o guardo. */
    default boolean desactualizado() throws PersistenciaException {
        return false;
    }

    /** Guarda las notas calculadas de un partido (lista vacia = ya no tiene). */
    default void guardarNotas(int idPartido, List<NotaJugador> notas) throws PersistenciaException {
        // sin base de datos las notas se calculan al consultarlas
    }
}
