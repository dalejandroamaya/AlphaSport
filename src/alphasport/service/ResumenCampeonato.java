package alphasport.service;

import java.util.ArrayList;
import java.util.HashMap;
import alphasport.model.Campeonato;
import alphasport.model.Equipo;
import alphasport.model.Fase;
import alphasport.model.Partido;
import alphasport.model.TipoFase;

/**
 * Resultados finales de un campeonato para el historial: clasificacion
 * final, podio, premios (equipo mas goleador, valla menos vencida, fair
 * play, mayor goleada) y totales. Sirve para entregar premios.
 */
public class ResumenCampeonato {

    private final Campeonato campeonato;
    private final MotorCampeonato motor;
    private final ArrayList<FilaTabla> tablaGeneral;

    public ResumenCampeonato(Campeonato campeonato) {
        this.campeonato = campeonato;
        this.motor = new MotorCampeonato(campeonato);
        ArrayList<Integer> ids = new ArrayList<>();
        for (Equipo e : campeonato.getEquipos()) {
            ids.add(e.getId());
        }
        this.tablaGeneral = new TablaPosiciones(campeonato).calcular(campeonato.getPartidos(), ids);
    }

    public Campeonato getCampeonato() {
        return campeonato;
    }

    /**
     * Estadisticas de todos los partidos del torneo (todas las fases).
     */
    public ArrayList<FilaTabla> getTablaGeneral() {
        return tablaGeneral;
    }

    public FilaTabla filaDe(int idEquipo) {
        for (FilaTabla f : tablaGeneral) {
            if (f.getIdEquipo() == idEquipo) {
                return f;
            }
        }
        return null;
    }

    // =====================================================================
    //  CLASIFICACION FINAL
    // =====================================================================
    /**
     * Orden final de todos los equipos: el campeon primero; luego los que
     * llegaron a una fase mas avanzada; dentro de la misma fase, los que
     * llegaron mas lejos (llaves) o quedaron mejor en su tabla; y por ultimo
     * la tabla general.
     */
    public ArrayList<FilaTabla> clasificacionFinal() {
        HashMap<Integer, int[]> claves = new HashMap<>();
        for (FilaTabla f : tablaGeneral) {
            claves.put(f.getIdEquipo(), claveDe(f.getIdEquipo()));
        }
        ArrayList<FilaTabla> orden = new ArrayList<>(tablaGeneral); // ya viene ordenada por la tabla general
        orden.sort((a, b) -> {
            if (a.getIdEquipo() == campeonato.getIdCampeon()) {
                return -1;
            }
            if (b.getIdEquipo() == campeonato.getIdCampeon()) {
                return 1;
            }
            int[] ka = claves.get(a.getIdEquipo());
            int[] kb = claves.get(b.getIdEquipo());
            for (int i = 0; i < ka.length; i++) {
                if (ka[i] != kb[i]) {
                    return Integer.compare(kb[i], ka[i]);
                }
            }
            return 0;
        });
        return orden;
    }

    /**
     * {ultima fase en la que participo, que tan lejos llego en ella (mayor es mejor)}
     */
    private int[] claveDe(int idEquipo) {
        int ultimaFase = -1;
        for (int i = 0; i < campeonato.getFases().size(); i++) {
            if (campeonato.getFases().get(i).getParticipantes().contains(idEquipo)) {
                ultimaFase = i;
            }
        }
        if (ultimaFase == -1) {
            return new int[]{-1, 0};
        }
        Fase fase = campeonato.getFases().get(ultimaFase);
        int avance;
        if (fase.getTipo() == TipoFase.ELIMINATORIA || fase.getTipo() == TipoFase.DOBLE_ELIMINACION) {
            avance = 0; // ronda mas alta que jugo
            for (Partido p : campeonato.partidosDeFase(ultimaFase)) {
                if (p.involucra(idEquipo)) {
                    avance = Math.max(avance, p.getRonda());
                }
            }
        } else if (fase.getTipo() == TipoFase.GRUPOS) {
            avance = -posicionEnGrupo(ultimaFase, fase, idEquipo);
        } else {
            avance = -MotorCampeonato.ids(motor.tablaDeFase(ultimaFase)).indexOf(idEquipo);
        }
        return new int[]{ultimaFase, avance};
    }

    private int posicionEnGrupo(int indiceFase, Fase fase, int idEquipo) {
        for (int g = 0; g < fase.getGrupos().size(); g++) {
            int pos = MotorCampeonato.ids(motor.tablaDeGrupo(indiceFase, g)).indexOf(idEquipo);
            if (pos >= 0) {
                return pos;
            }
        }
        return 99;
    }

    // =====================================================================
    //  PREMIOS
    // =====================================================================
    public FilaTabla equipoMasGoleador() {
        FilaTabla mejor = null;
        for (FilaTabla f : tablaGeneral) {
            if (f.getJugados() > 0 && (mejor == null || f.getGolesFavor() > mejor.getGolesFavor())) {
                mejor = f;
            }
        }
        return mejor;
    }

    /**
     * Menos goles recibidos por partido (asi no gana un equipo que jugo menos).
     */
    public FilaTabla vallaMenosVencida() {
        FilaTabla mejor = null;
        for (FilaTabla f : tablaGeneral) {
            if (f.getJugados() == 0) {
                continue;
            }
            if (mejor == null || f.getGolesContra() * mejor.getJugados() < mejor.getGolesContra() * f.getJugados()) {
                mejor = f;
            }
        }
        return mejor;
    }

