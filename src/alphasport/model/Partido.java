package alphasport.model;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Antes Partido heredaba de Campeonato (un partido NO es un campeonato).
 * Ahora es una clase independiente que guarda a que fase, grupo y ronda
 * pertenece, ademas del marcador, penales, tarjetas y datos de programacion.
 */
public class Partido implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int SIN_PENALES = -1;
    /** Horas que dura la votacion del publico si el administrador no elige otra duracion. */
    public static final int HORAS_VOTACION_POR_DEFECTO = 6;

    private int id;
    private int paso;          // en que "avance" del campeonato se genero
    private int indiceFase;    // posicion de la fase dentro del campeonato
    private String grupo = ""; // "A", "B"... solo en fases de grupos
    private int ronda;         // jornada (fases de tabla) o ronda (eliminatorias)
    private String nombreRonda = "";
    private String serie = ""; // identifica la llave (ida y vuelta comparten serie)
    private int pierna;        // 0 = partido unico, 1 = ida, 2 = vuelta
    private String llave = ""; // doble eliminacion: Ganadores / Perdedores / Gran final
    private boolean eliminatorio; // si es true el partido o la serie necesita un ganador

    private int idLocal;
    private int idVisitante;

    private EstadoPartido estado = EstadoPartido.PENDIENTE;
    private int golesLocal;
    private int golesVisitante;
    private int penalesLocal = SIN_PENALES;
    private int penalesVisitante = SIN_PENALES;
    private int amarillasLocal;
    private int rojasLocal;
    private int amarillasVisitante;
    private int rojasVisitante;
    private ArrayList<Tarjeta> tarjetas = new ArrayList<>();
    private ArrayList<Cambio> cambios = new ArrayList<>();
    private ArrayList<Gol> goles = new ArrayList<>();                 // goles por jugador (null en archivos anteriores)
    private ArrayList<Integer> titularesLocal = new ArrayList<>();     // ids del XI inicial
    private ArrayList<Integer> titularesVisitante = new ArrayList<>();
    private String formacionLocal;
    private String formacionVisitante;

    private String fecha = "Por definir";
    private String hora = "";
    private String sede = "";
    private String observaciones = "";

   
    private Boolean walkover = false; // null en archivos anteriores: alli solo lo decia la observacion
    private long finalizadoEn;
    private int horasVotacion;      // 0 = por defecto (asi llegan los partidos de archivos anteriores)
    private long votacionCerradaEn; // hora en que el administrador la cerro antes de tiempo; 0 = no la cerro
    private ArrayList<VotoJugador> votos = new ArrayList<>();

    public Partido() {
    }

    public Partido(int id, int idLocal, int idVisitante) {
        this.id = id;
        this.idLocal = idLocal;
        this.idVisitante = idVisitante;
    }

    public boolean isJugado() {
        return estado == EstadoPartido.JUGADO;
    }

    public boolean tienePenales() {
        return penalesLocal != SIN_PENALES && penalesVisitante != SIN_PENALES;
    }

    public boolean involucra(int idEquipo) {
        return idLocal == idEquipo || idVisitante == idEquipo;
    }

    public int getRival(int idEquipo) {
        return idLocal == idEquipo ? idVisitante : idLocal;
    }

    public int getGolesDe(int idEquipo) {
        return idLocal == idEquipo ? golesLocal : golesVisitante;
    }

    /**
     * Ganador de este partido (contando penales). Devuelve -1 si no se ha
     * jugado o si quedo empatado sin penales.
     */
    public int getGanador() {
        if (!isJugado()) {
            return -1;
        }
        if (golesLocal > golesVisitante) {
            return idLocal;
        }
        if (golesVisitante > golesLocal) {
            return idVisitante;
        }
        if (tienePenales() && penalesLocal != penalesVisitante) {
            return penalesLocal > penalesVisitante ? idLocal : idVisitante;
        }
        return -1;
    }

    /**
     * Deja el partido como no jugado (por ejemplo para corregir un error).
     */
    public void reiniciarResultado() {
        estado = EstadoPartido.PENDIENTE;
        golesLocal = 0;
        golesVisitante = 0;
        penalesLocal = SIN_PENALES;
        penalesVisitante = SIN_PENALES;
        amarillasLocal = 0;
        rojasLocal = 0;
        amarillasVisitante = 0;
        rojasVisitante = 0;
        getTarjetas().clear();
        getCambios().clear();
        getGoles().clear();
        // El XI inicial, la formacion y las horas de votacion NO se borran: se deciden antes del pitazo.
        walkover = false;
        finalizadoEn = 0;
        votacionCerradaEn = 0;
        getVotos().clear();
    }

    /**
     * Copia el resultado y los datos de programacion de otro partido
     * (se usa al editar: el dialogo trabaja sobre una copia).
     */
    public void copiarDatosDe(Partido otro) {
        estado = otro.estado;
        golesLocal = otro.golesLocal;
        golesVisitante = otro.golesVisitante;
        penalesLocal = otro.penalesLocal;
        penalesVisitante = otro.penalesVisitante;
        amarillasLocal = otro.amarillasLocal;
        rojasLocal = otro.rojasLocal;
        amarillasVisitante = otro.amarillasVisitante;
        rojasVisitante = otro.rojasVisitante;
        tarjetas = new ArrayList<>();
        for (Tarjeta t : otro.getTarjetas()) {
            tarjetas.add(t.copia());
        }
        cambios = new ArrayList<>();
        for (Cambio c : otro.getCambios()) {
            cambios.add(c.copia());
        }
        goles = new ArrayList<>();
        for (Gol g : otro.getGoles()) {
            goles.add(g.copia());
        }
        titularesLocal = new ArrayList<>(otro.getTitularesLocal());
        titularesVisitante = new ArrayList<>(otro.getTitularesVisitante());
        formacionLocal = otro.formacionLocal;
        formacionVisitante = otro.formacionVisitante;
        fecha = otro.fecha;
        hora = otro.hora;
        sede = otro.sede;
        observaciones = otro.observaciones;
        walkover = otro.isWalkover();
        finalizadoEn = otro.finalizadoEn;
        horasVotacion = otro.horasVotacion;
        votacionCerradaEn = otro.votacionCerradaEn;
        votos = new ArrayList<>();
        for (VotoJugador v : otro.getVotos()) {
            votos.add(v.copia());
        }
    }

    /** true si se gano por W.O.: no se jugo, asi que no tiene notas ni votacion. */
    public boolean isWalkover() {
        if (walkover == null) { // archivos guardados antes de existir este dato
            walkover = isJugado() && observaciones != null
                    && (observaciones.startsWith("W.O. a favor de ") || observaciones.startsWith("W.O. por retiro de "));
        }
        return walkover;
    }

    public void setWalkover(boolean walkover) {
        this.walkover = walkover;
    }

    /** El resultado sigue siendo el de un W.O.: 3-0 y sin nada registrado de un partido jugado. */
    public boolean tieneResultadoDeWalkover() {
        return isJugado() && Math.max(golesLocal, golesVisitante) == 3 && Math.min(golesLocal, golesVisitante) == 0
                && getGoles().isEmpty() && getTarjetas().isEmpty() && getCambios().isEmpty();
    }

    public String getMarcador() {
        if (!isJugado()) {
            return "vs";
        }
        String texto = golesLocal + " - " + golesVisitante;
        if (tienePenales()) {
            texto += " (" + penalesLocal + "-" + penalesVisitante + " pen)";
        }
        return texto;
    }

    // ---------------- getters y setters ----------------
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getPaso() {
        return paso;
    }

    public void setPaso(int paso) {
        this.paso = paso;
    }

    public int getIndiceFase() {
        return indiceFase;
    }

    public void setIndiceFase(int indiceFase) {
        this.indiceFase = indiceFase;
    }

    public String getGrupo() {
        return grupo;
    }

    public void setGrupo(String grupo) {
        this.grupo = grupo;
    }

    public int getRonda() {
        return ronda;
    }

    public void setRonda(int ronda) {
        this.ronda = ronda;
    }

    public String getNombreRonda() {
        return nombreRonda;
    }

    public void setNombreRonda(String nombreRonda) {
        this.nombreRonda = nombreRonda;
    }

    public String getSerie() {
        return serie;
    }

    public void setSerie(String serie) {
        this.serie = serie;
    }

    public int getPierna() {
        return pierna;
    }

    public void setPierna(int pierna) {
        this.pierna = pierna;
    }

    public String getLlave() {
        return llave;
    }

    public void setLlave(String llave) {
        this.llave = llave;
    }

    public boolean isEliminatorio() {
        return eliminatorio;
    }

    public void setEliminatorio(boolean eliminatorio) {
        this.eliminatorio = eliminatorio;
    }

    public int getIdLocal() {
        return idLocal;
    }

    public void setIdLocal(int idLocal) {
        this.idLocal = idLocal;
    }

    public int getIdVisitante() {
        return idVisitante;
    }

    public void setIdVisitante(int idVisitante) {
        this.idVisitante = idVisitante;
    }

    public EstadoPartido getEstado() {
        return estado;
    }

    public void setEstado(EstadoPartido estado) {
        this.estado = estado;
    }

    public int getGolesLocal() {
        return golesLocal;
    }

    public void setGolesLocal(int golesLocal) {
        this.golesLocal = golesLocal;
    }

    public int getGolesVisitante() {
        return golesVisitante;
    }

    public void setGolesVisitante(int golesVisitante) {
        this.golesVisitante = golesVisitante;
    }

    public int getPenalesLocal() {
        return penalesLocal;
    }

    public void setPenalesLocal(int penalesLocal) {
        this.penalesLocal = penalesLocal;
    }

    public int getPenalesVisitante() {
        return penalesVisitante;
    }

    public void setPenalesVisitante(int penalesVisitante) {
        this.penalesVisitante = penalesVisitante;
    }

    public int getAmarillasLocal() {
        return amarillasLocal;
    }

    public void setAmarillasLocal(int amarillasLocal) {
        this.amarillasLocal = amarillasLocal;
    }

    public int getRojasLocal() {
        return rojasLocal;
    }

    public void setRojasLocal(int rojasLocal) {
        this.rojasLocal = rojasLocal;
    }

    public int getAmarillasVisitante() {
        return amarillasVisitante;
    }

    public void setAmarillasVisitante(int amarillasVisitante) {
        this.amarillasVisitante = amarillasVisitante;
    }

    public int getRojasVisitante() {
        return rojasVisitante;
    }

    public void setRojasVisitante(int rojasVisitante) {
        this.rojasVisitante = rojasVisitante;
    }

    public ArrayList<Tarjeta> getTarjetas() {
        if (tarjetas == null) { // archivos guardados antes de existir las tarjetas por jugador
            tarjetas = new ArrayList<>();
        }
        return tarjetas;
    }

    public ArrayList<Cambio> getCambios() {
        if (cambios == null) { // archivos guardados antes de existir los cambios
            cambios = new ArrayList<>();
        }
        return cambios;
    }

    public void setCambios(ArrayList<Cambio> cambios) {
        this.cambios = cambios;
    }

   
    public ArrayList<Gol> getGoles() {
        if (goles == null) { 
            goles = new ArrayList<>();
        }
        return goles;
    }

    public void setGoles(ArrayList<Gol> goles) {
        this.goles = goles;
    }

    public ArrayList<Integer> getTitularesLocal() {
        if (titularesLocal == null) { // archivos guardados antes de existir el XI inicial
            titularesLocal = new ArrayList<>();
        }
        return titularesLocal;
    }

    public ArrayList<Integer> getTitularesVisitante() {
        if (titularesVisitante == null) {
            titularesVisitante = new ArrayList<>();
        }
        return titularesVisitante;
    }

    public ArrayList<Integer> getTitularesDe(int idEquipo) {
        return idEquipo == idLocal ? getTitularesLocal() : getTitularesVisitante();
    }

    public void setTitulares(ArrayList<Integer> local, ArrayList<Integer> visitante) {
        this.titularesLocal = local;
        this.titularesVisitante = visitante;
    }

    public boolean isEnJuego() {
        return estado == EstadoPartido.EN_JUEGO;
    }

    /**
     * Guarda las tarjetas por jugador y actualiza los totales por equipo
     * (los usa el criterio de juego limpio de la tabla). Se pasa el id del
     * equipo local porque este objeto puede ser solo una copia de los datos.
     */
    public void setTarjetas(ArrayList<Tarjeta> tarjetas, int idEquipoLocal) {
        this.tarjetas = tarjetas;
        amarillasLocal = 0;
        rojasLocal = 0;
        amarillasVisitante = 0;
        rojasVisitante = 0;
        for (Tarjeta t : tarjetas) {
            boolean local = t.getIdEquipo() == idEquipoLocal;
            if (t.isRoja()) {
                if (local) {
                    rojasLocal++;
                } else {
                    rojasVisitante++;
                }
            } else if (local) {
                amarillasLocal++;
            } else {
                amarillasVisitante++;
            }
        }
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    public String getHora() {
        return hora;
    }

    public void setHora(String hora) {
        this.hora = hora;
    }

    public String getSede() {
        return sede;
    }

    public void setSede(String sede) {
        this.sede = sede;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    
    public long getFinalizadoEn() {
        return finalizadoEn;
    }

    public void setFinalizadoEn(long finalizadoEn) {
        this.finalizadoEn = finalizadoEn;
    }

    /** Horas que dura la votacion del publico desde el final del partido. */
    public int getHorasVotacion() {
        return horasVotacion > 0 ? horasVotacion : HORAS_VOTACION_POR_DEFECTO;
    }

    public void setHorasVotacion(int horasVotacion) {
        this.horasVotacion = horasVotacion;
    }

    /** Hora (ms) en que el administrador cerro la votacion antes de tiempo; 0 = no la cerro. */
    public long getVotacionCerradaEn() {
        return votacionCerradaEn;
    }

    public void setVotacionCerradaEn(long votacionCerradaEn) {
        this.votacionCerradaEn = votacionCerradaEn;
    }

    
    public ArrayList<VotoJugador> getVotos() {
        if (votos == null) { 
            votos = new ArrayList<>();
        }
        return votos;
    }

    @Override
    public String toString() {
        return "Partido{" + "id=" + id + ", local=" + idLocal + ", visitante=" + idVisitante
                + ", marcador=" + getMarcador() + ", estado=" + estado + '}';
    }

    /** Formacion del local ("" si el entrenador no la ha elegido). */
    public String getFormacionLocal() {
        return formacionLocal == null ? "" : formacionLocal;
    }

    public String getFormacionVisitante() {
        return formacionVisitante == null ? "" : formacionVisitante;
    }

    /** Formacion del equipo indicado ("" si no la ha elegido). */
    public String getFormacionDe(int idEquipo) {
        return idEquipo == idLocal ? getFormacionLocal() : getFormacionVisitante();
    }

    /** Guarda las formaciones; "" o null = sin definir. Se validan con {@link Formacion#desdeTexto}. */
    public void setFormaciones(String local, String visitante) {
        this.formacionLocal = normalizarFormacion(local);
        this.formacionVisitante = normalizarFormacion(visitante);
    }

    private static String normalizarFormacion(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        Formacion f = Formacion.desdeTexto(texto);
        if (f == null) {
            throw new IllegalArgumentException("Formación desconocida: " + texto);
        }
        return f.getTexto();
    }
}