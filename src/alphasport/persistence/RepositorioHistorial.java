package alphasport.persistence;

import alphasport.model.Campeonato;
import java.util.List;

/**
 * Acceso a los campeonatos archivados (historial y palmares).
 */
public interface RepositorioHistorial {

    void archivar(Campeonato campeonato) throws PersistenciaException;

    List<Campeonato> listar();

    boolean eliminar(String codigo);
}
