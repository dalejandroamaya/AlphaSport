package alphasport.service;

import alphasport.model.Campeonato;
import alphasport.model.ConfiguracionTorneo;
import alphasport.model.Permiso;
import alphasport.model.Rol;
import alphasport.model.TipoFormato;
import alphasport.model.Usuario;
import alphasport.persistence.PersistenciaException;
import alphasport.persistence.RepositorioCampeonato;
import alphasport.persistence.RepositorioCampeonatos;
import alphasport.persistence.RepositorioHistorial;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Los campeonatos en curso: cada uno tiene su propio {@link CampeonatoService} (con su
 * {@link ContextoCampeonato}, su version y su control de cambios), asi sus equipos, partidos, votos y noticias
 * nunca se mezclan con los de otro. Aqui viven las reglas de quien puede ver o cambiar cada uno:
 * <ul>
 * <li>Organizador: ve y gestiona solo los campeonatos que creo (o que tomo sin dueno).</li>
 * <li>Aficionado: ve todos los campeonatos en curso y no cambia ninguno.</li>
 * </ul>
 * Con {@link #unico(CampeonatoService)} hay un solo campeonato, como antes de que hubiera varios (pruebas y la
 * version con archivos): ahi no hay duenos.
 */
public class GestorCampeonatos {

    /** Crea el servicio de un campeonato sobre su repositorio (con el historial, el reloj y las fotos de la aplicacion). */
    @FunctionalInterface
    public interface FabricaServicio {
        CampeonatoService crear(RepositorioCampeonato repositorio);
    }

    /**
     * Lo que muestran las listas de campeonatos.
     *
     * @param propietario id del Organizador dueno (0 = sin dueno)
     * @param estado      «Por iniciar», «En curso» o «Terminado»
     * @param fase        en que va (jornada, fase o ronda), en palabras
     */
    public record Resumen(String codigo, String nombre, TipoFormato formato, String estado, String fase, int propietario,
            String organizador, int equipos, int partidosJugados, int partidos) {
    }

    /** Un campeonato recien creado y el resumen de los ascensos y descensos que se le aplicaron ("" si no hubo). */
    public record Creado(CampeonatoService servicio, String movimientos) {
    }

    private final RepositorioCampeonatos repositorios;
    private final RepositorioHistorial historial;
    private final FabricaServicio fabrica;
    /** Modo de un solo campeonato (null con varios). */
    private final CampeonatoService unico;
    /** Servicios ya abiertos, uno por campeonato en curso. */
    private final List<CampeonatoService> abiertos = new ArrayList<>();

    public GestorCampeonatos(RepositorioCampeonatos repositorios, RepositorioHistorial historial, FabricaServicio fabrica) {
        this.repositorios = repositorios;
        this.historial = historial;
        this.fabrica = fabrica;
        this.unico = null;
    }

    private GestorCampeonatos(CampeonatoService unico) {
        this.repositorios = null;
        this.historial = null;
        this.fabrica = null;
        this.unico = unico;
    }

    /** Un solo campeonato, sin duenos (pruebas y version con archivos): se comporta como antes de que hubiera varios. */
    public static GestorCampeonatos unico(CampeonatoService servicio) {
        return new GestorCampeonatos(servicio);
    }

    public boolean esUnico() {
        return unico != null;
    }

    /* ---------------------------------------------------------------- listas */

    /**
     * Los campeonatos en curso que el usuario puede ver: el Aficionado, todos; el Organizador, solo los suyos.
     * Se leen de nuevo cada vez: otro programa pudo crear o terminar alguno.
     */
    public synchronized List<Resumen> activos(Usuario u) {
        exigirUsuario(u);
        if (unico != null) {
            unico.sincronizar();
            return unico.hayCampeonato() ? List.of(resumen(unico, null)) : List.of();
        }
        List<Resumen> lista = new ArrayList<>();
        for (RepositorioCampeonatos.Activo a : leerActivos()) {
            if (puedeVer(u, a.propietario())) {
                abierto(a.codigo()).ifPresent(s -> lista.add(resumen(s, a.organizador())));
            }
        }
        return lista;
    }

    /** Solo para el Organizador: los campeonatos en curso que no tienen dueno y puede tomar. */
    public synchronized List<Resumen> sinOrganizador(Usuario u) {
        exigirUsuario(u);
        if (unico != null || !u.puede(Permiso.GESTIONAR_CAMPEONATO)) {
            return List.of();
        }
        List<Resumen> lista = new ArrayList<>();
        for (RepositorioCampeonatos.Activo a : leerActivos()) {
            if (a.propietario() == 0) {
                abierto(a.codigo()).ifPresent(s -> lista.add(resumen(s, null)));
            }
        }
        return lista;
    }

    /* ---------------------------------------------------------------- un campeonato */

