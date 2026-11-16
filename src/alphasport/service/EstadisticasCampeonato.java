package alphasport.service;

import alphasport.model.Campeonato;
import alphasport.model.Gol;
import alphasport.model.Partido;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Estadisticas GLOBALES del campeonato (todas las fases): goleadores, asistidores,
 * valla menos vencida y juego limpio acumulado. Las estadisticas de UN partido
 * no viven aqui: se muestran en el detalle del partido.
 *
 * Igual que la tabla de posiciones, todo se calcula a partir de los partidos
 * jugados cada vez (no se acumula nada), asi corregir un partido deja todo bien.
 * La valla y el juego limpio reutilizan la tabla general de {@link ResumenCampeonato}.
 */
public class EstadisticasCampeonato {

    /** Fila de goleadores / asistidores: un jugador con sus goles y asistencias. */
    public static final class FilaJugador {

        private final int idJugador;
        private final String nombre;
        private final int dorsal;
        private final int idEquipo;
        private final String equipo;
        int goles;
        int asistencias;

        FilaJugador(int idJugador, String nombre, int dorsal, int idEquipo, String equipo) {
            this.idJugador = idJugador;
            this.nombre = nombre;
            this.dorsal = dorsal;
            this.idEquipo = idEquipo;
            this.equipo = equipo;
        }

        public int getIdJugador() {
            return idJugador;
        }

        public String getNombre() {
            return nombre;
        }

        public int getDorsal() {
            return dorsal;
        }

        public int getIdEquipo() {
            return idEquipo;
        }

        public String getEquipo() {
            return equipo;
        }

        public int getGoles() {
            return goles;
        }

        public int getAsistencias() {
            return asistencias;
        }
    }

    /** Un gol con el partido donde se anoto (para auditar y corregir). */
    public record RegistroGol(Partido partido, Gol gol) {
    }

    /** Fila de valla menos vencida: la fila de la tabla general mas las vallas invictas. */
    public record FilaValla(FilaTabla fila, int vallasInvictas) {

        public double promedio() {
            return fila.getJugados() == 0 ? 0 : fila.getGolesContra() / (double) fila.getJugados();
        }
    }

    private final Campeonato campeonato;
    private final List<FilaJugador> goleadores;
    private final List<FilaJugador> asistidores;
    private final List<RegistroGol> registroGoles = new ArrayList<>();
    private final List<FilaValla> valla;
    private final List<FilaTabla> fairPlay;
    private int golesSinAutor;

    public EstadisticasCampeonato(Campeonato campeonato) {
        this.campeonato = campeonato;
        Map<Integer, FilaJugador> porJugador = new LinkedHashMap<>();
        Map<Integer, Integer> invictas = new LinkedHashMap<>();
        for (Partido p : campeonato.getPartidos()) {
            if (!p.isJugado()) {
                continue; // como en la tabla: un partido «En juego» todavia no cuenta
            }
            if (p.getGolesVisitante() == 0) {
                invictas.merge(p.getIdLocal(), 1, Integer::sum);
            }
            if (p.getGolesLocal() == 0) {
                invictas.merge(p.getIdVisitante(), 1, Integer::sum);
            }
            golesSinAutor += p.getGolesLocal() + p.getGolesVisitante() - p.getGoles().size();
            for (Gol g : p.getGoles()) {
                registroGoles.add(new RegistroGol(p, g));
                if (!g.isEnContra()) { // un autogol no cuenta para el goleador
                    fila(porJugador, g.getIdAutor(), g.getNombreAutor(), g.getDorsalAutor(), g.getIdEquipo()).goles++;
                }
                if (g.tieneAsistencia()) {
                    fila(porJugador, g.getIdAsistente(), g.getNombreAsistente(), g.getDorsalAsistente(), g.getIdEquipo()).asistencias++;
                }
            }
        }
        this.goleadores = porJugador.values().stream().filter(f -> f.goles > 0)
                .sorted(Comparator.comparingInt(FilaJugador::getGoles).reversed()
                        .thenComparing(Comparator.comparingInt(FilaJugador::getAsistencias).reversed())
                        .thenComparing(FilaJugador::getNombre))
                .toList();
        this.asistidores = porJugador.values().stream().filter(f -> f.asistencias > 0)
                .sorted(Comparator.comparingInt(FilaJugador::getAsistencias).reversed()
                        .thenComparing(Comparator.comparingInt(FilaJugador::getGoles).reversed())
                        .thenComparing(FilaJugador::getNombre))
                .toList();

        List<FilaTabla> general = new ResumenCampeonato(campeonato).getTablaGeneral().stream()
                .filter(f -> f.getJugados() > 0).toList();
        // Valla: menos goles recibidos POR PARTIDO (igual que el premio del historial), luego menos goles en total.
        this.valla = general.stream().map(f -> new FilaValla(f, invictas.getOrDefault(f.getIdEquipo(), 0)))
                .sorted(Comparator.comparingDouble(FilaValla::promedio)
                        .thenComparingInt(v -> v.fila().getGolesContra())
                        .thenComparing(Comparator.comparingInt(FilaValla::vallasInvictas).reversed()))
                .toList();
        // Juego limpio: menos puntos (amarilla = 1, roja = 3) por partido, luego menos en total.
        this.fairPlay = general.stream()
                .sorted(Comparator.comparingDouble((FilaTabla f) -> f.getPuntosFairPlay() / (double) f.getJugados())
                        .thenComparingInt(FilaTabla::getPuntosFairPlay))
                .toList();
    }

    private FilaJugador fila(Map<Integer, FilaJugador> mapa, int id, String nombre, int dorsal, int idEquipoDelGol) {
        // El equipo del jugador: el del gol; en un autogol el autor es del rival (pero el autogol no suma goles).
        return mapa.computeIfAbsent(id, k -> {
            int idEquipo = equipoDe(id, idEquipoDelGol);
            return new FilaJugador(id, nombre, dorsal, idEquipo, campeonato.nombreDe(idEquipo));
        });
    }

    private int equipoDe(int idJugador, int porDefecto) {
        return campeonato.getEquipos().stream()
                .filter(e -> e.getJugadores().stream().anyMatch(j -> j.getId() == idJugador))
                .map(e -> e.getId()).findFirst().orElse(porDefecto);
    }

    public List<FilaJugador> getGoleadores() {
        return goleadores;
    }

    public List<FilaJugador> getAsistidores() {
        return asistidores;
    }

    /** Todos los goles registrados, en el orden del fixture. */
    public List<RegistroGol> getRegistroGoles() {
        return registroGoles;
    }

    /** Goles del marcador que todavia no tienen autor registrado (para avisar al administrador). */
    public int getGolesSinAutor() {
        return golesSinAutor;
    }

    public List<FilaValla> getVallaMenosVencida() {
        return valla;
    }

    public List<FilaTabla> getFairPlay() {
        return fairPlay;
    }
}
