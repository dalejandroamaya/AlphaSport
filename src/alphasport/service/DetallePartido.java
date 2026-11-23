package alphasport.service;

import alphasport.model.Campeonato;
import alphasport.model.DirectorTecnico;
import alphasport.model.Equipo;
import alphasport.model.Formacion;
import alphasport.model.Jugador;
import alphasport.model.Partido;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Detalle de un partido: cara a cara, racha reciente de cada
 * equipo y alineaciones sobre la cancha segun la formacion elegida.
 *
 * Los equipos se reconocen por su NOMBRE, asi se cruzan los datos del campeonato
 * actual con los del historial (alli los ids de los equipos pueden ser otros).
 * Solo cuentan los partidos jugados ANTES del partido consultado.
 */
public class DetallePartido {

    /** Partidos que se muestran en el cara a cara y en la racha. */
    public static final int MAXIMO_CARA_A_CARA = 10;
    public static final int MAXIMO_RACHA = 5;

    /** Un partido ya jugado, sin depender de ids (sirve para actual e historial). */
    public record Registro(String competicion, String ronda, String fecha, String local, String visitante,
            int golesLocal, int golesVisitante, String ganador) {

        public boolean juega(String equipo) {
            return mismo(local, equipo) || mismo(visitante, equipo);
        }

        /** V (victoria), E (empate) o D (derrota) desde el punto de vista del equipo. */
        public char resultadoPara(String equipo) {
            if (ganador == null) {
                return 'E';
            }
            return mismo(ganador, equipo) ? 'V' : 'D';
        }

        public String marcador() {
            return golesLocal + " - " + golesVisitante;
        }
    }

    /** Resumen del cara a cara: victorias de cada uno y empates. */
    public record ResumenCaraACara(int victoriasLocal, int empates, int victoriasVisitante) {
    }

    /** Una casilla de la cancha: linea 0 = portero, luego defensa, medio, ataque. jugador null = por confirmar. */
    public record Puesto(int linea, int orden, Jugador jugador) {
    }

   
    public record Alineacion(String formacion, boolean formacionElegida, boolean confirmada, List<List<Puesto>> lineas,
            List<Jugador> suplentes, DirectorTecnico directorTecnico) {
    }

    private final Partido partido;
    private final String local;
    private final String visitante;
    private final List<Registro> caraACara;
    private final List<Registro> rachaLocal;
    private final List<Registro> rachaVisitante;
    private final Alineacion alineacionLocal;
    private final Alineacion alineacionVisitante;

    public DetallePartido(Campeonato actual, List<Campeonato> historial, Partido partido) {
        this.partido = partido;
        this.local = actual.nombreDe(partido.getIdLocal());
        this.visitante = actual.nombreDe(partido.getIdVisitante());
        List<Registro> previos = partidosPrevios(actual, historial, partido);
        this.caraACara = previos.stream().filter(r -> r.juega(local) && r.juega(visitante))
                .limit(MAXIMO_CARA_A_CARA).toList();
        this.rachaLocal = previos.stream().filter(r -> r.juega(local)).limit(MAXIMO_RACHA).toList();
        this.rachaVisitante = previos.stream().filter(r -> r.juega(visitante)).limit(MAXIMO_RACHA).toList();
        this.alineacionLocal = alineacion(actual.buscarEquipo(partido.getIdLocal()), partido.getFormacionLocal(),
                partido.getTitularesLocal());
        this.alineacionVisitante = alineacion(actual.buscarEquipo(partido.getIdVisitante()), partido.getFormacionVisitante(),
                partido.getTitularesVisitante());
    }

    private static List<Registro> partidosPrevios(Campeonato actual, List<Campeonato> historial, Partido partido) {
        List<Registro> lista = new ArrayList<>();
        List<Partido> delActual = actual.getPartidos();
        int posicion = delActual.indexOf(partido);
        for (int i = posicion - 1; i >= 0; i--) {
            agregarSiJugado(lista, actual, delActual.get(i));
        }
        List<Campeonato> anteriores = new ArrayList<>(historial);
        anteriores.removeIf(h -> h.getCodigo() != null && h.getCodigo().equals(actual.getCodigo()));
        anteriores.sort(Comparator.comparing((Campeonato h) -> h.getFechaArchivo() == null ? "" : h.getFechaArchivo()).reversed());
        for (Campeonato h : anteriores) {
            List<Partido> partidos = h.getPartidos();
            for (int i = partidos.size() - 1; i >= 0; i--) {
                agregarSiJugado(lista, h, partidos.get(i));
            }
        }
        return lista;
    }

