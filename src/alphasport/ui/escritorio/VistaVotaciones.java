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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * [REQ-07] Sección «Votaciones» del visitante: los partidos terminados con la votación abierta. En cada uno se
 * elige la nota (1 a 10) de los jugadores que se quiera y se envían todas juntas; un jugador ya calificado desde
 * este equipo muestra «Su voto». Cuando vence el tiempo el partido sale solo de la lista. No muestra notas, para
 * no influir en el voto: la nota final está en «Calificaciones», en el detalle del partido.
 */
final class VistaVotaciones {

    private static final String SIN_NOTA = "–";

    private final Aplicacion app;
    /** Notas elegidas que todavía no se envían (partido -> jugador -> nota): se conservan al redibujar. */
    private final Map<Integer, Map<Integer, Integer>> elegidas = new HashMap<>();
    private final Set<Integer> desplegados = new HashSet<>();
    private final Map<Integer, String> errores = new HashMap<>();
    /** Etiquetas «quedan…» en pantalla y a qué hora cierra cada una. */
    private final Map<Label, Long> relojes = new LinkedHashMap<>();
    private boolean primeraVez = true;

    VistaVotaciones(Aplicacion app) {
        this.app = app;
    }

    Node dibujar() {
        relojes.clear();
        VBox caja = new VBox(14);
        List<Map<String, Object>> votaciones = l(app.api.consultar("votaciones/" + Dispositivo.id()), "votaciones");
        if (votaciones.isEmpty()) {
            Label vacio = etiqueta("No hay votaciones abiertas. Cuando termine un partido aparecerá aquí para que califique a los jugadores.",
                    "vacio", "vidrio");
            vacio.setMaxWidth(Double.MAX_VALUE);
            vacio.setWrapText(true);
            caja.getChildren().add(vacio);
            return caja;
        }
        if (primeraVez && votaciones.size() == 1) {
            desplegados.add(n(m(votaciones.get(0), "partido"), "id")); // con un solo partido no hace falta abrirlo
        }
        primeraVez = false;
        for (Map<String, Object> v : votaciones) {
            caja.getChildren().add(tarjeta(m(v, "partido"), m(v, "calificaciones")));
        }
        // Mientras la sección está en pantalla, el tiempo que queda avanza y los partidos vencidos salen de la lista.
        Timeline reloj = new Timeline(new KeyFrame(Duration.seconds(15), e -> actualizarTiempo()));
        reloj.setCycleCount(Animation.INDEFINITE);
        caja.sceneProperty().addListener((o, antes, escena) -> {
            if (escena == null) {
                reloj.stop();
            } else {
                reloj.play();
            }
        });
        return caja;
    }

    private void actualizarTiempo() {
        long ahora = System.currentTimeMillis();
        if (relojes.values().stream().anyMatch(cierra -> cierra <= ahora)) {
            app.refrescar();
            return;
        }
        relojes.forEach((etiqueta, cierra) -> etiqueta.setText(TiempoVotacion.restante(cierra)));
    }

    /* ---------------------------------------------------------------- un partido */

    private Node tarjeta(Map<String, Object> p, Map<String, Object> calificaciones) {
        int id = n(p, "id");
        List<Map<String, Object>> local = l(calificaciones, "local"), visitante = l(calificaciones, "visitante");
        int total = local.size() + visitante.size();
        long votados = java.util.stream.Stream.concat(local.stream(), visitante.stream()).filter(j -> n(j, "miVoto") > 0).count();
        boolean completo = votados == total;

        Label marcador = etiqueta(s(p, "localNombre") + "  " + n(p, "gl") + " - " + n(p, "gv") + "  " + s(p, "visitanteNombre"), "h3");
        marcador.setWrapText(true);
        Label avance = etiqueta(completo ? "Ya calificó a todos los jugadores" : votados + " de " + total + " jugadores calificados", "sutil", "pequeno");
        VBox textos = new VBox(2, etiqueta(app.contexto(p), "sutil", "pequeno"), marcador, avance);
        HBox.setHgrow(textos, Priority.ALWAYS);
        Label tiempo = etiqueta(TiempoVotacion.restante(TiempoVotacion.cierraEn(m(p, "votacion"))), "chip", "chip-votacion");
        tiempo.setMinWidth(Region.USE_PREF_SIZE);
        relojes.put(tiempo, TiempoVotacion.cierraEn(m(p, "votacion")));
        boolean desplegado = desplegados.contains(id);
        Button abrir = boton(desplegado ? "Ocultar" : completo ? "Ver mis votos" : "Calificar", "mini", desplegado || completo ? "fantasma" : "primario");
        abrir.setMinWidth(Region.USE_PREF_SIZE);
        abrir.setOnAction(e -> {
            if (!desplegados.remove(id)) {
                desplegados.add(id);
            }
            app.mostrarVista("votaciones");
        });
        HBox cabecera = new HBox(14, textos, tiempo, abrir);
        cabecera.setAlignment(Pos.CENTER_LEFT);
        VBox tarjeta = new VBox(14, cabecera);
        tarjeta.getStyleClass().addAll("vidrio", "tarjeta-votacion");
        if (desplegado) {
            tarjeta.getChildren().addAll(jugadores(p, local, visitante));
            if (!completo) {
                tarjeta.getChildren().add(pie(p));
            }
        }
        return tarjeta;
    }

