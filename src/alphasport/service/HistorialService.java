package alphasport.service;

import alphasport.model.Campeonato;
import alphasport.persistence.PersistenciaException;
import alphasport.persistence.Serializacion;
import java.util.List;

/**
 * Historial y respaldos: archivar el campeonato en curso, listar y borrar los archivados, descargar o abrir un
 * respaldo y exportar el historial como texto.
 */
public class HistorialService {

    private final ContextoCampeonato ctx;

    public HistorialService(ContextoCampeonato ctx) {
        this.ctx = ctx;
    }

    public void archivarActual() throws PersistenciaException {
        ctx.archivar();
    }

    public List<Campeonato> listar() {
        try {
            return ctx.repositorioHistorial().listar();
        } catch (PersistenciaException e) {
            throw new ReglaNegocioException("CONEXION", e.getMessage());
        }
    }

    public void eliminarDelHistorial(String codigo) throws PersistenciaException {
        if (!ctx.repositorioHistorial().eliminar(codigo)) {
            throw new ReglaNegocioException("No se encontró ese campeonato en el historial.");
        }
    }

    public byte[] respaldo() throws PersistenciaException {
        ctx.exigirCampeonato();
        return Serializacion.aBytes(ctx.campeonato());
    }

    public void abrirRespaldo(byte[] datos) throws PersistenciaException {
        Campeonato abierto = Serializacion.desdeBytes(datos);
        ctx.reemplazarPor(abierto);
    }

    public String textoHistorial() {
        ctx.exigirCampeonato();
        return new ReporteService().historial(ctx.motor());
    }
}
