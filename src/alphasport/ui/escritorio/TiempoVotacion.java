package alphasport.ui.escritorio;

import static alphasport.ui.escritorio.Datos.b;
import static alphasport.ui.escritorio.Datos.m;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * [REQ-07] Textos del tiempo de votación que comparten las pantallas («quedan 23 h 5 min», «23/11 a las 18:00»).
 */
final class TiempoVotacion {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM 'a las' HH:mm", Locale.forLanguageTag("es"));

    private TiempoVotacion() {
    }

    /** Hora (ms) en que cierra la votación según el JSON; 0 si no la tiene. */
    static long cierraEn(Map<String, Object> votacion) {
        Object v = votacion == null ? null : votacion.get("cierraEn");
        return v instanceof Number x ? x.longValue() : 0;
    }

    /** «23/11 a las 18:00» */
    static String hora(long ms) {
        return ms <= 0 ? "" : HORA.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()));
    }

    /** «quedan 2 d 3 h», «quedan 23 h 5 min» o «quedan 8 min» */
    static String restante(long cierraEn) {
        long minutos = Math.max(0, (cierraEn - System.currentTimeMillis() + 59_999) / 60_000);
        long horas = minutos / 60, dias = horas / 24;
        if (dias > 1) {
            return "quedan " + dias + " d " + (horas % 24) + " h";
        }
        if (horas > 0) {
            return "quedan " + horas + " h " + (minutos % 60) + " min";
        }
        return minutos == 1 ? "queda 1 min" : "quedan " + minutos + " min";
    }

    /** true si el público puede votar ese partido ahora. */
    static boolean abierta(Map<String, Object> partido) {
        return b(m(partido, "votacion"), "abierta");
    }

    /** Un W.O. no se juega: no tiene votación. */
    static boolean esWalkover(Map<String, Object> partido) {
        return b(partido, "walkover");
    }
}
