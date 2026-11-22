package alphasport.ui.escritorio;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Fotos de jugadores (tipo cédula, 3:4) y escudos en el escritorio. Las imágenes guardadas se piden al
 * API por su nombre (respeta la sesión) y se recuerdan; las recién elegidas llegan como «data URL» y
 * el servicio las recorta y guarda al enviarlas. Sin foto se dibuja la persona «NN».
 */
final class Fotos {

    /** Lo mismo que acepta el servicio: el resto lo rechaza con su propio mensaje. */
    static final int MAXIMO_BYTES = alphasport.service.NoticiasService.MAXIMO_IMAGEN;

    private static final Map<String, Image> GUARDADAS = new HashMap<>();
    private static Aplicacion app;

    private Fotos() {
    }

    static void usar(Aplicacion aplicacion) {
        app = aplicacion;
    }

    /** El escudo guardado del equipo con ese id ("" si no tiene). */
    static String escudoDe(int idEquipo) {
        return app == null ? "" : Datos.s(app.equipoPorId(idEquipo), "escudo");
    }

    /** La imagen de una foto guardada (nombre) o recién elegida (data URL); null si no hay o no se pudo leer. */
    static Image imagen(String valor) {
        if (valor == null || valor.isEmpty()) {
            return null;
        }
        if (valor.startsWith("data:")) {
            return leer(decodificar(valor));
        }
        Image guardada = GUARDADAS.get(valor);
        if (guardada == null && app != null) {
            try {
                guardada = leer(app.api.foto(valor));
            } catch (Exception e) {
                guardada = null; // sin sesión, archivo borrado o nombre inválido: se ve «NN»
            }
            if (guardada != null) {
                GUARDADAS.put(valor, guardada); // los nombres nunca se reutilizan: el cambio trae otro nombre
            }
        }
        return guardada;
    }

