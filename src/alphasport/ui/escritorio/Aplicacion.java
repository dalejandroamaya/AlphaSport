package alphasport.ui.escritorio;

import static alphasport.ui.escritorio.Datos.b;
import static alphasport.ui.escritorio.Datos.l;
import static alphasport.ui.escritorio.Datos.m;
import static alphasport.ui.escritorio.Datos.mapa;
import static alphasport.ui.escritorio.Datos.n;
import static alphasport.ui.escritorio.Datos.s;

import alphasport.service.AutenticacionService;
import alphasport.service.CampeonatoService;
import alphasport.service.ReglaNegocioException;
import alphasport.ui.ApiControlador;
import alphasport.ui.Arranque;
import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Version de escritorio (JavaFX): misma interfaz y mismas reglas que la web,
 * hablando con la capa service a traves del ApiControlador, sin servidor.
 */
public class Aplicacion extends Application {

    static final String[] VISTAS = {"equipos", "partidos", "posiciones", "llaves", "sanciones", "reglas"};
    static final String[] NOMBRES_VISTAS = {"Equipos", "Partidos", "Posiciones", "Llaves", "Tarjetas", "Reglas"};
    static final String[] ICONOS_VISTAS = {
        "M12 2 4 5v6c0 5 3.4 9.4 8 11 4.6-1.6 8-6 8-11V5l-8-3Z",
        "M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2ZM3 10h18M8 3v4M16 3v4",
        "M4 20V10M10 20V4M16 20v-7M22 20H2",
        "M3 4h5v6H3M3 14h5v6H3M8 7h4v10H8M12 12h5M17 9h4v6h-4",
        "M6.5 4h6a1.5 1.5 0 0 1 1.5 1.5v10a1.5 1.5 0 0 1-1.5 1.5h-6A1.5 1.5 0 0 1 5 15.5v-10A1.5 1.5 0 0 1 6.5 4ZM15 7h3a1.5 1.5 0 0 1 1.5 1.5v10a1.5 1.5 0 0 1-1.5 1.5h-6a1.5 1.5 0 0 1-1.5-1.5V18",
        "M6 3h9l4 4v14H6zM15 3v4h4M9 12h7M9 16h7"};

    Stage stage;
    CampeonatoService servicio;
    ApiControlador api;
    Map<String, Object> catalogo;
    Map<String, Object> E = mapa("hay", false);
    Dialogos dialogos;
    boolean claro;

