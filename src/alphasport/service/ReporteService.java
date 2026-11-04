package alphasport.service;

import alphasport.model.Campeonato;
import alphasport.model.Equipo;
import alphasport.model.Fase;
import alphasport.model.Partido;
import alphasport.model.TipoFase;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Genera el historial del campeonato en texto plano para descargarlo.
 */
public class ReporteService {

    public String historial(MotorCampeonato motor) {
        Campeonato c = motor.getCampeonato();
        StringBuilder sb = new StringBuilder();
        linea(sb, "ALPHASPORT - HISTORIAL DEL CAMPEONATO");
        linea(sb, "");
        linea(sb, "CAMPEONATO: " + c.getNombreCampeonato());
        linea(sb, "FORMATO: " + c.getFormato().getNombre());
        linea(sb, c.getConfiguracion().resumen(c.getFormato()));
        linea(sb, "Desempate: " + c.getConfiguracion().getCriterios().stream()
                .map(Object::toString).collect(Collectors.joining(" > ")));
        linea(sb, "");
        linea(sb, "EQUIPOS:");
        for (Equipo e : c.getEquipos()) {
            linea(sb, "- " + e.getNombreEquipo() + " | DT: " + e.getNombreDirectorTecnico()
                    + " | Jugadores: " + e.getJugadores().size() + (e.isRetirado() ? " | RETIRADO" : ""));
        }
        for (int i = 0; i < c.getFases().size(); i++) {
            Fase fase = c.getFases().get(i);
            linea(sb, "");
            linea(sb, "=== " + fase.getNombre().toUpperCase() + " ===");
            if (fase.getTipo() == TipoFase.LIGA || fase.getTipo() == TipoFase.SUIZO) {
                tabla(sb, motor.tablaDeFase(i));
            } else if (fase.getTipo() == TipoFase.GRUPOS) {
                for (int g = 0; g < fase.getGrupos().size(); g++) {
                    linea(sb, "Grupo " + Fase.letraGrupo(g) + ":");
                    tabla(sb, motor.tablaDeGrupo(i, g));
                }
            }
            linea(sb, "RESULTADOS:");
            for (Partido p : c.partidosDeFase(i)) {
                linea(sb, (p.getGrupo().isEmpty() ? "" : "Grupo " + p.getGrupo() + " | ") + p.getNombreRonda()
                        + "\t" + c.nombreDe(p.getIdLocal()) + "\t" + p.getMarcador() + "\t" + c.nombreDe(p.getIdVisitante())
                        + "\t" + p.getFecha() + "\t" + p.getEstado());
            }
        }
        linea(sb, "");
        linea(sb, "TARJETAS:");
        for (Sanciones.Registro r : new Sanciones(c).listar()) {
            linea(sb, c.nombreDe(r.getTarjeta().getIdEquipo()) + "\t" + r.getTarjeta()
                    + (r.getFechasPendientes() > 0 ? "\tle faltan " + r.getFechasPendientes() + " fecha(s)" : ""));
        }
        linea(sb, "");
        linea(sb, c.isTerminado() ? "CAMPEON: " + c.nombreDe(c.getIdCampeon()) : "CAMPEONATO EN CURSO");
        return sb.toString();
    }

    private void tabla(StringBuilder sb, List<FilaTabla> filas) {
        linea(sb, "Pos\tEquipo\tPJ\tG\tE\tP\tGF\tGC\tDG\tPts");
        int pos = 1;
        for (FilaTabla f : filas) {
            linea(sb, pos++ + "\t" + f.getEquipo().getNombreEquipo() + "\t" + f.getJugados() + "\t" + f.getGanados()
                    + "\t" + f.getEmpatados() + "\t" + f.getPerdidos() + "\t" + f.getGolesFavor() + "\t"
                    + f.getGolesContra() + "\t" + f.getDiferenciaGoles() + "\t" + f.getPuntos());
        }
    }

    private void linea(StringBuilder sb, String texto) {
        sb.append(texto).append("\r\n");
    }
}
