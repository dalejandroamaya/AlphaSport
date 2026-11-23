package alphasport.service;

import alphasport.model.Cambio;
import alphasport.model.Campeonato;
import alphasport.model.Equipo;
import alphasport.model.Gol;
import alphasport.model.Jugador;
import alphasport.model.Partido;
import alphasport.model.Tarjeta;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reglas del futbol que debe cumplir un partido antes de guardarse: XI inicial, tarjetas, cambios y goles.
 * Es la unica fuente de estas reglas: el servicio las aplica a todo lo que llega (escritorio o web) y las
 * pantallas solo ayudan a no equivocarse.
 */
public final class ReglasPartido {

    /** Jugadores que inician el partido. */
    public static final int TITULARES = 11;
    /** Minuto mas alto que se acepta (90 + prorroga + descuento). */
    public static final int MINUTO_MAXIMO = 130;

    private ReglasPartido() {
    }

    /** Titulares que necesita un equipo: 11, o toda la plantilla si tiene menos. */
    public static int titularesNecesarios(Equipo equipo) {
        return Math.min(TITULARES, equipo.getJugadores().size());
    }

    /**
     * Revisa los datos editados de un partido. {@code partido} es el guardado (de el salen los equipos);
     * {@code datos}, lo que se quiere guardar. Lanza ReglaNegocioException con el primer problema.
     */
    public static void validar(Campeonato c, Partido partido, Partido datos) {
        Map<Integer, Set<Integer>> validos = jugadoresValidos(c, partido);
        titulares(c, partido, datos, validos);
        tarjetas(c, partido, datos, validos);
        cambios(c, partido, datos, validos);
        goles(c, partido, datos, validos);
        cantidadDeGoles(c, partido, datos);
    }

    /**
     * Por equipo, quienes pueden aparecer en el partido: su plantilla y quien ya aparecia en el (asi un
     * jugador que dejo el equipo despues no impide corregir el partido).
     */
    private static Map<Integer, Set<Integer>> jugadoresValidos(Campeonato c, Partido partido) {
        Map<Integer, Set<Integer>> validos = new HashMap<>();
        for (int idEquipo : new int[]{partido.getIdLocal(), partido.getIdVisitante()}) {
            Set<Integer> ids = new HashSet<>(partido.getTitularesDe(idEquipo));
            Equipo e = c.buscarEquipo(idEquipo);
            if (e != null) {
                e.getJugadores().forEach(j -> ids.add(j.getId()));
            }
            validos.put(idEquipo, ids);
        }
        partido.getTarjetas().forEach(t -> agregar(validos, t.getIdEquipo(), t.getIdJugador()));
        partido.getCambios().forEach(x -> {
            agregar(validos, x.getIdEquipo(), x.getIdSale());
            agregar(validos, x.getIdEquipo(), x.getIdEntra());
        });
        partido.getGoles().forEach(g -> {
            agregar(validos, g.isEnContra() ? partido.getRival(g.getIdEquipo()) : g.getIdEquipo(), g.getIdAutor());
            if (g.tieneAsistencia()) {
                agregar(validos, g.getIdEquipo(), g.getIdAsistente());
            }
        });
        return validos;
    }

    private static void agregar(Map<Integer, Set<Integer>> validos, int idEquipo, int idJugador) {
        Set<Integer> ids = validos.get(idEquipo);
        if (ids != null) {
            ids.add(idJugador);
        }
    }

    private static boolean juega(Map<Integer, Set<Integer>> validos, int idEquipo, int idJugador) {
        return validos.containsKey(idEquipo) && validos.get(idEquipo).contains(idJugador);
    }

    private static void minuto(int minuto, int desde, String que) {
        if (minuto < desde || minuto > MINUTO_MAXIMO) {
            throw new ReglaNegocioException("El minuto " + que + " debe estar entre 1 y " + MINUTO_MAXIMO + ".");
        }
    }

    /* ---------------------------------------------------------------- XI inicial */

