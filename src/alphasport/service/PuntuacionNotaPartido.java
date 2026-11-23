package alphasport.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * [REQ-07] Nota del sistema de un partido (escala 1.0 a 10.0): 6.0 de base para todo el que jugo; gol +1.0,
 * asistencia +0.5, amarilla -0.5, roja -2.0. El portero ademas suma +1.5 con la valla invicta y resta 0.3 por
 * cada gol recibido. Siempre queda entre 1.0 y 10.0, redondeada a 1 decimal. La version web tiene su gemela
 * en web/js/service/calificaciones.js.
 */
public final class PuntuacionNotaPartido implements ReglaPuntuacion {

    public static final PuntuacionNotaPartido REGLAS = new PuntuacionNotaPartido();

    public static final double NOTA_BASE = 6.0;
    public static final double PUNTOS_GOL = 1.0;
    public static final double PUNTOS_ASISTENCIA = 0.5;
    public static final double PUNTOS_AMARILLA = -0.5;
    public static final double PUNTOS_ROJA = -2.0;
    public static final double PUNTOS_VALLA_INVICTA = 1.5;
    public static final double PUNTOS_GOL_RECIBIDO = -0.3;
    public static final double NOTA_MINIMA = 1.0;
    public static final double NOTA_MAXIMA = 10.0;

    private PuntuacionNotaPartido() {
    }

    @Override
    public double puntuar(DesempenoJugador d, List<String> desglose) {
        double nota = NOTA_BASE;
        desglose.add("Base por jugar: " + texto(NOTA_BASE));
        nota += sumar(desglose, "Gol", d.goles(), PUNTOS_GOL);
        nota += sumar(desglose, "Asistencia", d.asistencias(), PUNTOS_ASISTENCIA);
        nota += sumar(desglose, "Tarjeta amarilla", d.amarillas(), PUNTOS_AMARILLA);
        nota += sumar(desglose, "Tarjeta roja", d.rojas(), PUNTOS_ROJA);
        if (d.portero()) {
            if (d.golesRecibidos() == 0) {
                nota += PUNTOS_VALLA_INVICTA;
                desglose.add("Valla invicta: " + conSigno(PUNTOS_VALLA_INVICTA));
            } else {
                nota += sumar(desglose, "Gol recibido", d.golesRecibidos(), PUNTOS_GOL_RECIBIDO);
            }
        }
        double acotada = Math.max(NOTA_MINIMA, Math.min(NOTA_MAXIMA, nota));
        if (acotada != nota) {
            desglose.add(acotada == NOTA_MAXIMA ? "Ajustada al máximo: " + texto(NOTA_MAXIMA) : "Ajustada al mínimo: " + texto(NOTA_MINIMA));
        }
        return redondear(acotada);
    }

    /** Agrega al desglose una linea por concepto («Gol ×2: +2.0») y devuelve los puntos. Sirve a otras reglas. */
    public static double sumar(List<String> desglose, String concepto, int veces, double puntos) {
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
}