    private Scene scene;
    private String vista = "equipos";
    private final StackPane contenido = new StackPane();
    private final Label titulo = new Label();
    private final Label chipFormato = new Label();
    private final Label chipEquipos = new Label();
    private final Label estado = new Label();
    private final Button btnAvanzar = new Button();
    private final MenuItem itemSiguiente = new MenuItem("Siguiente torneo corto (Clausura)…");
    private final Map<String, ToggleButton> navegacion = new LinkedHashMap<>();
    private final Label toast = new Label();
    private SequentialTransition animacionToast;
    private int filtroFase = -1;
    private String filtroEstado = "todos";
    private int filtroEquipo = -1;
    private int tablaElegida;
    private String filtroSanciones = "todas";

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        servicio = Arranque.servicio();
        api = new ApiControlador(servicio, new AutenticacionService());
        catalogo = api.consultar("catalogo");
        dialogos = new Dialogos(this);
        scene = new Scene(new StackPane(), 1280, 820);
        scene.getStylesheets().add(hojaEstilos());
        stage.setTitle("AlphaSport · Gestor de campeonatos");
        stage.getIcons().add(imagen("logo.png"));
        stage.setMinWidth(980);
        stage.setMinHeight(640);
        stage.setScene(scene);
        mostrarLogin();
        stage.setMaximized(true);
        stage.show();
    }

    String hojaEstilos() {
        URL recurso = getClass().getResource("escritorio.css");
        return recurso != null ? recurso.toExternalForm()
                : Path.of("src/alphasport/ui/escritorio/escritorio.css").toUri().toString();
    }

    static Image imagen(String nombre) {
        return new Image(Arranque.CARPETA_WEB.resolve("img").resolve(nombre).toUri().toString(), true);
    }

    private void ponerRaiz(Parent raiz) {
        raiz.getStyleClass().add("raiz");
        if (claro) {
            raiz.getStyleClass().add("claro");
        }
        scene.setRoot(raiz);
    }

    void alternarTema() {
        claro = !claro;
        if (claro) {
            scene.getRoot().getStyleClass().add("claro");
        } else {
            scene.getRoot().getStyleClass().remove("claro");
        }
    }

    Button botonTema(String texto) {
        Button b = boton(texto, "fantasma");
        b.setOnAction(e -> alternarTema());
        return b;
    }

    static Button boton(String texto, String... clases) {
        Button b = new Button(texto);
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.getStyleClass().add("btn");
        b.getStyleClass().addAll(clases);
        return b;
    }

    static Label etiqueta(String texto, String... clases) {
        Label l = new Label(texto);
        l.getStyleClass().addAll(clases);
        l.setWrapText(true);
        return l;
    }

    static Region espacio() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    /* ---------------------------------------------------------------- acciones */

    Map<String, Object> ejecutar(String ruta, Map<String, Object> cuerpo) {
        Map<String, Object> r;
        try {
            r = api.ejecutar(ruta, cuerpo);
        } catch (ReglaNegocioException e) {
            return mapa("ok", false, "codigo", e.getCodigo(), "error", e.getMessage());
        } catch (Exception e) {
            return mapa("ok", false, "codigo", "ERROR", "error", e.getMessage() == null ? e.toString() : e.getMessage());
        }
        E = m(r, "estado");
        render();
        return r;
    }

    void aviso(String texto) {
        if (texto == null || texto.isBlank()) {
            return;
        }
        toast.setText(texto);
        if (animacionToast != null) {
            animacionToast.stop();
        }
        FadeTransition entrar = new FadeTransition(Duration.millis(200), toast);
        entrar.setToValue(1);
        FadeTransition salir = new FadeTransition(Duration.millis(400), toast);
        salir.setToValue(0);
        animacionToast = new SequentialTransition(entrar, new PauseTransition(Duration.seconds(3.5)), salir);
        animacionToast.play();
    }

    /* ---------------------------------------------------------------- login */

    void mostrarLogin() {
        ImageView logo = new ImageView(imagen("logo-texto.png"));
        logo.setFitWidth(380);
        logo.setPreserveRatio(true);
        logo.setSmooth(true);
        logo.getStyleClass().add("logo-login");
        TextField usuario = new TextField();
        usuario.setPromptText("Usuario");
        PasswordField clave = new PasswordField();
        clave.setPromptText("Contraseña");
        Label error = etiqueta("", "error");
        error.setVisible(false);
        Button entrar = boton("Entrar al estadio", "primario", "ancho");
        entrar.setDefaultButton(true);
        entrar.setMaxWidth(Double.MAX_VALUE);
        entrar.setOnAction(e -> {
            try {
                api.ejecutar("login", mapa("usuario", usuario.getText(), "clave", clave.getText()));
                mostrarPrincipal();
            } catch (Exception ex) {
                error.setText(ex.getMessage());
                error.setVisible(true);
            }
        });
        VBox tarjeta = new VBox(12, logo, etiqueta("Gestión profesional de campeonatos de fútbol", "sutil", "centro"),
                etiqueta("Usuario", "campo-titulo"), usuario, etiqueta("Contraseña", "campo-titulo"), clave, error, entrar,
                etiqueta("Usuario de prueba: admin · Contraseña: 123", "sutil", "pequeno", "centro"));
        tarjeta.setAlignment(Pos.CENTER);
        tarjeta.getChildren().stream().filter(x -> x.getStyleClass().contains("campo-titulo"))
                .forEach(x -> ((Label) x).setMaxWidth(Double.MAX_VALUE));
        tarjeta.getStyleClass().addAll("vidrio", "login-tarjeta");
        tarjeta.setMaxSize(460, Region.USE_PREF_SIZE);
        Button tema = botonTema("🌓");
        StackPane.setAlignment(tema, Pos.TOP_RIGHT);
        StackPane.setMargin(tema, new Insets(18));
        StackPane fondo = new StackPane(tarjeta, tema);
        fondo.getStyleClass().add("login");
        ponerRaiz(fondo);
        usuario.requestFocus();
    }

    /* ---------------------------------------------------------------- estructura principal */

    void mostrarPrincipal() {
        E = api.consultar("estado");
        ImageView logo = new ImageView(imagen("logo.png"));
        logo.setFitWidth(150);
        logo.setPreserveRatio(true);
        logo.getStyleClass().add("logo-lateral");
        VBox marca = new VBox(2, logo, etiqueta("AlphaSport", "marca-nombre"), etiqueta("GESTOR DE CAMPEONATOS", "marca-sub"));
        marca.setAlignment(Pos.CENTER);
        marca.getStyleClass().add("marca-lateral");

        VBox nav = new VBox(6);
        ToggleGroup grupo = new ToggleGroup();
        for (int i = 0; i < VISTAS.length; i++) {
            String clave = VISTAS[i];
            SVGPath icono = new SVGPath();
            icono.setContent(ICONOS_VISTAS[i]);
            icono.getStyleClass().add("icono-nav");
            ToggleButton b = new ToggleButton(NOMBRES_VISTAS[i], icono);
            b.setGraphicTextGap(12);
            b.setToggleGroup(grupo);
            b.setMaxWidth(Double.MAX_VALUE);
            b.getStyleClass().add("nav");
            b.setOnAction(e -> mostrarVista(clave));
            navegacion.put(clave, b);
            nav.getChildren().add(b);
        }
        Button historial = boton("🏆  Historial", "fantasma");
        historial.setMaxWidth(Double.MAX_VALUE);
        historial.setOnAction(e -> dialogos.historial());
        Button tema = botonTema("🌓  Modo claro / oscuro");
        tema.setMaxWidth(Double.MAX_VALUE);
        Label modo = etiqueta("🖥  Escritorio Java", "chip", "chip-modo");
        modo.setMaxWidth(Double.MAX_VALUE);
        modo.setAlignment(Pos.CENTER);
        VBox lateral = new VBox(22, marca, nav, espacio(), new VBox(8, historial, tema, modo));
        lateral.getStyleClass().add("barra-lateral");
        lateral.setPrefWidth(250);

        titulo.getStyleClass().add("titulo");
        titulo.setWrapText(true);
        chipFormato.getStyleClass().add("chip");
        chipEquipos.getStyleClass().add("chip");
        estado.getStyleClass().add("estado-vivo");
        estado.setWrapText(true);
        VBox info = new VBox(6, etiqueta("CAMPEONATO EN CURSO", "sobretitulo"), titulo, new HBox(8, chipFormato, chipEquipos), estado);
        HBox.setHgrow(info, Priority.ALWAYS);
        btnAvanzar.getStyleClass().addAll("btn", "primario", "grande");
        btnAvanzar.setOnAction(e -> avanzar());
        Button campeon = boton("Ver campeón", "dorado");
        campeon.setOnAction(e -> dialogos.verCampeon());
        HBox acciones = new HBox(10, btnAvanzar, campeon, menuArchivo());
        acciones.setAlignment(Pos.CENTER_RIGHT);
        HBox cabecera = new HBox(20, info, acciones);
        cabecera.setAlignment(Pos.CENTER_LEFT);
        cabecera.getStyleClass().addAll("vidrio", "marcador-principal");

        ScrollPane desplazable = new ScrollPane(contenido);
        desplazable.setFitToWidth(true);
        desplazable.getStyleClass().add("desplazable");
        VBox centro = new VBox(22, cabecera, desplazable);
        VBox.setVgrow(desplazable, Priority.ALWAYS);
        centro.setPadding(new Insets(24, 32, 24, 32));

        BorderPane armazon = new BorderPane(centro);
        armazon.setLeft(lateral);
        toast.getStyleClass().add("aviso");
        toast.setOpacity(0);
        toast.setMouseTransparent(true);
        StackPane.setAlignment(toast, Pos.BOTTOM_CENTER);
        StackPane.setMargin(toast, new Insets(0, 0, 28, 0));
        StackPane raiz = new StackPane(armazon, toast);
        raiz.getStyleClass().add("fondo-app");
        ponerRaiz(raiz);
        if (!b(E, "hay")) {
            dialogos.nuevo(true, false);
        } else {
            render();
        }
    }

    private MenuButton menuArchivo() {
        MenuButton menu = new MenuButton("☰  Archivo");
        menu.getStyleClass().addAll("btn", "fantasma", "menu-archivo");
        MenuItem nuevo = new MenuItem("Nuevo campeonato…");
        nuevo.setOnAction(e -> {
            if (dialogos.confirmar("Nuevo campeonato", "El campeonato actual se reemplazará por uno nuevo. Si ya tiene partidos jugados "
                    + "se guardará en el historial (como «sin terminar» si no ha terminado). ¿Continuar?", "Continuar")) {
                dialogos.nuevo(false, false);
            }
        });
        itemSiguiente.setOnAction(e -> siguienteTorneo());
        MenuItem cambiar = new MenuItem("Cambiar formato del campeonato…");
        cambiar.setOnAction(e -> {
            String texto = b(m(E, "campeonato"), "iniciado")
                    ? "Se conservarán los equipos con su DT y jugadores, pero se borrarán el fixture y todos los resultados del campeonato actual. ¿Continuar?"
                    : "Puede escoger otro formato y otras reglas. Los equipos registrados se conservan. ¿Continuar?";
            if (dialogos.confirmar("Cambiar formato", texto, "Continuar")) {
                dialogos.nuevo(false, true);
            }
        });
        MenuItem cancelar = new MenuItem("Cancelar campeonato…");
        cancelar.getStyleClass().add("opcion-peligro");
        cancelar.setOnAction(e -> cancelarCampeonato());
        MenuItem respaldo = new MenuItem("Descargar respaldo");
        respaldo.setOnAction(e -> guardarRespaldo());
        MenuItem abrir = new MenuItem("Abrir respaldo…");
        abrir.setOnAction(e -> abrirRespaldo());
        MenuItem exportar = new MenuItem("Exportar historial (.txt)");
        exportar.setOnAction(e -> guardarTexto(s(m(E, "campeonato"), "nombre"), servicio.textoHistorial()));
        MenuItem historial = new MenuItem("Historial de campeonatos");
        historial.setOnAction(e -> dialogos.historial());
        MenuItem sesion = new MenuItem("Cerrar sesión");
        sesion.setOnAction(e -> mostrarLogin());
        MenuItem salir = new MenuItem("Salir");
        salir.setOnAction(e -> stage.close());
        menu.getItems().addAll(nuevo, itemSiguiente, cambiar, cancelar, new SeparatorMenuItem(), respaldo, abrir, exportar,
                historial, new SeparatorMenuItem(), sesion, salir);
        return menu;
    }

    private void cancelarCampeonato() {
        if (!dialogos.confirmar("Cancelar campeonato", "Se descartará «" + s(m(E, "campeonato"), "nombre")
                + "» por completo (equipos, partidos y resultados) y no quedará en el historial. Esta acción no se puede deshacer. "
                + "¿Cancelar el campeonato?", "Sí, cancelarlo")) {
            return;
        }
        Map<String, Object> r = ejecutar("cancelar", mapa());
        if (!b(r, "ok")) {
            dialogos.mensaje("Error", s(r, "error"));
            return;
        }
        dialogos.nuevo(true, false);
    }

    private void siguienteTorneo() {
        String nombre = dialogos.pedirTexto("Siguiente torneo corto", "Se creará un torneo nuevo con los mismos equipos, plantillas y reglas.\nNombre del nuevo torneo:",
                s(m(E, "campeonato"), "nombre").replace("Apertura", "Clausura"));
        if (nombre == null || nombre.isBlank()) {
            return;
        }
        Map<String, Object> r = ejecutar("siguiente", mapa("nombre", nombre));
        if (!b(r, "ok")) {
            dialogos.mensaje("Error", s(r, "error"));
            return;
        }
        aviso("Se creó «" + nombre + "». El torneo anterior quedó en el historial.");
    }

    private void guardarRespaldo() {
        FileChooser selector = new FileChooser();
        selector.setTitle("Guardar respaldo");
        selector.setInitialFileName(nombreArchivo(s(m(E, "campeonato"), "nombre")) + ".dat");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Respaldo de AlphaSport", "*.dat"));
        File archivo = selector.showSaveDialog(stage);
        if (archivo == null) {
            return;
        }
        try {
            Files.write(archivo.toPath(), servicio.respaldo());
            aviso("Respaldo guardado en " + archivo.getName());
        } catch (Exception e) {
            dialogos.mensaje("Error", e.getMessage());
        }
    }

    private void abrirRespaldo() {
        FileChooser selector = new FileChooser();
        selector.setTitle("Abrir respaldo");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Respaldo de AlphaSport", "*.dat"));
        File archivo = selector.showOpenDialog(stage);
        if (archivo == null) {
            return;
        }
        try {
            servicio.abrirRespaldo(Files.readAllBytes(archivo.toPath()));
            E = m(api.respuesta(null), "estado");
            render();
            aviso("Se abrió «" + s(m(E, "campeonato"), "nombre") + "».");
        } catch (Exception e) {
            dialogos.mensaje("Error", e.getMessage());
        }
    }

    void guardarTexto(String nombre, String texto) {
        FileChooser selector = new FileChooser();
        selector.setTitle("Guardar archivo de texto");
        selector.setInitialFileName(nombreArchivo(nombre) + ".txt");
        File archivo = selector.showSaveDialog(stage);
        if (archivo == null) {
            return;
        }
        try {
            Files.writeString(archivo.toPath(), texto.replaceAll("\r?\n", "\r\n"), StandardCharsets.UTF_8);
            aviso("Archivo guardado: " + archivo.getName());
        } catch (Exception e) {
            dialogos.mensaje("Error", e.getMessage());
        }
    }

    static String nombreArchivo(String nombre) {
        String limpio = nombre.trim().replaceAll("[^a-zA-Z0-9-_]+", "_");
        return limpio.isEmpty() ? "campeonato" : limpio;
    }

    void avanzar() {
        Map<String, Object> c = m(E, "campeonato");
        if (b(c, "terminado")) {
            ejecutar("archivar", mapa());
            dialogos.nuevo(false, false);
            return;
        }
        boolean iniciaba = !b(c, "iniciado");
        Map<String, Object> r = ejecutar("avanzar", mapa());
        if (!b(r, "ok")) {
            dialogos.mensaje("No se puede avanzar", s(r, "error"));
            return;
        }
        if (b(m(E, "campeonato"), "terminado")) {
            mostrarVista(l(E, "llaves").isEmpty() ? "posiciones" : "llaves");
            dialogos.verCampeon();
            aviso("El campeonato quedó guardado en el historial. Use «Terminar e iniciar otro» para el siguiente.");
        } else {
            if (iniciaba) {
                mostrarVista("partidos");
            }
            aviso(s(r, "mensaje"));
        }
    }

    /* ---------------------------------------------------------------- dibujo */

    void render() {
        if (!b(E, "hay")) {
            return;
        }
        Map<String, Object> c = m(E, "campeonato");
        titulo.setText(s(c, "nombre"));
        stage.setTitle(s(c, "nombre") + " · AlphaSport");
        chipFormato.setText(s(c, "formatoNombre"));
        chipEquipos.setText(n(c, "equiposActivos") + " equipos");
        estado.setText("●  " + s(c, "estado"));
        btnAvanzar.setText(b(c, "terminado") ? "Terminar e iniciar otro" : b(c, "iniciado") ? "Avanzar ronda / fase" : "Iniciar campeonato");
        btnAvanzar.getStyleClass().remove("dorado");
        if (b(c, "terminado")) {
            btnAvanzar.getStyleClass().add("dorado");
        }
        itemSiguiente.setDisable(!b(c, "puedeSiguiente"));
        mostrarVista(vista);
    }

    void mostrarVista(String nombre) {
        vista = nombre;
        ToggleButton b = navegacion.get(nombre);
        if (b != null) {
            b.setSelected(true);
        }
        if (!b(E, "hay")) {
            contenido.getChildren().clear();
            return;
        }
        Node nodo = switch (nombre) {
            case "partidos" -> vistaPartidos();
            case "posiciones" -> vistaPosiciones();
            case "llaves" -> vistaLlaves();
            case "sanciones" -> vistaSanciones();
            case "reglas" -> vistaReglas();
            default -> vistaEquipos();
        };
        contenido.getChildren().setAll(nodo);
    }

    private VBox seccion(String titulo, String ayuda, Node... derecha) {
        VBox textos = new VBox(4, etiqueta(titulo, "h2"));
        if (ayuda != null) {
            textos.getChildren().add(etiqueta(ayuda, "sutil"));
        }
        HBox.setHgrow(textos, Priority.ALWAYS);
        HBox cabecera = new HBox(12, textos);
        cabecera.getChildren().addAll(derecha);
        cabecera.setAlignment(Pos.BOTTOM_LEFT);
        VBox caja = new VBox(18, cabecera);
        caja.getStyleClass().add("vista");
        return caja;
    }

    private Node vacio(String texto) {
        Label l = etiqueta(texto, "vacio");
        l.setMaxWidth(Double.MAX_VALUE);
        l.getStyleClass().add("vidrio");
        return l;
    }

    private Node vistaEquipos() {
        boolean iniciado = b(m(E, "campeonato"), "iniciado");
        TextField nombre = new TextField();
        nombre.setPromptText("Nombre del equipo");
        nombre.setPrefWidth(240);
        nombre.setDisable(iniciado);
        Button agregar = boton("Agregar");
        agregar.setDisable(iniciado);
        Runnable rapido = () -> {
            if (nombre.getText().isBlank()) {
                return;
            }
            Map<String, Object> r = ejecutar("equipo/rapido", mapa("nombre", nombre.getText()));
            if (!b(r, "ok")) {
                aviso(s(r, "error"));
            }
        };
        agregar.setOnAction(e -> rapido.run());
        nombre.setOnAction(e -> rapido.run());
        Button completo = boton("+ Con plantilla", "fantasma");
        completo.setDisable(iniciado);
        completo.setOnAction(e -> dialogos.equipo(null));
        VBox caja = seccion("Equipos", iniciado
                ? "El campeonato ya inició: puede editar nombre, DT y jugadores, pero no agregar equipos nuevos."
                : "Registre los equipos (mínimo 2) y pulse «Iniciar campeonato» para generar el fixture automáticamente.",
                nombre, agregar, completo);
        List<Map<String, Object>> equipos = l(E, "equipos");
        if (equipos.isEmpty()) {
            caja.getChildren().add(vacio("⚽  Todavía no hay equipos registrados."));
            return caja;
        }
        FlowPane grid = new FlowPane(16, 16);
        for (int i = 0; i < equipos.size(); i++) {
            grid.getChildren().add(tarjetaEquipo(equipos.get(i), i, iniciado));
        }
        caja.getChildren().add(grid);
        return caja;
    }

    static StackPane escudo(Map<String, Object> e, double tamano) {
        String nombre = s(e, "nombre");
        Region forma = new Region();
        int tono = (n(e, "id") * 47) % 360;
        forma.setStyle("-fx-shape: 'M50 0 L100 14 L100 60 L50 100 L0 60 L0 14 Z'; -fx-background-color: linear-gradient(to bottom, hsb("
                + tono + ",72%,92%), hsb(" + tono + ",80%,42%));");
        forma.setPrefSize(tamano, tamano * 1.14);
        forma.setMaxSize(tamano, tamano * 1.14);
        Label iniciales = etiqueta(nombre.substring(0, Math.min(2, nombre.length())).toUpperCase(), "escudo-texto");
        StackPane s = new StackPane(forma, iniciales);
        s.setMinSize(tamano, tamano * 1.14);
        return s;
    }

    private Node tarjetaEquipo(Map<String, Object> e, int i, boolean iniciado) {
        Map<String, Object> dt = m(e, "dt");
        VBox datos = new VBox(3, etiqueta("#" + (i + 1) + (b(e, "retirado") ? " · RETIRADO" : ""), "sobretitulo"),
                etiqueta(s(e, "nombre"), "h3"),
                etiqueta("DT: " + (dt == null ? "Sin asignar" : s(dt, "nombre")) + " · " + l(e, "jugadores").size() + " jugadores", "sutil", "pequeno"),
                etiqueta("🏟  " + (s(e, "estadio").isEmpty() ? "Sin estadio" : s(e, "estadio")), "sutil", "pequeno"));
        HBox.setHgrow(datos, Priority.ALWAYS);
        Button editar = boton("Editar", "mini");
        editar.setOnAction(ev -> dialogos.equipo(e));
        HBox botones = new HBox(8, espacio(), editar);
        if (!b(e, "retirado")) {
            Button quitar = boton(iniciado ? "Retirar" : "Eliminar", "mini", "peligro");
            quitar.setOnAction(ev -> quitarEquipo(e, iniciado));
            botones.getChildren().add(quitar);
        }
        HBox fila = new HBox(14, escudo(e, 54), datos);
        fila.setAlignment(Pos.CENTER_LEFT);
        VBox tarjeta = new VBox(10, fila, botones);
        tarjeta.getStyleClass().addAll("vidrio", "tarjeta-equipo");
        if (b(e, "retirado")) {
            tarjeta.setOpacity(.5);
        }
        tarjeta.setPrefWidth(320);
        return tarjeta;
    }

    private void quitarEquipo(Map<String, Object> e, boolean iniciado) {
        String texto = iniciado
                ? "¿Retirar al equipo «" + s(e, "nombre") + "»?\nSus partidos pendientes de la fase actual se darán por ganados 3-0 (W.O.) al rival."
                : "¿Eliminar el equipo «" + s(e, "nombre") + "»?";
        if (!dialogos.confirmar("Confirmar", texto, iniciado ? "Retirar" : "Eliminar")) {
            return;
        }
        Map<String, Object> r = ejecutar("equipo/quitar", mapa("id", n(e, "id")));
        if (b(r, "ok")) {
            aviso(s(r, "mensaje"));
        } else {
            dialogos.mensaje("Error", s(r, "error"));
        }
    }

    private ComboBox<String> combo(List<String> textos, int seleccion, java.util.function.IntConsumer alCambiar) {
        ComboBox<String> c = new ComboBox<>(FXCollections.observableArrayList(textos));
        c.getSelectionModel().select(Math.max(0, Math.min(seleccion, textos.size() - 1)));
        c.setMinWidth(Region.USE_PREF_SIZE);
        c.setOnAction(e -> alCambiar.accept(c.getSelectionModel().getSelectedIndex()));
        return c;
    }

    String contexto(Map<String, Object> p) {
        List<String> partes = new ArrayList<>();
        partes.add(s(l(E, "fases").get(n(p, "fase")), "nombre"));
        if (!s(p, "grupo").isEmpty()) {
            partes.add("Grupo " + s(p, "grupo"));
        }
        if (!s(p, "nombreRonda").isEmpty()) {
            partes.add(s(p, "nombreRonda"));
        }
        return String.join(" · ", partes);
    }

    private Node vistaPartidos() {
        List<Map<String, Object>> fases = l(E, "fases");
        List<Map<String, Object>> equipos = l(E, "equipos");
        List<String> textosFase = new ArrayList<>(List.of("Todas las fases"));
        fases.forEach(f -> textosFase.add(s(f, "nombre")));
        List<String> textosEquipo = new ArrayList<>(List.of("Todos los equipos"));
        equipos.forEach(e -> textosEquipo.add(s(e, "nombre")));
        String[] estados = {"todos", "pendientes", "jugados", "aplazados"};
        ComboBox<String> cFase = combo(textosFase, filtroFase + 1, i -> { filtroFase = i - 1; mostrarVista("partidos"); });
        ComboBox<String> cEstado = combo(List.of("Todos", "Pendientes", "Jugados", "Aplazados / suspendidos"),
                List.of(estados).indexOf(filtroEstado), i -> { filtroEstado = estados[i]; mostrarVista("partidos"); });
        int indiceEquipo = 0;
        for (int i = 0; i < equipos.size(); i++) {
            if (n(equipos.get(i), "id") == filtroEquipo) {
                indiceEquipo = i + 1;
            }
        }
        ComboBox<String> cEquipo = combo(textosEquipo, indiceEquipo, i -> {
            filtroEquipo = i == 0 ? -1 : n(equipos.get(i - 1), "id");
            mostrarVista("partidos");
        });
        VBox caja = seccion("Partidos", "Toque un partido para registrar el resultado, las tarjetas y la programación.", cFase, cEstado, cEquipo);
        Map<String, List<Map<String, Object>>> bloques = new LinkedHashMap<>();
        for (Map<String, Object> p : l(E, "partidos")) {
            boolean jugado = b(p, "jugado");
            String est = s(p, "estado");
            boolean pasa = (filtroFase < 0 || n(p, "fase") == filtroFase)
                    && (filtroEquipo < 0 || n(p, "local") == filtroEquipo || n(p, "visitante") == filtroEquipo)
                    && (filtroEstado.equals("todos") || (filtroEstado.equals("pendientes") && !jugado)
                    || (filtroEstado.equals("jugados") && jugado)
                    || (filtroEstado.equals("aplazados") && (est.equals("APLAZADO") || est.equals("SUSPENDIDO"))));
            if (pasa) {
                bloques.computeIfAbsent(contexto(p), k -> new ArrayList<>()).add(p);
            }
        }
        if (bloques.isEmpty()) {
            caja.getChildren().add(vacio(b(m(E, "campeonato"), "iniciado") ? "No hay partidos con estos filtros."
                    : "📅  El fixture se genera automáticamente al pulsar «Iniciar campeonato»."));
            return caja;
        }
        bloques.forEach((tituloBloque, partidos) -> {
            FlowPane grid = new FlowPane(14, 14);
            partidos.forEach(p -> grid.getChildren().add(tarjetaPartido(p)));
            caja.getChildren().add(new VBox(10, etiqueta(tituloBloque.toUpperCase(), "titulo-bloque"), grid));
        });
        return caja;
    }

    static Label etiquetaEstado(String estado) {
        String texto = switch (estado) {
            case "JUGADO" -> "Jugado";
            case "APLAZADO" -> "Aplazado";
            case "SUSPENDIDO" -> "Suspendido";
            default -> "Pendiente";
        };
        return etiqueta(texto, "etiqueta", "etiqueta-" + estado.toLowerCase());
    }

    private Node tarjetaPartido(Map<String, Object> p) {
        boolean jugado = b(p, "jugado");
        Label local = etiqueta(s(p, "localNombre"), "equipo-partido");
        Label visitante = etiqueta(s(p, "visitanteNombre"), "equipo-partido");
        if (n(p, "ganador") == n(p, "local")) {
            local.getStyleClass().add("ganador");
        }
        if (n(p, "ganador") == n(p, "visitante")) {
            visitante.getStyleClass().add("ganador");
        }
        local.setAlignment(Pos.CENTER_RIGHT);
        local.setPrefWidth(120);
        visitante.setPrefWidth(120);
        VBox marcador = new VBox(etiqueta(jugado ? n(p, "gl") + " : " + n(p, "gv") : "VS", jugado ? "led" : "vs"));
        if (b(p, "penales")) {
            marcador.getChildren().add(etiqueta(n(p, "pl") + "-" + n(p, "pv") + " pen", "penales-texto"));
        }
        marcador.setAlignment(Pos.CENTER);
        marcador.getStyleClass().add("resultado");
        HBox centro = new HBox(12, local, marcador, visitante);
        centro.setAlignment(Pos.CENTER);
        HBox meta = new HBox(8, etiqueta(Datos.fecha(p), "sutil", "pequeno"), espacio(), etiquetaEstado(s(p, "estado")));
        VBox tarjeta = new VBox(10, meta, centro);
        String obs = String.join(" · ", List.of(s(p, "sede"), s(p, "obs")).stream().filter(x -> !x.isEmpty()).toList());
        if (!obs.isEmpty()) {
            tarjeta.getChildren().add(etiqueta(obs, "sutil", "pequeno"));
        }
        tarjeta.getStyleClass().addAll("vidrio", "partido");
        if (jugado) {
            tarjeta.getStyleClass().add("jugado");
        }
        tarjeta.setPrefWidth(380);
        tarjeta.setOnMouseClicked(e -> dialogos.partido(p));
        return tarjeta;
    }

    static TableView<Map<String, Object>> tabla(List<Map<String, Object>> filas, String[][] columnas) {
        TableView<Map<String, Object>> t = new TableView<>(FXCollections.observableArrayList(filas));
        for (String[] col : columnas) {
            TableColumn<Map<String, Object>, String> c = new TableColumn<>(col[0]);
            c.setCellValueFactory(d -> new SimpleStringProperty(col[1].equals("#")
                    ? String.valueOf(t.getItems().indexOf(d.getValue()) + 1) : s(d.getValue(), col[1])));
            c.setSortable(false);
            if (col.length > 2) {
                c.getStyleClass().add("num");
                c.setPrefWidth(Double.parseDouble(col[2]));
            }
            t.getColumns().add(c);
        }
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        t.setPlaceholder(etiqueta("Sin datos.", "sutil"));
        t.setFixedCellSize(40);
        t.prefHeightProperty().bind(t.fixedCellSizeProperty().multiply(Math.max(filas.size(), 3)).add(46));
        return t;
    }

    static final String[][] COLUMNAS_TABLA = {{"#", "#", "50"}, {"Equipo", "nombre"}, {"PJ", "pj", "60"}, {"G", "g", "55"},
        {"E", "e", "55"}, {"P", "p", "55"}, {"GF", "gf", "60"}, {"GC", "gc", "60"}, {"DG", "dg", "60"}, {"TA", "ta", "55"},
        {"TR", "tr", "55"}, {"Pts", "pts", "65"}};

    private Node vistaPosiciones() {
        List<Map<String, Object>> tablas = l(E, "tablas");
        tablaElegida = Math.min(tablaElegida, tablas.size() - 1);
        ComboBox<String> selector = combo(tablas.stream().map(t -> s(t, "texto")).toList(), tablaElegida,
                i -> { tablaElegida = i; mostrarVista("posiciones"); });
        VBox caja = seccion("Tabla de posiciones", null, selector);
        Map<String, Object> t = tablas.get(Math.max(0, tablaElegida));
        int directos = n(t, "directos");
        int repechaje = n(t, "repechaje");
        TableView<Map<String, Object>> tabla = tabla(l(t, "filas"), COLUMNAS_TABLA);
        tabla.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Map<String, Object> fila, boolean vacia) {
                super.updateItem(fila, vacia);
                getStyleClass().removeAll("clasifica", "repechaje");
                if (!vacia && fila != null) {
                    int i = getIndex();
                    if (i < directos) {
                        getStyleClass().add("clasifica");
                    } else if (i < directos + repechaje) {
                        getStyleClass().add("repechaje");
                    }
                }
            }
        });
        String leyenda = s(E, "leyenda") + (directos > 0 ? " Verde: clasifica." : "") + (repechaje > 0 ? " Ámbar: repechaje." : "");
        caja.getChildren().addAll(tabla, etiqueta(leyenda, "sutil", "pequeno"));
        return caja;
    }

    private Node vistaLlaves() {
        VBox caja = seccion("Llaves", null);
        Map<String, Object> c = m(E, "campeonato");
        if (b(c, "terminado")) {
            VBox campeon = new VBox(4, etiqueta("🏆", "trofeo"), etiqueta("CAMPEÓN", "sobretitulo", "oro"), etiqueta(s(c, "campeonNombre"), "campeon-nombre"));
            campeon.setAlignment(Pos.CENTER);
            campeon.getStyleClass().add("campeon-caja");
            caja.getChildren().add(campeon);
        }
        List<Map<String, Object>> llaves = l(E, "llaves");
        if (llaves.isEmpty()) {
            caja.getChildren().add(vacio("Todavía no hay llaves de eliminación. Aparecen aquí cuando el campeonato llega a una fase de eliminación directa."));
        }
        for (Map<String, Object> ll : llaves) {
            VBox fase = new VBox(10, etiqueta(s(ll, "nombre"), "h3"));
            for (Map<String, Object> sec : l(ll, "secciones")) {
                if (!s(sec, "titulo").isEmpty()) {
                    fase.getChildren().add(etiqueta(s(sec, "titulo"), "sutil"));
                }
                HBox columnas = new HBox(36);
                for (Map<String, Object> col : l(sec, "columnas")) {
                    VBox columna = new VBox(14, etiqueta(s(col, "nombre").toUpperCase(), "titulo-ronda"));
                    columna.setAlignment(Pos.CENTER);
                    columna.setPrefWidth(230);
                    for (Object cruce : Datos.lista(col, "cruces")) {
                        VBox caja2 = new VBox();
                        caja2.getStyleClass().add("cruce");
                        for (Object o : (List<?>) cruce) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> lado = (Map<String, Object>) o;
                            HBox fila = new HBox(10, etiqueta(s(lado, "nombre"), "lado-nombre"), espacio(), etiqueta(s(lado, "goles"), "lado-goles"));
                            fila.getStyleClass().add("lado");
                            if (b(lado, "gana")) {
                                fila.getStyleClass().add("gana");
                            }
                            caja2.getChildren().add(fila);
                        }
                        columna.getChildren().add(caja2);
                    }
                    columnas.getChildren().add(columna);
                }
                ScrollPane desplazable = new ScrollPane(columnas);
                desplazable.getStyleClass().add("desplazable");
                desplazable.setFitToHeight(true);
                fase.getChildren().add(desplazable);
            }
            Map<String, Object> vivos = m(ll, "vivos");
            if (vivos != null) {
                fase.getChildren().addAll(
                        etiqueta("Llave de ganadores (0 derrotas): " + unir(Datos.lista(vivos, "ganadores")), "sutil"),
                        etiqueta("Llave de perdedores (1 derrota): " + unir(Datos.lista(vivos, "perdedores")), "sutil"));
            }
            fase.getStyleClass().addAll("vidrio", "llave-fase");
            caja.getChildren().add(fase);
        }
        return caja;
    }

    private static String unir(List<Object> nombres) {
        return nombres.isEmpty() ? "—" : String.join(", ", nombres.stream().map(String::valueOf).toList());
    }

    private Node vistaSanciones() {
        Map<String, Object> sanciones = m(E, "sanciones");
        String[] filtros = {"todas", "rojas", "pendientes"};
        ComboBox<String> filtro = combo(List.of("Todas las tarjetas", "Solo rojas", "Suspensiones vigentes"),
                List.of(filtros).indexOf(filtroSanciones), i -> { filtroSanciones = filtros[i]; mostrarVista("sanciones"); });
        VBox caja = seccion("Tarjetas y sanciones", s(sanciones, "resumen"), filtro);
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Map<String, Object> r : l(sanciones, "registros")) {
            boolean roja = s(r, "tipo").equals("ROJA");
            if (filtroSanciones.equals("rojas") && !roja || filtroSanciones.equals("pendientes") && n(r, "pendientes") == 0) {
                continue;
            }
            filas.add(mapa("partido", s(r, "partido") + "  (" + s(r, "ronda") + ")", "fecha", Datos.fecha(r), "equipo", s(r, "equipo"),
                    "jugador", "#" + n(r, "dorsal") + " " + s(r, "jugador"), "tarjeta", roja ? "Roja" : "Amarilla",
                    "fechas", roja ? String.valueOf(n(r, "fechas")) : "—",
                    "estado", !roja ? "—" : n(r, "pendientes") > 0 ? "Le falta(n) " + n(r, "pendientes") : "Cumplida"));
        }
        TableView<Map<String, Object>> tabla = tabla(filas, new String[][]{{"Partido", "partido"}, {"Fecha", "fecha"},
            {"Equipo", "equipo"}, {"Jugador", "jugador"}, {"Tarjeta", "tarjeta"}, {"Fechas", "fechas", "80"}, {"Estado", "estado"}});
        tabla.setPlaceholder(etiqueta("No hay tarjetas registradas. Se agregan al editar un partido.", "sutil"));
        caja.getChildren().add(tabla);
        return caja;
    }

    private Node vistaReglas() {
        Map<String, Object> c = m(E, "campeonato");
        VBox formato = new VBox(10, etiqueta("FORMATO", "sobretitulo"), etiqueta(s(c, "formatoNombre"), "h3"),
                etiqueta(s(c, "formatoDescripcion"), "sutil"));
        for (Object regla : Datos.lista(c, "reglas")) {
            formato.getChildren().add(etiqueta("•  " + regla, "regla"));
        }
        formato.getStyleClass().addAll("vidrio", "tarjeta");
        Dialogos.EditorCriterios editor = new Dialogos.EditorCriterios(this);
        editor.poner(Datos.lista(c, "criterios").stream().map(String::valueOf).toList());
        Button aplicar = boton("Aplicar criterios");
        aplicar.setOnAction(e -> {
            if (l(E, "fases").size() > 1 && !dialogos.confirmar("Confirmar",
                    "Las tablas se recalcularán con el nuevo orden, pero las fases que ya se generaron no cambian. ¿Aplicar de todas formas?", "Aplicar")) {
                return;
            }
            Map<String, Object> r = ejecutar("criterios", mapa("criterios", editor.obtener()));
            if (b(r, "ok")) {
                aviso("Criterios de desempate actualizados.");
            } else {
                dialogos.mensaje("Error", s(r, "error"));
            }
        });
        VBox criterios = new VBox(10, etiqueta("CRITERIOS DE DESEMPATE", "sobretitulo"), etiqueta("Orden de aplicación", "h3"), editor, aplicar);
        criterios.getStyleClass().addAll("vidrio", "tarjeta");
        HBox.setHgrow(formato, Priority.ALWAYS);
        HBox.setHgrow(criterios, Priority.ALWAYS);
        formato.setPrefWidth(420);
        criterios.setPrefWidth(420);
        VBox caja = seccion("Reglas del campeonato", null);
        caja.getChildren().add(new HBox(18, formato, criterios));
        return caja;
    }

    String nombreCriterio(String clave) {
        for (Map<String, Object> c : l(catalogo, "criterios")) {
            if (s(c, "clave").equals(clave)) {
                return s(c, "texto");
            }
        }
        return clave;
    }

    Map<String, Object> equipoPorId(int id) {
        return l(E, "equipos").stream().filter(e -> n(e, "id") == id).findFirst().orElse(null);
    }
}
