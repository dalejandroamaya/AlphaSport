package alphasport.ui.escritorio;

import static alphasport.ui.escritorio.Aplicacion.boton;
import static alphasport.ui.escritorio.Aplicacion.etiqueta;
import static alphasport.ui.escritorio.Datos.b;
import static alphasport.ui.escritorio.Datos.l;
import static alphasport.ui.escritorio.Datos.lista;
import static alphasport.ui.escritorio.Datos.n;
import static alphasport.ui.escritorio.Datos.s;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * [REQ-07] Pestaña «Calificaciones» del detalle del partido: nota de cada jugador que jugó (la final,
 * con la del sistema y la del público por separado y el desglose en un tooltip), el MVP destacado y,
 * para el visitante con la votación abierta, un selector del 1 al 10 por jugador. Solo dibuja el JSON
 * de Presentador.calificaciones; el cálculo y las reglas están en CalculadoraCalificaciones.
 */
final class VistaCalificaciones {

    /** Registra un voto y devuelve la respuesta de la API ({ok, error, calificaciones}). */
    interface Votante {
        Map<String, Object> votar(int idJugador, int valor);
    }

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM 'a las' HH:mm", Locale.forLanguageTag("es"));

    private final Map<String, Object> partido;
    private final boolean puedeVotar;
    private final Votante votante;
    private final VBox caja = new VBox(16);
    private String aviso = "";
    private boolean avisoEsError;

    private VistaCalificaciones(Map<String, Object> partido, boolean puedeVotar, Votante votante) {
        this.partido = partido;
        this.puedeVotar = puedeVotar && votante != null;
        this.votante = votante;
        caja.getStyleClass().addAll("detalle-panel", "calificaciones");
    }

    /**
     * @param puedeVotar true para el visitante (el administrador solo consulta)
     * @param votante    registra el voto; null = solo lectura
     */
    static Node construir(Map<String, Object> calificaciones, Map<String, Object> partido, boolean puedeVotar, Votante votante) {
        VistaCalificaciones vista = new VistaCalificaciones(partido, puedeVotar, votante);
        vista.dibujar(calificaciones);
        ScrollPane desplazable = new ScrollPane(vista.caja);
        desplazable.setFitToWidth(true);
        desplazable.getStyleClass().add("desplazable");
        desplazable.setPrefViewportHeight(560);
        return desplazable;
    }

    private void dibujar(Map<String, Object> c) {
        caja.getChildren().clear();
        if (c == null || !b(c, "disponible")) {
            String motivo = c == null ? "" : s(c, "motivo");
            caja.getChildren().add(etiqueta(motivo.isEmpty() ? "Las calificaciones aparecen cuando el partido termina." : motivo, "vacio"));
            return;
        }
        caja.getChildren().add(estado(c));
        if (!aviso.isEmpty()) {
            Label mensaje = etiqueta(aviso, avisoEsError ? "error" : "calificacion-gracias");
            mensaje.setWrapText(true);
            caja.getChildren().add(mensaje);
        }
        Map<String, Object> mvp = buscar(c, n(c, "mvp"));
        if (mvp != null) {
            caja.getChildren().add(tarjetaMvp(mvp));
        }
        boolean abierta = s(c, "votacion").equals("ABIERTA");
        HBox columnas = new HBox(18, columna(s(partido, "localNombre"), l(c, "local"), abierta),
                columna(s(partido, "visitanteNombre"), l(c, "visitante"), abierta));
        caja.getChildren().addAll(columnas, leyenda());
    }

