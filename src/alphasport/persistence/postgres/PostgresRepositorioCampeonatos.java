package alphasport.persistence.postgres;

import alphasport.persistence.PersistenciaException;
import alphasport.persistence.RepositorioCampeonato;
import alphasport.persistence.RepositorioCampeonatos;
import java.util.List;

/**
 * Los campeonatos en curso en PostgreSQL: varias filas de torneos con situacion ACTUAL, cada una con su dueno
 * (propietario_id). Cada campeonato se abre con su propio {@link PostgresRepositorioCampeonato}, atado a su fila.
 */
public class PostgresRepositorioCampeonatos implements RepositorioCampeonatos {

    private final ConexionDB conexion;
    private final TorneoDAO torneos = new TorneoDAO();

    public PostgresRepositorioCampeonatos(ConexionDB conexion) {
        this.conexion = conexion;
    }

    @Override
    public List<Activo> activos() throws PersistenciaException {
        return conexion.ejecutar(con -> torneos.activos(con).stream()
                .map(a -> new Activo(a.codigo(), a.propietario(), a.organizador())).toList());
    }

    @Override
    public RepositorioCampeonato abrir(String codigo) {
        return PostgresRepositorioCampeonato.de(conexion, codigo);
    }

    @Override
    public RepositorioCampeonato nuevo() {
        return PostgresRepositorioCampeonato.nuevo(conexion);
    }

    @Override
    public boolean tomar(String codigo, int propietario) throws PersistenciaException {
        return conexion.ejecutar(con -> torneos.tomar(con, codigo, propietario));
    }
}
