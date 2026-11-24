package alphasport.persistence;

import java.util.List;

/**
 * Los campeonatos en curso (puede haber varios a la vez, cada uno con su dueno). Da un
 * {@link RepositorioCampeonato} atado a cada uno: lo que se guarda en uno nunca toca a otro.
 * La version real es {@link alphasport.persistence.postgres.PostgresRepositorioCampeonatos}; la de memoria es
 * para las pruebas.
 */
public interface RepositorioCampeonatos {

    /**
     * Un campeonato en curso.
     *
     * @param propietario id del Organizador dueno (0 = sin dueno)
     * @param organizador su nombre para mostrar (null si no tiene dueno)
     */
    record Activo(String codigo, int propietario, String organizador) {
    }

    /** Los campeonatos en curso, del mas antiguo al mas nuevo. */
    List<Activo> activos() throws PersistenciaException;

    /** Repositorio atado al campeonato en curso con ese codigo (si no existe, cargar() devuelve vacio). */
    RepositorioCampeonato abrir(String codigo);

    /** Repositorio para un campeonato que todavia no existe: el primer guardado lo crea. */
    RepositorioCampeonato nuevo();

    /**
     * Un Organizador toma un campeonato en curso que no tiene dueno. Si ya tiene dueno no cambia nada.
     *
     * @return true si lo tomo
     */
    boolean tomar(String codigo, int propietario) throws PersistenciaException;
}
