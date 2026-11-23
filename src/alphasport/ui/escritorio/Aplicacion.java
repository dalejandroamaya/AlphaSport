package alphasport.ui.escritorio;

import static alphasport.ui.escritorio.Datos.b;
import static alphasport.ui.escritorio.Datos.l;
import static alphasport.ui.escritorio.Datos.m;
import static alphasport.ui.escritorio.Datos.mapa;
import static alphasport.ui.escritorio.Datos.n;
import static alphasport.ui.escritorio.Datos.s;

import alphasport.model.Usuario;
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

    static final String[] VISTAS = {"equipos", "partidos", "votaciones", "posiciones", "llaves", "sanciones", "noticias", "reglas"};
    /** Vistas que el visitante no ve (no le aportan: son de gestión). */
    static final java.util.Set<String> VISTAS_SOLO_ADMIN = java.util.Set.of("equipos", "reglas");
    /** Vistas que solo ve el visitante: el administrador no vota [REQ-07]. */
    static final java.util.Set<String> VISTAS_SOLO_VISITANTE = java.util.Set.of("votaciones");
    /** Vista con la que entra el visitante. */
    static final String VISTA_INICIAL_VISITANTE = "partidos";
    static final String[] NOMBRES_VISTAS = {"Equipos", "Partidos", "Votaciones", "Posiciones", "Llaves", "Estadísticas", "Noticias", "Reglas"};
    static final String[] ICONOS_VISTAS = {
        "M12 2 4 5v6c0 5 3.4 9.4 8 11 4.6-1.6 8-6 8-11V5l-8-3Z",
        "M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2ZM3 10h18M8 3v4M16 3v4",
        "M12 3l2.7 5.8 6.3.8-4.6 4.3 1.2 6.2L12 17l-5.6 3.1 1.2-6.2L3 9.6l6.3-.8Z",
        "M4 20V10M10 20V4M16 20v-7M22 20H2",
        "M3 4h5v6H3M3 14h5v6H3M8 7h4v10H8M12 12h5M17 9h4v6h-4",
        "M6.5 4h6a1.5 1.5 0 0 1 1.5 1.5v10a1.5 1.5 0 0 1-1.5 1.5h-6A1.5 1.5 0 0 1 5 15.5v-10A1.5 1.5 0 0 1 6.5 4ZM15 7h3a1.5 1.5 0 0 1 1.5 1.5v10a1.5 1.5 0 0 1-1.5 1.5h-6a1.5 1.5 0 0 1-1.5-1.5V18",
        "M4 5h13v14H6a2 2 0 0 1-2-2V5ZM17 9h3v8a2 2 0 0 1-2 2M7 9h7M7 13h7M7 16h4",
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
    private int pestanaEstadisticas; // pestaña elegida en Estadísticas (se conserva al redibujar)
    private String pestanaNoticias;  // «pendientes» o «publicadas» (null = se elige al entrar)
    private String pestanaPartidos = "previa"; // «previa» o «resultados» en Gestionar partidos
    private VistaVotaciones votaciones = new VistaVotaciones(this); // [REQ-07] recuerda las notas sin enviar

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        try {
            servicio = Arranque.servicio();
            api = new ApiControlador(servicio, Arranque.autenticacion(), Arranque.noticias());
        } catch (alphasport.persistence.PersistenciaException e) {
            avisarSinDatos(e);
            return;
        }
        // Si se pierde la conexión en medio de una consulta se avisa, en vez de fallar en silencio.
        Thread.currentThread().setUncaughtExceptionHandler((hilo, e) -> {
            if (e instanceof ReglaNegocioException) {
                aviso(e.getMessage());
            } else {
                e.printStackTrace();
            }
        });
        catalogo = api.consultar("catalogo");
        dialogos = new Dialogos(this);
        Fotos.usar(this);
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

    /** Sin datos no se abre la ventana principal: se explica qué revisar. */
    private static void avisarSinDatos(Exception e) {
        javafx.scene.control.Alert alerta = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR, e.getMessage());
        alerta.setTitle("AlphaSport");
        alerta.setHeaderText("No se pudieron abrir los datos");
        alerta.getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        alerta.showAndWait();
        javafx.application.Platform.exit();
    }

    String hojaEstilos() {
        URL recurso = getClass().getResource("escritorio.css");
        return recurso != null ? recurso.toExternalForm()
                : Path.of("src/alphasport/ui/escritorio/escritorio.css").toUri().toString();
    }

    static Image imagen(String nombre) {
        return new Image(Arranque.CARPETA_WEB.resolve("img").resolve(nombre).toUri().toString(), true);
    }

    /** Inter (regular, semibold y bold), la misma fuente de la web, guardada en web/fuentes. */
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

    /** true si el usuario actual puede abrir esa vista (y si es «Llaves», si el formato las tiene). */
    boolean puedeVer(String vista) {
        if (vista.equals("llaves") && !usaLlaves()) {
            return false;
        }
        return puedeEditar() ? !VISTAS_SOLO_VISITANTE.contains(vista) : !VISTAS_SOLO_ADMIN.contains(vista);
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

    /** Vuelve a pedir el estado y dibuja (por ejemplo, cuando vence una votación sin que nadie haga nada). */
    void refrescar() {
        if (api.getUsuarioActual() != null) {
            E = api.consultar("estado");
            render();
        }
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

    private static void mostrarErrorLogin(Label error, Throwable ex) {
        if (!(ex instanceof alphasport.service.ReglaNegocioException)) {
            ex.printStackTrace(); // un error inesperado queda también en la consola para revisarlo
        }
        boolean malCompilado = String.valueOf(ex.getMessage()).contains("Unresolved compilation");
        error.setText(malCompilado
                ? "El programa quedó mal compilado. Ciérrelo, limpie la compilación en VS Code (Java: Clean Java Language Server Workspace) y ábralo de nuevo."
                : ex.getMessage() == null ? "No se pudo entrar: " + ex : ex.getMessage());
        error.setWrapText(true);
        error.setVisible(true);
    }

    void mostrarLogin() {
        ImageView logo = new ImageView(imagen("logo-texto.png"));
        logo.setFitWidth(260);
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
            } catch (Exception | Error ex) {
                // Nunca falla en silencio: si algo se rompe al entrar (por ejemplo, el programa quedó mal
                // compilado y lanza «Unresolved compilation problem»), se muestra aquí en vez de no hacer nada.
                mostrarErrorLogin(error, ex);
            }
        });
        Button visitante = boton("Entrar como visitante (solo lectura)", "fantasma", "ancho");
        visitante.setMaxWidth(Double.MAX_VALUE);
        visitante.setOnAction(e -> {
            try {
                api.ejecutar("login/visitante", mapa());
                mostrarPrincipal();
            } catch (Exception | Error ex) {
                mostrarErrorLogin(error, ex);
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
        votaciones = new VistaVotaciones(this); // otra sesión: sin las notas a medio elegir de la anterior
        ImageView logo = new ImageView(imagen("logo.png"));
        logo.setFitWidth(150);
        logo.setPreserveRatio(true);
        logo.getStyleClass().add("logo-lateral");
        logo.setFitWidth(112);
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
            if ((puedeEditar() ? VISTAS_SOLO_VISITANTE : VISTAS_SOLO_ADMIN).contains(clave)) {
                continue; // el visitante no ve Equipos ni Reglas; el administrador no ve Votaciones
            }
            SVGPath icono = new SVGPath();
            icono.setContent(ICONOS_VISTAS[i]);
            icono.getStyleClass().add("icono-nav");
            // El administrador gestiona los partidos (previa y resultados); el visitante solo los consulta.
            String nombre = clave.equals("partidos") && puedeEditar() ? "Gestionar partidos" : NOMBRES_VISTAS[i];
            ToggleButton b = new ToggleButton(nombre, icono);
            b.setGraphicTextGap(12);
            b.setToggleGroup(grupo);
            b.setMaxWidth(Double.MAX_VALUE);
            b.getStyleClass().add("nav");
            b.setOnAction(e -> mostrarVista(clave));
            navegacion.put(clave, b);
            nav.getChildren().add(b);
            // «Llaves» siempre se crea y solo se oculta: así aparece en cuanto el formato las tiene.
            b.setVisible(puedeVer(clave));
            b.setManaged(puedeVer(clave));
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
        scene.getRoot().getStyleClass().add("plano"); // la aplicación va sobre fondo liso; la foto queda en la entrada
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
            pestanaNoticias = null; // el próximo administrador entra a lo que necesite atención
            pestanaPartidos = "previa";
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

    /** Clausura de un torneo corto o nueva temporada de una liga (con ascensos y descensos si hay divisiones). */
    private void siguienteTorneo() {
        Map<String, Object> c = m(E, "campeonato");
        boolean liga = b(c, "esLiga");
        boolean conDivisiones = l(c, "divisiones").size() > 1;
        String actual = s(c, "nombre");
        // Nombre sugerido: «Apertura» -> «Clausura»; en una liga sube el número final («Temporada 1» -> «Temporada 2»).
        java.util.regex.Matcher numero = java.util.regex.Pattern.compile("(\\d+)$").matcher(actual);
        String sugerido = !liga ? actual.replace("Apertura", "Clausura")
                : numero.find() ? actual.substring(0, numero.start()) + (Long.parseLong(numero.group(1)) + 1) : actual + " 2";
        String texto = liga
                ? "Se creará la nueva temporada con los mismos equipos, plantillas y reglas."
                + (conDivisiones && n(c, "ascensos") > 0 ? "\nSuben los " + n(c, "ascensos") + " primeros y bajan los " + n(c, "ascensos") + " últimos de cada división." : "")
                + "\nNombre de la nueva temporada:"
                : "Se creará un torneo nuevo con los mismos equipos, plantillas y reglas.\nNombre del nuevo torneo:";
        String nombre = dialogos.pedirTexto(liga ? "Nueva temporada" : "Siguiente torneo corto", texto, sugerido);
        if (nombre == null || nombre.isBlank()) {
            return;
        }
        Map<String, Object> r = ejecutar("siguiente", mapa("nombre", nombre));
        if (!b(r, "ok")) {
            dialogos.mensaje("Error", s(r, "error"));
            return;
        }
        mostrarVista("equipos");
        if (!s(r, "mensaje").isEmpty()) {
            dialogos.mensaje("Ascensos y descensos", "Se creó «" + nombre + "». El torneo anterior quedó en el historial.\n\n" + s(r, "mensaje"));
        } else {
            aviso("Se creó «" + nombre + "». El torneo anterior quedó en el historial.");
        }
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
        itemSiguiente.setText(s(c, "textoSiguiente").isEmpty() ? "Siguiente torneo corto (Clausura)…" : s(c, "textoSiguiente"));
        // Sin llaves (formato Liga) el botón «Llaves» desaparece del menú lateral.
        ToggleButton botonLlaves = navegacion.get("llaves");
        if (botonLlaves != null) {
            botonLlaves.setVisible(puedeVer("llaves"));
            botonLlaves.setManaged(puedeVer("llaves"));
        }
        // «Votaciones (2)»: cuántos partidos puede votar el visitante ahora.
        ToggleButton botonVotaciones = navegacion.get("votaciones");
        if (botonVotaciones != null) {
            long abiertas = l(E, "partidos").stream().filter(TiempoVotacion::abierta).count();
            botonVotaciones.setText(abiertas == 0 ? "Votaciones" : "Votaciones (" + abiertas + ")");
        }
        mostrarVista(vista);
    }

    /** true si el formato del campeonato puede tener llaves (todos menos Liga). */
    boolean usaLlaves() {
        Map<String, Object> c = m(E, "campeonato");
        // sin campeonato no hay llaves que mostrar
        return c != null && !Boolean.FALSE.equals(c.get("usaLlaves"));
    }

    void mostrarVista(String nombre) {
        if (nombre.equals("llaves") && !usaLlaves()) {
            nombre = "posiciones"; // sin llaves, la tabla es donde se define el campeón
        }
        if (!puedeVer(nombre)) {
            nombre = VISTA_INICIAL_VISITANTE;
        }
        vista = nombre;
        ToggleButton b = navegacion.get(nombre);
        if (b != null) {
            b.setSelected(true);
        }
        // Las noticias no dependen del campeonato: se ven aunque no haya uno en curso.
        if (!b(E, "hay") && !nombre.equals("noticias")) {
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
            case "votaciones" -> vistaVotaciones();
            case "posiciones" -> vistaPosiciones();
            case "llaves" -> vistaLlaves();
            case "sanciones" -> vistaEstadisticas();
            case "reglas" -> vistaReglas();
            case "noticias" -> vistaNoticias();
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
        Map<String, Object> c = m(E, "campeonato");
        if (puedeEditar() && b(c, "esLiga")) {
            caja.getChildren().add(barraDivisiones(c));
        }
        List<Map<String, Object>> equipos = l(E, "equipos");
        if (equipos.isEmpty()) {
            caja.getChildren().add(vacio("Todavía no hay equipos. Escriba un nombre arriba y pulse «Agregar»."));
            return caja;
        }
        List<Map<String, Object>> divisiones = l(c, "divisiones");
        if (divisiones.size() <= 1) {
            FlowPane grid = new FlowPane(16, 16);
            for (int i = 0; i < equipos.size(); i++) {
                grid.getChildren().add(tarjetaEquipo(equipos.get(i), i, iniciado));
            }
            caja.getChildren().add(grid);
            return caja;
        }
        // Liga con divisiones: un bloque por división (Liga A, Liga B...).
        for (Map<String, Object> d : divisiones) {
            List<Map<String, Object>> deDivision = equipos.stream().filter(e -> n(e, "division") == n(d, "indice")).toList();
            HBox titulo = new HBox(10, etiqueta(s(d, "nombre"), "h3"),
                    etiqueta(deDivision.size() == 1 ? "1 equipo" : deDivision.size() + " equipos", "sutil"));
            titulo.setAlignment(Pos.BASELINE_LEFT);
            FlowPane grid = new FlowPane(16, 16);
            for (Map<String, Object> e : deDivision) {
                grid.getChildren().add(tarjetaEquipo(e, equipos.indexOf(e), iniciado));
            }
            if (deDivision.isEmpty()) {
                grid.getChildren().add(etiqueta("Sin equipos en esta división.", "sutil"));
            }
            caja.getChildren().add(new VBox(10, titulo, grid));
        }
        return caja;
    }

    /** Barra de divisiones (administrador, formato Liga): ver, agregar y quitar divisiones antes de iniciar. */
    private Node barraDivisiones(Map<String, Object> c) {
        List<Map<String, Object>> divisiones = l(c, "divisiones");
        boolean bloqueada = b(c, "iniciado");
        String ayuda = divisiones.size() > 1
                ? "Cada división es una liga aparte; el campeón sale de la " + s(divisiones.get(0), "nombre") + "."
                + (n(c, "ascensos") > 0 ? " En la siguiente temporada suben y bajan " + n(c, "ascensos") + " por división." : "")
                : "Liga de una sola división. Agregue divisiones para tener Liga A, Liga B, Liga C…";
        if (bloqueada) {
            ayuda += " Ya inició: los cambios de división se hacen en la siguiente temporada.";
        }
        Label textoAyuda = etiqueta(ayuda, "sutil", "pequeno");
        textoAyuda.setWrapText(true);
        VBox textos = new VBox(2, etiqueta("Divisiones", "jugador-texto", "titulo-alineacion"), textoAyuda);
        HBox.setHgrow(textos, Priority.ALWAYS);
        HBox chips = new HBox(6);
        for (Map<String, Object> d : divisiones) {
            Label chip = etiqueta(s(d, "nombre") + " · " + n(d, "equipos"), "chip");
            chip.setMinWidth(Region.USE_PREF_SIZE); // la ayuda larga no la aplasta letra por letra
            chips.getChildren().add(chip);
        }
        Button agregar = boton("+ Agregar división", "mini");
        agregar.setDisable(bloqueada || divisiones.size() >= n(c, "maximoDivisiones"));
        agregar.setOnAction(e -> accionDivision("division/agregar", mapa()));
        Button quitar = boton("Quitar la última", "mini", "fantasma");
        quitar.setDisable(bloqueada || divisiones.size() <= 1);
        quitar.setOnAction(e -> accionDivision("division/quitar", mapa()));
        HBox barra = new HBox(14, textos, chips, agregar, quitar);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.getStyleClass().addAll("vidrio", "barra-divisiones");
        return barra;
    }

    private void accionDivision(String ruta, Map<String, Object> cuerpo) {
        Map<String, Object> r = ejecutar(ruta, cuerpo);
        if (!b(r, "ok")) {
            dialogos.mensaje("Divisiones", s(r, "error"));
            return;
        }
        aviso(s(r, "mensaje").isEmpty() ? "Equipo movido de división." : s(r, "mensaje"));
    }

    static StackPane escudo(Map<String, Object> e, double tamano) {
        if (Fotos.escudo(s(e, "escudo"), tamano) instanceof StackPane imagen) {
            return imagen; // el escudo subido; si no hay (o no se pudo leer), el de iniciales
        }
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
        // Antes de iniciar, el administrador mueve el equipo a otra división.
        List<Map<String, Object>> divisiones = l(m(E, "campeonato"), "divisiones");
        if (divisiones.size() > 1 && !iniciado && !b(e, "retirado")) {
            ComboBox<String> division = combo(divisiones.stream().map(d -> s(d, "nombre")).toList(), n(e, "division"),
                    elegida -> accionDivision("equipo/division", mapa("id", n(e, "id"), "division", elegida)));
            botones.getChildren().add(0, division);
        }
        soloAdmin(botones);
        if (!b(e, "retirado")) {
            Button quitar = boton(iniciado ? "Retirar" : "Eliminar", "mini", "peligro");
            quitar.setOnAction(ev -> quitarEquipo(e, iniciado));
            botones.getChildren().add(quitar);
        }
        StackPane escudoTarjeta = escudo(e, 54);
        if (puedeEditar() && !b(e, "retirado")) {
            // Sin botón extra: se toca el escudo para subirlo, cambiarlo o eliminarlo.
            Fotos.tocable(escudoTarjeta, s(e, "escudo").isEmpty() ? "Subir el escudo de " + s(e, "nombre")
                    : "Cambiar o eliminar el escudo de " + s(e, "nombre"), () -> tocarEscudo(escudoTarjeta, e, null));
        }
        HBox fila = new HBox(14, escudoTarjeta, datos);
        fila.setAlignment(Pos.CENTER_LEFT);
        VBox tarjeta = new VBox(10, fila, botones);
        tarjeta.getStyleClass().addAll("vidrio", "tarjeta-equipo");
        if (b(e, "retirado")) {
            tarjeta.setOpacity(.5);
        }
        tarjeta.setPrefWidth(320);
        return tarjeta;
    }

    /**
     * Cambia o elimina el escudo guardado de un equipo (tocándolo en su tarjeta o en un partido).
     * {@code despues} se ejecuta si se guardó (por ejemplo, para redibujar el detalle del partido).
     */
    void tocarEscudo(Node ancla, Map<String, Object> e, Runnable despues) {
        Fotos.tocar(ancla, !s(e, "escudo").isEmpty(), true,
                imagen -> cambiarImagen("equipo/escudo", mapa("id", n(e, "id"), "imagen", imagen), "Escudo", despues),
                error -> dialogos.mensaje("Escudo", error));
    }

    /** Cambia o elimina la foto guardada de un jugador (tocándola en la alineación). */
    void tocarFotoJugador(Node ancla, int idEquipo, Map<String, Object> j, Runnable despues) {
        Fotos.tocar(ancla, !s(j, "foto").isEmpty(), false,
                imagen -> cambiarImagen("jugador/foto", mapa("equipo", idEquipo, "jugador", n(j, "id"), "imagen", imagen), "Foto del jugador", despues),
                error -> dialogos.mensaje("Foto del jugador", error));
    }

    private void cambiarImagen(String ruta, Map<String, Object> cuerpo, String titulo, Runnable despues) {
        Map<String, Object> r = ejecutar(ruta, cuerpo);
        if (!b(r, "ok")) {
            dialogos.mensaje(titulo, s(r, "error"));
            return;
        }
        aviso(s(r, "mensaje"));
        if (despues != null) {
            despues.run();
        }
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
            partes.add(s(p, "grupoNombre").isEmpty() ? "Grupo " + s(p, "grupo") : s(p, "grupoNombre")); // «Liga B» en divisiones
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
        // Filtros comunes (fase y equipo) y filtro de estado (solo donde hay partidos de todos los estados).
        java.util.function.Predicate<Map<String, Object>> faseYEquipo = p -> (filtroFase < 0 || n(p, "fase") == filtroFase)
                && (filtroEquipo < 0 || n(p, "local") == filtroEquipo || n(p, "visitante") == filtroEquipo);
        java.util.function.Predicate<Map<String, Object>> porEstado = p -> {
            boolean jugado = b(p, "jugado");
            String est = s(p, "estado");
            return filtroEstado.equals("todos") || (filtroEstado.equals("pendientes") && !jugado)
                    || (filtroEstado.equals("jugados") && jugado)
                    || (filtroEstado.equals("aplazados") && (est.equals("APLAZADO") || est.equals("SUSPENDIDO")));
        };
        List<Map<String, Object>> todos = l(E, "partidos");
        String sinFixture = "El fixture se genera automáticamente al pulsar «Iniciar campeonato».";
        boolean iniciado = b(m(E, "campeonato"), "iniciado");
        if (!puedeEditar()) {
            VBox caja = seccion("Partidos", "Calendario y resultados. Toque cualquier partido para ver la previa, el cara a cara, la racha y las alineaciones.",
                    cFase, cEstado, cEquipo);
            caja.getChildren().add(bloquesPartidos(todos.stream().filter(faseYEquipo.and(porEstado)).toList(), false,
                    iniciado ? "No hay partidos con estos filtros." : sinFixture));
            return caja;
        }
        // Administrador: dos pasos separados, como en Noticias. Primero la previa (antes del pitazo), después el resultado.
        VBox caja = seccion("Gestionar partidos", "Prepare cada partido en la «Previa» y, cuando se juegue, cargue lo ocurrido en «Registrar resultados».",
                cFase, cEquipo);
        List<Map<String, Object>> porJugar = todos.stream().filter(p -> !b(p, "jugado")).filter(faseYEquipo).toList();
        List<Map<String, Object>> resultados = todos.stream().filter(faseYEquipo.and(porEstado)).toList();
        VBox panelPrevia = panelPestana("Partidos del fixture que todavía no se juegan. Defina fecha, hora, sede, formación y XI titular de cada equipo.");
        panelPrevia.getChildren().add(bloquesPartidos(porJugar, true, !iniciado ? sinFixture
                : todos.stream().anyMatch(p -> !b(p, "jugado")) ? "No hay partidos por jugar con estos filtros." : "Todos los partidos ya se jugaron."));
        VBox panelResultados = panelPestana("Elija el partido que se jugó (o se está jugando) para cargar el marcador o el W.O., los goles, las tarjetas y los cambios.");
        HBox filtroResultados = new HBox(10, etiqueta("Mostrar", "sutil"), cEstado);
        filtroResultados.setAlignment(Pos.CENTER_LEFT);
        panelResultados.getChildren().addAll(filtroResultados, bloquesPartidos(resultados, false,
                iniciado ? "No hay partidos con estos filtros." : sinFixture));
        javafx.scene.control.Tab tabPrevia = new javafx.scene.control.Tab("Previa del partido (" + porJugar.size() + ")", panelPrevia);
        javafx.scene.control.Tab tabResultados = new javafx.scene.control.Tab("Registrar resultados", panelResultados);
        javafx.scene.control.TabPane pestanas = new javafx.scene.control.TabPane(tabPrevia, tabResultados);
        pestanas.setTabClosingPolicy(javafx.scene.control.TabPane.TabClosingPolicy.UNAVAILABLE);
        pestanas.getStyleClass().addAll("detalle-pestanas", "pestanas-noticias");
        pestanas.getSelectionModel().select(pestanaPartidos.equals("previa") ? tabPrevia : tabResultados);
        pestanas.getSelectionModel().selectedItemProperty().addListener((o, antes, ahora) ->
                pestanaPartidos = ahora == tabPrevia ? "previa" : "resultados");
        caja.getChildren().add(pestanas);
        return caja;
    }

    /** [REQ-07] Solo visitante: califica a los jugadores de los partidos con la votación abierta. */
    private Node vistaVotaciones() {
        VBox caja = seccion("Votaciones", "Califique del 1 al 10 a los jugadores de los partidos terminados antes de que cierre la votación.");
        caja.getChildren().add(votaciones.dibujar());
        return caja;
    }

    /** Panel de una pestaña con su explicación corta arriba. */
    private static VBox panelPestana(String ayuda) {
        VBox panel = new VBox(14);
        panel.getStyleClass().add("detalle-panel");
        Label texto = etiqueta(ayuda, "sutil");
        texto.setWrapText(true);
        panel.getChildren().add(texto);
        return panel;
    }

    /** Tarjetas de partidos agrupadas por fase / fecha. {@code previa}: cada tarjeta muestra cómo va su previa. */
    private Node bloquesPartidos(List<Map<String, Object>> partidos, boolean previa, String vacia) {
        if (partidos.isEmpty()) {
            return vacio(vacia);
        }
        Map<String, List<Map<String, Object>>> bloques = new LinkedHashMap<>();
        partidos.forEach(p -> bloques.computeIfAbsent(contexto(p), k -> new ArrayList<>()).add(p));
        VBox caja = new VBox(12);
        bloques.forEach((tituloBloque, lista) -> {
            // Un panel por ronda: barra con el título y los partidos en filas separadas por una línea fina.
            Label barra = etiqueta(tituloBloque.toUpperCase(), "titulo-bloque");
            barra.setMaxWidth(Double.MAX_VALUE);
            VBox filas = new VBox();
            for (int i = 0; i < lista.size(); i++) {
                Node fila = tarjetaPartido(lista.get(i), previa);
                if (i == lista.size() - 1) {
                    fila.getStyleClass().add("ultima");
                }
                filas.getChildren().add(fila);
            }
            VBox bloque = new VBox(barra, filas);
            bloque.getStyleClass().add("bloque-ronda");
            caja.getChildren().add(bloque);
        });
        return caja;
    }

    /** Fichas de la previa: formación y XI de cada equipo («4-4-2 · XI 11/11» o «XI 3/11»). */
    private VBox fichasPrevia(Map<String, Object> p) {
        HBox fichas = new HBox(6);
        fichas.setAlignment(Pos.CENTER);
        String[][] lados = {{"local", "titularesLocal", "formacionLocal"}, {"visitante", "titularesVisitante", "formacionVisitante"}};
        boolean todoListo = true;
        for (String[] lado : lados) {
            int plantilla = (int) l(equipoPorId(n(p, lado[0])), "jugadores").stream().filter(j -> n(j, "id") > 0).count();
            int necesarios = Math.min(alphasport.service.ReglasPartido.TITULARES, plantilla);
            int elegidos = Datos.lista(p, lado[1]).size();
            boolean listo = necesarios > 0 && elegidos == necesarios;
            todoListo &= listo && !s(p, lado[2]).isEmpty();
            String formacion = s(p, lado[2]).isEmpty() ? "Sin formación" : s(p, lado[2]);
            Label ficha = etiqueta(formacion + " · XI " + elegidos + "/" + necesarios, "etiqueta",
                    listo ? "etiqueta-jugado" : "etiqueta-pendiente");
            ficha.setMinWidth(Region.USE_PREF_SIZE); // en una sola línea
            fichas.getChildren().add(ficha);
        }
        VBox caja = new VBox(4, fichas, etiqueta(todoListo ? "Previa lista" : "Previa incompleta", "sutil", "pequeno"));
        caja.setAlignment(Pos.CENTER);
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

    private Node tarjetaPartido(Map<String, Object> p, boolean previa) {
        boolean jugado = conMarcador(p);
        Label local = etiqueta(s(p, "localNombre"), "equipo-partido");
        Label visitante = etiqueta(s(p, "visitanteNombre"), "equipo-partido");
        if (n(p, "ganador") == n(p, "local")) {
            local.getStyleClass().add("ganador");
        }
        if (n(p, "ganador") == n(p, "visitante")) {
            visitante.getStyleClass().add("ganador");
        }
        HBox filaLocal = new HBox(8, escudoFila(n(p, "local"), s(p, "localNombre")), local);
        HBox filaVisitante = new HBox(8, escudoFila(n(p, "visitante"), s(p, "visitanteNombre")), visitante);
        filaLocal.setAlignment(Pos.CENTER_LEFT);
        filaVisitante.setAlignment(Pos.CENTER_LEFT);
        VBox equipos = new VBox(4, filaLocal, filaVisitante);
        // La fecha solo se muestra si está definida: no repetir «Por definir» en cada partido.
        String fecha = s(p, "fecha").matches("\\d{4}-\\d{2}-\\d{2}") ? Datos.fecha(p) : "";
        VBox meta = new VBox(4);
        if (!fecha.isEmpty()) {
            meta.getChildren().add(etiqueta(fecha, "sutil", "pequeno"));
        }
        meta.getChildren().add(etiquetaEstado(s(p, "estado")));
        meta.setAlignment(Pos.CENTER_LEFT);
        meta.setMinWidth(150);
        meta.setPrefWidth(150);
        // Marcador apilado: los goles de cada equipo a la altura de su nombre.
        VBox marcador = new VBox(4);
        if (jugado) {
            Label gl = etiqueta(String.valueOf(n(p, "gl")), "cifras"), gv = etiqueta(String.valueOf(n(p, "gv")), "cifras");
            if (s(p, "estado").equals("EN_JUEGO")) {
                gl.getStyleClass().add("en-vivo");
                gv.getStyleClass().add("en-vivo");
            }
            marcador.getChildren().addAll(gl, gv);
            if (b(p, "penales")) {
                marcador.getChildren().add(etiqueta(n(p, "pl") + "-" + n(p, "pv") + " pen", "penales-texto"));
            }
        } else {
            marcador.getChildren().add(etiqueta("VS", "vs"));
        }
        marcador.setAlignment(Pos.CENTER_RIGHT);
        marcador.setMinWidth(40);
        marcador.getStyleClass().add("resultado");
        VBox columna = new VBox(4, equipos);
        String obs = String.join(" · ", List.of(s(p, "sede"), s(p, "obs")).stream().filter(x -> !x.isEmpty()).toList());
        if (!obs.isEmpty()) {
            columna.getChildren().add(etiqueta(obs, "sutil", "pequeno"));
        }
        String resumen = jugado && !previa ? incidencias(p) : "";
        if (!resumen.isEmpty()) {
            Label inc = etiqueta(resumen, "sutil", "pequeno", "incidencias");
            inc.setMaxWidth(Double.MAX_VALUE);
            columna.getChildren().add(inc);
        }
        if (previa) {
            VBox fichas = fichasPrevia(p);
            fichas.setAlignment(Pos.CENTER_LEFT);
            ((HBox) fichas.getChildren().get(0)).setAlignment(Pos.CENTER_LEFT);
            columna.getChildren().add(fichas);
        }
        HBox.setHgrow(columna, Priority.ALWAYS);
        HBox tarjeta = new HBox(16, meta, columna, marcador);
        tarjeta.setAlignment(Pos.CENTER_LEFT);
        tarjeta.getStyleClass().add("partido");
        if (b(p, "jugado")) {
            tarjeta.getStyleClass().add("jugado");
        }
        if (s(p, "estado").equals("EN_JUEGO")) {
            tarjeta.getStyleClass().add("en-vivo");
        }
        tarjeta.setMaxWidth(Double.MAX_VALUE);
        tarjeta.setOnMouseClicked(e -> abrirPartido(p, previa));
        return tarjeta;
    }

    /** Escudo pequeño junto al nombre del equipo en la fila del partido (su imagen o las iniciales). */
    private Node escudoFila(int id, String nombre) {
        Map<String, Object> e = equipoPorId(id);
        StackPane escudo = escudo(e != null ? e : mapa("id", id, "nombre", nombre), 16);
        escudo.getStyleClass().add("escudo-mini");
        return escudo;
    }

    /**
     * Lo que le falta a la previa de un partido: XI completo y formación de cada equipo, fecha y hora, y sede.
     * Lista vacía = previa completa.
     */
    List<String> faltantesPrevia(Map<String, Object> p) {
        List<String> falta = new ArrayList<>();
        String[][] lados = {{"local", "localNombre", "titularesLocal", "formacionLocal"},
            {"visitante", "visitanteNombre", "titularesVisitante", "formacionVisitante"}};
        for (String[] lado : lados) {
            long plantilla = l(equipoPorId(n(p, lado[0])), "jugadores").stream().filter(j -> n(j, "id") > 0).count();
            int necesarios = (int) Math.min(alphasport.service.ReglasPartido.TITULARES, plantilla);
            if (necesarios > 0 && Datos.lista(p, lado[2]).size() != necesarios) {
                falta.add("el XI titular de " + s(p, lado[1]));
            }
        }
        for (String[] lado : lados) {
            if (s(p, lado[3]).isEmpty()) {
                falta.add("la formación de " + s(p, lado[1]));
            }
        }
        if (!s(p, "fecha").matches("\\d{4}-\\d{2}-\\d{2}") || s(p, "hora").isBlank()) {
            falta.add("la fecha y la hora");
        }
        if (s(p, "sede").isBlank()) {
            falta.add("la sede");
        }
        return falta;
    }

    /** Nada de la previa: ni XI, ni formación, ni fecha. */
    private static boolean previaVacia(Map<String, Object> p) {
        return Datos.lista(p, "titularesLocal").isEmpty() && Datos.lista(p, "titularesVisitante").isEmpty()
                && s(p, "formacionLocal").isEmpty() && s(p, "formacionVisitante").isEmpty()
                && !s(p, "fecha").matches("\\d{4}-\\d{2}-\\d{2}");
    }

    /** «a, b y c» */
    static String enumerar(List<String> partes) {
        return partes.size() <= 1 ? String.join("", partes)
                : String.join(", ", partes.subList(0, partes.size() - 1)) + " y " + partes.get(partes.size() - 1);
    }

    /**
     * «Registrar resultados» de un partido que todavía no tiene marcador y cuya previa no está completa:
     * se advierte antes de abrir el editor. «Ir a registrar previa» lleva a la pestaña Previa con ese
     * partido abierto; «Continuar de todas formas» abre el resultado (goles y tarjetas usan la plantilla
     * completa del equipo). Cerrar la advertencia no abre nada.
     */
    private void abrirResultado(Map<String, Object> p) {
        List<String> falta = conMarcador(p) ? List.of() : faltantesPrevia(p);
        if (!falta.isEmpty()) {
            String texto = previaVacia(p)
                    ? "Aún no ha registrado la previa de este partido (alineaciones, formación y datos logísticos). "
                            + "¿Está seguro de que desea registrar el resultado directamente?"
                    : "La previa de este partido está incompleta: falta " + enumerar(falta) + ". "
                            + "¿Está seguro de que desea registrar el resultado directamente?";
            String eleccion = dialogos.elegir("Atención: previa sin registrar", texto, "Continuar de todas formas", "Ir a registrar previa");
            if (eleccion == null) {
                return;
            }
            if (eleccion.equals("si")) {
                pestanaPartidos = "previa";   // la vista vuelve a dibujarse ya en la pestaña Previa
                mostrarVista("partidos");
                dialogos.partido(p, true);
                return;
            }
        }
        dialogos.partido(p, false);
    }

    /** Administrador: editor de la previa o del resultado (según la pestaña). Visitante: detalle del partido. */
    private void abrirPartido(Map<String, Object> p, boolean previa) {
        if (puedeEditar() && !previa) {
            abrirResultado(p);
        } else if (puedeEditar()) {
            dialogos.partido(p, true);
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
        int descenso = n(t, "descenso");       // liga con divisiones: los últimos bajan
        int totalFilas = l(t, "filas").size();
        TableView<Map<String, Object>> tabla = tabla(l(t, "filas"), COLUMNAS_TABLA);
        tabla.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Map<String, Object> fila, boolean vacia) {
                super.updateItem(fila, vacia);
                getStyleClass().removeAll("clasifica", "repechaje", "lider", "desciende");
                if (!vacia && fila != null) {
                    int i = getIndex();
                    if (descenso > 0 && i >= totalFilas - descenso) {
                        getStyleClass().add("desciende");
                    }
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
        String leyenda = s(E, "leyenda") + (directos > 0 ? (b(t, "division") ? " Verde: asciende." : " Verde: clasifica.") : "")
                + (descenso > 0 ? " Rojo: desciende." : "") + (repechaje > 0 ? " Ámbar: repechaje." : "")
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
    
    private Node vistaEstadisticas() {
        Map<String, Object> est = m(E, "estadisticas");
        String ayuda = puedeEditar()
                ? "Datos de todo el campeonato. Toque un gol o una tarjeta de los registros para abrir su partido y corregirlo."
                : "Datos de todo el campeonato. Las estadísticas de cada partido están en su detalle: tóquelo en Partidos.";
        VBox caja = seccion("Estadísticas del campeonato", ayuda);
        javafx.scene.control.TabPane pestanas = new javafx.scene.control.TabPane(
                new javafx.scene.control.Tab("Goleadores", goleadores(est)),
                new javafx.scene.control.Tab("Asistencias", asistencias(est)),
                new javafx.scene.control.Tab("Valla menos vencida", valla(est)),
                new javafx.scene.control.Tab("Juego limpio", juegoLimpio(est)));
        pestanas.setTabClosingPolicy(javafx.scene.control.TabPane.TabClosingPolicy.UNAVAILABLE);
        pestanas.getStyleClass().add("detalle-pestanas");
        pestanas.getSelectionModel().select(pestanaEstadisticas);
        pestanas.getSelectionModel().selectedIndexProperty().addListener((o, antes, ahora) -> pestanaEstadisticas = ahora.intValue());
        caja.getChildren().add(pestanas);
        return caja;
    }

    private static VBox panelEstadistica() {
        VBox v = new VBox(14);
        v.getStyleClass().add("detalle-panel");
        return v;
    }

    private Node goleadores(Map<String, Object> est) {
        VBox v = panelEstadistica();
        List<Map<String, Object>> filas = l(est, "goleadores").stream().map(this::filaJugador).toList();
        TableView<Map<String, Object>> tabla = tabla(filas, new String[][]{{"#", "#", "50"}, {"Jugador", "jugador"},
            {"Equipo", "equipoNombre"}, {"Goles", "goles", "80"}, {"Asist.", "asistencias", "80"}});
        tabla.setPlaceholder(etiqueta("Todavía no hay goles con autor. Se registran al editar un partido jugado.", "sutil"));
        v.getChildren().add(tabla);
        int sinAutor = n(est, "golesSinAutor");
        if (sinAutor > 0) {
            v.getChildren().add(etiqueta((sinAutor == 1 ? "1 gol del marcador todavía no tiene" : sinAutor + " goles del marcador todavía no tienen")
                    + " autor registrado" + (puedeEditar() ? ": regístrelos en el editor del partido." : "."), "sutil", "pequeno"));
        }
        // Registro para auditar (solo administrador): cada gol con su partido; al tocarlo se abre el editor.
        // El visitante no lo necesita: los goles de cada partido los ve en el detalle, pestaña «Resumen».
        if (!puedeEditar()) {
            return v;
        }
        List<Map<String, Object>> goles = new ArrayList<>();
        for (Map<String, Object> g : l(est, "goles")) {
            goles.add(mapa("idPartido", n(g, "partido"),
                    "partido", s(g, "partidoTexto") + (s(g, "ronda").isEmpty() ? "" : "  (" + s(g, "ronda") + ")"),
                    "minuto", n(g, "minuto") > 0 ? n(g, "minuto") + "′" : "–", "equipo", s(g, "equipoNombre"),
                    "gol", "#" + n(g, "autorDorsal") + " " + s(g, "autorNombre") + (b(g, "enContra") ? " (en contra)" : ""),
                    "asistencia", n(g, "asistente") == 0 ? "—" : "#" + n(g, "asistenteDorsal") + " " + s(g, "asistenteNombre")));
        }
        TableView<Map<String, Object>> registro = tabla(goles, new String[][]{{"Partido", "partido"}, {"Min.", "minuto", "70"},
            {"Equipo", "equipo"}, {"Gol", "gol"}, {"Asistencia", "asistencia"}});
        registro.setPlaceholder(etiqueta("Sin goles registrados.", "sutil"));
        abrirAlTocar(registro);
        v.getChildren().addAll(etiqueta("Registro de goles", "h3"), registro);
        return v;
    }

    private Node asistencias(Map<String, Object> est) {
        VBox v = panelEstadistica();
        List<Map<String, Object>> filas = l(est, "asistidores").stream().map(this::filaJugador).toList();
        TableView<Map<String, Object>> tabla = tabla(filas, new String[][]{{"#", "#", "50"}, {"Jugador", "jugador"},
            {"Equipo", "equipoNombre"}, {"Asistencias", "asistencias", "110"}, {"Goles", "goles", "80"}});
        tabla.setPlaceholder(etiqueta("Todavía no hay asistencias registradas. Se anotan junto con cada gol.", "sutil"));
        v.getChildren().add(tabla);
        return v;
    }

    private Map<String, Object> filaJugador(Map<String, Object> f) {
        return mapa("jugador", "#" + n(f, "dorsal") + " " + s(f, "nombre"), "equipoNombre", s(f, "equipoNombre"),
                "goles", n(f, "goles"), "asistencias", n(f, "asistencias"));
    }

    private Node valla(Map<String, Object> est) {
        VBox v = panelEstadistica();
        TableView<Map<String, Object>> tabla = tabla(l(est, "valla"), new String[][]{{"#", "#", "50"}, {"Equipo", "nombre"},
            {"PJ", "pj", "70"}, {"Goles recibidos", "gc", "140"}, {"Por partido", "promedio", "120"}, {"Vallas invictas", "invictas", "140"}});
        tabla.setPlaceholder(etiqueta("Todavía no hay partidos jugados.", "sutil"));
        v.getChildren().addAll(tabla, etiqueta("Orden: menos goles recibidos por partido; luego menos goles en total y más vallas invictas.", "sutil", "pequeno"));
        return v;
    }

    private Node juegoLimpio(Map<String, Object> est) {
        VBox v = panelEstadistica();
        TableView<Map<String, Object>> tabla = tabla(l(est, "fairPlay"), new String[][]{{"#", "#", "50"}, {"Equipo", "nombre"},
            {"PJ", "pj", "70"}, {"Amarillas", "ta", "100"}, {"Rojas", "tr", "80"}, {"Puntos", "puntos", "90"}, {"Por partido", "promedio", "120"}});
        tabla.setPlaceholder(etiqueta("Todavía no hay partidos jugados.", "sutil"));
        v.getChildren().addAll(tabla, etiqueta("Amarilla = 1 punto, roja = 3. Gana el que tenga menos puntos por partido.", "sutil", "pequeno"));

        // Tarjetas y sanciones acumuladas (también para auditar: al tocar una se abre su partido).
        Map<String, Object> sanciones = m(E, "sanciones");
        String[] filtros = {"todas", "rojas", "pendientes"};
        ComboBox<String> filtro = combo(List.of("Todas las tarjetas", "Solo rojas", "Suspensiones vigentes"),
                List.of(filtros).indexOf(filtroSanciones), i -> { filtroSanciones = filtros[i]; mostrarVista("sanciones"); });
        VBox textos = new VBox(2, etiqueta("Tarjetas y sanciones", "h3"), etiqueta(s(sanciones, "resumen"), "sutil", "pequeno"));
        HBox.setHgrow(textos, Priority.ALWAYS);
        HBox cabecera = new HBox(10, textos, filtro);
        cabecera.setAlignment(Pos.CENTER_LEFT);
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Map<String, Object> r : l(sanciones, "registros")) {
            boolean roja = s(r, "tipo").equals("ROJA");
            if (filtroSanciones.equals("rojas") && !roja || filtroSanciones.equals("pendientes") && n(r, "pendientes") == 0) {
                continue;
            }
            filas.add(mapa("idPartido", n(r, "idPartido"), "partido", s(r, "partido") + "  (" + s(r, "ronda") + ")", "fecha", Datos.fecha(r),
                    "equipo", s(r, "equipo"), "jugador", "#" + n(r, "dorsal") + " " + s(r, "jugador"), "tarjeta", roja ? "Roja" : "Amarilla",
                    "fechas", roja ? String.valueOf(n(r, "fechas")) : "—",
                    "estado", !roja ? "—" : n(r, "pendientes") == 1 ? "Falta 1 fecha" : n(r, "pendientes") > 1 ? "Faltan " + n(r, "pendientes") + " fechas" : "Cumplida"));
        }
        TableView<Map<String, Object>> registro = tabla(filas, new String[][]{{"Partido", "partido"}, {"Fecha", "fecha"},
            {"Equipo", "equipo"}, {"Jugador", "jugador"}, {"Tarjeta", "tarjeta"}, {"Fechas", "fechas", "80"}, {"Estado", "estado"}});
        registro.setPlaceholder(etiqueta("No hay tarjetas registradas. Se agregan al editar un partido.", "sutil"));
        abrirAlTocar(registro);
        v.getChildren().addAll(cabecera, registro);
        return v;
    }

    /** Al tocar una fila con «idPartido» se abre ese partido: editor (administrador) o detalle (visitante). */
    private void abrirAlTocar(TableView<Map<String, Object>> tabla) {
        tabla.setRowFactory(tv -> {
            TableRow<Map<String, Object>> fila = new TableRow<>();
            fila.setOnMouseClicked(e -> {
                if (!fila.isEmpty() && fila.getItem() != null) {
                    l(E, "partidos").stream().filter(p -> n(p, "id") == n(fila.getItem(), "idPartido")).findFirst()
                            .ifPresent(p -> abrirPartido(p, !conMarcador(p))); // jugado: su resultado; sin jugar: su previa
                }
            });
            fila.getStyleClass().add("fila-clicable");
            return fila;
        });
    }

    /* ---------------------------------------------------------------- noticias */

    /**
     * Noticias. Administrador: crea, edita y elimina noticias y modera las
     * propuestas de los visitantes. Visitante: lee, comenta y propone noticias (quedan pendientes).
     */
    private Node vistaNoticias() {
        Map<String, Object> datos;
        try {
            datos = m(api.consultar("noticias"), "noticias");
        } catch (RuntimeException e) {
            return vacio("No se pudieron cargar las noticias: " + e.getMessage());
        }
        boolean admin = puedeEditar();
        Button nueva = boton(admin ? "+ Nueva noticia" : "Proponer una noticia", "primario");
        nueva.setOnAction(e -> dialogos.noticia(null, !admin));
        VBox caja = seccion("Noticias", admin
                ? "Publique noticias oficiales y apruebe o rechace las que envían los visitantes."
                : "Noticias del torneo. Puede comentar o proponer una noticia: el administrador la revisa antes de publicarla.", nueva);
        List<Map<String, Object>> publicadas = l(datos, "publicadas");
        if (!admin) {
            // Visitante: solo el feed público (aprobado = true).
            if (publicadas.isEmpty()) {
                caja.getChildren().add(vacio("Todavía no hay noticias publicadas. ¡Proponga la primera!"));
            }
            for (Map<String, Object> nt : publicadas) {
                caja.getChildren().add(tarjetaNoticia(nt, false));
            }
            return caja;
        }
        // Administrador: dos pestañas separadas para no mezclar lo que espera moderación con lo publicado.
        List<Map<String, Object>> pendientes = l(datos, "pendientes");
        if (pestanaNoticias == null) {
            pestanaNoticias = pendientes.isEmpty() ? "publicadas" : "pendientes"; // al entrar: lo que necesita atención
        }
        javafx.scene.control.Tab tabPendientes = new javafx.scene.control.Tab("Pendientes por aprobar (" + pendientes.size() + ")",
                listaNoticias(pendientes, true, "Aportes enviados por los visitantes. Nadie los ve hasta que usted los apruebe.",
                        "No hay aportes de visitantes por revisar."));
        javafx.scene.control.Tab tabPublicadas = new javafx.scene.control.Tab("Publicadas y oficiales (" + publicadas.size() + ")",
                listaNoticias(publicadas, false, "Lo que ven los visitantes: las noticias oficiales y los aportes ya aprobados.",
                        "Todavía no hay noticias publicadas. Use «+ Nueva noticia» para crear la primera."));
        if (!pendientes.isEmpty()) {
            tabPendientes.getStyleClass().add("pestana-con-pendientes");
        }
        javafx.scene.control.TabPane pestanas = new javafx.scene.control.TabPane(tabPendientes, tabPublicadas);
        pestanas.setTabClosingPolicy(javafx.scene.control.TabPane.TabClosingPolicy.UNAVAILABLE);
        pestanas.getStyleClass().addAll("detalle-pestanas", "pestanas-noticias");
        pestanas.getSelectionModel().select(pestanaNoticias.equals("pendientes") ? tabPendientes : tabPublicadas);
        pestanas.getSelectionModel().selectedItemProperty().addListener((o, antes, ahora) ->
                pestanaNoticias = ahora == tabPendientes ? "pendientes" : "publicadas");
        caja.getChildren().add(pestanas);
        return caja;
    }

    /** Contenido de una pestaña de noticias del administrador: explicación corta y las tarjetas (o un aviso si está vacía). */
    private Node listaNoticias(List<Map<String, Object>> noticias, boolean pendientes, String ayuda, String vacia) {
        VBox lista = new VBox(14);
        lista.getStyleClass().add("detalle-panel");
        Label texto = etiqueta(ayuda, "sutil");
        texto.setWrapText(true);
        lista.getChildren().add(texto);
        if (noticias.isEmpty()) {
            lista.getChildren().add(vacio(vacia));
        }
        for (Map<String, Object> nt : noticias) {
            lista.getChildren().add(tarjetaNoticia(nt, pendientes));
        }
        return lista;
    }

    /** Foto de una noticia (se pide al API: respeta los permisos y sirve con cualquier repositorio). Null si no hay. */
    javafx.scene.image.Image fotoNoticia(String nombre) {
        if (nombre == null || nombre.isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = api.imagenNoticia(nombre);
            javafx.scene.image.Image foto = bytes == null ? null : new javafx.scene.image.Image(new java.io.ByteArrayInputStream(bytes));
            return foto == null || foto.isError() ? null : foto;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Foto con esquinas redondeadas a un tamaño FIJO calculado una sola vez con las medidas de la imagen.
     *
     * Antes el alto del recorte estaba enlazado (bind) a vista.boundsInLocal; pero los límites de un nodo
     * ya incluyen su recorte, así que recorte y límites se recalculaban el uno al otro sin fin
     * (computeLocalBounds -> Rectangle.getHeight -> DoublePropertyBase.computeValue) hasta un
     * StackOverflowError en cada dibujo: la ventana quedaba congelada. Aquí no hay ningún enlace.
     */
    static javafx.scene.image.ImageView fotoRedondeada(javafx.scene.image.Image foto, double anchoMaximo, double altoMaximo) {
        double ancho = foto.getWidth() > 0 ? foto.getWidth() : anchoMaximo;
        double alto = foto.getHeight() > 0 ? foto.getHeight() : altoMaximo;
        double escala = Math.min(anchoMaximo / ancho, altoMaximo / alto);   // cabe en el recuadro sin deformarse
        double w = Math.round(ancho * escala), h = Math.round(alto * escala);
        javafx.scene.image.ImageView vista = new javafx.scene.image.ImageView(foto);
        vista.setFitWidth(w);
        vista.setFitHeight(h);
        vista.setPreserveRatio(true);
        vista.setSmooth(true);
        javafx.scene.shape.Rectangle recorte = new javafx.scene.shape.Rectangle(w, h); // medidas fijas: sin bind
        recorte.setArcWidth(24);
        recorte.setArcHeight(24);
        vista.setClip(recorte);
        return vista;
    }

    private Node tarjetaNoticia(Map<String, Object> nt, boolean pendiente) {
        boolean admin = puedeEditar();
        int id = n(nt, "id");
        Label titulo = etiqueta(s(nt, "titulo"), "h3");
        titulo.setWrapText(true);
        HBox meta = new HBox(8, etiqueta("Por " + s(nt, "autor") + " · " + s(nt, "fecha"), "sutil", "pequeno"));
        meta.setAlignment(Pos.CENTER_LEFT);
        if (pendiente) {
            meta.getChildren().add(etiqueta("Pendiente de aprobación", "etiqueta", "etiqueta-pendiente"));
        } else if (b(nt, "deVisitante")) {
            meta.getChildren().add(etiqueta(admin ? "Aporte de visitante · aprobado" : "Aporte de visitante", "chip"));
        } else if (admin) {
            meta.getChildren().add(etiqueta("Oficial", "chip"));
        }
        Label cuerpo = etiqueta(s(nt, "cuerpo"), "noticia-cuerpo");
        cuerpo.setWrapText(true);
        VBox textos = new VBox(6, titulo, meta, cuerpo);
        HBox.setHgrow(textos, Priority.ALWAYS);
        HBox arriba = new HBox(18);
        javafx.scene.image.Image foto = fotoNoticia(s(nt, "imagen"));
        if (foto != null) {
            arriba.getChildren().add(fotoRedondeada(foto, 260, 360));
        }
        arriba.getChildren().add(textos);
        VBox tarjeta = new VBox(14, arriba);

        HBox acciones = new HBox(8);
        acciones.setAlignment(Pos.CENTER_LEFT);
        if (admin && pendiente) {
            Button aprobar = boton("Aprobar", "primario", "mini");
            aprobar.setOnAction(e -> accionNoticia("noticia/aprobar", mapa("id", id)));
            Button rechazar = boton("Rechazar y eliminar", "peligro", "mini");
            rechazar.setOnAction(e -> {
                if (dialogos.confirmar("Rechazar propuesta", "¿Rechazar «" + s(nt, "titulo") + "»? Se borra la propuesta y su foto.", "Rechazar")) {
                    accionNoticia("noticia/eliminar", mapa("id", id, "rechazo", true));
                }
            });
            Button editar = boton("Editar antes de publicar", "fantasma", "mini");
            editar.setOnAction(e -> dialogos.noticia(nt, false));
            acciones.getChildren().addAll(aprobar, rechazar, editar);
        } else if (admin) {
            Button editar = boton("Editar", "fantasma", "mini");
            editar.setOnAction(e -> dialogos.noticia(nt, false));
            Button eliminar = boton("Eliminar", "peligro", "mini");
            eliminar.setOnAction(e -> {
                if (dialogos.confirmar("Eliminar noticia", "¿Eliminar «" + s(nt, "titulo") + "» con su foto y sus comentarios?", "Eliminar")) {
                    accionNoticia("noticia/eliminar", mapa("id", id));
                }
            });
            acciones.getChildren().addAll(editar, eliminar);
        }
        if (!acciones.getChildren().isEmpty()) {
            tarjeta.getChildren().add(acciones);
        }
        if (!pendiente) {
            tarjeta.getChildren().add(comentarios(nt));
        }
        tarjeta.getStyleClass().addAll("vidrio", "tarjeta-noticia");
        return tarjeta;
    }

    /** Comentarios de una noticia publicada y el formulario para comentar (con filtro anti-obscenidades en el servicio). */
    private Node comentarios(Map<String, Object> nt) {
        List<Map<String, Object>> lista = l(nt, "comentarios");
        VBox caja = new VBox(8, etiqueta("Comentarios (" + lista.size() + ")", "jugador-texto", "titulo-alineacion"));
        for (Map<String, Object> c : lista) {
            Label texto = etiqueta(s(c, "texto"), "jugador-texto");
            texto.setWrapText(true);
            VBox contenido = new VBox(2, etiqueta(s(c, "autor") + " · " + s(c, "fecha"), "sutil", "pequeno"), texto);
            HBox.setHgrow(contenido, Priority.ALWAYS);
            HBox fila = new HBox(10, contenido);
            if (puedeEditar()) {
                Button quitar = boton("Quitar", "mini", "peligro");
                quitar.setOnAction(e -> accionNoticia("comentario/eliminar", mapa("noticia", n(nt, "id"), "id", n(c, "id"))));
                fila.getChildren().add(quitar);
            }
            fila.setAlignment(Pos.CENTER_LEFT);
            fila.getStyleClass().add("fila-lista");
            caja.getChildren().add(fila);
        }
        TextField autor = new TextField();
        autor.setPromptText(puedeEditar() ? "Administración" : "Su nombre (opcional)");
        autor.setPrefWidth(170);
        TextField texto = new TextField();
        texto.setPromptText("Escriba un comentario…");
        HBox.setHgrow(texto, Priority.ALWAYS);
        Button comentar = boton("Comentar");
        Runnable enviar = () -> {
            if (texto.getText().isBlank()) {
                texto.requestFocus();
                return;
            }
            String nombre = autor.getText().isBlank() && puedeEditar() ? "Administración" : autor.getText();
            accionNoticia("noticia/comentar", mapa("id", n(nt, "id"), "autor", nombre, "texto", texto.getText()));
        };
        comentar.setOnAction(e -> enviar.run());
        texto.setOnAction(e -> enviar.run());
        HBox formulario = new HBox(8, autor, texto, comentar);
        formulario.setAlignment(Pos.CENTER_LEFT);
        caja.getChildren().add(formulario);
        return caja;
    }

    void accionNoticia(String ruta, Map<String, Object> cuerpo) {
        Map<String, Object> r = ejecutar(ruta, cuerpo);
        if (b(r, "ok")) {
            aviso(s(r, "mensaje"));
        } else {
            dialogos.mensaje("Noticias", s(r, "error"));
        }
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
