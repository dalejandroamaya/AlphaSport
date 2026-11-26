package alphasport.service;

import alphasport.model.Campeonato;
import alphasport.model.EstadoPartido;
import alphasport.model.Partido;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cronograma automático: el organizador da la fecha de inicio, los días de juego, las horas y (si quiere) las canchas,
 * y el programa reparte los partidos pendientes. Cada jornada empieza en un día de juego nuevo, ningún equipo juega
 * dos veces el mismo día y en cada día caben tantos partidos como horas por canchas. Sin canchas, cada partido va en
 * el estadio del local. También revisa las fechas que se escriben a mano en un partido.
 */
public final class Cronograma {

    private static final DateTimeFormatter DMA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Cronograma() {
    }

    /** Datos que da el organizador. {@code todos}: también reprograma los pendientes que ya tenían fecha. */
    public record Pedido(LocalDate inicio, Set<DayOfWeek> dias, List<String> horas, List<String> sedes, boolean todos) {
    }

    /**
     * Reparte los partidos pendientes y devuelve cuántos programó y entre qué fechas.
     *
     * @param hoy fecha de hoy (la fecha de inicio no puede ser anterior, ni a la creación del campeonato)
     */
    public static String programar(Campeonato c, Pedido p, LocalDate hoy) {
        LocalDate minimo = minimoPermitido(c, hoy);
        if (p.inicio() == null) {
            throw new ReglaNegocioException("Escoja la fecha de inicio.");
        }
        if (p.inicio().isBefore(minimo)) {
            throw new ReglaNegocioException("La fecha de inicio no puede ser anterior al " + minimo.format(DMA) + ".");
        }
        if (p.dias() == null || p.dias().isEmpty()) {
            throw new ReglaNegocioException("Marque al menos un día de juego.");
        }
        List<String> horas = new ArrayList<>();
        for (String h : p.horas() == null ? List.<String>of() : p.horas()) {
            String limpia = h == null ? "" : h.trim();
            if (!limpia.matches("([01]\\d|2[0-3]):[0-5]\\d")) {
                throw new ReglaNegocioException("La hora «" + limpia + "» no es válida: escríbala como 15:00.");
            }
            if (!horas.contains(limpia)) {
                horas.add(limpia);
            }
        }
        if (horas.isEmpty()) {
            throw new ReglaNegocioException("Escriba al menos una hora de juego, por ejemplo 15:00.");
        }
        horas.sort(Comparator.naturalOrder());
        List<String> sedes = new ArrayList<>();
        for (String s : p.sedes() == null ? List.<String>of() : p.sedes()) {
            if (s != null && !s.isBlank() && !sedes.contains(s.trim())) {
                sedes.add(s.trim());
            }
        }
        // Jornadas (paso + ronda), en orden: solo partidos pendientes (sin fecha, o todos si se pidió).
        Map<String, List<Partido>> jornadas = new LinkedHashMap<>();
        c.getPartidos().stream()
                .filter(x -> x.getEstado() == EstadoPartido.PENDIENTE && !x.isJugado())
                .filter(x -> p.todos() || fecha(x.getFecha()) == null) // sin fecha = «Por definir"
                .sorted(Comparator.comparingInt(Partido::getPaso).thenComparingInt(Partido::getRonda).thenComparingInt(Partido::getId))
                .forEach(x -> jornadas.computeIfAbsent(x.getPaso() + "-" + x.getRonda(), k -> new ArrayList<>()).add(x));
        if (jornadas.isEmpty()) {
            throw new ReglaNegocioException(p.todos() ? "No hay partidos pendientes para programar."
                    : "Todos los partidos pendientes ya tienen fecha. Marque «Reprogramar también los que ya tienen fecha» para cambiarlas.");
        }
        int cupoPorDia = sedes.isEmpty() ? Integer.MAX_VALUE : horas.size() * sedes.size();
        LocalDate dia = siguienteDiaDeJuego(p.inicio(), p.dias());
        LocalDate primero = null, ultimo = null;
        int programados = 0;
        for (List<Partido> jornada : jornadas.values()) {
            List<Partido> faltan = new ArrayList<>(jornada);
            while (!faltan.isEmpty()) {
                Set<Integer> juegan = new HashSet<>();
                Map<String, Integer> usoHora = new HashMap<>(); // hora -> partidos a esa hora (con canchas)
                Map<String, Set<String>> estadiosPorHora = new HashMap<>(); // sin canchas: un estadio, un partido por hora
                int enElDia = 0;
                List<Partido> siguenFaltando = new ArrayList<>();
                for (Partido x : faltan) {
                    if (enElDia >= cupoPorDia || juegan.contains(x.getIdLocal()) || juegan.contains(x.getIdVisitante())) {
                        siguenFaltando.add(x);
                        continue;
                    }
                    String sedeLocal = estadioDe(c, x);
                    String hora = null, sede = null;
                    for (String h : horas) {
                        if (sedes.isEmpty()) {
                            if (!estadiosPorHora.computeIfAbsent(h, k -> new HashSet<>()).contains(sedeLocal)) {
                                hora = h;
                                sede = sedeLocal;
                                break;
                            }
                        } else if (usoHora.getOrDefault(h, 0) < sedes.size()) {
                            hora = h;
                            sede = sedes.get(usoHora.getOrDefault(h, 0));
                            break;
                        }
                    }
                    if (hora == null) {
                        siguenFaltando.add(x);
                        continue;
                    }
                    usoHora.merge(hora, 1, Integer::sum);
                    estadiosPorHora.computeIfAbsent(hora, k -> new HashSet<>()).add(sede);
                    x.setFecha(dia.toString());
                    x.setHora(hora);
                    x.setSede(sede);
                    juegan.add(x.getIdLocal());
                    juegan.add(x.getIdVisitante());
                    enElDia++;
                    programados++;
                    primero = primero == null ? dia : primero;
                    ultimo = dia;
                }
                faltan = siguenFaltando;
                dia = siguienteDiaDeJuego(dia.plusDays(1), p.dias()); // lo que no cupo y la jornada siguiente, otro día
            }
        }
        return "Se programaron " + programados + (programados == 1 ? " partido" : " partidos") + " del " + primero.format(DMA)
                + " al " + ultimo.format(DMA) + ". Puede ajustar cualquiera en su «Previa».";
    }