    /**
     * Menos puntos de tarjetas por partido (amarilla = 1, roja = 3).
     */
    public FilaTabla premioFairPlay() {
        FilaTabla mejor = null;
        for (FilaTabla f : tablaGeneral) {
            if (f.getJugados() == 0) {
                continue;
            }
            if (mejor == null || f.getPuntosFairPlay() * mejor.getJugados() < mejor.getPuntosFairPlay() * f.getJugados()) {
                mejor = f;
            }
        }
        return mejor;
    }

    public Partido mayorGoleada() {
        Partido mayor = null;
        for (Partido p : campeonato.getPartidos()) {
            if (!p.isJugado()) {
                continue;
            }
            int diferencia = Math.abs(p.getGolesLocal() - p.getGolesVisitante());
            if (mayor == null || diferencia > Math.abs(mayor.getGolesLocal() - mayor.getGolesVisitante())) {
                mayor = p;
            }
        }
        return mayor;
    }

    public int partidosJugados() {
        int total = 0;
        for (Partido p : campeonato.getPartidos()) {
            if (p.isJugado()) {
                total++;
            }
        }
        return total;
    }

    public int golesTotales() {
        int total = 0;
        for (Partido p : campeonato.getPartidos()) {
            if (p.isJugado()) {
                total += p.getGolesLocal() + p.getGolesVisitante();
            }
        }
        return total;
    }

    /**
     * Texto con el podio y los premios (se muestra en el historial y se
     * puede exportar para hacer diplomas o entregar premios).
     */
    public String texto() {
        Campeonato c = campeonato;
        StringBuilder sb = new StringBuilder();
        sb.append("ALPHASPORT - RESUMEN DEL CAMPEONATO\n\n");
        sb.append("CAMPEONATO: ").append(c.getNombreCampeonato()).append("\n");
        sb.append("Formato: ").append(c.getFormato().getNombre()).append("\n");
        if (c.getFechaArchivo() != null) {
            sb.append("Fecha: ").append(c.getFechaArchivo()).append("\n");
        }
        sb.append("Estado: ").append(c.isTerminado() ? "Terminado" : "Sin terminar").append("\n\n");

        ArrayList<FilaTabla> podio = clasificacionFinal();
        sb.append("=========== PODIO ===========\n");
        String[] puestos = {"Campeon", "Subcampeon", "Tercer lugar"};
        for (int i = 0; i < 3 && i < podio.size(); i++) {
            if (i == 0 && !c.isTerminado()) {
                sb.append("(el campeonato no termino: se muestran los primeros de la clasificacion)\n");
            }
            Equipo e = podio.get(i).getEquipo();
            sb.append(String.format("%-14s %s  (DT: %s)%n", puestos[i] + ":", e.getNombreEquipo(), e.getNombreDirectorTecnico()));
        }

        sb.append("\n=========== PREMIOS ===========\n");
        FilaTabla goleador = equipoMasGoleador();
        FilaTabla valla = vallaMenosVencida();
        FilaTabla fairPlay = premioFairPlay();
        Partido goleada = mayorGoleada();
        if (goleador != null) {
            sb.append("Equipo mas goleador:  ").append(goleador.getEquipo().getNombreEquipo())
                    .append(" (").append(goleador.getGolesFavor()).append(" goles)\n");
        }
        if (valla != null) {
            sb.append("Valla menos vencida:  ").append(valla.getEquipo().getNombreEquipo())
                    .append(" (").append(valla.getGolesContra()).append(" goles recibidos en ")
                    .append(valla.getJugados()).append(" partidos)\n");
        }
        if (fairPlay != null) {
            sb.append("Premio Fair Play:     ").append(fairPlay.getEquipo().getNombreEquipo())
                    .append(" (").append(fairPlay.getAmarillas()).append(" amarillas, ")
                    .append(fairPlay.getRojas()).append(" rojas)\n");
        }
        if (goleada != null) {
            sb.append("Mayor goleada:        ").append(c.nombreDe(goleada.getIdLocal())).append(" ")
                    .append(goleada.getGolesLocal()).append(" - ").append(goleada.getGolesVisitante()).append(" ")
                    .append(c.nombreDe(goleada.getIdVisitante())).append(" (").append(goleada.getNombreRonda()).append(")\n");
        }

        int jugados = partidosJugados();
        int goles = golesTotales();
        sb.append("\n=========== CIFRAS ===========\n");
        sb.append("Equipos: ").append(c.getEquipos().size()).append("   Partidos jugados: ").append(jugados)
                .append("   Goles: ").append(goles);
        if (jugados > 0) {
            sb.append(String.format("   Promedio: %.2f goles por partido", goles / (double) jugados));
        }
        sb.append("\n\n=========== CLASIFICACION FINAL ===========\n");
        int pos = 1;
        for (FilaTabla f : podio) {
            sb.append(String.format("%2d. %-22s PJ %2d  G %2d  E %2d  P %2d  GF %3d  GC %3d  Pts %3d%n", pos++,
                    f.getEquipo().getNombreEquipo(), f.getJugados(), f.getGanados(), f.getEmpatados(), f.getPerdidos(),
                    f.getGolesFavor(), f.getGolesContra(), f.getPuntos()));
        }
        return sb.toString();
    }
}