    /** Franja con el estado de la votación (abierta hasta..., cerrada o sin registro). */
    private Node estado(Map<String, Object> c) {
        String votacion = s(c, "votacion");
        long cierra = numeroLargo(c.get("cierraEn"));
        String texto = switch (votacion) {
            case "ABIERTA" -> "Votación del público abierta: " + restante(cierra) + " (cierra el " + hora(cierra) + ")."
                    + (puedeVotar ? " Califique a cada jugador del 1 al 10." : " Votan los visitantes.");
            case "CERRADA" -> "Votación cerrada" + (cierra > 0 ? " el " + hora(cierra) : "") + ": estas son las notas definitivas.";
            case "SIN_REGISTRO" -> "Partido jugado antes de existir las votaciones: se muestra solo la nota del sistema.";
            default -> "";
        };
        Label l = etiqueta(texto, "calificacion-estado", votacion.equals("ABIERTA") ? "calificacion-abierta" : "calificacion-cerrada");
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    private Node tarjetaMvp(Map<String, Object> j) {
        Label estrella = etiqueta("★", "mvp-estrella");
        Label titulo = etiqueta("MVP del partido", "mvp-titulo");
        Label nombre = etiqueta("#" + n(j, "dorsal") + " " + s(j, "nombre"), "h3");
        String equipo = n(j, "equipo") == n(partido, "local") ? s(partido, "localNombre") : s(partido, "visitanteNombre");
        Label detalle = etiqueta(equipo + " · " + resumenAcciones(j), "sutil", "pequeno");
        VBox textos = new VBox(2, titulo, nombre, detalle);
        HBox.setHgrow(textos, Priority.ALWAYS);
        Label nota = etiqueta(texto(numero(j, "notaFinal")), "nota", "nota-mvp");
        HBox fila = new HBox(14, estrella, textos, nota);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().addAll("vidrio", "tarjeta-mvp");
        return fila;
    }

    private Node columna(String equipo, List<Map<String, Object>> jugadores, boolean abierta) {
        VBox col = new VBox(8, etiqueta(equipo, "h3"));
        if (jugadores.isEmpty()) {
            col.getChildren().add(etiqueta("No se registró quién jugó por este equipo.", "sutil"));
        }
        boolean suplentes = false;
        for (Map<String, Object> j : jugadores) {
            if (!b(j, "titular") && !suplentes) {
                suplentes = true;
                col.getChildren().add(etiqueta("Ingresaron desde el banco", "sutil", "pequeno"));
            }
            col.getChildren().add(fila(j, abierta));
        }
        col.setPrefWidth(430);
        HBox.setHgrow(col, Priority.ALWAYS);
        return col;
    }

    private Node fila(Map<String, Object> j, boolean abierta) {
        Label dorsal = etiqueta(String.valueOf(n(j, "dorsal")), "calificacion-dorsal");
        dorsal.setMinWidth(30);
        dorsal.setAlignment(Pos.CENTER);
        Label nombre = etiqueta(s(j, "nombre"), "jugador-texto");
        HBox linea1 = new HBox(6, nombre);
        linea1.setAlignment(Pos.CENTER_LEFT);
        if (b(j, "portero")) {
            Label por = etiqueta("POR", "chip", "chip-portero");
            por.setMinWidth(Label.USE_PREF_SIZE); // nunca se parte en dos líneas
            linea1.getChildren().add(por);
        }
        if (b(j, "mvp")) {
            Label mvp = etiqueta("★ MVP", "chip", "chip-mvp");
            mvp.setMinWidth(Label.USE_PREF_SIZE);
            linea1.getChildren().add(mvp);
        }
        String publico = n(j, "votos") == 0 ? "Público: sin votos"
                : "Público " + texto(numero(j, "promedioPublico")) + " (" + n(j, "votos") + (n(j, "votos") == 1 ? " voto)" : " votos)");
        Label linea2 = etiqueta("Sistema " + texto(numero(j, "notaSistema")) + " · " + publico
                + (resumenAcciones(j).isEmpty() ? "" : " · " + resumenAcciones(j)), "sutil", "pequeno");
        VBox textos = new VBox(1, linea1, linea2);
        HBox.setHgrow(textos, Priority.ALWAYS);
        textos.setMaxWidth(Double.MAX_VALUE);

        double notaFinal = numero(j, "notaFinal");
        Label nota = etiqueta(texto(notaFinal), "nota", claseNota(notaFinal));
        nota.setMinWidth(Label.USE_PREF_SIZE);
        HBox fila = new HBox(10, dorsal, textos);
        fila.setAlignment(Pos.CENTER_LEFT);
        if (puedeVotar && abierta) {
            fila.getChildren().add(controlVoto(j));
        }
        fila.getChildren().add(nota);
        fila.getStyleClass().addAll("fila-calificacion", b(j, "mvp") ? "fila-mvp" : "fila-normal");
        Tooltip.install(fila, new Tooltip(desglose(j)));
        return fila;
    }

    /** Selector del 1 al 10 y botón «Votar»; si este dispositivo ya votó, muestra su voto. */
    private Node controlVoto(Map<String, Object> j) {
        if (n(j, "miVoto") > 0) {
            Label ya = etiqueta("Su voto: " + n(j, "miVoto"), "chip", "chip-voto");
            ya.setMinWidth(Label.USE_PREF_SIZE);
            return ya;
        }
        ComboBox<Integer> valor = new ComboBox<>(FXCollections.observableArrayList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        valor.setPromptText("1–10");
        valor.setPrefWidth(84);
        valor.setAccessibleText("Calificación para " + s(j, "nombre"));
        Button votar = boton("Votar", "mini");
        votar.disableProperty().bind(valor.valueProperty().isNull());
        votar.setOnAction(e -> {
            Map<String, Object> r = votante.votar(n(j, "id"), valor.getValue());
            if (b(r, "ok")) {
                aviso = "¡Gracias! Su calificación de " + valor.getValue() + " a " + s(j, "nombre") + " quedó registrada.";
                avisoEsError = false;
                dibujar(Datos.m(r, "calificaciones"));
            } else {
                aviso = s(r, "error");
                avisoEsError = true;
                caja.getChildren().removeIf(nodo -> nodo.getStyleClass().contains("error"));
                Label error = etiqueta(aviso, "error");
                error.setWrapText(true);
                caja.getChildren().add(1, error);
            }
        });
        HBox control = new HBox(6, valor, votar);
        control.setAlignment(Pos.CENTER_RIGHT);
        control.setMinWidth(Label.USE_PREF_SIZE);
        return control;
    }

    private static Node leyenda() {
        Label l = etiqueta("Nota del sistema: 6.0 por jugar, +1.0 por gol, +0.5 por asistencia, −0.5 por amarilla, −2.0 por roja; "
                + "el portero suma +1.5 con la valla invicta y resta 0.3 por gol recibido (entre 1.0 y 10.0). "
                + "Con votos del público, la nota final es el promedio de las dos. Pase el mouse por un jugador para ver su desglose.",
                "sutil", "pequeno");
        l.setWrapText(true);
        return l;
    }

    /* ---------------------------------------------------------------- ayudas */

    /** Verde desde 7.0, rojo por debajo de 5.0 y neutra en medio (igual que en la web). */
    static String claseNota(double nota) {
        return nota >= 7.0 ? "nota-alta" : nota < 5.0 ? "nota-baja" : "nota-media";
    }

    private static String desglose(Map<String, Object> j) {
        StringBuilder t = new StringBuilder("#" + n(j, "dorsal") + " " + s(j, "nombre") + "\n");
        for (Object linea : lista(j, "desglose")) {
            t.append("  ").append(linea).append('\n');
        }
        t.append("Nota del sistema: ").append(texto(numero(j, "notaSistema"))).append('\n');
        if (n(j, "votos") > 0) {
            t.append("Promedio del público: ").append(texto(numero(j, "promedioPublico"))).append(" (").append(n(j, "votos"))
                    .append(n(j, "votos") == 1 ? " voto)\n" : " votos)\n");
            t.append("Nota final = (sistema + público) / 2 = ").append(texto(numero(j, "notaFinal")));
        } else {
            t.append("Sin votos del público: la nota final es la del sistema.");
        }
        return t.toString();
    }

    /** «2 G · 1 A · 1 TA» (goles, asistencias, tarjetas). */
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

    private static long numeroLargo(Object v) {
        return v instanceof Number x ? x.longValue() : 0;
    }

    private static String texto(double nota) {
        return String.format(Locale.ROOT, "%.1f", nota);
    }

    private static String hora(long ms) {
        return ms <= 0 ? "" : HORA.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()));
    }

    /** «quedan 23 h 5 min» */
    private static String restante(long cierra) {
        long minutos = Math.max(0, (cierra - System.currentTimeMillis()) / 60000);
        long horas = minutos / 60;
        return "quedan " + (horas > 0 ? horas + " h " + (minutos % 60) + " min" : minutos + " min");
    }
}