    /** XI de cada equipo: de su plantilla, sin repetir y completo. Un XI sin cambios no se vuelve a revisar. */
    private static void titulares(Campeonato c, Partido partido, Partido datos, Map<Integer, Set<Integer>> validos) {
        for (int idEquipo : new int[]{partido.getIdLocal(), partido.getIdVisitante()}) {
            List<Integer> xi = idEquipo == partido.getIdLocal() ? datos.getTitularesLocal() : datos.getTitularesVisitante();
            if (xi.isEmpty() || xi.equals(partido.getTitularesDe(idEquipo))) {
                continue;
            }
            Equipo equipo = c.buscarEquipo(idEquipo);
            for (int id : xi) {
                if (!juega(validos, idEquipo, id)) {
                    throw new ReglaNegocioException("Uno de los titulares de " + equipo.getNombreEquipo() + " no está en su plantilla.");
                }
            }
            if (new HashSet<>(xi).size() != xi.size()) {
                throw new ReglaNegocioException("En el XI inicial de " + equipo.getNombreEquipo() + " hay un jugador repetido.");
            }
            int necesarios = titularesNecesarios(equipo);
            if (xi.size() != necesarios) {
                throw new ReglaNegocioException("El XI inicial de " + equipo.getNombreEquipo() + " debe tener " + necesarios
                        + " jugadores (tiene " + xi.size() + ").");
            }
        }
    }

    /* ---------------------------------------------------------------- tarjetas */

    private static void tarjetas(Campeonato c, Partido partido, Partido datos, Map<Integer, Set<Integer>> validos) {
        for (Tarjeta t : datos.getTarjetas()) {
            if (!partido.involucra(t.getIdEquipo())) {
                throw new ReglaNegocioException("Una de las tarjetas es de un equipo que no juega este partido.");
            }
            if (!juega(validos, t.getIdEquipo(), t.getIdJugador())) {
                throw new ReglaNegocioException("La tarjeta de " + t.getNombreJugador() + " es de un jugador que no está en "
                        + c.nombreDe(t.getIdEquipo()) + ".");
            }
            minuto(t.getMinuto(), 0, "de la tarjeta");
        }
    }

    /* ---------------------------------------------------------------- cambios */

    /**
     * Cambios en orden de minuto: sale alguien que esta en la cancha, entra un suplente, y quien salio no
     * vuelve a entrar.
     */
    private static void cambios(Campeonato c, Partido partido, Partido datos, Map<Integer, Set<Integer>> validos) {
        List<Cambio> cambios = new ArrayList<>(datos.getCambios());
        for (Cambio x : cambios) {
            if (!partido.involucra(x.getIdEquipo())) {
                throw new ReglaNegocioException("Uno de los cambios es de un equipo que no juega este partido.");
            }
            minuto(x.getMinuto(), 1, "del cambio");
            if (!juega(validos, x.getIdEquipo(), x.getIdSale()) || !juega(validos, x.getIdEquipo(), x.getIdEntra())) {
                throw new ReglaNegocioException("Uno de los cambios es de un jugador que no está en " + c.nombreDe(x.getIdEquipo()) + ".");
            }
            if (x.getIdSale() == x.getIdEntra()) {
                throw new ReglaNegocioException("En un cambio el jugador que sale y el que entra deben ser distintos.");
            }
        }
        cambios.sort(Comparator.comparingInt(Cambio::getMinuto));
        Map<Integer, List<Integer>> xi = Map.of(partido.getIdLocal(), datos.getTitularesLocal(),
                partido.getIdVisitante(), datos.getTitularesVisitante());
        Map<Integer, Set<Integer>> enCancha = new HashMap<>();
        xi.forEach((idEquipo, ids) -> enCancha.put(idEquipo, new HashSet<>(ids)));
        Set<Integer> salieron = new HashSet<>();
        Set<Integer> entraron = new HashSet<>();
        for (Cambio x : cambios) {
            if (xi.get(x.getIdEquipo()).isEmpty()) {
                throw new ReglaNegocioException("Antes de registrar cambios escoja el XI inicial de " + c.nombreDe(x.getIdEquipo()) + ".");
            }
            Set<Integer> cancha = enCancha.get(x.getIdEquipo());
            if (salieron.contains(x.getIdSale())) {
                throw new ReglaNegocioException("#" + x.getDorsalSale() + " " + x.getNombreSale() + " ya había salido antes del minuto " + x.getMinuto() + ".");
            }
            if (!cancha.contains(x.getIdSale())) {
                throw new ReglaNegocioException("#" + x.getDorsalSale() + " " + x.getNombreSale() + " no está en la cancha en el minuto "
                        + x.getMinuto() + ": solo puede salir un titular o alguien que ya entró.");
            }
            if (xi.get(x.getIdEquipo()).contains(x.getIdEntra())) {
                throw new ReglaNegocioException("#" + x.getDorsalEntra() + " " + x.getNombreEntra() + " es titular: solo pueden entrar suplentes.");
            }
            if (salieron.contains(x.getIdEntra()) || entraron.contains(x.getIdEntra())) {
                throw new ReglaNegocioException("#" + x.getDorsalEntra() + " " + x.getNombreEntra()
                        + (salieron.contains(x.getIdEntra()) ? " ya fue sustituido y no puede volver a entrar." : " ya había entrado en otro cambio."));
            }
            salieron.add(x.getIdSale());
            entraron.add(x.getIdEntra());
            cancha.remove(x.getIdSale());
            cancha.add(x.getIdEntra());
        }
    }

