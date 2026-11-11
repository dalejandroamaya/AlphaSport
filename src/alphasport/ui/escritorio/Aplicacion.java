package alphasport.ui.escritorio;

import static alphasport.ui.escritorio.Datos.b;
import static alphasport.ui.escritorio.Datos.l;
import static alphasport.ui.escritorio.Datos.m;
import static alphasport.ui.escritorio.Datos.mapa;
import static alphasport.ui.escritorio.Datos.n;
import static alphasport.ui.escritorio.Datos.s;

import alphasport.model.Usuario;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.Pane;
import javafx.scene.text.Font;
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
    /** Vistas que el visitante no ve (no le aportan: son de gestión). */
    static final java.util.Set<String> VISTAS_SOLO_ADMIN = java.util.Set.of("equipos", "reglas");
    /** Vista con la que entra el visitante. */
    static final String VISTA_INICIAL_VISITANTE = "partidos";
    static final String[] NOMBRES_VISTAS = {"Equipos", "Partidos", "Posiciones", "Llaves", "Estadísticas", "Reglas"};
    static final String[] ICONOS_VISTAS = {
        "M12 2 4 5v6c0 5 3.4 9.4 8 11 4.6-1.6 8-6 8-11V5l-8-3Z",
        "M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2ZM3 10h18M8 3v4M16 3v4",
        "M4 20V10M10 20V4M16 20v-7M22 20H2",
        "M3 4h5v6H3M3 14h5v6H3M8 7h4v10H8M12 12h5M17 9h4v6h-4",
        "M6.5 4h6a1.5 1.5 0 0 1 1.5 1.5v10a1.5 1.5 0 0 1-1.5 1.5h-6A1.5 1.5 0 0 1 5 15.5v-10A1.5 1.5 0 0 1 6.5 4ZM15 7h3a1.5 1.5 0 0 1 1.5 1.5v10a1.5 1.5 0 0 1-1.5 1.5h-6a1.5 1.5 0 0 1-1.5-1.5V18",
        "M6 3h9l4 4v14H6zM15 3v4h4M9 12h7M9 16h7"};
    /* Iconos de trazo (24x24) en lugar de emojis. */
    static final String ICONO_TROFEO = "M8 4h8v5a4 4 0 0 1-8 0V4ZM8 6H5a3 3 0 0 0 3 4M16 6h3a3 3 0 0 1-3 4M12 13v4M9 17h6v4H9z";
    static final String ICONO_LUNA = "M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z";
    static final String ICONO_MENU = "M4 7h16M4 12h16M4 17h16";
    static final String ICONO_PERSONA = "M12 4a4 4 0 1 1 0 8a4 4 0 1 1 0-8ZM4 21c0-4 4-6 8-6s8 2 8 6";
    static final String ICONO_SALE = "M17 4v14M13 14l4 4 4-4";
    static final String ICONO_ENTRA = "M7 20V6M3 10l4-4 4 4";
    static final String ICONO_DADO = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18ZM12 7l3.5 2.6-1.3 4.1H9.8L8.5 9.6Z";
    static final String ICONO_ESTADIO = "M3 10c0-2 4-4 9-4s9 2 9 4v7c0 2-4 4-9 4s-9-2-9-4zM3 10c0 2 4 4 9 4s9-2 9-4";

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
    private final Label chipEstado = new Label();
    private final Button btnCampeon = new Button("Ver campeón");
    private HBox cabeceraTorneo;
    private String vistaAnimada = "";
    private Region fotoFondo;
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
    private int fichaElegida = -1;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        servicio = Arranque.servicio();
        api = new ApiControlador(servicio, new AutenticacionService());
        catalogo = api.consultar("catalogo");
        dialogos = new Dialogos(this);
        cargarFuentes();
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

    /** Bricolage Grotesque (títulos y cifras) y Onest (interfaz), las mismas fuentes de la web, guardadas en web/fuentes. */
    private static void cargarFuentes() {
        Path carpeta = Arranque.CARPETA_WEB.resolve("fuentes");
        if (!Files.isDirectory(carpeta)) {
            return;
        }
        try (var archivos = Files.list(carpeta)) {
            archivos.filter(p -> p.toString().endsWith(".ttf")).forEach(p -> Font.loadFont(p.toUri().toString(), 14));
        } catch (Exception e) {
            // Sin las fuentes propias se usa Segoe UI, definido como respaldo en el CSS.
        }
    }

    static SVGPath icono(String trazo, String... clases) {
        SVGPath s = new SVGPath();
        s.setContent(trazo);
        s.getStyleClass().add("icono");
        s.getStyleClass().addAll(clases);
        return s;
    }

    /*
     * Escenario común a todas las pantallas: la foto del estadio (noche o día) levemente desenfocada,
     * un velo para la lectura y dos haces de reflector que se encienden al abrir y oscilan despacio.
     */
    private StackPane escenario(Node contenido) {
        fotoFondo = new Region();
        fotoFondo.getStyleClass().add("foto-fondo");
        fotoFondo.setEffect(new GaussianBlur(4));
        fotoFondo.setMouseTransparent(true);
        actualizarFoto();
        Region velo = new Region();
        velo.getStyleClass().add("velo");
        velo.setMouseTransparent(true);
        Pane capaFocos = new Pane(haz(true), haz(false));
        capaFocos.setMouseTransparent(true);
        StackPane s = new StackPane(fotoFondo, velo, capaFocos, contenido);
        s.setMinSize(0, 0);
        return s;
    }

    private SVGPath haz(boolean izquierdo) {
        SVGPath h = new SVGPath();
        h.setContent("M-26 0 L26 0 L330 1100 L-330 1100 Z");
        h.getStyleClass().add("haz");
        h.setEffect(new GaussianBlur(18));
        h.setOpacity(0);
        h.layoutXProperty().bind(scene.widthProperty().multiply(izquierdo ? 0.2 : 0.8));
        h.setLayoutY(-60);
        double giro = izquierdo ? -22 : 22;
        h.getTransforms().add(new javafx.scene.transform.Rotate(giro, 0, 0));
        FadeTransition encender = new FadeTransition(Duration.millis(1600), h);
        encender.setDelay(Duration.millis(izquierdo ? 150 : 350));
        encender.setFromValue(0);
        encender.setToValue(1);
        encender.setInterpolator(Interpolator.EASE_OUT);
        RotateTransition oscilar = new RotateTransition(Duration.seconds(14), h);
        oscilar.setByAngle(izquierdo ? 5 : -5);
        oscilar.setAutoReverse(true);
        oscilar.setCycleCount(RotateTransition.INDEFINITE);
        oscilar.setInterpolator(Interpolator.EASE_BOTH);
        encender.setOnFinished(e -> oscilar.play());
        encender.play();
        return h;
    }

    private void actualizarFoto() {
        if (fotoFondo == null) {
            return;
        }
        Path foto = Arranque.CARPETA_WEB.resolve("img").resolve(claro ? "estadio-dia.jpg" : "estadio-noche.jpg");
        fotoFondo.setStyle("-fx-background-image: url('" + foto.toUri() + "'); -fx-background-size: cover; -fx-background-position: center 62%;");
    }

    private void ponerRaiz(Parent raiz) {
        StackPane escena = escenario(raiz);
        escena.getStyleClass().add("raiz");
        if (claro) {
            escena.getStyleClass().add("claro");
        }
        scene.setRoot(escena);
    }

    void alternarTema() {
        claro = !claro;
        if (claro) {
            scene.getRoot().getStyleClass().add("claro");
        } else {
            scene.getRoot().getStyleClass().remove("claro");
        }
        actualizarFoto();
    }

    /** En la coronación, los focos del estadio se vuelven dorados. */
    void coronacion(boolean activa) {
        if (activa) {
            scene.getRoot().getStyleClass().add("coronacion");
        } else {
            scene.getRoot().getStyleClass().remove("coronacion");
        }
    }

    Button botonTema(String texto) {
        Button b = boton(texto, "fantasma");
        b.setGraphic(icono(ICONO_LUNA));
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

    /** Flechas de cambio: verde la del que entra, roja la del que sale. */
    static Node iconoCambio() {
        SVGPath entra = icono(ICONO_ENTRA, "icono-mini", "icono-entra");
        SVGPath sale = icono(ICONO_SALE, "icono-mini", "icono-sale");
        HBox h = new HBox(-6, entra, sale);
        h.setAlignment(Pos.CENTER);
        return h;
    }

    static Region espacio() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    /** Muestra u oculta las etiquetas de la cabecera (formato, equipos y estado del torneo). */
    private void mostrarDatosCabecera(boolean visibles) {
        for (Node nodo : List.of(chipEstado, chipFormato, chipEquipos, estado)) {
            nodo.setVisible(visibles);
            nodo.setManaged(visibles);
        }
    }

    /* ---------------------------------------------------------------- permisos */

    /** true si quien inicio sesion puede crear, editar o eliminar. */
    boolean puedeEditar() {
        Usuario u = api.getUsuarioActual();
        return u != null && u.puedeEditar();
    }

    /** true si el usuario actual puede abrir esa vista. */
    boolean puedeVer(String vista) {
        return puedeEditar() || !VISTAS_SOLO_ADMIN.contains(vista);
    }

    /** Oculta (y saca del layout) los controles de edicion cuando el usuario es visitante. */
    void soloAdmin(Node... nodos) {
        boolean admin = puedeEditar();
        for (Node nodo : nodos) {
            nodo.setVisible(admin);
            nodo.setManaged(admin);
        }
    }

    /** Lo mismo para opciones de menu (MenuItem no es un Node). */
    void soloAdmin(MenuItem... items) {
        for (MenuItem item : items) {
            item.setVisible(puedeEditar());
        }
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
        Button visitante = boton("Entrar como visitante (solo lectura)", "fantasma", "ancho");
        visitante.setMaxWidth(Double.MAX_VALUE);
        visitante.setOnAction(e -> {
            try {
                api.ejecutar("login/visitante", mapa());
                mostrarPrincipal();
            } catch (Exception ex) {
                error.setText(ex.getMessage());
                error.setVisible(true);
            }
        });
        VBox tarjeta = new VBox(12, logo, etiqueta("Gestión profesional de campeonatos de fútbol", "sutil", "centro"),
                etiqueta("Usuario", "campo-titulo"), usuario, etiqueta("Contraseña", "campo-titulo"), clave, error, entrar, visitante);
        tarjeta.setAlignment(Pos.CENTER);
        tarjeta.getChildren().stream().filter(x -> x.getStyleClass().contains("campo-titulo"))
                .forEach(x -> ((Label) x).setMaxWidth(Double.MAX_VALUE));
        tarjeta.getStyleClass().addAll("vidrio", "login-tarjeta");
        tarjeta.setMaxSize(440, Region.USE_PREF_SIZE);
        Button tema = botonTema("");
        tema.getStyleClass().add("icono-solo");
        StackPane.setAlignment(tema, Pos.TOP_RIGHT);
        StackPane.setMargin(tema, new Insets(18));
        Label credito = etiqueta(claro ? "Foto: Miguel Ramírez · Unsplash" : "Foto: Josemar Duarte · Unsplash", "credito-foto");
        StackPane.setAlignment(credito, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(credito, new Insets(0, 16, 12, 0));
        StackPane fondo = new StackPane(tarjeta, tema, credito);
        fondo.getStyleClass().add("login");
        ponerRaiz(fondo);
        entrada(tarjeta, 350);
        usuario.requestFocus();
    }

    /* ---------------------------------------------------------------- estructura principal */

    void mostrarPrincipal() {
        E = api.consultar("estado");
        ImageView logo = new ImageView(imagen("logo.png"));
        logo.setFitWidth(150);
        logo.setPreserveRatio(true);
        logo.getStyleClass().add("logo-lateral");
        logo.setFitWidth(128);
        VBox marca = new VBox(2, logo, etiqueta("AlphaSport", "marca-nombre"), etiqueta("Gestor de campeonatos", "marca-sub"));
        marca.setAlignment(Pos.CENTER);
        marca.getStyleClass().add("marca-lateral");

        VBox nav = new VBox(6);
        ToggleGroup grupo = new ToggleGroup();
        navegacion.clear();
        if (!puedeVer(vista)) {
            vista = VISTA_INICIAL_VISITANTE;
        }
        for (int i = 0; i < VISTAS.length; i++) {
            String clave = VISTAS[i];
            if (!puedeVer(clave)) {
                continue; // el visitante no ve Equipos ni Reglas
            }
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
        Button historial = boton("Historial", "fantasma");
        historial.setGraphic(icono(ICONO_TROFEO));
        historial.setMaxWidth(Double.MAX_VALUE);
        historial.setOnAction(e -> dialogos.historial());
        Button tema = botonTema("Modo claro / oscuro");
        tema.setMaxWidth(Double.MAX_VALUE);
        Label modo = etiqueta("Escritorio Java", "chip", "chip-modo");
        modo.setMaxWidth(Double.MAX_VALUE);
        modo.setAlignment(Pos.CENTER);
        Usuario usuario = api.getUsuarioActual();
        Label chipRol = etiqueta(usuario.getRol().getNombre() + (usuario.puedeEditar() ? "" : " · solo lectura"), "chip");
        chipRol.setMaxWidth(Double.MAX_VALUE);
        chipRol.setAlignment(Pos.CENTER);
        VBox lateral = new VBox(22, marca, nav, espacio(), new VBox(8, chipRol, historial, tema, modo));
        lateral.getStyleClass().addAll("vidrio", "barra-lateral");
        lateral.setPrefWidth(248);
        lateral.setMinWidth(248);
        BorderPane.setMargin(lateral, new Insets(20, 0, 20, 20));

        titulo.getStyleClass().add("titulo");
        titulo.setWrapText(true);
        chipEstado.getStyleClass().addAll("chip", "chip-estado");
        chipFormato.getStyleClass().add("chip");
        chipEquipos.getStyleClass().add("chip");
        estado.getStyleClass().add("estado-vivo");
        javafx.scene.shape.Circle punto = new javafx.scene.shape.Circle(4.5);
        punto.getStyleClass().add("punto-vivo");
        estado.setGraphic(punto);
        estado.setGraphicTextGap(9);
        estado.setWrapText(true);
        // El estado va en un chip junto al formato: sin rótulos pequeños encima del título.
        VBox info = new VBox(8, titulo, new HBox(8, chipEstado, chipFormato, chipEquipos), estado);
        HBox.setHgrow(info, Priority.ALWAYS);
        btnAvanzar.getStyleClass().addAll("btn", "primario", "grande");
        btnAvanzar.setOnAction(e -> avanzar());
        btnCampeon.getStyleClass().addAll("btn", "dorado");
        btnCampeon.setMinWidth(Region.USE_PREF_SIZE);
        btnCampeon.setOnAction(e -> dialogos.verCampeon());
        soloAdmin(btnAvanzar);
        HBox acciones = new HBox(10, btnAvanzar, btnCampeon, menuArchivo());
        acciones.setAlignment(Pos.CENTER_RIGHT);
        cabeceraTorneo = new HBox(20, info, acciones);
        cabeceraTorneo.setAlignment(Pos.CENTER_LEFT);
        cabeceraTorneo.getStyleClass().addAll("vidrio", "marcador-principal");

        ScrollPane desplazable = new ScrollPane(contenido);
        desplazable.setFitToWidth(true);
        desplazable.getStyleClass().add("desplazable");
        VBox centro = new VBox(20, cabeceraTorneo, desplazable);
        VBox.setVgrow(desplazable, Priority.ALWAYS);
        centro.setPadding(new Insets(20, 20, 20, 20));

        BorderPane armazon = new BorderPane(centro);
        armazon.setLeft(lateral);
        entrada(lateral, 0);
        entrada(cabeceraTorneo, 80);
        toast.getStyleClass().add("aviso");
        toast.setOpacity(0);
        toast.setMouseTransparent(true);
        StackPane.setAlignment(toast, Pos.BOTTOM_CENTER);
        StackPane.setMargin(toast, new Insets(0, 0, 28, 0));
        StackPane raiz = new StackPane(armazon, toast);
        raiz.getStyleClass().add("fondo-app");
        ponerRaiz(raiz);
        if (b(E, "hay")) {
            render();
        } else if (puedeEditar()) {
            dialogos.nuevo(true, false);
        } else {
            titulo.setText("Sin campeonato activo");
            // Sin campeonato no hay formato, equipos, estado ni campeón que mostrar en la cabecera.
            mostrarDatosCabecera(false);
            btnCampeon.setVisible(false);
            btnCampeon.setManaged(false);
            contenido.getChildren().setAll(vacio("Todavía no hay un campeonato en curso. Vuelva más tarde."));
        }
    }

    private MenuButton menuArchivo() {
        MenuButton menu = new MenuButton("Archivo");
        menu.setGraphic(icono(ICONO_MENU));
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
        sesion.setOnAction(e -> {
            try {
                api.ejecutar("logout", mapa());
            } catch (Exception ignorado) {
                // cerrar sesion nunca falla
            }
            mostrarLogin();
        });
        soloAdmin(nuevo, itemSiguiente, cambiar, cancelar, abrir);
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
        mostrarDatosCabecera(true);
        titulo.setText(s(c, "nombre"));
        stage.setTitle(s(c, "nombre") + " · AlphaSport");
        chipFormato.setText(s(c, "formatoNombre"));
        chipEquipos.setText(n(c, "equiposActivos") + " equipos");
        estado.setText(s(c, "estado"));
        boolean terminado = b(c, "terminado");
        chipEstado.setText(terminado ? "Terminado" : b(c, "iniciado") ? "En curso" : "Por iniciar");
        btnAvanzar.setText(terminado ? "Terminar e iniciar otro" : b(c, "iniciado") ? "Cerrar ronda y avanzar" : "Iniciar campeonato");
        btnAvanzar.getStyleClass().remove("dorado");
        if (terminado) {
            btnAvanzar.getStyleClass().add("dorado");
        }
        // «Ver campeón» y el dorado de la cabecera solo existen cuando hay campeón.
        btnCampeon.setVisible(terminado);
        btnCampeon.setManaged(terminado);
        if (cabeceraTorneo != null) {
            cabeceraTorneo.getStyleClass().remove("terminado");
            if (terminado) {
                cabeceraTorneo.getStyleClass().add("terminado");
            }
        }
        estado.getStyleClass().removeAll("en-juego", "terminado");
        estado.getStyleClass().add(terminado ? "terminado" : "en-juego");
        itemSiguiente.setDisable(!b(c, "puedeSiguiente"));
        mostrarVista(vista);
    }

    void mostrarVista(String nombre) {
        if (!puedeVer(nombre)) {
            nombre = VISTA_INICIAL_VISITANTE;
        }
        vista = nombre;
        ToggleButton b = navegacion.get(nombre);
        if (b != null) {
            b.setSelected(true);
        }
        if (!b(E, "hay")) {
            // El visitante no puede crear campeonatos: en cualquier vista ve el mismo aviso (antes quedaba en blanco).
            if (puedeEditar()) {
                contenido.getChildren().clear();
            } else {
                contenido.getChildren().setAll(vacio("Todavía no hay un campeonato en curso. Vuelva más tarde."));
            }
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
        // La vista entra subiendo y aclarándose, solo al cambiar de sección (no en cada guardado).
        if (!nombre.equals(vistaAnimada)) {
            vistaAnimada = nombre;
            entrada(nodo, 0);
        }
    }

    /** Entrada suave: sube 14 px y aparece, con la curva de la web (ease-out). */
    static void entrada(Node nodo, int retraso) {
        nodo.setOpacity(0);
        nodo.setTranslateY(14);
        FadeTransition aparecer = new FadeTransition(Duration.millis(550), nodo);
        aparecer.setToValue(1);
        TranslateTransition subir = new TranslateTransition(Duration.millis(550), nodo);
        subir.setToY(0);
        ParallelTransition t = new ParallelTransition(aparecer, subir);
        t.setInterpolator(Interpolator.SPLINE(.22, 1, .36, 1));
        aparecer.setInterpolator(Interpolator.SPLINE(.22, 1, .36, 1));
        subir.setInterpolator(Interpolator.SPLINE(.22, 1, .36, 1));
        t.setDelay(Duration.millis(retraso));
        t.play();
    }

    /* Cada vista abre con su propio panel de vidrio: título, ayuda y filtros adentro. */
    private VBox seccion(String titulo, String ayuda, Node... derecha) {
        VBox textos = new VBox(4, etiqueta(titulo, "h2"));
        if (ayuda != null) {
            textos.getChildren().add(etiqueta(ayuda, "sutil"));
        }
        HBox.setHgrow(textos, Priority.ALWAYS);
        HBox cabecera = new HBox(10, textos);
        cabecera.getChildren().addAll(derecha);
        cabecera.setAlignment(Pos.CENTER_LEFT);
        cabecera.getStyleClass().addAll("vidrio", "cabecera-vista");
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
        Button prueba = boton("Datos de prueba", "fantasma");
        prueba.setGraphic(icono(ICONO_DADO, "icono-mini"));
        prueba.setTooltip(new javafx.scene.control.Tooltip("Crear equipos inventados con plantilla completa a partir de una semilla"));
        // Con el campeonato iniciado los datos de prueba solo completan plantillas vacías.
        prueba.setDisable(iniciado && l(E, "equipos").stream().allMatch(e -> b(e, "retirado") || !l(e, "jugadores").isEmpty()));
        prueba.setOnAction(e -> dialogos.datosPrueba());
        soloAdmin(nombre, agregar, completo, prueba);
        VBox caja = seccion("Equipos", !puedeEditar() ? "Equipos inscritos, con su director técnico y plantilla."
                : iniciado
                ? "El campeonato ya inició: puede editar nombre, DT y jugadores, pero no agregar equipos nuevos."
                : "Registre los equipos (mínimo 2) y pulse «Iniciar campeonato» para generar el fixture automáticamente.",
                nombre, agregar, completo, prueba);
        List<Map<String, Object>> equipos = l(E, "equipos");
        if (equipos.isEmpty()) {
            caja.getChildren().add(vacio("Todavía no hay equipos. Escriba un nombre arriba y pulse «Agregar»."));
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
        int jugadores = l(e, "jugadores").size();
        Label dtTexto = etiqueta("DT: " + (dt == null ? "Sin asignar" : s(dt, "nombre")) + " · " + (jugadores == 1 ? "1 jugador" : jugadores + " jugadores"), "sutil", "pequeno");
        dtTexto.setGraphic(icono(ICONO_PERSONA, "icono-mini"));
        Label estadioTexto = etiqueta(s(e, "estadio").isEmpty() ? "Sin estadio" : s(e, "estadio"), "sutil", "pequeno");
        estadioTexto.setGraphic(icono(ICONO_ESTADIO, "icono-mini"));
        HBox nombreFila = new HBox(8, etiqueta(s(e, "nombre"), "h3"));
        nombreFila.setAlignment(Pos.CENTER_LEFT);
        if (b(e, "retirado")) {
            nombreFila.getChildren().add(etiqueta("Retirado", "etiqueta", "etiqueta-retirado"));
        }
        VBox datos = new VBox(4, nombreFila, dtTexto, estadioTexto);
        HBox.setHgrow(datos, Priority.ALWAYS);
        Button editar = boton("Editar", "mini");
        editar.setOnAction(ev -> dialogos.equipo(e));
        HBox botones = new HBox(8, espacio(), editar);
        soloAdmin(botones);
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
        ComboBox<String> cEstado = combo(List.of("Todos los estados", "Pendientes", "Jugados", "Aplazados / suspendidos"),
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
        VBox caja = seccion("Partidos", puedeEditar() ? "Abra un partido para registrar el resultado, las tarjetas y la programación."
                : "Calendario y resultados. Toque cualquier partido para ver el cara a cara, la racha y las alineaciones.", cFase, cEstado, cEquipo);
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
                    : "El fixture se genera automáticamente al pulsar «Iniciar campeonato»."));
            return caja;
        }
        bloques.forEach((tituloBloque, partidos) -> {
            FlowPane grid = new FlowPane(14, 14);
            partidos.forEach(p -> grid.getChildren().add(tarjetaPartido(p)));
            caja.getChildren().add(new VBox(10, etiqueta(tituloBloque, "titulo-bloque"), grid));
        });
        return caja;
    }

    static Label etiquetaEstado(String estado) {
        String texto = switch (estado) {
            case "JUGADO" -> "Jugado";
            case "EN_JUEGO" -> "● En juego";
            case "APLAZADO" -> "Aplazado";
            case "SUSPENDIDO" -> "Suspendido";
            default -> "Pendiente";
        };
        return etiqueta(texto, "etiqueta", "etiqueta-" + estado.toLowerCase());
    }

    static boolean conMarcador(Map<String, Object> p) {
        return b(p, "jugado") || s(p, "estado").equals("EN_JUEGO");
    }

    /** Resumen corto: «2 cambios · 3 amarillas · 1 roja». */
    static String incidencias(Map<String, Object> p) {
        long rojas = l(p, "tarjetas").stream().filter(t -> s(t, "tipo").equals("ROJA")).count();
        long amarillas = l(p, "tarjetas").size() - rojas;
        int cambios = l(p, "cambios").size();
        List<String> partes = new ArrayList<>();
        if (cambios > 0) {
            partes.add(cambios == 1 ? "1 cambio" : cambios + " cambios");
        }
        if (amarillas > 0) {
            partes.add(amarillas == 1 ? "1 amarilla" : amarillas + " amarillas");
        }
        if (rojas > 0) {
            partes.add(rojas == 1 ? "1 roja" : rojas + " rojas");
        }
        return String.join(" · ", partes);
    }

    private Node tarjetaPartido(Map<String, Object> p) {
        boolean jugado = conMarcador(p);
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
        VBox marcador = new VBox(etiqueta(jugado ? n(p, "gl") + " : " + n(p, "gv") : "VS", jugado ? "cifras" : "vs"));
        if (b(p, "penales")) {
            marcador.getChildren().add(etiqueta(n(p, "pl") + "-" + n(p, "pv") + " pen", "penales-texto"));
        }
        marcador.setAlignment(Pos.CENTER);
        marcador.getStyleClass().add("resultado");
        HBox centro = new HBox(12, local, marcador, visitante);
        centro.setAlignment(Pos.CENTER);
        // La fecha solo se muestra si está definida: no repetir «Por definir» en cada partido.
        String fecha = s(p, "fecha").matches("\\d{4}-\\d{2}-\\d{2}") ? Datos.fecha(p) : "";
        HBox meta = new HBox(8, etiqueta(fecha, "sutil", "pequeno"), espacio(), etiquetaEstado(s(p, "estado")));
        VBox tarjeta = new VBox(10, meta, centro);
        String obs = String.join(" · ", List.of(s(p, "sede"), s(p, "obs")).stream().filter(x -> !x.isEmpty()).toList());
        if (!obs.isEmpty()) {
            tarjeta.getChildren().add(etiqueta(obs, "sutil", "pequeno"));
        }
        String resumen = jugado ? incidencias(p) : "";
        if (!resumen.isEmpty()) {
            Label inc = etiqueta(resumen, "sutil", "pequeno", "incidencias");
            inc.setMaxWidth(Double.MAX_VALUE);
            tarjeta.getChildren().add(inc);
        }
        tarjeta.getStyleClass().addAll("vidrio", "partido");
        if (b(p, "jugado")) {
            tarjeta.getStyleClass().add("jugado");
        }
        if (s(p, "estado").equals("EN_JUEGO")) {
            tarjeta.getStyleClass().add("en-vivo");
        }
        tarjeta.setPrefWidth(380);
        tarjeta.setOnMouseClicked(e -> abrirPartido(p));
        return tarjeta;
    }

    /** Administrador: editor del partido. Visitante: detalle estilo Flashscore (cualquier estado). */
    private void abrirPartido(Map<String, Object> p) {
        if (puedeEditar()) {
            dialogos.partido(p);
        } else {
            dialogos.detallePartido(p);
        }
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
            if (col[1].equals("pts") || col[1].equals("#")) {
                c.getStyleClass().add(col[1].equals("pts") ? "puntos" : "posicion");
            }
            t.getColumns().add(c);
        }
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        t.setPlaceholder(etiqueta("Sin datos.", "sutil"));
        t.setFixedCellSize(40);
        t.prefHeightProperty().bind(t.fixedCellSizeProperty().multiply(Math.max(filas.size(), 3)).add(72));
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
                getStyleClass().removeAll("clasifica", "repechaje", "lider");
                if (!vacia && fila != null) {
                    int i = getIndex();
                    // El líder queda bajo un foco dorado en cuanto se juega algún partido.
                    if (i == 0 && n(fila, "pj") > 0) {
                        getStyleClass().add("lider");
                    }
                    if (i < directos) {
                        getStyleClass().add("clasifica");
                    } else if (i < directos + repechaje) {
                        getStyleClass().add("repechaje");
                    }
                }
            }
        });
        String leyenda = s(E, "leyenda") + (directos > 0 ? " Verde: clasifica." : "") + (repechaje > 0 ? " Ámbar: repechaje." : "")
                + (!l(t, "filas").isEmpty() && n(l(t, "filas").get(0), "pj") > 0 ? " Dorado: líder." : "");
        caja.getChildren().addAll(tabla, etiqueta(leyenda, "sutil", "pequeno"));
        return caja;
    }

    private Node vistaLlaves() {
        VBox caja = seccion("Llaves", null);
        Map<String, Object> c = m(E, "campeonato");
        if (b(c, "terminado")) {
            HBox campeon = new HBox(18, Dialogos.trofeo(0.5), new VBox(2, etiqueta(s(c, "campeonNombre"), "campeon-nombre"), etiqueta("Campeón del torneo", "campeon-sub")));
            campeon.setAlignment(Pos.CENTER_LEFT);
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
        VBox todo = new VBox(26, vistaFicha(), vistaTarjetas());
        todo.getStyleClass().add("vista");
        return todo;
    }

    /** Estadísticas de un partido: marcador, cifras por equipo y los cambios y tarjetas minuto a minuto. */
    private Node vistaFicha() {
        List<Map<String, Object>> partidos = l(E, "partidos").stream().filter(Aplicacion::conMarcador).toList();
        if (partidos.isEmpty()) {
            VBox caja = seccion("Estadísticas del partido", "Escoja un partido para ver sus cambios y tarjetas minuto a minuto.");
            caja.getChildren().add(vacio("Todavía no hay partidos jugados ni en juego. Al registrar un resultado, sus cambios y tarjetas aparecen aquí."));
            return caja;
        }
        if (partidos.stream().noneMatch(p -> n(p, "id") == fichaElegida)) {
            Map<String, Object> vivo = partidos.stream().filter(p -> s(p, "estado").equals("EN_JUEGO")).findFirst().orElse(null);
            fichaElegida = n(vivo != null ? vivo : partidos.get(partidos.size() - 1), "id");
        }
        int indice = 0;
        for (int i = 0; i < partidos.size(); i++) {
            if (n(partidos.get(i), "id") == fichaElegida) {
                indice = i;
            }
        }
        ComboBox<String> selector = combo(partidos.stream().map(p -> (s(p, "estado").equals("EN_JUEGO") ? "En juego · " : "")
                + s(p, "localNombre") + " " + n(p, "gl") + "-" + n(p, "gv") + " " + s(p, "visitanteNombre") + " · "
                + (s(p, "nombreRonda").isEmpty() ? contexto(p) : s(p, "nombreRonda"))).toList(), indice,
                i -> { fichaElegida = n(partidos.get(i), "id"); mostrarVista("sanciones"); });
        selector.setPrefWidth(420);
        VBox caja = seccion("Estadísticas del partido", "Escoja un partido para ver sus cambios y tarjetas minuto a minuto.", selector);
        caja.getChildren().add(ficha(partidos.get(indice)));
        return caja;
    }

    private Node ficha(Map<String, Object> p) {
        int local = n(p, "local"), visitante = n(p, "visitante");
        List<Map<String, Object>> tarjetas = l(p, "tarjetas"), cambios = l(p, "cambios");

        String fecha = s(p, "fecha").matches("\\d{4}-\\d{2}-\\d{2}") ? Datos.fecha(p) : "";
        String meta = String.join(" · ", java.util.stream.Stream.of(contexto(p), fecha, s(p, "sede")).filter(x -> !x.isEmpty()).toList());
        HBox filaMeta = new HBox(8, etiqueta(meta, "sutil", "pequeno"), espacio(), etiquetaEstado(s(p, "estado")));
        filaMeta.setAlignment(Pos.CENTER_LEFT);

        Label nLocal = etiqueta(s(p, "localNombre"), "equipo-nombre");
        Label nVisitante = etiqueta(s(p, "visitanteNombre"), "equipo-nombre");
        if (n(p, "ganador") == local) {
            nLocal.getStyleClass().add("ganador");
        }
        if (n(p, "ganador") == visitante) {
            nVisitante.getStyleClass().add("ganador");
        }
        nLocal.setMaxWidth(Double.MAX_VALUE);
        nVisitante.setMaxWidth(Double.MAX_VALUE);
        nLocal.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(nLocal, Priority.ALWAYS);
        HBox.setHgrow(nVisitante, Priority.ALWAYS);
        VBox goles = new VBox(etiqueta(n(p, "gl") + " : " + n(p, "gv"), "ficha-goles"));
        if (b(p, "penales")) {
            goles.getChildren().add(etiqueta(n(p, "pl") + "-" + n(p, "pv") + " pen", "penales-texto"));
        }
        goles.setAlignment(Pos.CENTER);
        HBox marcador = new HBox(20, nLocal, goles, nVisitante);
        marcador.setAlignment(Pos.CENTER);
        marcador.getStyleClass().add("marcador-partido");

        java.util.function.BiFunction<Integer, String, Long> contar = (equipo, tipo) -> tipo.equals("CAMBIO")
                ? cambios.stream().filter(x -> n(x, "equipo") == equipo).count()
                : tarjetas.stream().filter(t -> n(t, "equipo") == equipo && (tipo.equals("ROJA") == s(t, "tipo").equals("ROJA"))).count();
        VBox cifras = new VBox(12);
        cifras.setMaxWidth(620);
        for (String[] c : new String[][]{{"Cambios", "CAMBIO"}, {"Amarillas", "AMARILLA"}, {"Rojas", "ROJA"}}) {
            cifras.getChildren().add(cifraDuelo(c[0], contar.apply(local, c[1]), contar.apply(visitante, c[1])));
        }
        HBox centroCifras = new HBox(cifras);
        centroCifras.setAlignment(Pos.CENTER);
        HBox.setHgrow(cifras, Priority.ALWAYS);

        // eventos ordenados por minuto (los que no tienen minuto van al final)
        List<Object[]> eventos = new ArrayList<>();
        for (Map<String, Object> x : cambios) {
            Label entra = etiqueta("Entra #" + n(x, "entraDorsal") + " " + s(x, "entraNombre"), "entra-texto");
            Label sale = etiqueta("Sale #" + n(x, "saleDorsal") + " " + s(x, "saleNombre"), "sale-texto");
            eventos.add(new Object[]{n(x, "minuto"), n(x, "equipo"), iconoCambio(), new VBox(1, entra, sale)});
        }
        for (Map<String, Object> t : tarjetas) {
            boolean roja = s(t, "tipo").equals("ROJA");
            Region carton = new Region();
            carton.getStyleClass().addAll("carton", roja ? "carton-roja" : "carton-amarilla");
            carton.setMinSize(12, 17);
            carton.setMaxSize(12, 17);
            carton.setRotate(-6);
            int fechas = n(t, "fechas");
            eventos.add(new Object[]{n(t, "minuto"), n(t, "equipo"), carton, new VBox(1, etiqueta("#" + n(t, "dorsal") + " " + s(t, "nombre"), "jugador-texto"),
                    etiqueta(roja ? "Roja · " + fechas + (fechas == 1 ? " fecha" : " fechas") + " fuera" : "Amarilla", "sutil", "pequeno"))});
        }
        eventos.sort(Comparator.comparingInt(e -> (int) e[0] > 0 ? (int) e[0] : 999));
        // Linea de tiempo: local a la izquierda, visitante a la derecha y el minuto en el eje central.
        javafx.scene.layout.GridPane linea = new javafx.scene.layout.GridPane();
        linea.setHgap(10);
        linea.setVgap(8);
        linea.getStyleClass().add("linea-tiempo");
        javafx.scene.layout.ColumnConstraints lado = new javafx.scene.layout.ColumnConstraints();
        lado.setPercentWidth(44);
        javafx.scene.layout.ColumnConstraints eje = new javafx.scene.layout.ColumnConstraints();
        eje.setPercentWidth(12);
        eje.setHalignment(javafx.geometry.HPos.CENTER);
        linea.getColumnConstraints().addAll(lado, eje, lado);
        int filaEvento = 0;
        for (Object[] e : eventos) {
            boolean deLocal = (int) e[1] == local;
            VBox textos = (VBox) e[3];
            HBox evento = deLocal ? new HBox(8, textos, (Node) e[2]) : new HBox(8, (Node) e[2], textos);
            evento.setAlignment(deLocal ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            textos.setAlignment(deLocal ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            evento.getStyleClass().add("evento");
            evento.setMaxWidth(Double.MAX_VALUE);
            Label minuto = etiqueta((int) e[0] > 0 ? e[0] + "′" : "–", "minuto-eje");
            minuto.setMinWidth(54);
            minuto.setAlignment(Pos.CENTER);
            linea.add(evento, deLocal ? 0 : 2, filaEvento);
            linea.add(minuto, 1, filaEvento);
            filaEvento++;
        }
        VBox ficha = new VBox(16, filaMeta, marcador, centroCifras);
        if (!Datos.lista(p, "titularesLocal").isEmpty() || !Datos.lista(p, "titularesVisitante").isEmpty()) {
            HBox alineaciones = new HBox(16, alineacion(p, local, s(p, "localNombre"), Datos.lista(p, "titularesLocal")),
                    alineacion(p, visitante, s(p, "visitanteNombre"), Datos.lista(p, "titularesVisitante")));
            ficha.getChildren().addAll(etiqueta("XI inicial", "h3"), alineaciones);
        }
        ficha.getChildren().add(etiqueta("Minuto a minuto", "h3"));
        ficha.getChildren().add(eventos.isEmpty()
                ? etiqueta("No se registraron cambios ni tarjetas en este partido. Se anotan al abrir el partido en Partidos.", "sutil")
                : linea);
        Button editar = boton("Editar partido", "mini", "fantasma");
        editar.setOnAction(e -> dialogos.partido(p));
        soloAdmin(editar);
        ficha.getChildren().add(new HBox(editar));
        ficha.getStyleClass().addAll("vidrio", "ficha");
        if (s(p, "estado").equals("EN_JUEGO")) {
            ficha.getStyleClass().add("en-vivo");
        }
        return ficha;
    }

    /** XI inicial de un equipo con quién salió y quién entró (y en qué minuto). */
    private Node alineacion(Map<String, Object> p, int idEquipo, String nombre, List<Object> ids) {
        List<Map<String, Object>> cambios = l(p, "cambios");
        List<Map<String, Object>> plantilla = l(equipoPorId(idEquipo), "jugadores");
        VBox columna = new VBox(5, etiqueta(nombre, "jugador-texto", "titulo-alineacion"));
        if (ids.isEmpty()) {
            columna.getChildren().add(etiqueta("Sin XI registrado.", "sutil", "pequeno"));
        }
        plantilla.stream().filter(j -> ids.contains(n(j, "id"))).sorted(Comparator.comparingInt(j -> n(j, "dorsal"))).forEach(j -> {
            Map<String, Object> salio = cambios.stream().filter(x -> n(x, "sale") == n(j, "id")).findFirst().orElse(null);
            HBox fila = new HBox(10, etiqueta(String.valueOf(n(j, "dorsal")), "dorsal"),
                    new VBox(0, etiqueta(s(j, "nombre"), "jugador-texto"), etiqueta(s(j, "posicion"), "sutil", "pequeno")), espacio());
            if (salio != null) {
                HBox marca = new HBox(4, icono(ICONO_SALE, "icono-mini", "icono-sale"), etiqueta(n(salio, "minuto") + "′", "sale-texto"));
                marca.setAlignment(Pos.CENTER);
                fila.getChildren().add(marca);
            }
            fila.setAlignment(Pos.CENTER_LEFT);
            fila.getStyleClass().add("fila-alineacion");
            columna.getChildren().add(fila);
        });
        List<Map<String, Object>> entraron = cambios.stream().filter(x -> n(x, "equipo") == idEquipo).toList();
        if (!entraron.isEmpty()) {
            columna.getChildren().add(etiqueta("Entraron", "sutil", "pequeno"));
            for (Map<String, Object> x : entraron) {
                HBox fila = new HBox(10, etiqueta(String.valueOf(n(x, "entraDorsal")), "dorsal"),
                        new VBox(0, etiqueta(s(x, "entraNombre"), "jugador-texto"), etiqueta("por #" + n(x, "saleDorsal"), "sutil", "pequeno")),
                        espacio(), etiqueta(n(x, "minuto") + "′", "entra-texto"));
                fila.setAlignment(Pos.CENTER_LEFT);
                fila.getStyleClass().addAll("fila-alineacion", "entro");
                columna.getChildren().add(fila);
            }
        }
        columna.setPrefWidth(1);
        HBox.setHgrow(columna, Priority.ALWAYS);
        return columna;
    }

    /** Fila «2  Cambios  0» con una barra a cada lado, como en las transmisiones. */
    private static Node cifraDuelo(String titulo, long izquierda, long derecha) {
        long total = izquierda + derecha;
        Label l = etiqueta(String.valueOf(izquierda), "cifra-duelo");
        Label r = etiqueta(String.valueOf(derecha), "cifra-duelo");
        Label t = etiqueta(titulo, "sutil");
        Region medio = new Region();
        HBox.setHgrow(medio, Priority.ALWAYS);
        Region medio2 = new Region();
        HBox.setHgrow(medio2, Priority.ALWAYS);
        HBox textos = new HBox(l, medio, t, medio2, r);
        textos.setAlignment(Pos.CENTER);
        StackPane barraIzq = barra(total == 0 ? 0 : (double) izquierda / total, true);
        StackPane barraDer = barra(total == 0 ? 0 : (double) derecha / total, false);
        HBox.setHgrow(barraIzq, Priority.ALWAYS);
        HBox.setHgrow(barraDer, Priority.ALWAYS);
        HBox barras = new HBox(4, barraIzq, barraDer);
        return new VBox(4, textos, barras);
    }

    private static StackPane barra(double fraccion, boolean izquierda) {
        Region fondo = new Region();
        fondo.getStyleClass().add("barra-fondo");
        fondo.setMaxWidth(Double.MAX_VALUE);
        Region lleno = new Region();
        lleno.getStyleClass().add(izquierda ? "barra-local" : "barra-visitante");
        StackPane sp = new StackPane(fondo, lleno);
        sp.setAlignment(izquierda ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        sp.setPrefWidth(10);
        sp.setMinHeight(6);
        sp.setMaxHeight(6);
        lleno.maxWidthProperty().bind(sp.widthProperty().multiply(fraccion));
        lleno.setMinWidth(0);
        return sp;
    }

    private Node vistaTarjetas() {
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
                    "estado", !roja ? "—" : n(r, "pendientes") == 1 ? "Falta 1 fecha" : n(r, "pendientes") > 1 ? "Faltan " + n(r, "pendientes") + " fechas" : "Cumplida"));
        }
        TableView<Map<String, Object>> tabla = tabla(filas, new String[][]{{"Partido", "partido"}, {"Fecha", "fecha"},
            {"Equipo", "equipo"}, {"Jugador", "jugador"}, {"Tarjeta", "tarjeta"}, {"Fechas", "fechas", "80"}, {"Estado", "estado"}});
        tabla.setPlaceholder(etiqueta("No hay tarjetas registradas. Se agregan al editar un partido.", "sutil"));
        caja.getChildren().add(tabla);
        return caja;
    }

    private Node vistaReglas() {
        Map<String, Object> c = m(E, "campeonato");
        VBox formato = new VBox(10, etiqueta(s(c, "formatoNombre"), "h3"),
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
        editor.setDisable(!puedeEditar());
        soloAdmin(aplicar);
        VBox criterios = new VBox(10, etiqueta("Criterios de desempate", "h3"), etiqueta("Se aplican en este orden cuando dos equipos empatan en puntos.", "sutil"), editor, aplicar);
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