    private Node jugadores(Map<String, Object> p, List<Map<String, Object>> local, List<Map<String, Object>> visitante) {
        HBox columnas = new HBox(18, columna(p, s(p, "localNombre"), local), columna(p, s(p, "visitanteNombre"), visitante));
        columnas.setFillHeight(false);
        return columnas;
    }

    private Node columna(Map<String, Object> p, String equipo, List<Map<String, Object>> jugadores) {
        VBox col = new VBox(6, etiqueta(equipo, "campo-titulo"));
        if (jugadores.isEmpty()) {
            col.getChildren().add(etiqueta("No se registró quién jugó por este equipo.", "sutil"));
        }
        for (Map<String, Object> j : jugadores) {
            col.getChildren().add(fila(p, j));
        }
        col.setPrefWidth(1);
        col.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(col, Priority.ALWAYS);
        return col;
    }

    private Node fila(Map<String, Object> p, Map<String, Object> j) {
        Label nombre = etiqueta("#" + n(j, "dorsal") + " " + s(j, "nombre"), "jugador-texto");
        Label detalle = etiqueta(s(j, "posicion") + (b(j, "titular") ? "" : " · entró desde el banco"), "sutil", "pequeno");
        VBox textos = new VBox(1, nombre, detalle);
        HBox.setHgrow(textos, Priority.ALWAYS);
        HBox fila = new HBox(10, Fotos.cedula(s(j, "foto"), 30), textos, control(p, j));
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().addAll("fila-calificacion", n(j, "miVoto") > 0 ? "fila-votada" : "fila-normal");
        return fila;
    }

    /** La nota que se elige (1 a 10) o, si este equipo ya votó por el jugador, su voto. */
    private Node control(Map<String, Object> p, Map<String, Object> j) {
        if (n(j, "miVoto") > 0) {
            Label ya = etiqueta("Su voto: " + n(j, "miVoto"), "chip", "chip-voto");
            ya.setMinWidth(Region.USE_PREF_SIZE);
            return ya;
        }
        int idPartido = n(p, "id"), idJugador = n(j, "id");
        List<String> notas = new ArrayList<>(List.of(SIN_NOTA));
        for (int nota = 1; nota <= 10; nota++) {
            notas.add(String.valueOf(nota));
        }
        ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList(notas));
        Integer elegida = elegidas.getOrDefault(idPartido, Map.of()).get(idJugador);
        combo.getSelectionModel().select(elegida == null ? SIN_NOTA : elegida.toString());
        combo.setMinWidth(78);
        combo.setPrefWidth(78);
        combo.setVisibleRowCount(11);
        combo.setAccessibleText("Nota del 1 al 10 para " + s(j, "nombre"));
        combo.setOnAction(e -> {
            Map<Integer, Integer> delPartido = elegidas.computeIfAbsent(idPartido, k -> new LinkedHashMap<>());
            if (combo.getValue().equals(SIN_NOTA)) {
                delPartido.remove(idJugador);
            } else {
                delPartido.put(idJugador, Integer.valueOf(combo.getValue()));
            }
            Button enviar = (Button) combo.getScene().lookup("#enviar-" + idPartido);
            actualizarEnviar(enviar, idPartido);
        });
        return combo;
    }

    /* ---------------------------------------------------------------- enviar */

    private Node pie(Map<String, Object> p) {
        int id = n(p, "id");
        Label ayuda = etiqueta("Elija la nota de los jugadores que quiera calificar. Cada jugador se califica una sola vez.", "sutil", "pequeno");
        ayuda.setWrapText(true);
        HBox.setHgrow(ayuda, Priority.ALWAYS);
        ayuda.setMaxWidth(Double.MAX_VALUE);
        Button enviar = boton("", "primario");
        enviar.setId("enviar-" + id);
        enviar.setMinWidth(Region.USE_PREF_SIZE);
        actualizarEnviar(enviar, id);
        enviar.setOnAction(e -> enviar(id));
        HBox fila = new HBox(14, ayuda, enviar);
        fila.setAlignment(Pos.CENTER_LEFT);
        VBox pie = new VBox(8);
        if (errores.containsKey(id)) {
            Label error = etiqueta(errores.remove(id), "error");
            error.setWrapText(true);
            pie.getChildren().add(error);
        }
        pie.getChildren().add(fila);
        return pie;
    }

    private void actualizarEnviar(Button enviar, int idPartido) {
        int cuantas = elegidas.getOrDefault(idPartido, Map.of()).size();
        enviar.setText(cuantas == 0 ? "Enviar calificaciones" : cuantas == 1 ? "Enviar 1 calificación" : "Enviar " + cuantas + " calificaciones");
        enviar.setDisable(cuantas == 0);
    }

    private void enviar(int idPartido) {
        List<Map<String, Object>> votos = new ArrayList<>();
        elegidas.getOrDefault(idPartido, Map.of()).forEach((jugador, nota) -> votos.add(mapa("jugador", jugador, "valor", nota)));
        Map<String, Object> r = app.ejecutar("calificacion/votar", mapa("partido", idPartido, "dispositivo", Dispositivo.id(), "votos", votos));
        if (b(r, "ok")) {
            elegidas.remove(idPartido);
            app.aviso(s(r, "mensaje"));
        } else {
            errores.put(idPartido, s(r, "error"));
            app.aviso(s(r, "error")); // si la votación cerró, el partido ya no está en la lista para mostrarlo
        }
        app.refrescar();
    }
}
