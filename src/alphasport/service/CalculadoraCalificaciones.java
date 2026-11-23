package alphasport.service;

import alphasport.model.Campeonato;
import alphasport.model.Cambio;
import alphasport.model.Equipo;
import alphasport.model.Gol;
import alphasport.model.Jugador;
import alphasport.model.Partido;
import alphasport.model.Tarjeta;
import alphasport.model.VotoJugador;
import alphasport.service.CalificacionesPartido.Calificacion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * [REQ-07] Reglas de la calificacion de jugadores (escala 1.0 a 10.0), enfoque hibrido:
 * <ol>
 * <li><b>Nota del sistema</b>: 6.0 de base para todo el que jugo (titular o ingresado de cambio);
 * gol +1.0, asistencia +0.5, amarilla -0.5, roja -2.0. El portero ademas suma +1.5 con la valla
 * invicta y resta 0.3 por cada gol recibido. Siempre queda entre 1.0 y 10.0.</li>
 * <li><b>Voto del publico</b>: mientras la votacion esta abierta cada visitante califica a cada jugador del
 * 1 al 10, una sola vez por dispositivo (ver {@link ReglasVotacion}).</li>
 * <li><b>Nota final</b>: la del sistema si no hay votos; si los hay, (sistema + promedio del publico) / 2,
 * redondeada a 1 decimal. El MVP es el jugador con la nota final mas alta.</li>
 * </ol>
 * Solo tiene metodos estaticos; el resultado de {@link #calcular} es un {@link CalificacionesPartido}. La version web tiene su gemela en
 * web/js/service/calificaciones.js con las mismas reglas.
 */
public final class CalculadoraCalificaciones {

    public static final double NOTA_BASE = 6.0;
    public static final double PUNTOS_GOL = 1.0;
    public static final double PUNTOS_ASISTENCIA = 0.5;
    public static final double PUNTOS_AMARILLA = -0.5;
    public static final double PUNTOS_ROJA = -2.0;
    public static final double PUNTOS_VALLA_INVICTA = 1.5;
    public static final double PUNTOS_GOL_RECIBIDO = -0.3;
    public static final double NOTA_MINIMA = 1.0;
    public static final double NOTA_MAXIMA = 10.0;

    private CalculadoraCalificaciones() {
    }

    /* ---------------------------------------------------------------- calculo */

    /**
     * Califica a los jugadores del partido.
     *
     * @param ahora       hora actual en ms (se pasa para poder probar el cierre de la votacion)
     * @param dispositivo identificador del navegador o equipo que consulta (para mostrar su voto); puede ser null
     */
    public static CalificacionesPartido calcular(Campeonato campeonato, Partido partido, long ahora, String dispositivo) {
        String motivo = "";
        List<Calificacion> local = List.of();
        List<Calificacion> visitante = List.of();
        if (!partido.isJugado()) {
            motivo = "Las calificaciones aparecen cuando el partido termina.";
        } else if (partido.isWalkover()) {
            motivo = "Partido definido por W.O.: no se jugó, así que no hay calificaciones.";
        } else {
            local = calificar(campeonato, partido, partido.getIdLocal(), dispositivo);
            visitante = calificar(campeonato, partido, partido.getIdVisitante(), dispositivo);
            if (local.isEmpty() && visitante.isEmpty()) {
                motivo = "No se registró quién jugó (XI inicial, cambios, goles ni tarjetas): no hay a quién calificar.";
            }
        }
        int idMvp = mvp(local, visitante);
        return new CalificacionesPartido(ReglasVotacion.estado(partido, ahora), ReglasVotacion.cierraEn(partido), motivo,
                marcarMvp(local, idMvp), marcarMvp(visitante, idMvp), idMvp);
    }

    /* ---------------------------------------------------------------- nota del sistema */

    /**
     * Ids de quienes jugaron por ese equipo, en orden: titulares, ingresados de cambio y, por si el
     * XI no se registro, cualquiera con un gol, una asistencia o una tarjeta en el partido.
     */
    static Set<Integer> participantes(Campeonato c, Partido p, int idEquipo) {
        Set<Integer> ids = new LinkedHashSet<>(p.getTitularesDe(idEquipo));
        for (Cambio x : p.getCambios()) {
            if (x.getIdEquipo() == idEquipo) {
                ids.add(x.getIdEntra());
            }
        }
        for (Gol g : p.getGoles()) {
            int equipoAutor = g.isEnContra() ? p.getRival(g.getIdEquipo()) : g.getIdEquipo();
            if (equipoAutor == idEquipo) {
                ids.add(g.getIdAutor());
            }
            if (g.getIdEquipo() == idEquipo && g.getIdAsistente() != Gol.SIN_ASISTENCIA) {
                ids.add(g.getIdAsistente());
            }
        }
        for (Tarjeta t : p.getTarjetas()) {
            if (t.getIdEquipo() == idEquipo) {
                ids.add(t.getIdJugador());
            }
        }
        // Solo cuentan los que siguen en la plantilla (un jugador borrado ya no se puede mostrar ni votar).
        Equipo equipo = c.buscarEquipo(idEquipo);
        ids.removeIf(id -> equipo == null || equipo.getJugadores().stream().noneMatch(j -> j.getId() == id));
        return ids;
    }

    private static List<Calificacion> calificar(Campeonato c, Partido p, int idEquipo, String dispositivo) {
        Equipo equipo = c.buscarEquipo(idEquipo);
        Set<Integer> titulares = new LinkedHashSet<>(p.getTitularesDe(idEquipo));
        int recibidos = p.getGolesDe(p.getRival(idEquipo));
        List<Calificacion> lista = new ArrayList<>();
        for (int id : participantes(c, p, idEquipo)) {
            Jugador j = equipo.getJugadores().stream().filter(x -> x.getId() == id).findFirst().orElseThrow();
            boolean portero = DetallePartido.grupo(j) == 0;
            int goles = (int) p.getGoles().stream().filter(g -> !g.isEnContra() && g.getIdAutor() == id).count();
            int asistencias = (int) p.getGoles().stream().filter(g -> g.getIdAsistente() == id).count();
            int amarillas = (int) p.getTarjetas().stream().filter(t -> t.getIdJugador() == id && !t.isRoja()).count();
            int rojas = (int) p.getTarjetas().stream().filter(t -> t.getIdJugador() == id && t.isRoja()).count();
            List<String> desglose = new ArrayList<>();
            double nota = notaSistema(portero, goles, asistencias, amarillas, rojas, recibidos, desglose);
            List<VotoJugador> votos = p.getVotos().stream().filter(v -> v.getIdJugador() == id).toList();
            double promedio = votos.stream().mapToInt(VotoJugador::getValor).average().orElse(0);
            double notaFinal = votos.isEmpty() ? nota : redondear((nota + promedio) / 2);
            int miVoto = dispositivo == null ? 0 : votos.stream().filter(v -> dispositivo.equals(v.getDispositivo()))
                    .mapToInt(VotoJugador::getValor).findFirst().orElse(0);
            lista.add(new Calificacion(id, idEquipo, j.getNombre(), j.getDorsal(), j.getPosicion(), j.getFoto(), portero,
                    titulares.contains(id), goles, asistencias, amarillas, rojas, portero ? recibidos : 0, nota,
                    List.copyOf(desglose), votos.size(), redondear(promedio), notaFinal, false, miVoto));
        }
        // Titulares primero y luego los que entraron; dentro de cada grupo, por dorsal.
        lista.sort(Comparator.comparing((Calificacion x) -> !x.titular()).thenComparingInt(Calificacion::dorsal));
        return lista;
    }

    /**
     * Nota del sistema con su explicacion linea por linea en {@code desglose}
     * (por ejemplo «Gol ×2: +2.0»). Publica para poder probar las reglas por separado.
     */
    public static double notaSistema(boolean portero, int goles, int asistencias, int amarillas, int rojas,
            int golesRecibidos, List<String> desglose) {
        double nota = NOTA_BASE;
        desglose.add("Base por jugar: " + texto(NOTA_BASE));
        nota += sumar(desglose, "Gol", goles, PUNTOS_GOL);
        nota += sumar(desglose, "Asistencia", asistencias, PUNTOS_ASISTENCIA);
        nota += sumar(desglose, "Tarjeta amarilla", amarillas, PUNTOS_AMARILLA);
        nota += sumar(desglose, "Tarjeta roja", rojas, PUNTOS_ROJA);
        if (portero) {
            if (golesRecibidos == 0) {
                nota += PUNTOS_VALLA_INVICTA;
                desglose.add("Valla invicta: " + conSigno(PUNTOS_VALLA_INVICTA));
            } else {
                nota += sumar(desglose, "Gol recibido", golesRecibidos, PUNTOS_GOL_RECIBIDO);
            }
        }
        double acotada = Math.max(NOTA_MINIMA, Math.min(NOTA_MAXIMA, nota));
        if (acotada != nota) {
            desglose.add(acotada == NOTA_MAXIMA ? "Ajustada al máximo: " + texto(NOTA_MAXIMA) : "Ajustada al mínimo: " + texto(NOTA_MINIMA));
        }
        return redondear(acotada);
    }

    private static double sumar(List<String> desglose, String concepto, int veces, double puntos) {
        if (veces <= 0) {
            return 0;
        }
        double total = veces * puntos;
        desglose.add(concepto + (veces > 1 ? " ×" + veces : "") + ": " + conSigno(total));
        return total;
    }

    /** Redondeo a 1 decimal «mitad hacia arriba» (8.45 -> 8.5), sin errores de coma flotante. */
    public static double redondear(double valor) {
        return BigDecimal.valueOf(valor).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static String texto(double valor) {
        return String.format(Locale.ROOT, "%.1f", redondear(valor));
    }

    private static String conSigno(double valor) {
        return (valor >= 0 ? "+" : "−") + texto(Math.abs(valor));
    }

    /* ---------------------------------------------------------------- MVP */

    /**
     * Id del MVP: la nota final mas alta. Empates: mas goles, mas asistencias, mejor nota del sistema,
     * titular antes que suplente y, al final, el local y el dorsal mas bajo. 0 si nadie jugo.
     */
    private static int mvp(List<Calificacion> local, List<Calificacion> visitante) {
        List<Calificacion> todos = new ArrayList<>(local);
        todos.addAll(visitante);
        return todos.stream().min(Comparator.comparingDouble((Calificacion x) -> -x.notaFinal())
                .thenComparingInt(x -> -x.goles())
                .thenComparingInt(x -> -x.asistencias())
                .thenComparingDouble(x -> -x.notaSistema())
                .thenComparing(x -> !x.titular())
                .thenComparingInt(x -> local.contains(x) ? 0 : 1)
                .thenComparingInt(Calificacion::dorsal))
                .map(Calificacion::idJugador).orElse(0);
    }

    private static List<Calificacion> marcarMvp(List<Calificacion> lista, int idMvp) {
        return lista.stream().map(x -> x.idJugador() != idMvp ? x
                : new Calificacion(x.idJugador(), x.idEquipo(), x.nombre(), x.dorsal(), x.posicion(), x.foto(), x.portero(),
                        x.titular(), x.goles(), x.asistencias(), x.amarillas(), x.rojas(), x.golesRecibidos(),
                        x.notaSistema(), x.desglose(), x.votos(), x.promedioPublico(), x.notaFinal(), true, x.miVoto()))
                .toList();
    }
}