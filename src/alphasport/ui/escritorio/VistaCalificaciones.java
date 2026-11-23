package alphasport.ui.escritorio;

import static alphasport.ui.escritorio.Aplicacion.boton;
import static alphasport.ui.escritorio.Aplicacion.etiqueta;
import static alphasport.ui.escritorio.Datos.b;
import static alphasport.ui.escritorio.Datos.l;
import static alphasport.ui.escritorio.Datos.n;
import static alphasport.ui.escritorio.Datos.s;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * [REQ-07] Pestaña «Calificaciones» del detalle del partido: la nota final de cada jugador que jugó (la del
 * sistema promediada con los votos del público) y el MVP destacado. Aquí no se vota: el visitante lo hace en la
 * sección «Votaciones» ({@link VistaVotaciones}). Solo dibuja el JSON de Presentador.calificaciones.
 */
final class VistaCalificaciones {

    private final Map<String, Object> partido;

    private VistaCalificaciones(Map<String, Object> partido) {
        this.partido = partido;
    }

    /** @param irAVotaciones lleva al visitante a «Votaciones» mientras está abierta; null = no se ofrece */
    static Node construir(Map<String, Object> calificaciones, Map<String, Object> partido, Runnable irAVotaciones) {
        return new VistaCalificaciones(partido).dibujar(calificaciones, irAVotaciones);
    }

    private Node dibujar(Map<String, Object> c, Runnable irAVotaciones) {
        VBox caja = new VBox(14);
        caja.getStyleClass().addAll("detalle-panel", "calificaciones");
        if (c == null || !b(c, "disponible")) {
            String motivo = c == null ? "" : s(c, "motivo");
            caja.getChildren().add(etiqueta(motivo.isEmpty() ? "Las calificaciones aparecen cuando el partido termina." : motivo, "vacio"));
            return caja;
        }
        caja.getChildren().add(estado(c, irAVotaciones));
        Map<String, Object> mvp = buscar(c, n(c, "mvp"));
        if (mvp != null) {
            caja.getChildren().add(tarjetaMvp(mvp));
        }
        caja.getChildren().add(new HBox(18, columna(s(partido, "localNombre"), l(c, "local")), columna(s(partido, "visitanteNombre"), l(c, "visitante"))));
        return caja;
    }

    /** Una línea: si la nota ya es definitiva o hasta cuándo puede cambiar. */
    private Node estado(Map<String, Object> c, Runnable irAVotaciones) {
        long cierra = TiempoVotacion.cierraEn(c);
        boolean abierta = s(c, "votacion").equals("ABIERTA");
        Label texto = etiqueta(switch (s(c, "votacion")) {
            case "ABIERTA" -> "Nota final provisional: la votación del público cierra el " + TiempoVotacion.hora(cierra) + ".";
            case "CERRADA" -> "Nota final definitiva: promedio del sistema y los votos del público.";
            default -> "Nota final del sistema: este partido se jugó antes de existir las votaciones.";
        }, "calificacion-estado", abierta ? "calificacion-abierta" : "calificacion-cerrada");
        texto.setWrapText(true);
        texto.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(texto, Priority.ALWAYS);
        HBox fila = new HBox(10, texto);
        fila.setAlignment(Pos.CENTER_LEFT);
        if (abierta && irAVotaciones != null) {
            Button votar = boton("Ir a votar", "primario");
            votar.setMinWidth(Region.USE_PREF_SIZE);
            votar.setOnAction(e -> irAVotaciones.run());
            fila.getChildren().add(votar);
        }
        return fila;
    }

    private Node tarjetaMvp(Map<String, Object> j) {
        Label titulo = etiqueta("★ MVP del partido", "mvp-titulo");
        Label nombre = etiqueta("#" + n(j, "dorsal") + " " + s(j, "nombre"), "h3");
        String equipo = n(j, "equipo") == n(partido, "local") ? s(partido, "localNombre") : s(partido, "visitanteNombre");
        String acciones = resumenAcciones(j);
        Label detalle = etiqueta(equipo + (acciones.isEmpty() ? "" : " · " + acciones), "sutil", "pequeno");
        VBox textos = new VBox(2, titulo, nombre, detalle);
        HBox.setHgrow(textos, Priority.ALWAYS);
        Label nota = etiqueta(texto(numero(j, "notaFinal")), "nota", "nota-mvp");
        nota.setMinWidth(Region.USE_PREF_SIZE);
        HBox fila = new HBox(14, Fotos.cedula(s(j, "foto"), 48), textos, nota);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().addAll("vidrio", "tarjeta-mvp");
        return fila;
    }

