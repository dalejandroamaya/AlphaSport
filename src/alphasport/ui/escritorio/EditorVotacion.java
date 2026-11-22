package alphasport.ui.escritorio;

import static alphasport.ui.escritorio.Aplicacion.boton;
import static alphasport.ui.escritorio.Aplicacion.etiqueta;
import static alphasport.ui.escritorio.Datos.b;
import static alphasport.ui.escritorio.Datos.l;
import static alphasport.ui.escritorio.Datos.m;
import static alphasport.ui.escritorio.Datos.mapa;
import static alphasport.ui.escritorio.Datos.n;
import static alphasport.ui.escritorio.Datos.s;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * [REQ-07] Grupo «Votación del público» del editor del partido (administrador): cuántas horas dura (12, 24, 48 o
 * las que escriba), contadas desde el final del partido, y un botón para cerrarla antes de tiempo. Las horas se
 * guardan con el partido; el cierre es inmediato.
 */
final class EditorVotacion {

    private static final String OTRO = "Otro tiempo…";

    private final Aplicacion app;
    private final int idPartido;
    private final List<Integer> opciones = new ArrayList<>();
    private final int minimo;
    private final int maximo;
    private final ComboBox<String> tiempo = new ComboBox<>();
    private final TextField horas = new TextField();
    private final Label estado = etiqueta("", "pequeno");
    private final Button cerrar = boton("Cerrar votación ahora", "mini", "peligro");
    private final VBox nodo;

    EditorVotacion(Aplicacion app, Map<String, Object> p) {
        this.app = app;
        idPartido = n(p, "id");
        Map<String, Object> reglas = m(app.catalogo, "votacion");
        Datos.lista(reglas, "opciones").forEach(o -> opciones.add(((Number) o).intValue()));
        minimo = n(reglas, "minimo");
        maximo = n(reglas, "maximo");
        List<String> textos = new ArrayList<>();
        opciones.forEach(h -> textos.add(h + " horas"));
        textos.add(OTRO);
        tiempo.setItems(FXCollections.observableArrayList(textos));
        tiempo.setAccessibleText("Tiempo de votación");
        horas.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().matches("\\d{0,3}") ? c : null));
        horas.setPrefWidth(80);
        horas.setAccessibleText("Horas de votación");
        int actuales = n(m(p, "votacion"), "horas");
        int indice = opciones.indexOf(actuales);
        tiempo.getSelectionModel().select(indice >= 0 ? indice : opciones.size());
        horas.setText(String.valueOf(actuales));
        HBox campoHoras = new HBox(8, horas, etiqueta("horas (de " + minimo + " a " + maximo + ")", "sutil", "pequeno"));
        campoHoras.setAlignment(Pos.CENTER_LEFT);
        Runnable mostrarHoras = () -> {
            boolean otro = OTRO.equals(tiempo.getValue());
            campoHoras.setVisible(otro);
            campoHoras.setManaged(otro);
        };
        tiempo.setOnAction(e -> {
            mostrarHoras.run();
            if (OTRO.equals(tiempo.getValue())) {
                horas.requestFocus();
                horas.selectAll();
            }
        });
        mostrarHoras.run();
        cerrar.setMinWidth(Region.USE_PREF_SIZE);
        cerrar.setOnAction(e -> cerrarAhora());
        estado.setWrapText(true);
        HBox fila = new HBox(12, tiempo, campoHoras, Aplicacion.espacio(), cerrar);
        fila.setAlignment(Pos.CENTER_LEFT);
        Label ayuda = etiqueta("Tiempo que tienen los visitantes para calificar a los jugadores, contado desde el final del partido.", "sutil", "pequeno");
        ayuda.setWrapText(true);
        nodo = new VBox(10, etiqueta("Votación del público", "sobretitulo"), ayuda, fila, estado);
        nodo.getStyleClass().add("grupo");
        dibujarEstado(p);
        // Sin votación posible (W.O. o partido anterior a las votaciones) solo se explica por qué.
        boolean sinVotacion = b(p, "jugado") && (TiempoVotacion.esWalkover(p) || TiempoVotacion.cierraEn(m(p, "votacion")) == 0);
        for (Node x : List.of(ayuda, fila)) {
            x.setVisible(!sinVotacion);
            x.setManaged(!sinVotacion);
        }
    }

    Node nodo() {
        return nodo;
    }

    /** Horas elegidas; null si lo escrito no sirve (ver {@link #error}). */
    Integer horas() {
        int i = tiempo.getSelectionModel().getSelectedIndex();
        if (i >= 0 && i < opciones.size()) {
            return opciones.get(i);
        }
        if (horas.getText().isBlank()) {
            return null;
        }
        int escritas = Integer.parseInt(horas.getText());
        return escritas < minimo || escritas > maximo ? null : escritas;
    }

    String error() {
        horas.requestFocus();
        return "El tiempo de votación debe estar entre " + minimo + " y " + maximo + " horas.";
    }

    private void dibujarEstado(Map<String, Object> p) {
        Map<String, Object> v = m(p, "votacion");
        long cierra = TiempoVotacion.cierraEn(v);
        boolean abierta = b(v, "abierta");
        String votos = n(v, "votos") == 1 ? "1 voto" : n(v, "votos") + " votos";
        estado.setText(!b(p, "jugado") ? "Se abre cuando el partido quede «Jugado»."
                : TiempoVotacion.esWalkover(p) ? "Partido definido por W.O.: no tiene votación."
                : cierra == 0 ? "Partido jugado antes de existir las votaciones: no tiene votación."
                : abierta ? "Abierta: " + TiempoVotacion.restante(cierra) + " (cierra el " + TiempoVotacion.hora(cierra) + "). Lleva " + votos + "."
                : cierra > System.currentTimeMillis() ? "No se registró quién jugó: no hay a quién calificar."
                : "Cerrada el " + TiempoVotacion.hora(cierra) + " con " + votos + ". Para abrirla de nuevo, elija otro tiempo y guarde.");
        estado.getStyleClass().removeAll("votacion-abierta", "sutil");
        estado.getStyleClass().add(abierta ? "votacion-abierta" : "sutil");
        cerrar.setVisible(abierta);
        cerrar.setManaged(abierta);
    }

    private void cerrarAhora() {
        if (!app.dialogos.confirmar("Cerrar la votación", "Los visitantes ya no podrán calificar este partido y las notas finales quedarán definitivas. "
                + "¿Cerrarla ahora?", "Cerrar votación")) {
            return;
        }
        Map<String, Object> r = app.ejecutar("votacion/cerrar", mapa("partido", idPartido));
        app.aviso(b(r, "ok") ? s(r, "mensaje") : s(r, "error"));
        l(app.E, "partidos").stream().filter(x -> n(x, "id") == idPartido).findFirst().ifPresent(this::dibujarEstado);
    }
}