    private static void agregarSiJugado(List<Registro> lista, Campeonato c, Partido p) {
        if (!p.isJugado()) {
            return;
        }
        int ganador = p.getGanador();
        String ronda = !p.getNombreRonda().isBlank() ? p.getNombreRonda()
                : p.getIndiceFase() < c.getFases().size() ? c.getFases().get(p.getIndiceFase()).getNombre() : "";
        lista.add(new Registro(c.getNombreCampeonato(), ronda, p.getFecha(), c.nombreDe(p.getIdLocal()),
                c.nombreDe(p.getIdVisitante()), p.getGolesLocal(), p.getGolesVisitante(),
                ganador < 0 ? null : c.nombreDe(ganador)));
    }

    static Alineacion alineacion(Equipo equipo, String textoFormacion, List<Integer> titulares) {
        Formacion elegida = Formacion.desdeTexto(textoFormacion);
        Formacion formacion = elegida != null ? elegida : Formacion.POR_DEFECTO;
        List<Jugador> xi = new ArrayList<>();
        if (equipo != null) {
            for (int id : titulares) {
                equipo.getJugadores().stream().filter(j -> j.getId() == id).findFirst().ifPresent(xi::add);
            }
        }
        boolean confirmada = !xi.isEmpty();
        // Portero: el primero marcado como tal (o el primero de la lista si nadie lo es).
        Jugador portero = xi.stream().filter(j -> grupo(j) == 0).findFirst().orElse(xi.isEmpty() ? null : xi.get(0));
        List<Jugador> campo = new ArrayList<>(xi);
        campo.remove(portero);
        campo.sort(Comparator.comparingInt(DetallePartido::grupo).thenComparingInt(DetallePartido::lado));

        List<List<Puesto>> lineas = new ArrayList<>();
        lineas.add(List.of(new Puesto(0, 0, portero)));
        int siguiente = 0;
        int[] cantidades = formacion.getLineas();
        for (int l = 0; l < cantidades.length; l++) {
            List<Jugador> deLinea = new ArrayList<>();
            for (int k = 0; k < cantidades[l]; k++) {
                deLinea.add(siguiente < campo.size() ? campo.get(siguiente++) : null);
            }
            // dentro de la linea: izquierda -> centro -> derecha (las casillas vacias van al final)
            deLinea.sort(Comparator.comparingInt((Jugador j) -> j == null ? 9 : lado(j)));
            List<Puesto> puestos = new ArrayList<>();
            for (int k = 0; k < deLinea.size(); k++) {
                puestos.add(new Puesto(l + 1, k, deLinea.get(k)));
            }
            lineas.add(puestos);
        }
        List<Jugador> suplentes = !confirmada ? List.of() : equipo.getJugadores().stream().filter(j -> !xi.contains(j))
                .sorted(Comparator.comparingInt(Jugador::getDorsal)).toList();
        return new Alineacion(formacion.getTexto(), elegida != null, confirmada, lineas, suplentes,
                equipo == null ? null : equipo.getDirectorTecnico());
    }

    /** 0 portero, 1 defensa, 2 mediocampo, 3 ataque (segun Jugador.GRUPOS_POSICIONES). */
    static int grupo(Jugador j) {
        String posicion = j.getPosicion() == null ? "" : j.getPosicion();
        for (int g = 0; g < Jugador.GRUPOS_POSICIONES.length; g++) {
            String[] grupo = Jugador.GRUPOS_POSICIONES[g];
            for (int k = 1; k < grupo.length; k++) {
                if (grupo[k].equalsIgnoreCase(posicion)) {
                    return g;
                }
            }
        }
        return 2; // posicion desconocida: al medio
    }

    /** 0 izquierda, 1 centro, 2 derecha. */
    static int lado(Jugador j) {
        String posicion = j.getPosicion() == null ? "" : j.getPosicion().toLowerCase(Locale.ROOT);
        return posicion.contains("izquierd") ? 0 : posicion.contains("derech") ? 2 : 1;
    }

    private static boolean mismo(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }


    public Partido getPartido() {
        return partido;
    }

    public String getLocal() {
        return local;
    }

    public String getVisitante() {
        return visitante;
    }

    public List<Registro> getCaraACara() {
        return caraACara;
    }

    public ResumenCaraACara getResumenCaraACara() {
        int vl = 0, e = 0, vv = 0;
        for (Registro r : caraACara) {
            char x = r.resultadoPara(local);
            if (x == 'V') {
                vl++;
            } else if (x == 'D') {
                vv++;
            } else {
                e++;
            }
        }
        return new ResumenCaraACara(vl, e, vv);
    }

    public List<Registro> getRachaLocal() {
        return rachaLocal;
    }

    public List<Registro> getRachaVisitante() {
        return rachaVisitante;
    }

    public Alineacion getAlineacionLocal() {
        return alineacionLocal;
    }

    public Alineacion getAlineacionVisitante() {
        return alineacionVisitante;
    }
}