    /* ---------------------------------------------------------------- goles */

    /**
     * El autor es del equipo que suma el gol (o del rival si es en contra) y la asistencia, opcional, es de
     * un companero distinto del autor.
     */
    private static void goles(Campeonato c, Partido partido, Partido datos, Map<Integer, Set<Integer>> validos) {
        for (Gol g : datos.getGoles()) {
            if (!partido.involucra(g.getIdEquipo())) {
                throw new ReglaNegocioException("Uno de los goles es de un equipo que no juega este partido.");
            }
            minuto(g.getMinuto(), 0, "del gol");
            int equipoAutor = g.isEnContra() ? partido.getRival(g.getIdEquipo()) : g.getIdEquipo();
            if (!juega(validos, equipoAutor, g.getIdAutor())) {
                throw new ReglaNegocioException("Uno de los goles es de un jugador que no está en la plantilla"
                        + (g.isEnContra() ? " del rival." : " de su equipo."));
            }
            if (g.tieneAsistencia()) {
                if (g.isEnContra()) {
                    throw new ReglaNegocioException("Un gol en contra no tiene asistencia.");
                }
                if (!juega(validos, g.getIdEquipo(), g.getIdAsistente())) {
                    throw new ReglaNegocioException("La asistencia del gol de " + g.getNombreAutor() + " es de un jugador que no está en su equipo.");
                }
                if (g.getIdAsistente() == g.getIdAutor()) {
                    throw new ReglaNegocioException(g.getNombreAutor() + " no puede darse la asistencia a sí mismo.");
                }
            }
        }
    }

    /** Nunca hay mas goles con autor que goles en el marcador (tambien al bajar el marcador). */
    private static void cantidadDeGoles(Campeonato c, Partido partido, Partido datos) {
        for (int idEquipo : new int[]{partido.getIdLocal(), partido.getIdVisitante()}) {
            long registrados = datos.getGoles().stream().filter(g -> g.getIdEquipo() == idEquipo).count();
            int marcador = idEquipo == partido.getIdLocal() ? datos.getGolesLocal() : datos.getGolesVisitante();
            if (registrados > marcador) {
                throw new ReglaNegocioException("Hay " + registrados + (registrados == 1 ? " gol registrado" : " goles registrados")
                        + " de " + c.nombreDe(idEquipo) + ", pero el marcador dice " + marcador
                        + ". Corrija el marcador o quite goles en el editor del partido.");
            }
        }
    }

    /** Jugador del campeonato por id, en cualquier equipo (null si ya no esta en ninguna plantilla). */
    public static Jugador buscarJugador(Campeonato c, int idJugador) {
        for (Equipo e : c.getEquipos()) {
            for (Jugador j : e.getJugadores()) {
                if (j.getId() == idJugador) {
                    return j;
                }
            }
        }
        return null;
    }
}