    /** El estadio del local, o «Por definir» si no tiene. */
    private static String estadioDe(Campeonato c, Partido x) {
        var e = c.buscarEquipo(x.getIdLocal());
        return e == null || e.getEstadio() == null || e.getEstadio().isBlank() ? "Por definir" : e.getEstadio().trim();
    }

    private static LocalDate siguienteDiaDeJuego(LocalDate desde, Set<DayOfWeek> dias) {
        LocalDate d = desde;
        while (!dias.contains(d.getDayOfWeek())) {
            d = d.plusDays(1);
        }
        return d;
    }

    /** La fecha más temprana permitida: el día en que se creó el campeonato (o hoy, si no se sabe). */
    public static LocalDate minimoPermitido(Campeonato c, LocalDate hoy) {
        LocalDate creado = fecha(c.getFechaCreacion());
        return creado != null ? creado : hoy;
    }

    /**
     * Revisa la fecha que se escribió en un partido: no antes de que existiera el campeonato, y si ya se jugó (o se
     * está jugando) no en el futuro.
     */
    public static void validarFecha(Campeonato c, Partido datos, LocalDate hoy) {
        LocalDate f = fecha(datos.getFecha());
        if (f == null) {
            return; // «Por definir»: todavía sin fecha
        }
        LocalDate creado = fecha(c.getFechaCreacion());
        if (creado != null && f.isBefore(creado)) {
            throw new ReglaNegocioException("La fecha del partido no puede ser anterior al " + creado.format(DMA)
                    + ", el día en que se creó el campeonato.");
        }
        if ((datos.isJugado() || datos.getEstado() == EstadoPartido.EN_JUEGO) && f.isAfter(hoy)) {
            throw new ReglaNegocioException("Un partido jugado no puede tener una fecha futura (" + f.format(DMA)
                    + "). Corrija la fecha o déjelo pendiente.");
        }
    }

    static LocalDate fecha(String texto) {
        try {
            return texto == null || texto.isBlank() ? null : LocalDate.parse(texto.trim());
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }
}