    private Node columna(String equipo, List<Map<String, Object>> jugadores) {
        VBox col = new VBox(6, etiqueta(equipo, "h3"));
        if (jugadores.isEmpty()) {
            col.getChildren().add(etiqueta("No se registró quién jugó por este equipo.", "sutil"));
        }
        boolean suplentes = false;
        for (Map<String, Object> j : jugadores) {
            if (!b(j, "titular") && !suplentes) {
                suplentes = true;
                col.getChildren().add(etiqueta("Ingresaron desde el banco", "sutil", "pequeno"));
            }
            col.getChildren().add(fila(j));
        }
        col.setPrefWidth(1);
        col.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(col, Priority.ALWAYS);
        return col;
    }

    private Node fila(Map<String, Object> j) {
        HBox linea = new HBox(6, etiqueta("#" + n(j, "dorsal") + " " + s(j, "nombre"), "jugador-texto"));
        linea.setAlignment(Pos.CENTER_LEFT);
        if (b(j, "portero")) {
            linea.getChildren().add(chip("POR", "chip-portero"));
        }
        if (b(j, "mvp")) {
            linea.getChildren().add(chip("★ MVP", "chip-mvp"));
        }
        VBox textos = new VBox(1, linea);
        String acciones = resumenAcciones(j);
        if (!acciones.isEmpty()) {
            textos.getChildren().add(etiqueta(acciones, "sutil", "pequeno"));
        }
        HBox.setHgrow(textos, Priority.ALWAYS);
        textos.setMaxWidth(Double.MAX_VALUE);
        double notaFinal = numero(j, "notaFinal");
        Label nota = etiqueta(texto(notaFinal), "nota", claseNota(notaFinal));
        nota.setMinWidth(Region.USE_PREF_SIZE);
        nota.setAccessibleText("Nota final " + texto(notaFinal));
        HBox fila = new HBox(10, Fotos.cedula(s(j, "foto"), 27), textos, nota);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().addAll("fila-calificacion", b(j, "mvp") ? "fila-mvp" : "fila-normal");
        return fila;
    }

    /* ---------------------------------------------------------------- ayudas */

    private static Label chip(String texto, String clase) {
        Label l = etiqueta(texto, "chip", clase);
        l.setMinWidth(Region.USE_PREF_SIZE); // nunca se parte en dos líneas
        return l;
    }

    /** Verde desde 7.0, rojo por debajo de 5.0 y neutra en medio (igual que en la web). */
    private static String claseNota(double nota) {
        return nota >= 7.0 ? "nota-alta" : nota < 5.0 ? "nota-baja" : "nota-media";
    }

    /** «2 G · 1 A · 1 TA» (goles, asistencias, tarjetas; el portero, valla invicta o goles recibidos). */
    private static String resumenAcciones(Map<String, Object> j) {
        StringBuilder t = new StringBuilder();
        agregar(t, n(j, "goles"), "G");
        agregar(t, n(j, "asistencias"), "A");
        agregar(t, n(j, "amarillas"), "TA");
        agregar(t, n(j, "rojas"), "TR");
        if (b(j, "portero")) {
            agregar(t, Math.max(1, n(j, "golesRecibidos")), n(j, "golesRecibidos") == 0 ? "valla invicta" : "GR");
        }
        return t.toString();
    }

    private static void agregar(StringBuilder t, int valor, String sigla) {
        if (valor <= 0) {
            return;
        }
        if (t.length() > 0) {
            t.append(" · ");
        }
        t.append(sigla.equals("valla invicta") ? sigla : valor + " " + sigla);
    }

    private static Map<String, Object> buscar(Map<String, Object> c, int id) {
        if (id == 0) {
            return null;
        }
        for (String lado : new String[] {"local", "visitante"}) {
            for (Map<String, Object> j : l(c, lado)) {
                if (n(j, "id") == id) {
                    return j;
                }
            }
        }
        return null;
    }

    private static double numero(Map<String, Object> m, String clave) {
        Object v = m == null ? null : m.get(clave);
        return v instanceof Number x ? x.doubleValue() : 0;
    }

    private static String texto(double nota) {
        return String.format(Locale.ROOT, "%.1f", nota);
    }
}
