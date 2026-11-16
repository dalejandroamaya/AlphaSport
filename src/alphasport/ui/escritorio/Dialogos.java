package alphasport.ui.escritorio;

import static alphasport.ui.escritorio.Aplicacion.boton;
import static alphasport.ui.escritorio.Aplicacion.etiqueta;
import static alphasport.ui.escritorio.Datos.b;
import static alphasport.ui.escritorio.Datos.l;
import static alphasport.ui.escritorio.Datos.m;
import static alphasport.ui.escritorio.Datos.mapa;
import static alphasport.ui.escritorio.Datos.n;
import static alphasport.ui.escritorio.Datos.s;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.scene.Group;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * Ventanas emergentes de la version de escritorio.
 */
class Dialogos {

    private final Aplicacion app;

    Dialogos(Aplicacion app) {
        this.app = app;
    }

    /* ---------------------------------------------------------------- base */

    /*
     * Ventana suave: sin el marco de Windows, con esquinas redondeadas, sombra, borde de luz y entrada animada.
     * Se arrastra desde cualquier zona libre y se cierra con Esc.
     */
    private Stage ventana(String titulo, Node contenido, double ancho) {
        return ventana(titulo, contenido, ancho, "dialogo");
    }

    private Stage ventana(String titulo, Node contenido, double ancho, String clase) {
        Stage v = new Stage(StageStyle.TRANSPARENT);
        v.initOwner(app.stage);
        v.initModality(Modality.APPLICATION_MODAL);
        v.setTitle(titulo);
        ScrollPane desplazable = new ScrollPane(contenido);
        desplazable.setFitToWidth(true);
        desplazable.getStyleClass().add("desplazable");
        StackPane panel = new StackPane(desplazable);
        panel.getStyleClass().addAll("dialogo-panel", clase);
        StackPane raiz = new StackPane(panel);
        raiz.getStyleClass().addAll("raiz", "dialogo-sombra");
        if (app.claro) {
            raiz.getStyleClass().add("claro");
        }
        raiz.setPadding(new Insets(24));
        Scene escena = new Scene(raiz, ancho + 48, -1);
        escena.setFill(Color.TRANSPARENT);
        escena.getStylesheets().add(app.hojaEstilos());
        // Alto según el ancho real: así los textos largos (que se parten en varias líneas) no dejan los botones fuera.
        raiz.applyCss();
        desplazable.setPrefViewportHeight(contenido.prefHeight(ancho - 4));
        escena.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                v.close();
            }
        });
        double[] origen = new double[2];
        raiz.setOnMousePressed(e -> { origen[0] = e.getScreenX() - v.getX(); origen[1] = e.getScreenY() - v.getY(); });
        raiz.setOnMouseDragged(e -> { v.setX(e.getScreenX() - origen[0]); v.setY(e.getScreenY() - origen[1]); });
        v.setScene(escena);
        v.getIcons().add(Aplicacion.imagen("logo.png"));
        // Nunca más alto que la pantalla: si el contenido no cabe (por ejemplo la cancha del detalle del partido),
        // se desplaza dentro de la ventana. Antes la ventana medía más que la pantalla, «Cerrar» quedaba fuera
        // y, como la ventana es modal, el programa parecía congelado.
        double altoPantalla = javafx.stage.Screen.getPrimary().getVisualBounds().getHeight();
        double maximoVisor = Math.max(240, altoPantalla - 48 - 2 * 24 - 40);
        desplazable.setPrefViewportHeight(Math.min(desplazable.getPrefViewportHeight(), maximoVisor));
        v.setMaxHeight(Math.min(app.stage.getHeight() - 20, altoPantalla));
        v.setOnShown(e -> {
            v.setX(app.stage.getX() + (app.stage.getWidth() - v.getWidth()) / 2);
            v.setY(app.stage.getY() + Math.max(10, (app.stage.getHeight() - v.getHeight()) / 2));
            panel.setScaleX(.97);
            panel.setScaleY(.97);
            panel.setOpacity(0);
            javafx.animation.ScaleTransition crecer = new javafx.animation.ScaleTransition(Duration.millis(380), panel);
            crecer.setToX(1);
            crecer.setToY(1);
            FadeTransition aparecer = new FadeTransition(Duration.millis(320), panel);
            aparecer.setToValue(1);
            javafx.animation.ParallelTransition t = new javafx.animation.ParallelTransition(crecer, aparecer);
            t.setInterpolator(Interpolator.SPLINE(.22, 1, .36, 1));
            crecer.setInterpolator(Interpolator.SPLINE(.22, 1, .36, 1));
            t.play();
        });
        return v;
    }

    /** Trofeo dibujado con trazos SVG y degradado dorado (JavaFX no carga archivos SVG). */
    static Group trofeo(double escala) {
        Paint oro = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#fff0b8")), new Stop(.35, Color.web("#f6c453")), new Stop(.75, Color.web("#d9a02a")), new Stop(1, Color.web("#9c6a0c")));
        SVGPath asaIzq = new SVGPath();
        asaIzq.setContent("M32 26H17c-4.5 0-7 3.4-7 7.5C10 50 22 60 35 62");
        SVGPath asaDer = new SVGPath();
        asaDer.setContent("M88 26h15c4.5 0 7 3.4 7 7.5C110 50 98 60 85 62");
        for (SVGPath asa : List.of(asaIzq, asaDer)) {
            asa.setFill(Color.TRANSPARENT);
            asa.setStroke(oro);
            asa.setStrokeWidth(7);
            asa.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        }
        SVGPath copa = new SVGPath();
        copa.setContent("M28 12h64v30c0 26-14 43-32 45-18-2-32-19-32-45z");
        copa.setFill(oro);
        SVGPath brillo = new SVGPath();
        brillo.setContent("M36 18v22c0 16 6 28 14 34");
        brillo.setFill(Color.TRANSPARENT);
        brillo.setStroke(Color.rgb(255, 255, 255, .45));
        brillo.setStrokeWidth(4);
        brillo.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        SVGPath estrella = new SVGPath();
        estrella.setContent("M60 32l3.5 9.2 9.8.5-7.6 6.2 2.5 9.5L60 52l-8.2 5.4 2.5-9.5-7.6-6.2 9.8-.5z");
        estrella.setFill(Color.web("#fff6d6"));
        SVGPath pie = new SVGPath();
        pie.setContent("M53 86h14v16H53zM42 100h36v7H42z");
        pie.setFill(oro);
        SVGPath base = new SVGPath();
        base.setContent("M34 107h52l6 23H28z");
        base.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE, new Stop(0, Color.web("#2a3443")), new Stop(1, Color.web("#121821"))));
        SVGPath placa = new SVGPath();
        placa.setContent("M46 113h28v9H46z");
        placa.setFill(oro);
        Group g = new Group(asaIzq, asaDer, copa, brillo, estrella, pie, base, placa);
        g.setScaleX(escala);
        g.setScaleY(escala);
        g.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, .5), 18, 0, 0, 10));
        return new Group(g);
    }

    private static VBox cuerpo(String titulo, Node... nodos) {
        VBox v = new VBox(14, etiqueta(titulo, "h3"));
        v.getChildren().addAll(nodos);
        v.setPadding(new Insets(24));
        return v;
    }

    private static HBox acciones(Node... botones) {
        HBox h = new HBox(10, Aplicacion.espacio());
        h.getChildren().addAll(botones);
        h.setAlignment(Pos.CENTER_RIGHT);
        return h;
    }

    void mensaje(String titulo, String texto) {
        Button aceptar = boton("Aceptar", "primario");
        aceptar.setDefaultButton(true);
        Stage v = ventana(titulo, cuerpo(titulo, etiqueta(texto, "msg-texto"), acciones(aceptar)), 480);
        aceptar.setOnAction(e -> v.close());
        v.showAndWait();
    }

    boolean confirmar(String titulo, String texto, String textoSi) {
        boolean[] respuesta = {false};
        Button no = boton("Cancelar", "fantasma");
        Button si = boton(textoSi, "primario");
        si.setDefaultButton(true);
        Stage v = ventana(titulo, cuerpo(titulo, etiqueta(texto, "msg-texto"), acciones(no, si)), 480);
        no.setOnAction(e -> v.close());
        si.setOnAction(e -> {
            respuesta[0] = true;
            v.close();
        });
        v.showAndWait();
        return respuesta[0];
    }

    /**
     * Advertencia con dos caminos. Devuelve {@code "si"} (botón principal), {@code "no"} (botón
     * secundario) o {@code null} si se cerró sin elegir (Esc o la X).
     */
    String elegir(String titulo, String texto, String textoNo, String textoSi) {
        String[] respuesta = {null};
        Button no = boton(textoNo, "fantasma");
        Button si = boton(textoSi, "primario");
        si.setDefaultButton(true);
        Label mensajeTexto = etiqueta(texto, "msg-texto");
        mensajeTexto.setWrapText(true);
        Label icono = etiqueta("!", "icono-advertencia");
        HBox fila = new HBox(14, icono, mensajeTexto);
        fila.setAlignment(Pos.TOP_LEFT);
        Stage v = ventana(titulo, cuerpo(titulo, fila, acciones(no, si)), 520);
        no.setOnAction(e -> {
            respuesta[0] = "no";
            v.close();
        });
        si.setOnAction(e -> {
            respuesta[0] = "si";
            v.close();
        });
        v.showAndWait();
        return respuesta[0];
    }

    String pedirTexto(String titulo, String texto, String inicial) {
        String[] respuesta = {null};
        TextField entrada = new TextField(inicial);
        Button no = boton("Cancelar", "fantasma");
        Button si = boton("Aceptar", "primario");
        si.setDefaultButton(true);
        Stage v = ventana(titulo, cuerpo(titulo, etiqueta(texto, "msg-texto"), entrada, acciones(no, si)), 480);
        no.setOnAction(e -> v.close());
        si.setOnAction(e -> {
            respuesta[0] = entrada.getText().trim();
            v.close();
        });
        v.showAndWait();
        return respuesta[0];
    }

    void verCampeon() {
        Map<String, Object> f = m(app.E, "campeonFicha");
        if (f == null) {
            mensaje("Campeón", "El campeonato aún no termina.\n" + s(m(app.E, "campeonato"), "estado"));
            return;
        }
        // Coronación: trofeo bajo tres focos dorados, el nombre del campeón y sus cifras en tableros.
        Group copa = trofeo(1.1);
        StackPane escena = new StackPane(rayosDorados(), copa);
        escena.setPrefHeight(190);
        Label nombre = etiqueta(s(f, "nombre"), "campeon-titulo");
        Label torneo = etiqueta("Campeón de " + s(m(app.E, "campeonato"), "nombre"), "campeon-torneo");
        Label dt = etiqueta("Director técnico: " + s(f, "dt"), "campeon-dt");
        HBox cifras = new HBox(8);
        String[][] datos = {{"pj", "Jugados"}, {"g", "Ganados"}, {"e", "Empates"}, {"p", "Perdidos"}, {"gf", "A favor"}, {"gc", "En contra"}};
        for (String[] d : datos) {
            VBox tablero = new VBox(0, etiqueta(String.valueOf(n(f, d[0])), "campeon-cifra"), etiqueta(d[1], "campeon-cifra-texto"));
            tablero.setAlignment(Pos.CENTER);
            tablero.getStyleClass().add("campeon-tablero");
            HBox.setHgrow(tablero, Priority.ALWAYS);
            tablero.setMaxWidth(Double.MAX_VALUE);
            cifras.getChildren().add(tablero);
        }
        Button tabla = boton("Ver la tabla final", "fantasma", "sobre-foto");
        Button cerrar = boton("Celebrar y cerrar", "dorado");
        cerrar.setDefaultButton(true);
        HBox botones = new HBox(10, tabla, cerrar);
        botones.setAlignment(Pos.CENTER);
        // Liga con divisiones: el campeón de cada división por separado (Liga A, Liga B, Liga C...).
        List<Map<String, Object>> porDivision = l(app.E, "campeonesDivision");
        VBox divisiones = new VBox(6);
        if (porDivision.size() > 1) {
            torneo.setText("Campeón de la " + s(porDivision.get(0), "division") + " · " + s(m(app.E, "campeonato"), "nombre"));
            divisiones.getChildren().add(etiqueta("Campeones por división", "campeon-cifra-texto"));
            for (Map<String, Object> c : porDivision) {
                Label division = etiqueta(s(c, "division"), "campeon-cifra-texto");
                division.setMinWidth(70);
                VBox datosCampeon = new VBox(0, etiqueta(s(c, "nombre"), "campeon-division-nombre"),
                        etiqueta("DT: " + s(c, "dt"), "campeon-cifra-texto"));
                Label resumen = etiqueta(n(c, "pts") + " pts · " + n(c, "pj") + " PJ · " + n(c, "g") + "G " + n(c, "e") + "E " + n(c, "p") + "P",
                        "campeon-cifra-texto");
                HBox fila = new HBox(12, division, datosCampeon, Aplicacion.espacio(), resumen);
                fila.setAlignment(Pos.CENTER_LEFT);
                fila.getStyleClass().add("campeon-tablero");
                fila.setPadding(new Insets(8, 14, 8, 14));
                divisiones.getChildren().add(fila);
            }
        }
        VBox cuerpo = new VBox(6, escena, nombre, torneo, dt, new Region(), cifras, new Region(), botones);
        if (!divisiones.getChildren().isEmpty()) {
            cuerpo.getChildren().add(7, divisiones); // antes de los botones
        }
        cuerpo.setAlignment(Pos.CENTER);
        cuerpo.setPadding(new Insets(28, 26, 24, 26));
        VBox.setVgrow(cuerpo.getChildren().get(4), Priority.NEVER);
        ((Region) cuerpo.getChildren().get(4)).setMinHeight(10);
        ((Region) cuerpo.getChildren().get(6)).setMinHeight(12);
        Stage v = ventana("Campeón del torneo", cuerpo, 640, "dialogo-campeon");
        StackPane panel = (StackPane) ((StackPane) v.getScene().getRoot()).getChildren().get(0);
        Region foto = new Region();
        foto.getStyleClass().add("foto-campeon");
        foto.setStyle("-fx-background-image: url('" + alphasport.ui.Arranque.CARPETA_WEB.resolve("img").resolve("estadio-noche.jpg").toUri()
                + "'); -fx-background-size: cover; -fx-background-position: center 60%;");
        Region velo = new Region();
        velo.getStyleClass().add("velo-campeon");
        panel.getChildren().addAll(0, List.of(foto, velo));
        javafx.scene.shape.Rectangle recorte = new javafx.scene.shape.Rectangle();
        recorte.widthProperty().bind(panel.widthProperty());
        recorte.heightProperty().bind(panel.heightProperty());
        recorte.setArcWidth(56);
        recorte.setArcHeight(56);
        panel.setClip(recorte);
        // El trofeo se alza y los focos de la ventana principal se vuelven dorados mientras dura la celebración.
        copa.setTranslateY(36);
        copa.setOpacity(0);
        TranslateTransition alzar = new TranslateTransition(Duration.millis(1100), copa);
        alzar.setToY(0);
        alzar.setDelay(Duration.millis(150));
        alzar.setInterpolator(Interpolator.SPLINE(.22, 1, .36, 1));
        FadeTransition aparecer = new FadeTransition(Duration.millis(700), copa);
        aparecer.setToValue(1);
        aparecer.setDelay(Duration.millis(150));
        v.setOnShowing(e -> { alzar.play(); aparecer.play(); app.coronacion(true); });
        v.setOnHidden(e -> app.coronacion(false));
        tabla.setOnAction(e -> { v.close(); app.mostrarVista("posiciones"); });
        cerrar.setOnAction(e -> v.close());
        v.showAndWait();
    }

    private static Pane rayosDorados() {
        Pane capa = new Pane();
        capa.setMouseTransparent(true);
        double[][] focos = {{0.18, -14}, {0.5, 0}, {0.82, 14}};
        for (double[] fo : focos) {
            SVGPath h = new SVGPath();
            h.setContent("M-14 0 L14 0 L120 260 L-120 260 Z");
            h.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE, new Stop(0, Color.rgb(255, 214, 120, .55)), new Stop(1, Color.TRANSPARENT)));
            h.setEffect(new javafx.scene.effect.GaussianBlur(10));
            h.setBlendMode(javafx.scene.effect.BlendMode.SCREEN);
            h.layoutXProperty().bind(capa.widthProperty().multiply(fo[0]));
            h.setLayoutY(-40);
            h.setRotate(fo[1]);
            capa.getChildren().add(h);
            FadeTransition encender = new FadeTransition(Duration.millis(1400), h);
            encender.setFromValue(0);
            encender.setToValue(1);
            encender.play();
        }
        return capa;
    }

    private static Label error() {
        Label l = etiqueta("", "error");
        l.setVisible(false);
        l.setManaged(false);
        return l;
    }

    private static void mostrarError(Label l, String texto) {
        l.setText(texto == null ? "" : texto);
        boolean hay = texto != null && !texto.isBlank();
        l.setVisible(hay);
        l.setManaged(hay);
    }

    private static VBox campo(String titulo, Node control) {
        VBox v = new VBox(4, etiqueta(titulo, "campo-titulo"), control);
        HBox.setHgrow(v, Priority.ALWAYS);
        if (control instanceof javafx.scene.control.Control c) {
            c.setMaxWidth(Double.MAX_VALUE);
        }
        return v;
    }

    private static VBox grupo(String titulo, Node... nodos) {
        VBox v = new VBox(12, etiqueta(titulo, "sobretitulo"));
        v.getChildren().addAll(nodos);
        v.getStyleClass().add("grupo");
        return v;
    }

    private static Spinner<Integer> numero(int min, int max, int valor) {
        Spinner<Integer> s = new Spinner<>(min, max, Math.max(min, Math.min(max, valor)));
        s.setEditable(true);
        s.setPrefWidth(110);
        return s;
    }

    /** Casilla de goles del marcador LED: solo admite 0 a 99 y puede quedar vacía. */
    private static TextField gol(String valor, String nombreAccesible) {
        TextField t = new TextField(valor);
        t.setTextFormatter(new TextFormatter<String>(cambio -> cambio.getControlNewText().matches("\\d{0,2}") ? cambio : null));
        t.setPromptText("–");
        t.setAccessibleText(nombreAccesible);
        t.getStyleClass().add("led-gol");
        return t;
    }

    /* ---------------------------------------------------------------- criterios */

    static class EditorCriterios extends VBox {

        private final Aplicacion app;
        private final List<String> lista = new ArrayList<>();

        EditorCriterios(Aplicacion app) {
            super(8);
            this.app = app;
        }

        List<String> obtener() {
            return new ArrayList<>(lista);
        }

        void poner(List<String> criterios) {
            lista.clear();
            lista.addAll(criterios);
            dibujar();
        }

        private void dibujar() {
            getChildren().clear();
            for (int i = 0; i < lista.size(); i++) {
                int pos = i;
                Label orden = etiqueta(String.valueOf(i + 1), "orden");
                Label texto = etiqueta(app.nombreCriterio(lista.get(i)), "criterio-texto");
                HBox.setHgrow(texto, Priority.ALWAYS);
                texto.setMaxWidth(Double.MAX_VALUE);
                Button subir = boton("▲", "mini", "fantasma");
                subir.setDisable(i == 0);
                subir.setOnAction(e -> mover(pos, -1));
                Button bajar = boton("▼", "mini", "fantasma");
                bajar.setDisable(i == lista.size() - 1);
                bajar.setOnAction(e -> mover(pos, 1));
                Button quitar = boton("✕", "mini", "peligro");
                quitar.setOnAction(e -> {
                    lista.remove(pos);
                    dibujar();
                });
                HBox fila = new HBox(8, orden, texto, subir, bajar, quitar);
                fila.setAlignment(Pos.CENTER_LEFT);
                fila.getStyleClass().add("criterio");
                getChildren().add(fila);
            }
            List<String> libres = new ArrayList<>();
            for (Map<String, Object> c : l(app.catalogo, "criterios")) {
                if (!lista.contains(s(c, "clave"))) {
                    libres.add(s(c, "clave"));
                }
            }
            if (!libres.isEmpty()) {
                ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList(libres.stream().map(app::nombreCriterio).toList()));
                combo.getSelectionModel().selectFirst();
                Button agregar = boton("Agregar");
                agregar.setOnAction(e -> {
                    lista.add(libres.get(combo.getSelectionModel().getSelectedIndex()));
                    dibujar();
                });
                HBox fila = new HBox(8, combo, agregar);
                HBox.setHgrow(combo, Priority.ALWAYS);
                combo.setMaxWidth(Double.MAX_VALUE);
                getChildren().add(fila);
            }
        }

        private void mover(int i, int dir) {
            String x = lista.get(i);
            lista.set(i, lista.get(i + dir));
            lista.set(i + dir, x);
            dibujar();
        }
    }

    /* ---------------------------------------------------------------- nuevo / cambiar formato */

    void nuevo(boolean obligatorio, boolean cambiar) {
        Map<String, Object> cfg = m(app.catalogo, "configuracion");
        Map<String, Object> actual = m(app.E, "campeonato");
        String[] formato = {cambiar ? s(actual, "formato") : "LIGA"};
        TextField nombre = new TextField(cambiar ? s(actual, "nombre") : "");
        nombre.setPromptText("Ej.: Copa Universitaria 2026");

        Spinner<Integer> victoria = numero(0, 10, n(cfg, "puntosVictoria"));
        Spinner<Integer> empate = numero(0, 10, n(cfg, "puntosEmpate"));
        Spinner<Integer> tamGrupo = numero(3, 8, n(cfg, "tamanoGrupo"));
        Spinner<Integer> clasGrupo = numero(1, 4, n(cfg, "clasificadosPorGrupo"));
        Spinner<Integer> clasPlayoff = numero(2, 16, n(cfg, "clasificadosPlayoff"));
        ComboBox<String> tipoPlayoff = new ComboBox<>(FXCollections.observableArrayList("Llave de eliminación", "Cuadrangulares"));
        tipoPlayoff.getSelectionModel().select(s(cfg, "tipoPlayoff").equals("CUADRANGULARES") ? 1 : 0);
        Spinner<Integer> rondas = numero(1, 20, n(cfg, "rondasSuizo"));
        Spinner<Integer> directos = numero(0, 32, n(cfg, "directosSuizo"));
        Spinner<Integer> repechaje = numero(0, 32, n(cfg, "repechajeSuizo"));
        // Liga con divisiones (Liga A, B...). Al cambiar el formato de una liga se conservan sus divisiones.
        boolean eraLiga = cambiar && b(actual, "esLiga");
        Spinner<Integer> divisiones = numero(1, 6, eraLiga ? Math.max(1, l(actual, "divisiones").size()) : Math.max(1, n(cfg, "divisiones")));
        Spinner<Integer> ascensos = numero(0, 10, eraLiga ? n(actual, "ascensos") : n(cfg, "ascensos"));
        CheckBox idaVuelta = new CheckBox("Liga / grupos a ida y vuelta");
        idaVuelta.setSelected(b(cfg, "idaYVuelta"));
        CheckBox elimIdaVuelta = new CheckBox("Eliminatorias a ida y vuelta");
        elimIdaVuelta.setSelected(b(cfg, "eliminatoriaIdaYVuelta"));
        CheckBox finalUnica = new CheckBox("Final a partido único");
        finalUnica.setSelected(b(cfg, "finalUnica"));
        CheckBox ventaja = new CheckBox("Ventaja deportiva para el mejor de la fase regular");
        ventaja.setSelected(b(cfg, "ventajaDeportiva"));
        CheckBox reinicio = new CheckBox("Gran final con reinicio");
        reinicio.setSelected(b(cfg, "granFinalConReinicio"));
        CheckBox sorteo = new CheckBox("Sorteo aleatorio (si no, por orden de registro)");
        sorteo.setSelected(b(cfg, "sorteoAleatorio"));
        int previos = !obligatorio && !cambiar && b(app.E, "hay")
                ? (int) l(app.E, "equipos").stream().filter(e -> !b(e, "retirado")).count() : 0;
        CheckBox mismos = new CheckBox("Usar los mismos " + previos + " equipos de «" + s(actual, "nombre") + "» (con su DT y jugadores)");
        mismos.setSelected(previos > 0);
        mismos.setVisible(previos > 0);
        mismos.setManaged(previos > 0);
        EditorCriterios criterios = new EditorCriterios(app);
        criterios.poner(Datos.lista(cfg, "criterios").stream().map(String::valueOf).toList());

        Map<Node, String> para = new LinkedHashMap<>();
        String tabla = "LIGA GRUPOS_Y_ELIMINATORIA TORNEO_CORTO SUIZO";
        String elim = "ELIMINACION_DIRECTA GRUPOS_Y_ELIMINATORIA TORNEO_CORTO SUIZO";
        FlowPane campos = new FlowPane(14, 12);
        Object[][] definicion = {{"Puntos por victoria", victoria, tabla}, {"Puntos por empate", empate, tabla},
            {"Equipos por grupo", tamGrupo, "GRUPOS_Y_ELIMINATORIA"}, {"Clasificados por grupo", clasGrupo, "GRUPOS_Y_ELIMINATORIA"},
            {"Clasificados a la liguilla", clasPlayoff, "TORNEO_CORTO"}, {"Tipo de liguilla", tipoPlayoff, "TORNEO_CORTO"},
            {"Rondas del suizo", rondas, "SUIZO"}, {"Clasificados directos", directos, "SUIZO"}, {"Equipos al repechaje", repechaje, "SUIZO"},
            {"Divisiones (Liga A, B…)", divisiones, "LIGA"}, {"Ascensos y descensos por temporada", ascensos, "LIGA"}};
        for (Object[] d : definicion) {
            VBox c = campo((String) d[0], (Node) d[1]);
            c.setPrefWidth(200);
            campos.getChildren().add(c);
            para.put(c, (String) d[2]);
        }
        para.put(idaVuelta, "LIGA GRUPOS_Y_ELIMINATORIA TORNEO_CORTO");
        para.put(elimIdaVuelta, elim);
        para.put(finalUnica, elim);
        para.put(ventaja, "TORNEO_CORTO");
        para.put(reinicio, "DOBLE_ELIMINACION");
        VBox checks = new VBox(8, idaVuelta, elimIdaVuelta, finalUnica, ventaja, reinicio, sorteo, mismos);

        Runnable actualizar = () -> para.forEach((nodo, formatos) -> {
            boolean ver = List.of(formatos.split(" ")).contains(formato[0]) && (nodo != finalUnica || elimIdaVuelta.isSelected());
            nodo.setVisible(ver);
            nodo.setManaged(ver);
        });
        elimIdaVuelta.setOnAction(e -> actualizar.run());

        FlowPane tarjetas = new FlowPane(10, 10);
        ToggleGroup grupoFormato = new ToggleGroup();
        for (Map<String, Object> f : l(app.catalogo, "formatos")) {
            ToggleButton t = new ToggleButton();
            Label nombreFormato = etiqueta(s(f, "nombre"), "formato-nombre");
            Label descripcion = etiqueta(s(f, "descripcion"), "formato-desc");
            nombreFormato.setPrefWidth(250);
            descripcion.setPrefWidth(250);
            VBox contenidoFormato = new VBox(4, nombreFormato, descripcion);
            contenidoFormato.setPrefWidth(250);
            t.setGraphic(contenidoFormato);
            t.setMinHeight(128);
            t.setPrefHeight(128);
            t.setMaxHeight(128);
            t.setAlignment(Pos.TOP_LEFT);
            t.getStyleClass().add("tarjeta-formato");
            t.setToggleGroup(grupoFormato);
            t.setPrefWidth(282);
            t.setSelected(s(f, "clave").equals(formato[0]));
            t.setOnAction(e -> {
                formato[0] = s(f, "clave");
                t.setSelected(true);
                actualizar.run();
            });
            tarjetas.getChildren().add(t);
        }
        actualizar.run();

        Label err = error();
        Button cancelar = boton("Cancelar", "fantasma");
        cancelar.setVisible(!obligatorio);
        cancelar.setManaged(!obligatorio);
        Button crear = boton(cambiar ? "Aplicar nuevo formato" : "Crear campeonato", "primario");
        String titulo = cambiar ? "Cambiar formato del campeonato" : "Nuevo campeonato";
        Stage v = ventana(titulo, cuerpo(titulo, campo("Nombre del campeonato", nombre), etiqueta("Formato de competencia", "campo-titulo"),
                tarjetas, grupo("Reglas del formato", campos, checks), grupo("Criterios de desempate (en orden)", criterios), err,
                acciones(cancelar, crear)), 960);
        if (obligatorio) {
            v.setOnCloseRequest(e -> e.consume());
        }
        cancelar.setOnAction(e -> v.close());
        crear.setOnAction(e -> {
            Map<String, Object> config = mapa("puntosVictoria", victoria.getValue(), "puntosEmpate", empate.getValue(),
                    "idaYVuelta", idaVuelta.isSelected(), "eliminatoriaIdaYVuelta", elimIdaVuelta.isSelected(),
                    "finalUnica", !elimIdaVuelta.isSelected() || finalUnica.isSelected(), "sorteoAleatorio", sorteo.isSelected(),
                    "tamanoGrupo", tamGrupo.getValue(), "clasificadosPorGrupo", clasGrupo.getValue(),
                    "clasificadosPlayoff", clasPlayoff.getValue(),
                    "tipoPlayoff", tipoPlayoff.getSelectionModel().getSelectedIndex() == 1 ? "CUADRANGULARES" : "LLAVE",
                    "ventajaDeportiva", ventaja.isSelected(), "rondasSuizo", rondas.getValue(), "directosSuizo", directos.getValue(),
                    "repechajeSuizo", repechaje.getValue(), "granFinalConReinicio", reinicio.isSelected(),
                    "divisiones", formato[0].equals("LIGA") ? divisiones.getValue() : 1, "ascensos", ascensos.getValue(),
                    "criterios", criterios.obtener());
            Map<String, Object> r = cambiar
                    ? app.ejecutar("cambiarFormato", mapa("nombre", nombre.getText(), "formato", formato[0], "config", config))
                    : app.ejecutar("nuevo", mapa("nombre", nombre.getText(), "formato", formato[0], "config", config,
                            "mismosEquipos", mismos.isVisible() && mismos.isSelected()));
            if (!b(r, "ok")) {
                mostrarError(err, s(r, "error"));
                return;
            }
            v.close();
            app.mostrarVista("equipos");
            app.aviso(cambiar ? "Formato cambiado a «" + s(m(app.E, "campeonato"), "formatoNombre") + "». Se conservaron los equipos."
                    : "Campeonato creado. Ahora registre los equipos.");
        });
        v.showAndWait();
    }

    /* ---------------------------------------------------------------- equipo */

    void equipo(Map<String, Object> equipo) {
        TextField nombre = new TextField(s(equipo, "nombre"));
        TextField estadio = new TextField(s(equipo, "estadio"));
        Map<String, Object> dt = m(equipo, "dt");
        TextField dtNombre = new TextField(s(dt, "nombre"));
        TextField dtDocumento = new TextField(s(dt, "documento"));
        TextField dtNacionalidad = new TextField(s(dt, "nacionalidad"));
        List<Map<String, Object>> jugadores = new ArrayList<>();
        for (Map<String, Object> j : l(equipo, "jugadores")) {
            jugadores.add(new LinkedHashMap<>(j));
        }
        Spinner<Integer> dorsal = numero(1, 99, 1);
        TextField jgNombre = new TextField();
        TextField jgDocumento = new TextField();
        ComboBox<String> posicion = comboPosiciones();
        Label errJugador = error();
        VBox lista = new VBox(6);
        Runnable[] dibujar = new Runnable[1];
        Runnable siguienteDorsal = () -> {
            int d = 1;
            while (d < 99 && tieneDorsal(jugadores, d)) {
                d++;
            }
            dorsal.getValueFactory().setValue(d);
        };
        dibujar[0] = () -> {
            jugadores.sort(Comparator.comparingInt(j -> n(j, "dorsal")));
            lista.getChildren().clear();
            if (jugadores.isEmpty()) {
                lista.getChildren().add(etiqueta("Sin jugadores todavía.", "sutil"));
            }
            for (Map<String, Object> j : jugadores) {
                String tarjetas = (n(j, "amarillas") > 0 ? "Amarillas: " + n(j, "amarillas") + "  " : "") + (n(j, "rojas") > 0 ? "Rojas: " + n(j, "rojas") + "  " : "")
                        + (n(j, "pendientes") > 0 ? "Suspendido " + n(j, "pendientes") : "");
                Label datos = etiqueta(s(j, "nombre") + "   ·   " + s(j, "posicion") + (s(j, "documento").isEmpty() ? "" : "   ·   " + s(j, "documento"))
                        + (tarjetas.isEmpty() ? "" : "   " + tarjetas), "jugador-texto");
                HBox.setHgrow(datos, Priority.ALWAYS);
                datos.setMaxWidth(Double.MAX_VALUE);
                Button quitar = boton("Quitar", "mini", "peligro");
                quitar.setOnAction(e -> {
                    jugadores.remove(j);
                    dibujar[0].run();
                    siguienteDorsal.run();
                });
                HBox fila = new HBox(10, etiqueta(String.valueOf(n(j, "dorsal")), "dorsal"), datos, quitar);
                fila.setAlignment(Pos.CENTER_LEFT);
                fila.getStyleClass().add("fila-lista");
                lista.getChildren().add(fila);
            }
        };
        Button agregar = boton("Agregar");
        Runnable agregarJugador = () -> {
            int d = dorsal.getValue();
            String error = jgNombre.getText().isBlank() ? "Escriba el nombre del jugador."
                    : tieneDorsal(jugadores, d) ? "El dorsal " + d + " ya está en uso." : null;
            mostrarError(errJugador, error);
            if (error != null) {
                return;
            }
            jugadores.add(mapa("id", 0, "nombre", jgNombre.getText().trim(), "documento", jgDocumento.getText().trim(),
                    "dorsal", d, "posicion", posicion.getValue()));
            jgNombre.clear();
            jgDocumento.clear();
            dibujar[0].run();
            siguienteDorsal.run();
            jgNombre.requestFocus();
        };
        agregar.setOnAction(e -> agregarJugador.run());
        jgNombre.setOnAction(e -> agregarJugador.run());
        dibujar[0].run();
        siguienteDorsal.run();

        Label err = error();
        Button cancelar = boton("Cancelar", "fantasma");
        Button guardar = boton("Guardar equipo", "primario");
        HBox filaJugador = new HBox(10, campo("Dorsal", dorsal), campo("Nombre", jgNombre), campo("Documento", jgDocumento),
                campo("Posición", posicion), agregar);
        filaJugador.setAlignment(Pos.BOTTOM_LEFT);
        String titulo = equipo == null ? "Agregar equipo" : "Equipo: " + s(equipo, "nombre");
        Stage v = ventana(titulo, cuerpo(titulo, new HBox(12, campo("Nombre del equipo", nombre), campo("Estadio / cancha", estadio)),
                grupo("Director técnico", new HBox(12, campo("Nombre", dtNombre), campo("Documento", dtDocumento), campo("Nacionalidad", dtNacionalidad))),
                grupo("Plantilla", filaJugador, errJugador, lista), err, acciones(cancelar, guardar)), 860);
        cancelar.setOnAction(e -> v.close());
        guardar.setOnAction(e -> {
            Map<String, Object> r = app.ejecutar("equipo", mapa("id", equipo == null ? null : n(equipo, "id"),
                    "nombre", nombre.getText(), "estadio", estadio.getText(),
                    "dt", mapa("nombre", dtNombre.getText(), "documento", dtDocumento.getText(), "nacionalidad", dtNacionalidad.getText()),
                    "jugadores", jugadores));
            if (!b(r, "ok")) {
                mostrarError(err, s(r, "error"));
                return;
            }
            v.close();
            app.aviso("Equipo guardado.");
        });
        v.showAndWait();
    }

    /** Encabezado de grupo dentro de la lista de posiciones (no se puede elegir). */
    private static final String GRUPO = "\u0000";

    /**
     * Lista de posiciones agrupada por línea (Portería, Defensa, Mediocampo,
     * Ataque). Los encabezados se ven distintos y no se pueden escoger.
     */
    private ComboBox<String> comboPosiciones() {
        List<String> items = new ArrayList<>();
        List<Map<String, Object>> grupos = l(app.catalogo, "gruposPosiciones");
        if (grupos.isEmpty()) {
            Datos.lista(app.catalogo, "posiciones").forEach(p -> items.add(String.valueOf(p)));
        }
        for (Map<String, Object> g : grupos) {
            items.add(GRUPO + s(g, "grupo"));
            Datos.lista(g, "posiciones").forEach(p -> items.add(String.valueOf(p)));
        }
        ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList(items));
        combo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean vacia) {
                super.updateItem(item, vacia);
                getStyleClass().remove("grupo-lista");
                boolean grupo = !vacia && item != null && item.startsWith(GRUPO);
                setText(vacia || item == null ? null : grupo ? item.substring(1) : item);
                setDisable(grupo);
                if (grupo) {
                    getStyleClass().add("grupo-lista");
                }
            }
        });
        combo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean vacia) {
                super.updateItem(item, vacia);
                setText(vacia || item == null ? null : item.replace(GRUPO, ""));
            }
        });
        // Si se llega a escoger un encabezado (con el teclado), pasa a la primera posición del grupo.
        combo.getSelectionModel().selectedIndexProperty().addListener((o, antes, ahora) -> {
            int i = ahora.intValue();
            if (i >= 0 && items.get(i).startsWith(GRUPO) && i + 1 < items.size()) {
                javafx.application.Platform.runLater(() -> combo.getSelectionModel().select(i + 1));
            }
        });
        combo.getSelectionModel().select(items.isEmpty() ? -1 : items.get(0).startsWith(GRUPO) ? 1 : 0);
        combo.setVisibleRowCount(14);
        return combo;
    }

    private static boolean tieneDorsal(List<Map<String, Object>> jugadores, int dorsal) {
        return jugadores.stream().anyMatch(j -> n(j, "dorsal") == dorsal);
    }

    /* ---------------------------------------------------------------- partido */

    /**
     * Editor del partido en dos modos, uno por pestaña de «Gestionar partidos»:
     * <ul>
     * <li>{@code previa = true} — «Previa del partido»: fecha, hora, sede, formación y XI titular
     * (todo lo que se decide ANTES del pitazo).</li>
     * <li>{@code previa = false} — «Registrar resultados»: marcador o W.O., goles con asistencia,
     * tarjetas y cambios. El XI guardado en la previa ya viene cargado para los cambios.</li>
     * </ul>
     * Los dos modos envían el partido completo con la misma acción {@code partido}: lo que no se
     * muestra se reenvía tal como estaba, así ninguno borra lo que guardó el otro.
     */
    void partido(Map<String, Object> p, boolean previa) {
        // Un partido sin jugar abre con el marcador vacío: así nunca se guarda un 0:0 que no ocurrió.
        boolean yaJugado = b(p, "jugado");
        // Un partido «En juego» conserva el marcador que lleva.
        boolean conMarcador = Aplicacion.conMarcador(p);
        TextField gl = gol(conMarcador ? String.valueOf(n(p, "gl")) : "", "Goles " + s(p, "localNombre"));
        TextField gv = gol(conMarcador ? String.valueOf(n(p, "gv")) : "", "Goles " + s(p, "visitanteNombre"));
        // Cada modo ofrece solo los estados que le corresponden.
        boolean enJuegoAhora = s(p, "estado").equals("EN_JUEGO");
        String[] claves = !previa ? new String[] {"JUGADO", "EN_JUEGO"}
                : enJuegoAhora ? new String[] {"EN_JUEGO"} : new String[] {"PENDIENTE", "APLAZADO", "SUSPENDIDO"};
        ComboBox<String> estado = new ComboBox<>(FXCollections.observableArrayList(java.util.Arrays.stream(claves).map(c -> switch (c) {
            case "JUGADO" -> "Jugado (resultado final)";
            case "EN_JUEGO" -> "En juego (en vivo)";
            case "APLAZADO" -> "Aplazado";
            case "SUSPENDIDO" -> "Suspendido";
            default -> "Pendiente";
        }).toList()));
        estado.getSelectionModel().select(Math.max(0, List.of(claves).indexOf(s(p, "estado"))));
        java.util.function.Supplier<String> claveEstado = () -> claves[Math.max(0, estado.getSelectionModel().getSelectedIndex())];
        CheckBox hayPenales = new CheckBox("Se definió por penales");
        hayPenales.setSelected(b(p, "penales"));
        Spinner<Integer> pl = numero(0, 99, Math.max(0, n(p, "pl")));
        Spinner<Integer> pv = numero(0, 99, Math.max(0, n(p, "pv")));
        HBox penales = new HBox(10, hayPenales, pl, etiqueta(":", "sutil"), pv);
        penales.setAlignment(Pos.CENTER_LEFT);
        boolean conPenales = b(p, "eliminatorio") && n(p, "pierna") != 1;
        penales.setVisible(conPenales);
        penales.setManaged(conPenales);

        List<Map<String, Object>> tarjetas = new ArrayList<>();
        for (Map<String, Object> t : l(p, "tarjetas")) {
            tarjetas.add(new LinkedHashMap<>(t));
        }
        int[] idsEquipos = {n(p, "local"), n(p, "visitante")};
        // ---- XI inicial (se elige en la «Previa del partido»): id del equipo -> ids de los titulares
        Map<Integer, java.util.Set<Integer>> titulares = new LinkedHashMap<>();
        titulares.put(idsEquipos[0], new java.util.LinkedHashSet<>());
        titulares.put(idsEquipos[1], new java.util.LinkedHashSet<>());
        Datos.lista(p, "titularesLocal").forEach(o -> titulares.get(idsEquipos[0]).add(((Number) o).intValue()));
        Datos.lista(p, "titularesVisitante").forEach(o -> titulares.get(idsEquipos[1]).add(((Number) o).intValue()));
        java.util.function.Function<Integer, List<Map<String, Object>>> plantillaDe = idEq -> l(app.equipoPorId(idEq), "jugadores").stream()
                .filter(j -> n(j, "id") > 0).sorted(Comparator.comparingInt(j -> n(j, "dorsal"))).toList();
        // Para goles y tarjetas: primero los titulares de la previa (marcados), después los suplentes.
        java.util.function.Function<Integer, List<Map<String, Object>>> convocadosDe = idEq -> plantillaDe.apply(idEq).stream()
                .sorted(Comparator.comparingInt(j -> titulares.get(idEq).contains(n(j, "id")) ? 0 : 1)).toList();
        java.util.function.BiFunction<Integer, Map<String, Object>, String> textoConvocado = (idEq, j) ->
                textoJugador(j) + (titulares.get(idEq).contains(n(j, "id")) ? "  · titular" : "");
        ComboBox<String> tjEquipo = new ComboBox<>(FXCollections.observableArrayList(s(p, "localNombre"), s(p, "visitanteNombre")));
        tjEquipo.getSelectionModel().selectFirst();
        ComboBox<String> tjJugador = new ComboBox<>();
        List<Map<String, Object>> opciones = new ArrayList<>();
        Runnable llenarJugadores = () -> {
            int idEq = idsEquipos[Math.max(0, tjEquipo.getSelectionModel().getSelectedIndex())];
            opciones.clear();
            opciones.addAll(convocadosDe.apply(idEq));
            tjJugador.setItems(FXCollections.observableArrayList(opciones.isEmpty() ? List.of("(sin jugadores registrados)")
                    : opciones.stream().map(j -> textoConvocado.apply(idEq, j)).toList()));
            tjJugador.getSelectionModel().selectFirst();
        };
        tjEquipo.setOnAction(e -> llenarJugadores.run());
        llenarJugadores.run();
        ComboBox<String> tjTipo = new ComboBox<>(FXCollections.observableArrayList("Amarilla", "Roja"));
        tjTipo.getSelectionModel().selectFirst();
        Spinner<Integer> tjFechas = numero(1, 20, 1);
        TextField tjMinuto = minuto("–");
        VBox campoFechas = campo("Fechas fuera", tjFechas);
        VBox listaTarjetas = new VBox(6);
        Runnable[] dibujarTarjetas = new Runnable[1];
        dibujarTarjetas[0] = () -> {
            tarjetas.sort(Comparator.comparingInt(t -> n(t, "minuto") > 0 ? n(t, "minuto") : 999));
            listaTarjetas.getChildren().clear();
            if (tarjetas.isEmpty()) {
                listaTarjetas.getChildren().add(etiqueta("Sin tarjetas en este partido.", "sutil"));
            }
            for (Map<String, Object> t : tarjetas) {
                boolean roja = s(t, "tipo").equals("ROJA");
                Map<String, Object> eq = app.equipoPorId(n(t, "equipo"));
                Label texto = etiqueta((n(t, "minuto") > 0 ? n(t, "minuto") + "′   " : "") + (roja ? "Roja" : "Amarilla") + " · #" + n(t, "dorsal") + " " + s(t, "nombre") + " · " + s(eq, "nombre")
                        + (roja ? " · " + n(t, "fechas") + (n(t, "fechas") == 1 ? " fecha" : " fechas") + " fuera" : ""), "jugador-texto");
                HBox.setHgrow(texto, Priority.ALWAYS);
                texto.setMaxWidth(Double.MAX_VALUE);
                Button quitar = boton("Quitar", "mini", "peligro");
                quitar.setOnAction(e -> {
                    tarjetas.remove(t);
                    dibujarTarjetas[0].run();
                    listaTarjetas.requestFocus(); // el botón se borró: el foco se queda aquí y no salta al marcador
                });
                HBox fila = new HBox(10, texto, quitar);
                fila.setAlignment(Pos.CENTER_LEFT);
                fila.getStyleClass().add("fila-lista");
                listaTarjetas.getChildren().add(fila);
            }
        };
        dibujarTarjetas[0].run();
        Label err = error();
        Button agregarTarjeta = boton("Agregar");
        agregarTarjeta.setOnAction(e -> {
            int iJugador = tjJugador.getSelectionModel().getSelectedIndex();
            if (opciones.isEmpty() || iJugador < 0) {
                mostrarError(err, "Este equipo no tiene jugadores registrados. Agréguelos en la sección Equipos.");
                return;
            }
            Integer minutoTarjeta = leerMinuto(tjMinuto, true);
            if (minutoTarjeta == null) {
                mostrarError(err, "El minuto de la tarjeta debe estar entre 1 y 130 (o vacío si no se sabe).");
                tjMinuto.requestFocus();
                return;
            }
            mostrarError(err, null);
            Map<String, Object> jugador = opciones.get(iJugador);
            int idJugador = n(jugador, "id");
            boolean roja = tjTipo.getSelectionModel().getSelectedIndex() == 1;
            int fechas = tjFechas.getValue();
            boolean suspendido = l(p, "suspendidos").stream().anyMatch(x -> n(x, "jugador") == idJugador);
            if (suspendido && !confirmar("Jugador suspendido", s(jugador, "nombre")
                    + " estaba suspendido para este partido. ¿Registrar la tarjeta de todas formas?", "Registrar")) {
                return;
            }
            List<Map<String, Object>> delJugador = tarjetas.stream().filter(t -> n(t, "jugador") == idJugador).toList();
            if (roja && delJugador.stream().anyMatch(t -> s(t, "tipo").equals("ROJA"))) {
                mostrarError(err, s(jugador, "nombre") + " ya fue expulsado en este partido.");
                return;
            }
            if (!roja && delJugador.stream().anyMatch(t -> s(t, "tipo").equals("AMARILLA"))
                    && confirmar("Doble amarilla", s(jugador, "nombre") + " ya tiene una amarilla en este partido. "
                            + "¿Registrar la segunda como ROJA (expulsión, 1 fecha)?", "Sí, roja")) {
                roja = true;
                fechas = 1;
            }
            tarjetas.add(mapa("equipo", idsEquipos[tjEquipo.getSelectionModel().getSelectedIndex()], "jugador", idJugador,
                    "nombre", s(jugador, "nombre"), "dorsal", n(jugador, "dorsal"), "tipo", roja ? "ROJA" : "AMARILLA", "fechas", roja ? fechas : 0, "minuto", minutoTarjeta));
            dibujarTarjetas[0].run();
        });
        HBox filaTarjeta = new HBox(10, campo("Equipo", tjEquipo), campo("Jugador", tjJugador), campo("Tarjeta", tjTipo), campo("Minuto", tjMinuto), campoFechas, agregarTarjeta);
        filaTarjeta.setAlignment(Pos.BOTTOM_LEFT);
        List<Map<String, Object>> suspendidos = l(p, "suspendidos");
        Label avisoSuspendidos = etiqueta(suspendidos.isEmpty() ? "Sin jugadores suspendidos para este partido."
                : "Suspendidos para este partido: " + String.join(", ", suspendidos.stream()
                        .map(x -> s(x, "nombre") + " (" + s(x, "equipoNombre") + ")").toList()), suspendidos.isEmpty() ? "sutil" : "advertencia");
        VBox grupoTarjetas = grupo("Tarjetas por jugador", filaTarjeta, listaTarjetas, avisoSuspendidos);

        // ---- cambios: en el minuto indicado sale un jugador y entra otro del mismo equipo
        List<Map<String, Object>> cambios = new ArrayList<>();
        for (Map<String, Object> x : l(p, "cambios")) {
            cambios.add(new LinkedHashMap<>(x));
        }
        ComboBox<String> cbEquipo = new ComboBox<>(FXCollections.observableArrayList(s(p, "localNombre"), s(p, "visitanteNombre")));
        cbEquipo.getSelectionModel().selectFirst();
        cbEquipo.setMaxWidth(Double.MAX_VALUE);
        TextField cbMinuto = minuto("66");
        ComboBox<String> cbSale = new ComboBox<>();
        ComboBox<String> cbEntra = new ComboBox<>();
        cbSale.setPrefWidth(260);
        cbEntra.setPrefWidth(260);
        List<Map<String, Object>> puedenSalir = new ArrayList<>();
        List<Map<String, Object>> puedenEntrar = new ArrayList<>();
        Runnable[] llenarCambio = new Runnable[1];

        // 11 titulares, o toda la plantilla si tiene menos de 11.
        java.util.function.IntUnaryOperator necesarios = idEq -> Math.min(TITULARES, plantillaDe.apply(idEq).size());
        java.util.function.IntPredicate xiListo = idEq -> titulares.get(idEq).size() == necesarios.applyAsInt(idEq) && !titulares.get(idEq).isEmpty();
        // El XI se construye UNA sola vez. Al marcar un jugador solo se actualizan los controles que ya
        // existen (antes se borraba y se volvía a crear toda la lista: la casilla con el foco desaparecía,
        // JavaFX pasaba el foco al primer campo de la ventana —el marcador— y la ventana saltaba hasta él).
        HBox columnasXI = new HBox(14);
        Runnable[] dibujarXI = new Runnable[1];
        List<Runnable> actualizacionesXI = new ArrayList<>();
        dibujarXI[0] = () -> actualizacionesXI.forEach(Runnable::run);
        for (int k = 0; k < 2; k++) {
            int idEq = idsEquipos[k];
            java.util.Set<Integer> elegidos = titulares.get(idEq);
            int faltan = necesarios.applyAsInt(idEq);
            List<Map<String, Object>> jugadores = plantillaDe.apply(idEq);
            Label cuenta = etiqueta("", "etiqueta");
            cuenta.setMinWidth(Region.USE_PREF_SIZE);
            Button rapido = boton("Dorsales más bajos", "mini", "fantasma");
            Button limpiar = boton("Limpiar", "mini", "fantasma");
            Label nombreEq = etiqueta(s(p, k == 0 ? "localNombre" : "visitanteNombre"), "h3");
            HBox filaNombre = new HBox(8, nombreEq, cuenta);
            filaNombre.setAlignment(Pos.CENTER_LEFT);
            VBox cabecera = new VBox(6, filaNombre, new HBox(6, rapido, limpiar));
            VBox lista = new VBox(4);
            if (jugadores.isEmpty()) {
                lista.getChildren().add(etiqueta("Este equipo no tiene jugadores registrados. Agréguelos en Equipos.", "sutil"));
            }
            Map<Integer, CheckBox> cajas = new LinkedHashMap<>();
            for (Map<String, Object> j : jugadores) {
                int id = n(j, "id");
                boolean suspendido = l(p, "suspendidos").stream().anyMatch(x -> n(x, "jugador") == id);
                CheckBox caja = new CheckBox("#" + n(j, "dorsal") + "  " + s(j, "nombre") + "  ·  " + s(j, "posicion") + (suspendido ? "  ·  suspendido" : ""));
                caja.getStyleClass().add("xi-jugador");
                caja.setMaxWidth(Double.MAX_VALUE);
                caja.setOnAction(e -> {
                    if (caja.isSelected()) {
                        elegidos.add(id);
                    } else {
                        elegidos.remove(id);
                    }
                    dibujarXI[0].run();
                    llenarCambio[0].run();
                });
                cajas.put(id, caja);
                lista.getChildren().add(caja);
            }
            // Pone cada control en su estado actual sin crear ni quitar nodos (el foco y el desplazamiento se conservan).
            actualizacionesXI.add(() -> {
                boolean completo = elegidos.size() == faltan;
                java.util.Set<Integer> usados = new java.util.HashSet<>();
                cambios.stream().filter(x -> n(x, "equipo") == idEq).forEach(x -> usados.add(n(x, "sale")));
                cuenta.setText(elegidos.size() + "/" + faltan);
                cuenta.getStyleClass().removeAll("etiqueta-jugado", "etiqueta-pendiente");
                cuenta.getStyleClass().add(completo ? "etiqueta-jugado" : "etiqueta-pendiente");
                limpiar.setDisable(elegidos.isEmpty() || !usados.isEmpty());
                rapido.setDisable(jugadores.isEmpty() || !usados.isEmpty());
                cajas.forEach((id, caja) -> {
                    boolean marcado = elegidos.contains(id);
                    boolean suspendido = l(p, "suspendidos").stream().anyMatch(x -> n(x, "jugador") == id);
                    caja.setSelected(marcado); // por código: no dispara setOnAction
                    // No se desmarca a un titular que ya salió en un cambio, ni se marcan más de los necesarios.
                    caja.setDisable(marcado ? usados.contains(id) : completo || suspendido);
                    caja.getStyleClass().remove("titular");
                    if (marcado) {
                        caja.getStyleClass().add("titular");
                    }
                });
            });
            rapido.setOnAction(e -> {
                elegidos.clear();
                jugadores.stream().filter(j -> l(p, "suspendidos").stream().noneMatch(x -> n(x, "jugador") == n(j, "id")))
                        .limit(faltan).forEach(j -> elegidos.add(n(j, "id")));
                dibujarXI[0].run();
                llenarCambio[0].run();
            });
            limpiar.setOnAction(e -> {
                elegidos.clear();
                dibujarXI[0].run();
                llenarCambio[0].run();
            });
            ScrollPane desplazable = new ScrollPane(lista);
            desplazable.setFitToWidth(true);
            desplazable.setPrefViewportHeight(300);
            desplazable.getStyleClass().add("desplazable");
            VBox columna = new VBox(8, cabecera, desplazable);
            columna.setPrefWidth(1);
            HBox.setHgrow(columna, Priority.ALWAYS);
            columnasXI.getChildren().add(columna);
        }
        Label ayudaXI = etiqueta("Marque los 11 jugadores que inician el partido en cada equipo. Los demás quedan como suplentes "
                + "y son los únicos que pueden entrar en un cambio.", "sutil", "pequeno");
        ayudaXI.setWrapText(true);
        VBox grupoXI = grupo("XI inicial", ayudaXI, columnasXI);

        // ---- Formacion tactica de cada entrenador (se puede elegir antes de jugar el partido)
        List<String> formaciones = new ArrayList<>(List.of(SIN_FORMACION));
        Datos.lista(app.catalogo, "formaciones").forEach(f -> formaciones.add(String.valueOf(f)));
        ComboBox<String> formLocal = new ComboBox<>(FXCollections.observableArrayList(formaciones));
        ComboBox<String> formVisitante = new ComboBox<>(FXCollections.observableArrayList(formaciones));
        formLocal.getSelectionModel().select(s(p, "formacionLocal").isEmpty() ? SIN_FORMACION : s(p, "formacionLocal"));
        formVisitante.getSelectionModel().select(s(p, "formacionVisitante").isEmpty() ? SIN_FORMACION : s(p, "formacionVisitante"));
        VBox grupoFormacion = grupo("Formación táctica",
                new HBox(14, campo(s(p, "localNombre"), formLocal), campo(s(p, "visitanteNombre"), formVisitante)));

        // ---- Goles por jugador: autor, minuto y asistencia (opcional). Alimentan goleadores y asistencias.
        List<Map<String, Object>> goles = new ArrayList<>();
        for (Map<String, Object> g : l(p, "goles")) {
            goles.add(new LinkedHashMap<>(g));
        }
        ComboBox<String> glEquipo = new ComboBox<>(FXCollections.observableArrayList(s(p, "localNombre"), s(p, "visitanteNombre")));
        glEquipo.getSelectionModel().selectFirst();
        CheckBox glEnContra = new CheckBox("En contra (autogol)");
        TextField glMinuto = minuto("–");
        ComboBox<String> glAutor = new ComboBox<>();
        ComboBox<String> glAsistente = new ComboBox<>();
        glAutor.setPrefWidth(260);
        glAsistente.setPrefWidth(260);
        List<Map<String, Object>> autores = new ArrayList<>(), asistentes = new ArrayList<>();
        Runnable llenarGol = () -> {
            int iEq = Math.max(0, glEquipo.getSelectionModel().getSelectedIndex());
            boolean contra = glEnContra.isSelected();
            int idAutor = idsEquipos[contra ? 1 - iEq : iEq]; // un autogol lo marca un jugador del rival
            autores.clear();
            autores.addAll(convocadosDe.apply(idAutor));
            asistentes.clear();
            if (!contra) {
                asistentes.addAll(convocadosDe.apply(idsEquipos[iEq]));
            }
            glAutor.setItems(FXCollections.observableArrayList(autores.isEmpty() ? List.of("(sin jugadores registrados)")
                    : autores.stream().map(j -> textoConvocado.apply(idAutor, j)).toList()));
            glAutor.getSelectionModel().selectFirst();
            List<String> textos = new ArrayList<>(List.of("(sin asistencia)"));
            asistentes.stream().map(j -> textoConvocado.apply(idsEquipos[iEq], j)).forEach(textos::add);
            glAsistente.setItems(FXCollections.observableArrayList(textos));
            glAsistente.getSelectionModel().selectFirst();
            glAsistente.setDisable(contra);
        };
        glEquipo.setOnAction(e -> llenarGol.run());
        glEnContra.setOnAction(e -> llenarGol.run());
        llenarGol.run();
        VBox listaGoles = new VBox(6);
        Runnable[] dibujarGoles = new Runnable[1];
        dibujarGoles[0] = () -> {
            goles.sort(Comparator.comparingInt(g -> n(g, "minuto") > 0 ? n(g, "minuto") : 999));
            listaGoles.getChildren().clear();
            if (goles.isEmpty()) {
                listaGoles.getChildren().add(etiqueta("Sin goles registrados por jugador.", "sutil"));
            }
            for (Map<String, Object> g : goles) {
                Label texto = etiqueta((n(g, "minuto") > 0 ? n(g, "minuto") + "′   " : "") + "#" + n(g, "autorDorsal") + " " + s(g, "autorNombre")
                        + (b(g, "enContra") ? " (en contra)" : "")
                        + (n(g, "asistente") != 0 ? " · asistencia de #" + n(g, "asistenteDorsal") + " " + s(g, "asistenteNombre") : "")
                        + " · " + s(app.equipoPorId(n(g, "equipo")), "nombre"), "jugador-texto");
                HBox.setHgrow(texto, Priority.ALWAYS);
                texto.setMaxWidth(Double.MAX_VALUE);
                Button quitar = boton("Quitar", "mini", "peligro");
                quitar.setOnAction(e -> {
                    goles.remove(g);
                    dibujarGoles[0].run();
                    listaGoles.requestFocus(); // el botón se borró: el foco se queda aquí y no salta al marcador
                });
                HBox fila = new HBox(10, Aplicacion.icono(Aplicacion.ICONO_DADO, "icono-mini", "icono-gol"), texto, quitar);
                fila.setAlignment(Pos.CENTER_LEFT);
                fila.getStyleClass().add("fila-lista");
                listaGoles.getChildren().add(fila);
            }
        };
        dibujarGoles[0].run();
        Button agregarGol = boton("Agregar gol");
        agregarGol.setOnAction(e -> {
            int iEq = glEquipo.getSelectionModel().getSelectedIndex(), iAutor = glAutor.getSelectionModel().getSelectedIndex();
            if (autores.isEmpty() || iAutor < 0) {
                mostrarError(err, "Ese equipo no tiene jugadores registrados. Agréguelos en la sección Equipos.");
                return;
            }
            Integer min = leerMinuto(glMinuto, true);
            if (min == null) {
                mostrarError(err, "El minuto del gol debe estar entre 1 y 130 (o vacío si no se sabe).");
                glMinuto.requestFocus();
                return;
            }
            // No puede haber más goles registrados que los del marcador escrito arriba.
            TextField marcadorEquipo = iEq == 0 ? gl : gv;
            int enMarcador = marcadorEquipo.getText().isBlank() ? 0 : Integer.parseInt(marcadorEquipo.getText());
            long registrados = goles.stream().filter(g -> n(g, "equipo") == idsEquipos[iEq]).count();
            if (registrados >= enMarcador) {
                mostrarError(err, glEquipo.getValue() + " tiene " + enMarcador + (enMarcador == 1 ? " gol" : " goles")
                        + " en el marcador y ya " + (registrados == 1 ? "hay 1 registrado" : "hay " + registrados + " registrados")
                        + ". Suba primero el marcador.");
                marcadorEquipo.requestFocus();
                return;
            }
            mostrarError(err, null);
            Map<String, Object> autor = autores.get(iAutor);
            int iAsist = glAsistente.getSelectionModel().getSelectedIndex();
            Map<String, Object> asist = iAsist > 0 ? asistentes.get(iAsist - 1) : null;
            if (asist != null && n(asist, "id") == n(autor, "id")) {
                mostrarError(err, s(autor, "nombre") + " no puede darse la asistencia a sí mismo.");
                return;
            }
            goles.add(mapa("equipo", idsEquipos[iEq], "minuto", min, "enContra", glEnContra.isSelected(),
                    "autor", n(autor, "id"), "autorNombre", s(autor, "nombre"), "autorDorsal", n(autor, "dorsal"),
                    "asistente", asist == null ? 0 : n(asist, "id"), "asistenteNombre", asist == null ? "" : s(asist, "nombre"),
                    "asistenteDorsal", asist == null ? 0 : n(asist, "dorsal")));
            dibujarGoles[0].run();
            glMinuto.requestFocus();
        });
        VBox campoEquipoGol = campo("Equipo", glEquipo);
        HBox.setHgrow(campoEquipoGol, Priority.ALWAYS);
        HBox filaGol1 = new HBox(10, campoEquipoGol, campo("Minuto", glMinuto), glEnContra);
        HBox filaGol2 = new HBox(10, campo("Autor", glAutor), campo("Asistencia", glAsistente), agregarGol);
        filaGol1.setAlignment(Pos.BOTTOM_LEFT);
        filaGol2.setAlignment(Pos.BOTTOM_LEFT);
        VBox grupoGoles = grupo("Goles", filaGol1, filaGol2, listaGoles,
                etiqueta("Registrar quién marcó alimenta las tablas de goleadores y asistencias. No puede haber más goles registrados que en el marcador.",
                        "sutil", "pequeno"));
        boolean[] llenando = {false};
        llenarCambio[0] = () -> {
            llenando[0] = true;
            int idEquipo = idsEquipos[Math.max(0, cbEquipo.getSelectionModel().getSelectedIndex())];
            List<Map<String, Object>> plantilla = plantillaDe.apply(idEquipo);
            java.util.Set<Integer> xi = titulares.get(idEquipo);
            boolean listo = xiListo.test(idEquipo);
            String vacio = listo ? "(sin jugadores disponibles)" : "(primero escoja el XI inicial)";
            java.util.Set<Integer> salieron = new java.util.HashSet<>(), entraron = new java.util.HashSet<>();
            cambios.stream().filter(x -> n(x, "equipo") == idEquipo).forEach(x -> {
                salieron.add(n(x, "sale"));
                entraron.add(n(x, "entra"));
            });
            int saleAntes = cbSale.getSelectionModel().getSelectedIndex() >= 0 && cbSale.getSelectionModel().getSelectedIndex() < puedenSalir.size()
                    ? n(puedenSalir.get(cbSale.getSelectionModel().getSelectedIndex()), "id") : -1;
            puedenSalir.clear();
            // Sale: quien está en la cancha (titulares que no han salido y suplentes que ya entraron).
            plantilla.stream().filter(j -> listo && (xi.contains(n(j, "id")) || entraron.contains(n(j, "id"))) && !salieron.contains(n(j, "id")))
                    .forEach(puedenSalir::add);
            cbSale.setItems(FXCollections.observableArrayList(puedenSalir.isEmpty() ? List.of(vacio)
                    : puedenSalir.stream().map(Dialogos::textoJugador).toList()));
            int iSale = 0;
            for (int i = 0; i < puedenSalir.size(); i++) {
                if (n(puedenSalir.get(i), "id") == saleAntes) {
                    iSale = i;
                }
            }
            cbSale.getSelectionModel().select(iSale);
            int sale = puedenSalir.isEmpty() ? -1 : n(puedenSalir.get(iSale), "id");
            puedenEntrar.clear();
            // Entra: solo suplentes que todavía no han entrado.
            plantilla.stream().filter(j -> listo && !xi.contains(n(j, "id")) && !entraron.contains(n(j, "id")) && !salieron.contains(n(j, "id")))
                    .forEach(puedenEntrar::add);
            cbEntra.setItems(FXCollections.observableArrayList(puedenEntrar.isEmpty() ? List.of(vacio)
                    : puedenEntrar.stream().map(Dialogos::textoJugador).toList()));
            cbEntra.getSelectionModel().selectFirst();
            llenando[0] = false;
        };
        cbEquipo.setOnAction(e -> llenarCambio[0].run());
        cbSale.setOnAction(e -> {
            if (!llenando[0]) {
                llenarCambio[0].run();
            }
        });
        VBox listaCambios = new VBox(6);
        Runnable[] dibujarCambios = new Runnable[1];
        dibujarCambios[0] = () -> {
            cambios.sort(Comparator.comparingInt(x -> n(x, "minuto")));
            listaCambios.getChildren().clear();
            if (cambios.isEmpty()) {
                listaCambios.getChildren().add(etiqueta("Sin cambios en este partido.", "sutil"));
            }
            for (Map<String, Object> x : cambios) {
                Label min = etiqueta(n(x, "minuto") + "′", "minuto");
                Label sale = etiqueta("Sale #" + n(x, "saleDorsal") + " " + s(x, "saleNombre"), "sale-texto");
                Label entra = etiqueta("Entra #" + n(x, "entraDorsal") + " " + s(x, "entraNombre"), "entra-texto");
                Label equipo = etiqueta("· " + s(app.equipoPorId(n(x, "equipo")), "nombre"), "sutil");
                HBox textos = new HBox(8, min, Aplicacion.iconoCambio(), sale, entra, equipo);
                textos.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(textos, Priority.ALWAYS);
                Button quitar = boton("Quitar", "mini", "peligro");
                quitar.setOnAction(e -> {
                    cambios.remove(x);
                    dibujarCambios[0].run();
                    dibujarXI[0].run();
                    llenarCambio[0].run();
                    listaCambios.requestFocus(); // el botón se borró: el foco se queda aquí y no salta al marcador
                });
                HBox fila = new HBox(10, textos, quitar);
                fila.setAlignment(Pos.CENTER_LEFT);
                fila.getStyleClass().add("fila-lista");
                listaCambios.getChildren().add(fila);
            }
        };
        dibujarXI[0].run();
        llenarCambio[0].run();
        dibujarCambios[0].run();
        Button agregarCambio = boton("Agregar cambio");
        agregarCambio.setOnAction(e -> {
            Integer min = leerMinuto(cbMinuto, false);
            if (min == null) {
                mostrarError(err, "Escriba el minuto del cambio (entre 1 y 130).");
                cbMinuto.requestFocus();
                return;
            }
            int idEqCambio = idsEquipos[cbEquipo.getSelectionModel().getSelectedIndex()];
            if (!xiListo.test(idEqCambio)) {
                mostrarError(err, "Antes de registrar cambios escoja el XI inicial de " + cbEquipo.getValue() + " ("
                        + titulares.get(idEqCambio).size() + "/" + necesarios.applyAsInt(idEqCambio) + ").");
                return;
            }
            int iSale = cbSale.getSelectionModel().getSelectedIndex(), iEntra = cbEntra.getSelectionModel().getSelectedIndex();
            if (puedenSalir.isEmpty() || puedenEntrar.isEmpty() || iSale < 0 || iEntra < 0) {
                mostrarError(err, "Escoja el jugador que sale y el que entra. Si no aparecen, registre la plantilla en Equipos.");
                return;
            }
            mostrarError(err, null);
            Map<String, Object> sale = puedenSalir.get(iSale), entra = puedenEntrar.get(iEntra);
            cambios.add(mapa("equipo", idsEquipos[cbEquipo.getSelectionModel().getSelectedIndex()], "minuto", min,
                    "sale", n(sale, "id"), "saleNombre", s(sale, "nombre"), "saleDorsal", n(sale, "dorsal"),
                    "entra", n(entra, "id"), "entraNombre", s(entra, "nombre"), "entraDorsal", n(entra, "dorsal")));
            dibujarCambios[0].run();
            dibujarXI[0].run();
            llenarCambio[0].run();
            cbSale.requestFocus();
        });
        VBox campoEquipoCambio = campo("Equipo", cbEquipo);
        HBox.setHgrow(campoEquipoCambio, Priority.ALWAYS);
        HBox filaCambio1 = new HBox(10, campoEquipoCambio, campo("Minuto", cbMinuto));
        HBox filaCambio2 = new HBox(10, campo("Sale", cbSale), campo("Entra", cbEntra), agregarCambio);
        filaCambio1.setAlignment(Pos.BOTTOM_LEFT);
        filaCambio2.setAlignment(Pos.BOTTOM_LEFT);
        VBox grupoCambios = grupo("Cambios", filaCambio1, filaCambio2, listaCambios,
                etiqueta("El minuto se conserva para anotar seguido varios cambios hechos al mismo tiempo.", "sutil", "pequeno"));

        DatePicker fecha = new DatePicker();
        if (s(p, "fecha").matches("\\d{4}-\\d{2}-\\d{2}")) {
            fecha.setValue(LocalDate.parse(s(p, "fecha")));
        }
        TextField hora = new TextField(s(p, "hora"));
        hora.setPromptText("15:30");
        TextField sede = new TextField(s(p, "sede"));
        TextField obs = new TextField(s(p, "obs"));

        Button guardar = boton("Guardar", "primario");
        Runnable actualizar = () -> {
            boolean jugado = claveEstado.get().equals("JUGADO"), enJuego = claveEstado.get().equals("EN_JUEGO");
            gl.setDisable(!(jugado || enJuego));
            gv.setDisable(!(jugado || enJuego));
            guardar.setText(previa ? "Guardar previa" : enJuego ? "Guardar en vivo" : yaJugado ? "Guardar resultado" : "Registrar resultado");
            hayPenales.setDisable(!jugado);
            // Tarjetas y cambios se anotan mientras se juega («En juego») o después, con el resultado final.
            grupoTarjetas.setDisable(!(jugado || enJuego));
            grupoGoles.setDisable(!(jugado || enJuego));
            grupoCambios.setDisable(!(jugado || enJuego));
            pl.setDisable(!jugado || !hayPenales.isSelected());
            pv.setDisable(!jugado || !hayPenales.isSelected());
            boolean roja = tjTipo.getSelectionModel().getSelectedIndex() == 1;
            campoFechas.setVisible(roja);
            campoFechas.setManaged(roja);
        };
        estado.setOnAction(e -> actualizar.run());
        hayPenales.setOnAction(e -> actualizar.run());
        tjTipo.setOnAction(e -> actualizar.run());
        for (TextField campoGol : List.of(gl, gv)) {
            campoGol.textProperty().addListener((propiedad, antes, ahora) -> {
                if (!ahora.isEmpty()) {
                    campoGol.getStyleClass().remove("campo-invalido");
                }
            });
        }
        actualizar.run();

        Label local = etiqueta(s(p, "localNombre"), "equipo-nombre");
        Label visitante = etiqueta(s(p, "visitanteNombre"), "equipo-nombre");
        local.setMaxWidth(Double.MAX_VALUE);
        visitante.setMaxWidth(Double.MAX_VALUE);
        local.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(local, Priority.ALWAYS);
        HBox.setHgrow(visitante, Priority.ALWAYS);
        HBox marcador = new HBox(14, local, gl, etiqueta(":", "led-separador"), gv, visitante);
        marcador.setAlignment(Pos.CENTER);
        marcador.getStyleClass().add("marcador-partido");

        Button woLocal = boton("W.O. gana local", "mini", "fantasma");
        Button woVisitante = boton("W.O. gana visitante", "mini", "fantasma");
        Button borrar = boton("Borrar resultado", "mini", "peligro");
        borrar.setVisible(conMarcador);
        borrar.setManaged(conMarcador);
        Button cancelar = boton("Cancelar", "fantasma");
        Button verDetalle = boton("Ver detalle", "mini", "fantasma");
        HBox botones = previa ? new HBox(10, verDetalle, Aplicacion.espacio(), cancelar, guardar)
                : new HBox(10, verDetalle, woLocal, woVisitante, borrar, Aplicacion.espacio(), cancelar, guardar);
        botones.setAlignment(Pos.CENTER_LEFT);

        // Resumen de la alineación guardada en la previa: formación y cuántos titulares tiene cada equipo.
        Label resumenPrevia = etiqueta("", "sutil");
        resumenPrevia.setWrapText(true);
        Runnable actualizarResumen = () -> {
            List<String> partes = new ArrayList<>();
            for (int k = 0; k < 2; k++) {
                int idEq = idsEquipos[k];
                String f = formacionElegida(k == 0 ? formLocal : formVisitante);
                int faltan = necesarios.applyAsInt(idEq);
                partes.add(s(p, k == 0 ? "localNombre" : "visitanteNombre") + ": " + (f.isEmpty() ? "sin formación" : f)
                        + " · XI " + (faltan == 0 ? "sin plantilla" : titulares.get(idEq).size() + "/" + faltan));
            }
            resumenPrevia.setText(String.join("\n", partes)); // un equipo por línea
        };
        actualizacionesXI.add(actualizarResumen);
        formLocal.setOnAction(e -> actualizarResumen.run());
        formVisitante.setOnAction(e -> actualizarResumen.run());
        actualizarResumen.run();

        Node contenido;
        HBox programacion = new HBox(12, campo("Fecha", fecha), campo("Hora", hora), campo("Sede / estadio", sede), campo("Observaciones", obs));
        if (previa) {
            // ---- Previa: solo lo que se define antes del pitazo inicial.
            Label vs = etiqueta("VS", "vs");
            HBox duelo = new HBox(14, local, vs, visitante);
            duelo.setAlignment(Pos.CENTER);
            duelo.getStyleClass().add("marcador-partido");
            VBox grupoProgramacion = grupo("Fecha, hora y sede", programacion);
            if (!enJuegoAhora) {
                grupoProgramacion.getChildren().add(new HBox(14, campo("Estado", estado)));
            }
            Label ayudaPrevia = etiqueta("Lo que guarde aquí lo ve el visitante en la previa del partido y queda listo en «Registrar resultados» "
                    + "para anotar goles, tarjetas y cambios.", "sutil", "pequeno");
            ayudaPrevia.setWrapText(true);
            contenido = cuerpo("Previa del partido · " + app.contexto(p), duelo, ayudaPrevia, grupoProgramacion, grupoFormacion, grupoXI, err, botones);
        } else {
            // ---- Resultados: marcador y eventos del juego. La alineación de la previa queda plegada
            // (se puede corregir aquí si cambió a última hora, pero no estorba).
            boolean xiCompleto = xiListo.test(idsEquipos[0]) && xiListo.test(idsEquipos[1]);
            VBox alineacion = new VBox(12, grupoFormacion, grupoXI);
            alineacion.setVisible(!xiCompleto);
            alineacion.setManaged(!xiCompleto);
            Button plegar = boton(xiCompleto ? "Corregir alineación" : "Ocultar", "mini", "fantasma");
            plegar.setOnAction(e -> {
                boolean mostrar = !alineacion.isVisible();
                alineacion.setVisible(mostrar);
                alineacion.setManaged(mostrar);
                plegar.setText(mostrar ? "Ocultar" : "Corregir alineación");
            });
            Label tituloPrevia = etiqueta("Alineación de la previa", "h3");
            VBox textosPrevia = new VBox(2, tituloPrevia, resumenPrevia);
            HBox.setHgrow(textosPrevia, Priority.ALWAYS);
            HBox cabeceraPrevia = new HBox(10, textosPrevia, plegar);
            cabeceraPrevia.setAlignment(Pos.CENTER_LEFT);
            cabeceraPrevia.getStyleClass().add("resumen-previa");
            if (!xiCompleto) {
                Label sinXI = etiqueta("Para registrar cambios hace falta el XI inicial de los dos equipos. Defínalo en la «Previa del partido» o aquí mismo.",
                        "advertencia", "pequeno");
                sinXI.setWrapText(true);
                textosPrevia.getChildren().add(sinXI);
            }
            contenido = cuerpo("Registrar resultado · " + app.contexto(p), marcador, new HBox(14, campo("Estado", estado), penales), cabeceraPrevia, alineacion,
                    grupoGoles, grupoTarjetas, grupoCambios, err, botones);
        }
        Stage v = ventana(previa ? "Previa del partido" : "Registrar resultado", contenido, 900);
        cancelar.setOnAction(e -> v.close());
        boolean[] abrirDetalle = {false}; // el detalle se abre cuando este editor ya terminó (ver el final del método)
        verDetalle.setOnAction(e -> {
            abrirDetalle[0] = true;
            v.close();
        });

        java.util.function.Consumer<String> enviar = accion -> {
            boolean faltaLocal = gl.getText().isBlank(), faltaVisitante = gv.getText().isBlank();
            if (accion.equals("guardar") && claveEstado.get().equals("JUGADO") && (faltaLocal || faltaVisitante)) {
                if (faltaLocal) {
                    gl.getStyleClass().add("campo-invalido");
                }
                if (faltaVisitante) {
                    gv.getStyleClass().add("campo-invalido");
                }
                mostrarError(err, faltaLocal && faltaVisitante
                        ? "Escriba los goles de los dos equipos para registrar el resultado (o use «En juego» si todavía se está jugando)."
                        : "Falta el marcador de " + s(p, faltaLocal ? "localNombre" : "visitanteNombre") + ". Escriba 0 si no marcó goles.");
                (faltaLocal ? gl : gv).requestFocus();
                return;
            }
            if (accion.equals("guardar")) {
                // Un XI empezado tiene que quedar completo (o vacío) antes de guardar, en la previa o con el resultado.
                for (int k = 0; k < 2; k++) {
                    int idEq = idsEquipos[k], elegidos = titulares.get(idEq).size(), faltan = necesarios.applyAsInt(idEq);
                    if (elegidos > 0 && elegidos != faltan) {
                        mostrarError(err, "El XI inicial de " + s(p, k == 0 ? "localNombre" : "visitanteNombre") + " tiene " + elegidos
                                + " de " + faltan + " jugadores. Complételo o pulse «Limpiar».");
                        return;
                    }
                }
            }
            boolean penal = conPenales && hayPenales.isSelected();
            Map<String, Object> datos = mapa("estado", claveEstado.get(),
                    "gl", faltaLocal ? 0 : Integer.parseInt(gl.getText()), "gv", faltaVisitante ? 0 : Integer.parseInt(gv.getText()), "pl", penal ? pl.getValue() : -1, "pv", penal ? pv.getValue() : -1,
                    "tarjetas", tarjetas, "cambios", cambios, "goles", goles,
                    "titularesLocal", new ArrayList<>(titulares.get(idsEquipos[0])), "titularesVisitante", new ArrayList<>(titulares.get(idsEquipos[1])), "fecha", fecha.getValue() == null ? "" : fecha.getValue().toString(),
                    "hora", hora.getText(), "sede", sede.getText(), "obs", obs.getText(),
                    "formacionLocal", formacionElegida(formLocal), "formacionVisitante", formacionElegida(formVisitante));
            Map<String, Object> cuerpo = mapa("id", n(p, "id"), "datos", datos, "accion", accion, "confirmado", false);
            Map<String, Object> r = app.ejecutar("partido", cuerpo);
            if (!b(r, "ok") && s(r, "codigo").equals("CONFIRMAR")) {
                if (!confirmar("El cambio afecta rondas posteriores", s(r, "error"), "Sí, continuar")) {
                    return;
                }
                cuerpo.put("confirmado", true);
                r = app.ejecutar("partido", cuerpo);
            }
            if (!b(r, "ok")) {
                mostrarError(err, s(r, "error"));
                if (s(r, "codigo").equals("PENALES")) {
                    hayPenales.setSelected(true);
                    actualizar.run();
                }
                return;
            }
            v.close();
            app.aviso(previa ? "Previa guardada: el visitante ya la ve en el detalle del partido." : "Partido guardado.");
        };
        guardar.setOnAction(e -> enviar.accept("guardar"));
        woLocal.setOnAction(e -> enviar.accept("woLocal"));
        woVisitante.setOnAction(e -> enviar.accept("woVisitante"));
        borrar.setOnAction(e -> enviar.accept("borrar"));
        v.showAndWait();
        // «Ver detalle»: se abre DESPUÉS de cerrar el editor, no desde su botón mientras se cierra
        // (así abierta, la ventana modal podía quedar invisible y bloquear todo el programa).
        if (abrirDetalle[0]) {
            detallePartido(p);
        }
    }

    /** Jugadores que inician el partido. */
    static final int TITULARES = 11;

    private static String textoJugador(Map<String, Object> j) {
        return "#" + n(j, "dorsal") + " " + s(j, "nombre") + (s(j, "posicion").isEmpty() ? "" : " · " + s(j, "posicion"));
    }

    /** Campo corto para un minuto de juego (solo cifras). */
    private static TextField minuto(String ejemplo) {
        TextField t = new TextField();
        t.setPromptText(ejemplo);
        t.setPrefColumnCount(3);
        t.setPrefWidth(80);
        t.getStyleClass().add("campo-minuto");
        t.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().matches("\\d{0,3}") ? c : null));
        return t;
    }

    /** Minuto escrito (1 a 130). Si se permite vacío devuelve 0; si no es válido, null. */
    private static Integer leerMinuto(TextField t, boolean vacioPermitido) {
        if (t.getText().isBlank()) {
            return vacioPermitido ? 0 : null;
        }
        int m = Integer.parseInt(t.getText());
        return m >= 1 && m <= 130 ? m : null;
    }

    /* ---------------------------------------------------------------- datos de prueba */

    /* ---------------------------------------------------------------- detalle de partido (Flashscore) */

    static final String SIN_FORMACION = "Sin definir";

    private static String formacionElegida(ComboBox<String> combo) {
        String valor = combo.getValue();
        return valor == null || valor.equals(SIN_FORMACION) ? "" : valor;
    }

    /**
     * Abre el selector de archivos de Windows de forma segura desde una de nuestras ventanas.
     *
     * Las ventanas de los diálogos son StageStyle.TRANSPARENT (sin marco, con sombra): en Windows son
     * ventanas «en capas». Si una de ellas es la dueña del selector nativo, el selector puede abrirse
     * DETRÁS o sin foco, mientras la dueña queda bloqueada esperándolo: cada clic suena con el aviso
     * de Windows y el programa parece congelado. Por eso el selector se abre desde una ventanita
     * normal (sin transparencia, 1×1 px) puesta encima del diálogo, y al terminar —se elija, se cancele
     * o falle— se cierra y el foco vuelve al diálogo.
     *
     * @return el archivo elegido, o null si se canceló
     */
    private static java.io.File elegirArchivo(javafx.stage.FileChooser selector, Stage dialogo) {
        Stage anfitrion = new Stage(StageStyle.UNDECORATED);
        anfitrion.initOwner(dialogo);                 // hija del diálogo: queda encima y no está bloqueada
        anfitrion.setScene(new Scene(new Pane(), 1, 1));
        anfitrion.setX(dialogo.getX() + dialogo.getWidth() / 2);
        anfitrion.setY(dialogo.getY() + dialogo.getHeight() / 3);
        try {
            anfitrion.show();
            anfitrion.toFront();
            return selector.showOpenDialog(anfitrion);
        } finally {
            anfitrion.hide();
            dialogo.toFront();
            dialogo.requestFocus();
        }
    }

    /* ---------------------------------------------------------------- noticias */

    /**
     * Crear o editar una noticia (administrador, sale publicada) o proponerla (visitante, queda
     * pendiente). {@code existente} null = nueva. La foto se elige del disco y se envia como
     * «data URL», igual que en la web; el servicio la copia a datos/imagenes/.
     */
    void noticia(Map<String, Object> existente, boolean propuesta) {
        TextField titulo = new TextField(s(existente, "titulo"));
        titulo.setPromptText("Ej.: Los Pumas ganan el clásico en el último minuto");
        javafx.scene.control.TextArea cuerpo = new javafx.scene.control.TextArea(s(existente, "cuerpo"));
        cuerpo.setWrapText(true);
        cuerpo.setPrefRowCount(8);
        cuerpo.setPromptText("Texto de la noticia");
        TextField autor = new TextField();
        autor.setPromptText("Su nombre (opcional)");

        String[] imagen = {""};            // data URL de la foto nueva ("" = no se cambia)
        boolean[] quitar = {false};
        javafx.scene.image.ImageView vista = new javafx.scene.image.ImageView();
        vista.setFitWidth(220);
        vista.setPreserveRatio(true);
        Label sinFoto = etiqueta("Sin foto", "sutil");
        javafx.scene.image.Image actual = existente == null ? null : app.fotoNoticia(s(existente, "imagen"));
        vista.setImage(actual);
        sinFoto.setVisible(actual == null);
        sinFoto.setManaged(actual == null);
        Label err = error();
        Button elegir = boton("Elegir foto…", "fantasma");
        Button quitarFoto = boton("Quitar foto", "fantasma", "mini");
        quitarFoto.setDisable(actual == null);
        elegir.setOnAction(e -> {
            javafx.stage.FileChooser selector = new javafx.stage.FileChooser();
            selector.setTitle("Foto de la noticia");
            selector.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Imágenes (JPG, PNG, GIF)", "*.jpg", "*.jpeg", "*.png", "*.gif"));
            java.io.File archivo;
            elegir.setDisable(true); // evita abrir dos selectores a la vez
            try {
                archivo = elegirArchivo(selector, (Stage) elegir.getScene().getWindow());
            } catch (RuntimeException ex) {
                mostrarError(err, "No se pudo abrir el selector de archivos: " + ex.getMessage());
                return;
            } finally {
                elegir.setDisable(false);
            }
            if (archivo == null) {
                return; // canceló: la ventana sigue tal cual
            }
            try {
                byte[] bytes = java.nio.file.Files.readAllBytes(archivo.toPath());
                if (bytes.length > alphasport.service.NoticiasService.MAXIMO_IMAGEN) {
                    mostrarError(err, "La foto es muy pesada (máximo 5 MB).");
                    return;
                }
                javafx.scene.image.Image previa = new javafx.scene.image.Image(new java.io.ByteArrayInputStream(bytes));
                if (previa.isError()) {
                    mostrarError(err, "No se pudo leer esa imagen. Escoja un archivo JPG, PNG o GIF.");
                    return;
                }
                String nombre = archivo.getName().toLowerCase();
                String tipo = nombre.endsWith(".png") ? "png" : nombre.endsWith(".gif") ? "gif" : "jpeg";
                imagen[0] = "data:image/" + tipo + ";base64," + java.util.Base64.getEncoder().encodeToString(bytes);
                quitar[0] = false;
                vista.setImage(previa);
                sinFoto.setVisible(false);
                sinFoto.setManaged(false);
                quitarFoto.setDisable(false);
                mostrarError(err, null);
            } catch (java.io.IOException | RuntimeException ex) {
                mostrarError(err, "No se pudo abrir el archivo: " + ex.getMessage());
            }
        });
        quitarFoto.setOnAction(e -> {
            imagen[0] = "";
            quitar[0] = true;
            vista.setImage(null);
            sinFoto.setVisible(true);
            sinFoto.setManaged(true);
            quitarFoto.setDisable(true);
        });
        VBox foto = new VBox(8, vista, sinFoto, new HBox(8, elegir, quitarFoto));

        String tituloVentana = propuesta ? "Proponer una noticia" : existente == null ? "Nueva noticia" : "Editar noticia";
        Button cancelar = boton("Cancelar", "fantasma");
        Button guardar = boton(propuesta ? "Enviar para aprobación" : existente == null ? "Publicar" : "Guardar y publicar", "primario");
        VBox contenido = new VBox(14, campo("Título", titulo), campo("Texto", cuerpo));
        if (propuesta) {
            contenido.getChildren().add(campo("Su nombre", autor));
            Label aviso = etiqueta("Su noticia no se publica de inmediato: el administrador la revisa y la aprueba o la rechaza.", "sutil", "pequeno");
            aviso.setWrapText(true);
            contenido.getChildren().add(aviso);
        }
        contenido.getChildren().add(grupo("Foto (opcional)", foto));
        Stage v = ventana(tituloVentana, cuerpo(tituloVentana, contenido, err, acciones(cancelar, guardar)), 720);
        cancelar.setOnAction(e -> v.close());
        String[] enviada = {null}; // mensaje de confirmación de la propuesta (se muestra al cerrar este diálogo)
        guardar.setOnAction(e -> {
            Map<String, Object> datos = mapa("titulo", titulo.getText(), "cuerpo", cuerpo.getText(), "autor", autor.getText(),
                    "imagen", imagen[0], "quitarImagen", quitar[0]);
            if (existente != null) {
                datos.put("id", n(existente, "id"));
            }
            Map<String, Object> r = app.ejecutar(propuesta ? "noticia/proponer" : "noticia/guardar", datos);
            if (!b(r, "ok")) {
                mostrarError(err, s(r, "error"));
                return;
            }
            if (propuesta) {
                enviada[0] = s(r, "mensaje");
            } else {
                app.aviso(s(r, "mensaje"));
            }
            v.close();
        });
        v.showAndWait();
        // La confirmación se abre DESPUÉS de que este diálogo terminó, nunca desde su botón mientras se cierra:
        // abierta ahí, la ventana modal quedaba invisible (Windows no la pintaba) pero bloqueando todo el programa,
        // con el sonido de alerta en cada clic y Alt+F4 como única salida.
        if (enviada[0] != null) {
            mensaje("Noticia enviada", enviada[0]);
        }
    }

    /** Detalle de solo lectura: resumen del partido, cara a cara, racha y alineaciones. Lo usan visitante y administrador. */
    void detallePartido(Map<String, Object> p) {
        Node vista;
        try {
            Map<String, Object> detalle = app.api.consultar("partido/" + n(p, "id"));
            if (detalle == null || m(detalle, "partido") == null) {
                mensaje("Detalle del partido", "Todavía no hay información de este partido.");
                return;
            }
            vista = VistaDetallePartido.construir(detalle);
        } catch (RuntimeException e) {
            // Nunca se deja una ventana a medio armar: se avisa y la aplicación sigue respondiendo.
            mensaje("No se pudo abrir el partido", e.getMessage() == null ? "Todavía no hay información de este partido." : e.getMessage());
            return;
        }
        Button cerrar = boton("Cerrar", "primario");
        cerrar.setDefaultButton(true);
        Stage v = ventana("Detalle del partido", cuerpo(app.contexto(p), vista, acciones(cerrar)), 920);
        cerrar.setOnAction(e -> v.close());
        v.showAndWait();
    }

    void datosPrueba() {
        boolean iniciado = b(m(app.E, "campeonato"), "iniciado");
        int maximo = n(app.catalogo, "semillaMaximoEquipos") > 0 ? n(app.catalogo, "semillaMaximoEquipos") : 32;
        java.util.Random azar = new java.util.Random();
        TextField semilla = new TextField(String.valueOf(10000 + azar.nextInt(90000)));
        semilla.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().matches("\\d{0,9}") ? c : null));
        Button otra = boton("Otra", "mini", "fantasma");
        otra.setOnAction(e -> semilla.setText(String.valueOf(10000 + azar.nextInt(90000))));
        HBox filaSemilla = new HBox(8, semilla, otra);
        filaSemilla.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(semilla, Priority.ALWAYS);
        Spinner<Integer> cantidad = numero(0, maximo, iniciado ? 0 : 8);
        cantidad.setDisable(iniciado);
        CheckBox completar = new CheckBox("Completar también la plantilla de los equipos que no tienen jugadores");
        completar.setSelected(true);
        completar.setWrapText(true);
        long vacios = l(app.E, "equipos").stream().filter(e -> !b(e, "retirado") && l(e, "jugadores").isEmpty()).count();
        String avisoTexto = iniciado
                ? "El campeonato ya inició: solo se pueden completar plantillas (" + vacios + (vacios == 1 ? " equipo sin jugadores)." : " equipos sin jugadores).")
                : vacios > 0 ? "Hay " + vacios + (vacios == 1 ? " equipo" : " equipos") + " sin jugadores." : "";
        Label explicacion = etiqueta("Crea equipos inventados con director técnico, estadio y plantilla completa de 20 jugadores "
                + "(nombre, documento, dorsal y posición). La misma semilla genera siempre los mismos datos.", "sutil");
        explicacion.setWrapText(true);
        Label err = error();
        Button cancelar = boton("Cancelar", "fantasma");
        Button cargar = boton("Cargar datos", "primario");
        HBox campos = new HBox(12, campo("Semilla", filaSemilla), campo("Equipos nuevos", cantidad));
        VBox contenido = cuerpo("Datos de prueba", explicacion, campos, completar);
        if (!avisoTexto.isEmpty()) {
            contenido.getChildren().add(etiqueta(avisoTexto, "sutil", "pequeno"));
        }
        contenido.getChildren().addAll(err, acciones(cancelar, cargar));
        Stage v = ventana("Datos de prueba", contenido, 520);
        cancelar.setOnAction(e -> v.close());
        Runnable enviar = () -> {
            if (semilla.getText().isBlank()) {
                mostrarError(err, "Escriba una semilla (un número entero).");
                return;
            }
            Map<String, Object> r = app.ejecutar("semilla", mapa("semilla", Integer.parseInt(semilla.getText()),
                    "cantidad", cantidad.getValue(), "completar", completar.isSelected()));
            if (!b(r, "ok")) {
                mostrarError(err, s(r, "error"));
                return;
            }
            v.close();
            app.aviso(s(r, "mensaje"));
        };
        cargar.setOnAction(e -> enviar.run());
        semilla.setOnAction(e -> enviar.run());
        v.showAndWait();
    }

    /* ---------------------------------------------------------------- historial */

    void historial() {
        Map<String, Object> datos = app.api.consultar("historial");
        List<Map<String, Object>> lista = l(datos, "lista");
        VBox detalle = new VBox(14);
        HBox.setHgrow(detalle, Priority.ALWAYS);
        ListView<String> campeonatos = new ListView<>(FXCollections.observableArrayList(lista.stream()
                .map(h -> s(h, "nombre") + "\n" + s(h, "formato") + "\n" + s(h, "fecha") + " · "
                        + (s(h, "campeon").isEmpty() ? "Sin terminar" : "Campeón: " + s(h, "campeon"))).toList()));
        campeonatos.setPrefWidth(290);
        campeonatos.getStyleClass().add("lista-historial");
        Stage[] ventana = new Stage[1];
        campeonatos.getSelectionModel().selectedIndexProperty().addListener((obs, a, i) -> {
            if (i.intValue() >= 0) {
                dibujarDetalle(detalle, lista.get(i.intValue()), ventana);
            }
        });
        Node pestanaCampeonatos = lista.isEmpty()
                ? etiqueta("Todavía no hay campeonatos en el historial. Se guardan solos al terminar un campeonato.", "sutil")
                : new HBox(18, campeonatos, detalle);
        TableView<Map<String, Object>> palmares = Aplicacion.tabla(l(datos, "palmares"), new String[][]{{"Equipo", "equipo"},
            {"Títulos", "titulos", "70"}, {"2.º", "sub", "60"}, {"Podios", "podios", "75"}, {"Torneos", "participaciones", "80"},
            {"PJ", "pj", "60"}, {"G", "g", "55"}, {"E", "e", "55"}, {"P", "p", "55"}, {"GF", "gf", "60"}, {"GC", "gc", "60"}, {"Pts", "pts", "65"}});
        TabPane pestanas = new TabPane(new Tab("Campeonatos", pestanaCampeonatos), new Tab("Palmarés histórico", palmares));
        pestanas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Button cerrar = boton("Cerrar", "fantasma");
        ventana[0] = ventana("Historial de campeonatos", cuerpo("Historial de campeonatos", pestanas, acciones(cerrar)), 1100);
        cerrar.setOnAction(e -> ventana[0].close());
        if (!lista.isEmpty()) {
            campeonatos.getSelectionModel().selectFirst();
        }
        ventana[0].showAndWait();
    }

    private void dibujarDetalle(VBox detalle, Map<String, Object> h, Stage[] ventana) {
        String[] clases = {"oro", "plata", "bronce"};
        String[] puestos = {"Campeón", "Subcampeón", "Tercer lugar"};
        HBox podio = new HBox(12);
        List<Map<String, Object>> lugares = l(h, "podio");
        int[] orden = {1, 0, 2};
        for (int k : orden) {
            if (k >= lugares.size()) {
                continue;
            }
            Map<String, Object> f = lugares.get(k);
            VBox puesto = new VBox(4, etiqueta(b(h, "terminado") ? puestos[k] : (k + 1) + ".º (sin terminar)", "pequeno"),
                    etiqueta(s(f, "nombre"), "puesto-nombre"), etiqueta("DT: " + s(f, "dt"), "pequeno"));
            puesto.setAlignment(Pos.CENTER);
            puesto.getStyleClass().addAll("puesto", clases[k]);
            HBox.setHgrow(puesto, Priority.ALWAYS);
            puesto.setMaxWidth(Double.MAX_VALUE);
            podio.getChildren().add(puesto);
        }
        podio.setAlignment(Pos.BOTTOM_CENTER);
        FlowPane premios = new FlowPane(10, 10);
        for (Map<String, Object> p : l(h, "premios")) {
            VBox premio = new VBox(2, etiqueta(s(p, "titulo").replaceAll("^[^\\p{L}]+", ""), "sobretitulo"), etiqueta(s(p, "valor"), "premio-valor"),
                    etiqueta(s(p, "detalle"), "sutil", "pequeno"));
            premio.getStyleClass().add("premio");
            premio.setPrefWidth(200);
            premios.getChildren().add(premio);
        }
        TableView<Map<String, Object>> clasificacion = Aplicacion.tabla(l(h, "clasificacion"), Aplicacion.COLUMNAS_TABLA);
        TableView<Map<String, Object>> resultados = Aplicacion.tabla(l(h, "resultados"), new String[][]{{"Fase", "fase"}, {"Ronda", "ronda"},
            {"Local", "local"}, {"Marcador", "marcador"}, {"Visitante", "visitante"}, {"Estado", "estado"}});
        List<Map<String, Object>> filasTarjetas = new ArrayList<>();
        for (Map<String, Object> t : l(h, "tarjetas")) {
            filasTarjetas.add(mapa("equipo", s(t, "equipo"), "jugador", s(t, "jugador"), "partido", s(t, "partido"),
                    "tipo", s(t, "tipo").equals("ROJA") ? "Roja (" + n(t, "fechas") + " fecha/s)" : "Amarilla"));
        }
        TableView<Map<String, Object>> tarjetas = Aplicacion.tabla(filasTarjetas, new String[][]{{"Equipo", "equipo"},
            {"Jugador", "jugador"}, {"Tarjeta", "tipo"}, {"Partido", "partido"}});
        TabPane tablas = new TabPane(new Tab("Clasificación", clasificacion), new Tab("Resultados", resultados), new Tab("Tarjetas", tarjetas));
        tablas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Button eliminar = boton("Eliminar del historial", "mini", "peligro");
        eliminar.setOnAction(e -> {
            if (confirmar("Eliminar del historial", "¿Eliminar «" + s(h, "nombre") + "» del historial? Sus resultados dejarán de contar en el palmarés.", "Eliminar")) {
                Map<String, Object> r = app.ejecutar("historial/eliminar", mapa("codigo", s(h, "codigo")));
                if (!b(r, "ok")) {
                    mensaje("Error", s(r, "error"));
                }
                ventana[0].close();
                historial();
            }
        });
        app.soloAdmin(eliminar);
        Button descargar = boton("Descargar premios (.txt)", "mini");
        descargar.setOnAction(e -> app.guardarTexto(s(h, "nombre") + "_premios", s(h, "texto")));
        detalle.getChildren().setAll(podio, premios, tablas, new HBox(10, Aplicacion.espacio(), eliminar, descargar));
    }

}
