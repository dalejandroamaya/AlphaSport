package alphasport.persistence;

import alphasport.model.Campeonato;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;

/**
 * Varios campeonatos en curso en memoria, para las pruebas. Cada uno se guarda como copia (no se comparte el
 * objeto) y con una version, como en PostgreSQL: dos repositorios del mismo campeonato notan los cambios del otro
 * y no se pisan.
 */
public class RepositorioCampeonatosEnMemoria implements RepositorioCampeonatos {

    private record Guardado(byte[] datos, int version) {
    }

    private final Map<String, Guardado> guardados = new LinkedHashMap<>();
    /** Nombre del organizador a partir de su id (en la base sale de la tabla usuarios). */
    private final IntFunction<String> organizadores;

    public RepositorioCampeonatosEnMemoria() {
        this(id -> null);
    }

    public RepositorioCampeonatosEnMemoria(IntFunction<String> organizadores) {
        this.organizadores = organizadores;
    }

    @Override
    public synchronized List<Activo> activos() throws PersistenciaException {
        List<Activo> lista = new ArrayList<>();
        for (Map.Entry<String, Guardado> e : guardados.entrySet()) {
            int propietario = Serializacion.desdeBytes(e.getValue().datos()).getPropietario();
            lista.add(new Activo(e.getKey(), propietario, propietario > 0 ? organizadores.apply(propietario) : null));
        }
        return lista;
    }

    @Override
    public RepositorioCampeonato abrir(String codigo) {
        return new Uno(codigo);
    }

    @Override
    public RepositorioCampeonato nuevo() {
        return new Uno(null);
    }

    @Override
    public synchronized boolean tomar(String codigo, int propietario) throws PersistenciaException {
        Guardado g = guardados.get(codigo);
        if (g == null) {
            return false;
        }
        Campeonato c = Serializacion.desdeBytes(g.datos());
        if (c.getPropietario() != 0) {
            return false;
        }
        c.setPropietario(propietario);
        guardados.put(codigo, new Guardado(Serializacion.aBytes(c), g.version() + 1));
        return true;
    }

    /** Repositorio atado a un campeonato (por su codigo). */
    private final class Uno implements RepositorioCampeonato {

        private String codigo;
        /** Version que este repositorio leyo o guardo por ultima vez (0 = ninguna). */
        private int conocida;

        Uno(String codigo) {
            this.codigo = codigo;
        }

        @Override
        public void guardar(Campeonato c) throws PersistenciaException {
            synchronized (RepositorioCampeonatosEnMemoria.this) {
                Guardado actual = codigo == null ? null : guardados.get(codigo);
                int enLaBase = actual == null ? 0 : actual.version();
                if (enLaBase != conocida) {
                    throw new PersistenciaException("Otro programa cambió el campeonato al mismo tiempo.", null);
                }
                if (codigo != null && !codigo.equals(c.getCodigo())) {
                    guardados.remove(codigo); // se rehizo con otro codigo (otro formato o un respaldo)
                }
                codigo = c.getCodigo();
                Guardado previo = guardados.get(codigo);
                conocida = (previo == null ? 0 : previo.version()) + 1;
                guardados.put(codigo, new Guardado(Serializacion.aBytes(c), conocida));
            }
        }

        @Override
        public Optional<Campeonato> cargar() throws PersistenciaException {
            synchronized (RepositorioCampeonatosEnMemoria.this) {
                Guardado g = codigo == null ? null : guardados.get(codigo);
                conocida = g == null ? 0 : g.version();
                return g == null ? Optional.empty() : Optional.of(Serializacion.desdeBytes(g.datos()));
            }
        }

        @Override
        public boolean existe() {
            synchronized (RepositorioCampeonatosEnMemoria.this) {
                return codigo != null && guardados.containsKey(codigo);
            }
        }

        @Override
        public void eliminar() {
            synchronized (RepositorioCampeonatosEnMemoria.this) {
                if (codigo != null) {
                    guardados.remove(codigo);
                }
                conocida = 0;
            }
        }

        @Override
        public boolean desactualizado() {
            synchronized (RepositorioCampeonatosEnMemoria.this) {
                Guardado g = codigo == null ? null : guardados.get(codigo);
                return (g == null ? 0 : g.version()) != conocida;
            }
        }
    }
}
