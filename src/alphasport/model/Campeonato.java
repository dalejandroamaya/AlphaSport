package alphasport.model;

import java.io.Serializable;
import java.util.ArrayList;

public class Campeonato implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int SIN_CAMPEON = -1;

    private String nombreCampeonato;
    private TipoFormato formato;
    private ConfiguracionTorneo configuracion;
    private ArrayList<Equipo> equipos = new ArrayList<>();
    private ArrayList<Partido> partidos = new ArrayList<>();
    private ArrayList<Fase> fases = new ArrayList<>();
    private int siguienteIdEquipo = 1;
    private int siguienteIdPartido = 1;
    private int siguienteIdJugador = 1;
    private int pasoActual = 0;
    private int idCampeon = SIN_CAMPEON;
    private long semilla = System.currentTimeMillis();
    private String codigo;        // identifica el campeonato dentro del historial
    private String fechaArchivo;  // cuando se guardo en el historial

    public Campeonato() {
    }

    public Campeonato(String nombreCampeonato, TipoFormato formato, ConfiguracionTorneo configuracion) {
        this.nombreCampeonato = nombreCampeonato;
        this.formato = formato;
        this.configuracion = configuracion;
    }

    // ---------------- equipos ----------------
    public Equipo agregarEquipo(String nombre) {
        if (buscarEquipoPorNombre(nombre) != null) {
            return null;
        }
        Equipo nuevo = new Equipo(siguienteIdEquipo++, nombre);
        equipos.add(nuevo);
        return nuevo;
    }

    public Equipo buscarEquipo(int id) {
        for (Equipo e : equipos) {
            if (e.getId() == id) {
                return e;
            }
        }
        return null;
    }

    public Equipo buscarEquipoPorNombre(String nombre) {
        for (Equipo e : equipos) {
            if (e.getNombreEquipo().equalsIgnoreCase(nombre.trim())) {
                return e;
            }
        }
        return null;
    }

    /**
     * Copia equipos de otro campeonato (con su director tecnico y plantilla)
     * para empezar uno nuevo con los mismos participantes.
     */
    public void importarEquipos(ArrayList<Equipo> origen) {
        for (Equipo viejo : origen) {
            if (viejo.isRetirado() || buscarEquipoPorNombre(viejo.getNombreEquipo()) != null) {
                continue;
            }
            Equipo nuevo = agregarEquipo(viejo.getNombreEquipo());
            nuevo.setEstadio(viejo.getEstadio());
            DirectorTecnico dt = viejo.getDirectorTecnico();
            if (dt != null) {
                nuevo.setDirectorTecnico(new DirectorTecnico(dt.getNombre(), dt.getDocumento(), dt.getNacionalidad()));
            }
            for (Jugador j : viejo.getJugadores()) {
                Jugador copia = new Jugador(j.getNombre(), j.getDocumento(), j.getDorsal(), j.getPosicion());
                copia.setId(nuevoIdJugador());
                nuevo.agregarJugador(copia);
            }
        }
    }

    /**
     * Id unico para un jugador nuevo (los ids no se repiten entre equipos).
     */
    public int nuevoIdJugador() {
        if (siguienteIdJugador <= 0) { // archivos guardados antes de existir este campo
            siguienteIdJugador = 1;
        }
        for (Equipo e : equipos) {
            for (Jugador j : e.getJugadores()) {
                siguienteIdJugador = Math.max(siguienteIdJugador, j.getId() + 1);
            }
        }
        return siguienteIdJugador++;
    }

    public Jugador buscarJugador(int idEquipo, int idJugador) {
        Equipo e = buscarEquipo(idEquipo);
        if (e != null) {
            for (Jugador j : e.getJugadores()) {
                if (j.getId() == idJugador) {
                    return j;
                }
            }
        }
        return null;
    }

    public String nombreDe(int idEquipo) {
        if (idEquipo == Fase.LIBRE) {
            return "(libre)";
        }
        Equipo e = buscarEquipo(idEquipo);
        return e == null ? "?" : e.getNombreEquipo();
    }

    public ArrayList<Equipo> getEquiposActivos() {
        ArrayList<Equipo> activos = new ArrayList<>();
        for (Equipo e : equipos) {
            if (!e.isRetirado()) {
                activos.add(e);
            }
        }
        return activos;
    }

    // ---------------- partidos y fases ----------------
    public Partido crearPartido(int idLocal, int idVisitante) {
        Partido p = new Partido(siguienteIdPartido++, idLocal, idVisitante);
        partidos.add(p);
        return p;
    }

    public Partido buscarPartido(int id) {
        for (Partido p : partidos) {
            if (p.getId() == id) {
                return p;
            }
        }
        return null;
    }

    public ArrayList<Partido> partidosDeFase(int indiceFase) {
        ArrayList<Partido> lista = new ArrayList<>();
        for (Partido p : partidos) {
            if (p.getIndiceFase() == indiceFase) {
                lista.add(p);
            }
        }
        return lista;
    }

    public Fase getFaseActual() {
        return fases.isEmpty() ? null : fases.get(fases.size() - 1);
    }

    public boolean isIniciado() {
        return !fases.isEmpty();
    }

    public boolean isTerminado() {
        return idCampeon != SIN_CAMPEON;
    }

    // ---------------- getters y setters ----------------
    public String getNombreCampeonato() {
        return nombreCampeonato;
    }

    public void setNombreCampeonato(String nombreCampeonato) {
        this.nombreCampeonato = nombreCampeonato;
    }

    public TipoFormato getFormato() {
        return formato;
    }

    public void setFormato(TipoFormato formato) {
        this.formato = formato;
    }

    public ConfiguracionTorneo getConfiguracion() {
        return configuracion;
    }

    public void setConfiguracion(ConfiguracionTorneo configuracion) {
        this.configuracion = configuracion;
    }

    public ArrayList<Equipo> getEquipos() {
        return equipos;
    }

    public void setEquipos(ArrayList<Equipo> equipos) {
        this.equipos = equipos;
    }

    public ArrayList<Partido> getPartidos() {
        return partidos;
    }

    public void setPartidos(ArrayList<Partido> partidos) {
        this.partidos = partidos;
    }

    public ArrayList<Fase> getFases() {
        return fases;
    }

    public void setFases(ArrayList<Fase> fases) {
        this.fases = fases;
    }

    public int getPasoActual() {
        return pasoActual;
    }

    public void setPasoActual(int pasoActual) {
        this.pasoActual = pasoActual;
    }

    public int getIdCampeon() {
        return idCampeon;
    }

    public void setIdCampeon(int idCampeon) {
        this.idCampeon = idCampeon;
    }

    public String getCodigo() {
        if (codigo == null) {
            codigo = java.util.UUID.randomUUID().toString().substring(0, 8);
        }
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getFechaArchivo() {
        return fechaArchivo;
    }

    public void setFechaArchivo(String fechaArchivo) {
        this.fechaArchivo = fechaArchivo;
    }

    /**
     * true si ya se jugo al menos un partido (vale la pena guardarlo en el historial).
     */
    public boolean tienePartidosJugados() {
        for (Partido p : partidos) {
            if (p.isJugado()) {
                return true;
            }
        }
        return false;
    }

    public long getSemilla() {
        return semilla;
    }

    public void setSemilla(long semilla) {
        this.semilla = semilla;
    }
}
