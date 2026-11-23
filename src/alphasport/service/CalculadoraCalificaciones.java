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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * [REQ-07] Calificacion de los jugadores de un partido, enfoque hibrido:
 * <ol>
 * <li><b>Nota del sistema</b>: la calcula una {@link ReglaPuntuacion} a partir del {@link DesempenoJugador}; por defecto
 * {@link PuntuacionNotaPartido} (base 6.0, goles, asistencias, tarjetas y valla invicta, entre 1.0 y 10.0).</li>
 * <li><b>Voto del publico</b>: mientras la votacion esta abierta cada visitante califica a cada jugador del
 * 1 al 10, una sola vez por dispositivo (ver {@link ReglasVotacion}).</li>
 * <li><b>Nota final</b>: la del sistema si no hay votos; si los hay, (sistema + promedio del publico) / 2,
 * redondeada a 1 decimal. El MVP es el jugador con la nota final mas alta.</li>
 * </ol>
 * Solo tiene metodos estaticos; el resultado de {@link #calcular} es un {@link CalificacionesPartido}. Para otro conjunto de
 * puntos (Fantasy) basta con otra ReglaPuntuacion. La version web tiene su gemela en web/js/service/calificaciones.js.
 */
public final class CalculadoraCalificaciones {

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
        return calcular(campeonato, partido, ahora, dispositivo, PuntuacionNotaPartido.REGLAS);
    }

    /** Igual, con otras reglas para la nota del sistema. */
    public static CalificacionesPartido calcular(Campeonato campeonato, Partido partido, long ahora, String dispositivo,
            ReglaPuntuacion reglas) {
        String motivo = "";
        List<Calificacion> local = List.of();
        List<Calificacion> visitante = List.of();
        if (!partido.isJugado()) {
            motivo = "Las calificaciones aparecen cuando el partido termina.";
        } else if (partido.isWalkover()) {
            motivo = "Partido definido por W.O.: no se jugó, así que no hay calificaciones.";
        } else {
            local = calificar(campeonato, partido, partido.getIdLocal(), dispositivo, reglas);
            visitante = calificar(campeonato, partido, partido.getIdVisitante(), dispositivo, reglas);
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

    private static List<Calificacion> calificar(Campeonato c, Partido p, int idEquipo, String dispositivo, ReglaPuntuacion reglas) {
        Equipo equipo = c.buscarEquipo(idEquipo);
        List<Calificacion> lista = new ArrayList<>();
        for (int id : participantes(c, p, idEquipo)) {
            Jugador j = equipo.getJugadores().stream().filter(x -> x.getId() == id).findFirst().orElseThrow();
            DesempenoJugador d = DesempenoJugador.de(p, idEquipo, id, DetallePartido.grupo(j) == 0);
            List<String> desglose = new ArrayList<>();
            double nota = reglas.puntuar(d, desglose);
            List<VotoJugador> votos = p.getVotos().stream().filter(v -> v.getIdJugador() == id).toList();
            double promedio = votos.stream().mapToInt(VotoJugador::getValor).average().orElse(0);
            double notaFinal = votos.isEmpty() ? nota : PuntuacionNotaPartido.redondear((nota + promedio) / 2);
            int miVoto = dispositivo == null ? 0 : votos.stream().filter(v -> dispositivo.equals(v.getDispositivo()))
                    .mapToInt(VotoJugador::getValor).findFirst().orElse(0);
            lista.add(new Calificacion(id, idEquipo, j.getNombre(), j.getDorsal(), j.getPosicion(), j.getFoto(), d.portero(),
                    d.titular(), d.goles(), d.asistencias(), d.amarillas(), d.rojas(), d.portero() ? d.golesRecibidos() : 0, nota,
                    List.copyOf(desglose), votos.size(), PuntuacionNotaPartido.redondear(promedio), notaFinal, false, miVoto));
        }
        // Titulares primero y luego los que entraron; dentro de cada grupo, por linea (Porteria a Ataque) y dorsal.
        lista.sort(Comparator.comparing((Calificacion x) -> !x.titular()).thenComparingInt(x -> Jugador.lineaDe(x.posicion()))
                .thenComparingInt(Calificacion::dorsal));
        return lista;
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