    /**
     * El campeonato que el usuario puede ver, o error. CAMPEONATO si no esta en curso; PERMISO si es de otro
     * organizador.
     */
    public synchronized CampeonatoService paraLeer(String codigo, Usuario u) {
        exigirUsuario(u);
        CampeonatoService s = buscar(codigo);
        if (!puedeVer(u, s.propietario())) {
            throw new ReglaNegocioException("PERMISO", "Este campeonato es de otro organizador: solo puede ver y gestionar los suyos.");
        }
        return s;
    }

    /** El campeonato que el usuario puede cambiar: solo su dueno, y solo si su rol gestiona campeonatos. */
    public synchronized CampeonatoService paraGestionar(String codigo, Usuario u) {
        exigirUsuario(u);
        if (!u.puede(Permiso.GESTIONAR_CAMPEONATO)) {
            throw new ReglaNegocioException("PERMISO", "Su rol (" + u.getRol().getNombre() + ") no puede crear, editar ni eliminar.");
        }
        CampeonatoService s = buscar(codigo);
        if (!esDueno(u, s.propietario())) {
            throw new ReglaNegocioException("PERMISO", s.propietario() == 0
                    ? "Este campeonato no tiene organizador: tómelo antes de gestionarlo."
                    : "Este campeonato es de otro organizador: solo puede gestionar los suyos.");
        }
        return s;
    }

    /**
     * El campeonato a usar cuando la peticion no dice cual: si el usuario solo puede ver uno, ese; si no, ninguno
     * (hay que elegir). Nunca se elige «el primero» entre varios.
     */
    public synchronized Optional<CampeonatoService> porDefecto(Usuario u) {
        if (u == null) {
            return Optional.empty();
        }
        if (unico != null) {
            return Optional.of(unico);
        }
        List<RepositorioCampeonatos.Activo> visibles = leerActivos().stream().filter(a -> puedeVer(u, a.propietario())).toList();
        return visibles.size() == 1 ? abierto(visibles.get(0).codigo()) : Optional.empty();
    }

    /* ---------------------------------------------------------------- cambios */

    /**
     * Crea un campeonato del Organizador. Con {@code origen} (codigo de otro de sus campeonatos) copia sus equipos
     * y aplica los ascensos y descensos si corresponde. Con un solo campeonato, reemplaza al que estaba, como antes.
     */
    public synchronized Creado crear(Usuario u, String nombre, TipoFormato formato, ConfiguracionTorneo configuracion, String origen)
            throws PersistenciaException {
        exigirUsuario(u);
        if (!u.puede(Permiso.GESTIONAR_CAMPEONATO)) {
            throw new ReglaNegocioException("PERMISO", "Su rol (" + u.getRol().getNombre() + ") no puede crear campeonatos.");
        }
        if (unico != null) {
            return new Creado(unico, unico.crear(nombre, formato, configuracion, origen != null));
        }
        Campeonato anterior = origen == null || origen.isBlank() ? null : paraGestionar(origen, u).getCampeonato();
        CampeonatoService nuevo = fabrica.crear(repositorios.nuevo());
        nuevo.contexto().asignarPropietario(u.getId());
        String movimientos = nuevo.crear(nombre, formato, configuracion, anterior);
        abiertos.add(nuevo);
        return new Creado(nuevo, movimientos);
    }

    /** Termina un campeonato del Organizador: pasa al historial y deja de estar en curso. Los demas no se tocan. */
    public synchronized void terminar(String codigo, Usuario u) throws PersistenciaException {
        CampeonatoService s = paraGestionar(codigo, u);
        s.terminar();
        olvidarVacios();
    }

    /** Un Organizador toma un campeonato en curso que no tiene dueno. */
    public synchronized CampeonatoService tomar(String codigo, Usuario u) throws PersistenciaException {
        exigirUsuario(u);
        if (unico != null || !u.puede(Permiso.GESTIONAR_CAMPEONATO) || u.getId() <= 0) {
            throw new ReglaNegocioException("PERMISO", "Solo un Organizador con cuenta puede tomar un campeonato.");
        }
        CampeonatoService s = buscar(codigo);
        if (!repositorios.tomar(codigo, u.getId())) {
            throw new ReglaNegocioException("PERMISO", "Ese campeonato ya tiene organizador.");
        }
        s.contexto().asignarPropietario(u.getId());
        s.sincronizar(); // la fila cambio de version al tomarla
        return s;
    }

    /** Despues de una accion: suelta los servicios cuyo campeonato ya no esta en curso (se elimino o se termino). */
    public synchronized void olvidarVacios() {
        if (unico == null) {
            abiertos.removeIf(s -> !s.hayCampeonato());
        }
    }

    /* ---------------------------------------------------------------- historial */