    private static byte[] decodificar(String dataUrl) {
        int coma = dataUrl.indexOf(',');
        try {
            return coma < 0 ? null : Base64.getDecoder().decode(dataUrl.substring(coma + 1));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Image leer(byte[] datos) {
        if (datos == null || datos.length == 0) {
            return null;
        }
        Image img = new Image(new ByteArrayInputStream(datos));
        return img.isError() || img.getWidth() <= 0 ? null : img;
    }

    /** Foto tipo cédula de un jugador con el ancho dado (alto = 4/3), o la persona «NN». */
    static StackPane cedula(String foto, double ancho) {
        double alto = Math.round(ancho * 4 / 3);
        Image img = imagen(foto);
        StackPane caja = new StackPane();
        caja.getStyleClass().add("foto-cedula");
        if (img != null) {
            ImageView vista = new ImageView(img);
            // Recorte 3:4 centrado (un poco hacia arriba, donde va la cara), por si la imagen no viene recortada.
            double w = img.getWidth(), h = img.getHeight();
            double rw = Math.min(w, h * 3 / 4), rh = rw * 4 / 3;
            vista.setViewport(new Rectangle2D((w - rw) / 2, Math.max(0, (h - rh) * 0.3), rw, rh));
            vista.setFitWidth(ancho);
            vista.setFitHeight(alto);
            vista.setSmooth(true);
            caja.getChildren().add(vista);
        } else {
            caja.getChildren().add(nn(ancho, alto));
            caja.getStyleClass().add("nn-caja");
        }
        Rectangle recorte = new Rectangle(ancho, alto);
        recorte.setArcWidth(Math.max(6, ancho / 5));
        recorte.setArcHeight(Math.max(6, ancho / 5));
        caja.setClip(recorte);
        caja.setMinSize(ancho, alto);
        caja.setPrefSize(ancho, alto);
        caja.setMaxSize(ancho, alto);
        return caja;
    }

    /** La persona «NN» (sin foto): fondo, cabeza, hombros y el texto NN, con las medidas de la web (30x40). */
    private static Node nn(double ancho, double alto) {
        Region fondo = new Region();
        fondo.getStyleClass().add("nn-fondo");
        fondo.setPrefSize(ancho, alto);
        javafx.scene.shape.Circle cabeza = new javafx.scene.shape.Circle(15, 13.5, 6.5);
        SVGPath hombros = new SVGPath();
        hombros.setContent("M3.5 40C3.5 32 8.5 27.5 15 27.5S26.5 32 26.5 40Z");
        cabeza.getStyleClass().add("nn-figura");
        hombros.getStyleClass().add("nn-figura");
        javafx.scene.Group figura = new javafx.scene.Group(cabeza, hombros);
        figura.getTransforms().add(new javafx.scene.transform.Scale(ancho / 30, alto / 40, 0, 0));
        javafx.scene.layout.Pane lienzo = new javafx.scene.layout.Pane(figura); // Pane no mueve a sus hijos
        lienzo.setPrefSize(ancho, alto);
        Label texto = new Label("NN");
        texto.getStyleClass().add("nn-texto");
        texto.setStyle("-fx-font-size: " + Math.max(6, ancho * 0.25) + "px;");
        StackPane.setAlignment(texto, Pos.BOTTOM_CENTER);
        texto.setTranslateY(-alto * 0.01);
        StackPane s = new StackPane(fondo, lienzo, texto);
        s.setMouseTransparent(true);
        return s;
    }

    /** Imagen del escudo contenida en el espacio del escudo de iniciales; null si no tiene o no se pudo leer. */
    static Node escudo(String escudo, double tamano) {
        Image img = imagen(escudo);
        if (img == null) {
            return null;
        }
        ImageView vista = new ImageView(img);
        vista.setPreserveRatio(true);
        vista.setFitWidth(tamano);
        vista.setFitHeight(tamano * 1.14);
        vista.setSmooth(true);
        StackPane s = new StackPane(vista);
        s.getStyleClass().add("escudo-imagen");
        s.setMinSize(tamano, tamano * 1.14);
        s.setPrefSize(tamano, tamano * 1.14);
        s.setMaxSize(tamano, tamano * 1.14);
        return s;
    }

    /**
     * Abre el selector de imágenes y entrega la foto elegida como «data URL» (o null si se canceló).
     * Los errores (muy pesada, no es imagen) se avisan con {@code alError}.
     */
    static String elegir(Node desde, boolean esEscudo, Consumer<String> alError) {
        FileChooser selector = new FileChooser();
        selector.setTitle(esEscudo ? "Escudo del equipo" : "Foto del jugador (tipo cédula)");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes (JPG, PNG, GIF)", "*.jpg", "*.jpeg", "*.png", "*.gif"));
        File archivo;
        try {
            archivo = Dialogos.elegirArchivo(selector, (Stage) desde.getScene().getWindow());
        } catch (RuntimeException e) {
            alError.accept("No se pudo abrir el selector de archivos: " + e.getMessage());
            return null;
        }
        return archivo == null ? null : desdeArchivo(archivo, alError);
    }

    /** Lee un archivo de imagen y lo pasa a «data URL»; null (y aviso) si no sirve. */
    static String desdeArchivo(File archivo, Consumer<String> alError) {
        try {
            if (archivo.length() > MAXIMO_BYTES) {
                alError.accept("La imagen es muy pesada (máximo 5 MB).");
                return null;
            }
            byte[] bytes = java.nio.file.Files.readAllBytes(archivo.toPath());
            if (leer(bytes) == null) {
                alError.accept("No se pudo leer esa imagen. Escoja un archivo JPG, PNG o GIF.");
                return null;
            }
            String nombre = archivo.getName().toLowerCase();
            String tipo = nombre.endsWith(".png") ? "png" : nombre.endsWith(".gif") ? "gif" : "jpeg";
            return "data:image/" + tipo + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (java.io.IOException | RuntimeException e) {
            alError.accept("No se pudo abrir el archivo: " + e.getMessage());
            return null;
        }
    }

    /**
     * Al tocar una foto o un escudo: si no tiene, abre de una vez el selector; si tiene, un menú
     * «Cambiar foto / Eliminar foto». {@code enviar} recibe la data URL nueva o "" para eliminarla.
     */
    static void tocar(Node ancla, boolean tieneFoto, boolean esEscudo, Consumer<String> enviar, Consumer<String> alError) {
        Runnable cambiar = () -> {
            String nueva = elegir(ancla, esEscudo, alError);
            if (nueva != null) {
                enviar.accept(nueva);
            }
        };
        if (!tieneFoto) {
            cambiar.run();
            return;
        }
        String cosa = esEscudo ? "escudo" : "foto";
        MenuItem cambio = new MenuItem("Cambiar " + cosa);
        cambio.setOnAction(e -> cambiar.run());
        MenuItem quitar = new MenuItem("Eliminar " + cosa);
        quitar.setOnAction(e -> enviar.accept(""));
        ContextMenu menu = new ContextMenu(cambio, quitar);
        // El menú es otra ventana: lleva las clases del tema para que sus colores (-texto, -vidrio-denso…) existan.
        menu.getStyleClass().addAll("raiz", "menu-foto");
        if (app != null && app.claro) {
            menu.getStyleClass().add("claro");
        }
        menu.show(ancla, Side.BOTTOM, 0, 4);
    }

    /** Marca un nodo como «se puede tocar para cambiar la foto» (mano, realce y accesible con el teclado). */
    static void tocable(Node nodo, String ayuda, Runnable accion) {
        nodo.getStyleClass().add("foto-tocable");
        nodo.setCursor(javafx.scene.Cursor.HAND);
        nodo.setFocusTraversable(true);
        nodo.setAccessibleHelp(ayuda);
        javafx.scene.control.Tooltip.install(nodo, new javafx.scene.control.Tooltip(ayuda));
        nodo.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                e.consume();
                accion.run();
            }
        });
        nodo.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER || e.getCode() == javafx.scene.input.KeyCode.SPACE) {
                e.consume();
                accion.run();
            }
        });
    }
}
