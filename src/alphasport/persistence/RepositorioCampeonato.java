package alphasport.persistence;

import alphasport.model.Campeonato;

/**
 * Acceso al campeonato en curso. La implementacion actual usa archivos; en la
 * Fase 3 se reemplaza por una implementacion con base de datos sin tocar el servicio.
 */
public interface RepositorioCampeonato {

    void guardar(Campeonato campeonato) throws PersistenciaException;

    Campeonato cargar() throws PersistenciaException;

    boolean existe();
}