    /** Campeonatos archivados que ve el usuario: el Organizador, solo los suyos; el Aficionado, todos. */
    public List<Campeonato> historial(Usuario u) {
        exigirUsuario(u);
        List<Campeonato> todos = unico != null ? unico.historial().listar() : listarHistorial();
        return todos.stream().filter(c -> puedeVer(u, c.getPropietario())).toList();
    }

    /** Elimina del historial un campeonato archivado del Organizador. */
    public void eliminarDelHistorial(String codigo, Usuario u) throws PersistenciaException {
        exigirUsuario(u);
        if (!u.puede(Permiso.GESTIONAR_CAMPEONATO)) {
            throw new ReglaNegocioException("PERMISO", "Su rol (" + u.getRol().getNombre() + ") no puede eliminar del historial.");
        }
        if (unico != null) {
            unico.historial().eliminarDelHistorial(codigo);
            return;
        }
        Campeonato c = listarHistorial().stream().filter(x -> x.getCodigo().equals(codigo)).findFirst()
                .orElseThrow(() -> new ReglaNegocioException("No se encontró ese campeonato en el historial."));
        if (!esDueno(u, c.getPropietario())) {
            throw new ReglaNegocioException("PERMISO", "Ese campeonato del historial es de otro organizador.");
        }
        if (!historial.eliminar(codigo)) {
            throw new ReglaNegocioException("No se encontró ese campeonato en el historial.");
        }
    }

    /* ---------------------------------------------------------------- reglas y apoyo */

    /** El Aficionado ve todo; el Organizador, lo suyo. Con un solo campeonato no hay duenos. */
    private boolean puedeVer(Usuario u, int propietario) {
        return unico != null || u.getRol() != Rol.ORGANIZADOR || esDueno(u, propietario);
    }

    private boolean esDueno(Usuario u, int propietario) {
        return unico != null || (propietario > 0 && propietario == u.getId());
    }

    private static void exigirUsuario(Usuario u) {
        if (u == null) {
            throw new ReglaNegocioException("LOGIN", "Inicie sesión para continuar.");
        }
    }

    /** El servicio del campeonato en curso con ese codigo; CAMPEONATO si no esta. */
    private CampeonatoService buscar(String codigo) {
        if (unico != null) {
            unico.sincronizar();
            if (unico.hayCampeonato() && (codigo == null || codigo.equals(unico.getCampeonato().getCodigo()))) {
                return unico;
            }
            throw noEsta();
        }
        if (codigo == null || codigo.isBlank()) {
            throw new ReglaNegocioException("CAMPEONATO", "Elija un campeonato.");
        }
        return abierto(codigo).orElseThrow(GestorCampeonatos::noEsta);
    }

    private static ReglaNegocioException noEsta() {
        return new ReglaNegocioException("CAMPEONATO", "Ese campeonato ya no está en curso. Elija otro.");
    }

    /** El servicio abierto de ese campeonato, al dia con la base; lo abre si hace falta. Vacio si no esta en curso. */
    private Optional<CampeonatoService> abierto(String codigo) {
        for (CampeonatoService s : abiertos) {
            if (s.hayCampeonato() && s.getCampeonato().getCodigo().equals(codigo)) {
                s.sincronizar();
                if (s.hayCampeonato() && s.getCampeonato().getCodigo().equals(codigo)) {
                    return Optional.of(s);
                }
            }
        }
        olvidarVacios();
        CampeonatoService s = fabrica.crear(repositorios.abrir(codigo));
        try {
            s.cargarGuardado();
        } catch (PersistenciaException e) {
            throw new ReglaNegocioException("CONEXION", e.getMessage());
        }
        if (!s.hayCampeonato()) {
            return Optional.empty();
        }
        abiertos.add(s);
        return Optional.of(s);
    }

    private List<RepositorioCampeonatos.Activo> leerActivos() {
        try {
            return repositorios.activos();
        } catch (PersistenciaException e) {
            throw new ReglaNegocioException("CONEXION", e.getMessage());
        }
    }

    private List<Campeonato> listarHistorial() {
        try {
            return historial.listar();
        } catch (PersistenciaException e) {
            throw new ReglaNegocioException("CONEXION", e.getMessage());
        }
    }

    private static Resumen resumen(CampeonatoService s, String organizador) {
        Campeonato c = s.getCampeonato();
        String estado = c.isTerminado() ? "Terminado" : c.isIniciado() ? "En curso" : "Por iniciar";
        int jugados = (int) c.getPartidos().stream().filter(alphasport.model.Partido::isJugado).count();
        return new Resumen(c.getCodigo(), c.getNombreCampeonato(), c.getFormato(), estado, s.getMotor().describirEstado(),
                c.getPropietario(), organizador, c.getEquiposActivos().size(), jugados, c.getPartidos().size());
    }
}
