package alphasport.persistence.postgres;

import alphasport.model.Campeonato;
import alphasport.persistence.PersistenciaException;
import alphasport.persistence.RepositorioHistorial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Campeonatos archivados en PostgreSQL: cada uno es una copia completa con situacion ARCHIVADO.
 */
public class PostgresRepositorioHistorial implements RepositorioHistorial {

    private final ConexionDB conexion;
    private final CampeonatoDAO campeonatos = new CampeonatoDAO();
    /** Los archivados no cambian: se leen una vez y se vuelven a leer solo si su version es otra. */
    private final Map<TorneoDAO.Cabecera, Campeonato> leidos = new HashMap<>();

    public PostgresRepositorioHistorial(ConexionDB conexion) {
        this.conexion = conexion;
    }

    @Override
    public void archivar(Campeonato c) throws PersistenciaException {
        conexion.ejecutar(con -> {
            TorneoDAO.Cabecera anterior = buscar(con, c.getCodigo());
            int id;
            if (anterior == null) {
                id = campeonatos.torneos().insertar(con, c, TorneoDAO.ARCHIVADO);
            } else {
                id = anterior.id();
                campeonatos.torneos().avanzarVersion(con, id, anterior.version());
            }
            return campeonatos.escribir(con, id, c, new CampeonatoDAO.Huellas());
        });
    }

    @Override
    public List<Campeonato> listar() throws PersistenciaException {
        return conexion.ejecutar(con -> {
            List<TorneoDAO.Cabecera> archivados = campeonatos.torneos().archivados(con);
            leidos.keySet().retainAll(archivados);
            List<Campeonato> lista = new ArrayList<>();
            for (TorneoDAO.Cabecera t : archivados) {
                Campeonato c = leidos.get(t);
                if (c == null) {
                    c = campeonatos.leer(con, t.id());
                    leidos.put(t, c);
                }
                lista.add(c);
            }
            return lista;
        });
    }

    @Override
    public boolean eliminar(String codigo) throws PersistenciaException {
        return conexion.ejecutar(con -> {
            TorneoDAO.Cabecera t = buscar(con, codigo);
            return t != null && campeonatos.torneos().eliminar(con, t.id());
        });
    }

    private TorneoDAO.Cabecera buscar(java.sql.Connection con, String codigo) throws java.sql.SQLException {
        return campeonatos.torneos().archivados(con).stream().filter(t -> t.codigo().equals(codigo)).findFirst().orElse(null);
    }
}
