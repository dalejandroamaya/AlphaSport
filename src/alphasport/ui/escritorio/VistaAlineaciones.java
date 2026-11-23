package alphasport.ui.escritorio;

import static alphasport.ui.escritorio.Aplicacion.etiqueta;
import static alphasport.ui.escritorio.Datos.b;
import static alphasport.ui.escritorio.Datos.l;
import static alphasport.ui.escritorio.Datos.lista;
import static alphasport.ui.escritorio.Datos.m;
import static alphasport.ui.escritorio.Datos.n;
import static alphasport.ui.escritorio.Datos.s;

import java.util.List;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

/**
 * Pestaña «Alineaciones» del detalle del partido: una cancha horizontal (el local a la izquierda ataca hacia
 * la derecha y el visitante al revés) con la foto, el dorsal y el nombre de los titulares. La cancha se ajusta
 * a la pantalla para que se vea completa sin desplazarse; debajo van la formación, el estado de la alineación,
 * el cuerpo técnico, los cambios y los suplentes. Solo dibuja el JSON de Presentador.alineacion.
 */
final class VistaAlineaciones {

    private static final double ANCHO_MAXIMO = 960;
    private static final double ALTO_MINIMO = 260;
    private static final double ALTO_MAXIMO = 470;
    /** Lo que ocupan, alrededor de la cancha, la ventana, el marcador y las pestañas del detalle. */
    private static final double ALTO_RESERVADO = 452;
    private static final double ANCHO_RESERVADO = 190;

    private final Map<String, Object> partido;
    private final VistaDetallePartido.Edicion edicion;
    private final double ancho;
    private final double alto;
    private final double anchoFicha;
    private final double anchoFoto;
    private final double altoFoto;

    private VistaAlineaciones(Map<String, Object> partido, VistaDetallePartido.Edicion edicion) {
        this.partido = partido;
        this.edicion = edicion;
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        ancho = Math.min(ANCHO_MAXIMO, pantalla.getWidth() - ANCHO_RESERVADO);
        alto = Math.max(ALTO_MINIMO, Math.min(ALTO_MAXIMO, pantalla.getHeight() - ALTO_RESERVADO));
        // A lo ancho caben cinco líneas por equipo y a lo alto cinco jugadores por línea (foto y nombre).
        anchoFicha = Math.floor(ancho * 0.094);
        altoFoto = Math.min(52, Math.floor(alto / 5 - 26));
        anchoFoto = Math.round(altoFoto * 3 / 4);
    }

    /** @param edicion null = las fotos solo se ven (visitante) */
    static Node construir(Map<String, Object> alineaciones, Map<String, Object> partido, VistaDetallePartido.Edicion edicion) {
        return new VistaAlineaciones(partido, edicion).dibujar(m(alineaciones, "local"), m(alineaciones, "visitante"));
    }

    private Node dibujar(Map<String, Object> local, Map<String, Object> visitante) {
        Pane cancha = new Pane();
        cancha.setMinSize(ancho, alto);
        cancha.setPrefSize(ancho, alto);
        cancha.setMaxSize(ancho, alto);
        cancha.getStyleClass().add("cancha");
        cancha.setAccessibleText("Alineaciones: " + s(partido, "localNombre") + " a la izquierda y "
                + s(partido, "visitanteNombre") + " a la derecha");
        dibujarLineas(cancha);
        ubicar(cancha, local, true);
        ubicar(cancha, visitante, false);

        HBox debajo = new HBox(14, datosEquipo("localNombre", "local", local), datosEquipo("visitanteNombre", "visitante", visitante));
        VBox caja = new VBox(14, new StackPane(cancha), debajo);
        if (!b(local, "confirmada") || !b(visitante, "confirmada")) {
            Label nota = etiqueta("Las casillas con «?» son puestos por confirmar: el XI inicial se registra en la previa del partido.",
                    "sutil", "pequeno");
            nota.setWrapText(true);
            caja.getChildren().add(nota);
        }
        caja.getStyleClass().add("detalle-panel");
        return caja;
    }

    /* ---------------------------------------------------------------- cancha */

    private void dibujarLineas(Pane cancha) {
        double w = ancho, h = alto, m = 12;
        Line medio = new Line(w / 2, m, w / 2, h - m);
        medio.getStyleClass().add("cancha-linea");
        Circle circulo = new Circle(w / 2, h / 2, h * 0.15);
        circulo.getStyleClass().add("cancha-linea");
        double areaW = w * 0.13, areaH = h * 0.58, chicaW = w * 0.05, chicaH = h * 0.3;
        cancha.getChildren().addAll(rectangulo(m, m, w - 2 * m, h - 2 * m), medio, circulo,
                rectangulo(m, (h - areaH) / 2, areaW, areaH), rectangulo(w - m - areaW, (h - areaH) / 2, areaW, areaH),
                rectangulo(m, (h - chicaH) / 2, chicaW, chicaH), rectangulo(w - m - chicaW, (h - chicaH) / 2, chicaW, chicaH));
    }

    private static Rectangle rectangulo(double x, double y, double w, double h) {
        Rectangle r = new Rectangle(x, y, w, h);
        r.getStyleClass().add("cancha-linea");
        return r;
    }

    /**
     * Coloca las líneas del equipo en su mitad: el portero junto a su arco y la última línea cerca del medio
     * campo. Cada línea viene de izquierda a derecha vista por el equipo: el local mira hacia la derecha (su
     * izquierda queda arriba) y el visitante hacia la izquierda (su izquierda queda abajo).
     */
    private void ubicar(Pane cancha, Map<String, Object> alineacion, boolean local) {
        List<Object> lineas = lista(alineacion, "lineas");
        double arco = 0.06, frente = 0.437; // fracción del ancho (equipo local)
        int ultima = Math.max(1, lineas.size() - 1);
        for (int i = 0; i < lineas.size(); i++) {
            @SuppressWarnings("unchecked")
            List<Object> linea = (List<Object>) lineas.get(i);
            double x = arco + (frente - arco) * i / ultima;
            for (int k = 0; k < linea.size(); k++) {
                double y = (k + 0.5) / linea.size();
                @SuppressWarnings("unchecked")
                Map<String, Object> j = (Map<String, Object>) linea.get(k);
                Node ficha = ficha(j, local);
                double altoFicha = j == null ? 34 : altoFoto + 19; // foto y nombre, centrados en su puesto
                ficha.setLayoutX((local ? x : 1 - x) * ancho - anchoFicha / 2);
                ficha.setLayoutY(8 + (local ? y : 1 - y) * (alto - 16) - altoFicha / 2);
                cancha.getChildren().add(ficha);
            }
        }
    }

    private Node ficha(Map<String, Object> j, boolean local) {
        String clase = local ? "ficha-local" : "ficha-visitante";
        Label dorsal = etiqueta(j == null ? "?" : String.valueOf(n(j, "dorsal")), "ficha-dorsal", clase);
        double lado = j == null ? 34 : 20;
        dorsal.setMinSize(lado, lado);
        dorsal.setMaxSize(lado, lado);
        dorsal.setAlignment(Pos.CENTER);
        VBox v = new VBox(3);
        v.setAlignment(Pos.TOP_CENTER);
        v.setMinWidth(anchoFicha);
        v.setPrefWidth(anchoFicha);
        v.setMaxWidth(anchoFicha);
        if (j == null) { // las casillas por confirmar solo llevan «?»
            v.getChildren().add(dorsal);
            return v;
        }
        // La foto tipo cédula (o «NN») con el borde del color del equipo y el dorsal en la esquina.
        StackPane marco = new StackPane(Fotos.cedula(s(j, "foto"), anchoFoto));
        marco.getStyleClass().addAll("ficha-foto", clase);
        marco.setMaxSize(anchoFoto + 4, altoFoto + 4);
        dorsal.getStyleClass().add("ficha-dorsal-mini");
        StackPane conDorsal = new StackPane(marco, dorsal);
        StackPane.setAlignment(dorsal, Pos.BOTTOM_RIGHT);
        dorsal.setTranslateX(9);
        dorsal.setTranslateY(4);
        conDorsal.setMaxSize(anchoFoto + 4, altoFoto + 4);
        if (edicion != null && n(j, "id") > 0) {
            int idEquipo = n(partido, local ? "local" : "visitante");
            Fotos.tocable(marco, "Foto de " + s(j, "nombre") + (s(j, "foto").isEmpty() ? ": subir" : ": cambiar o eliminar"),
                    () -> edicion.foto(marco, idEquipo, j));
        }
        Label nombre = etiqueta(apellido(s(j, "nombre")), "ficha-nombre");
        nombre.setMaxWidth(anchoFicha);
        v.getChildren().addAll(conDorsal, nombre);
        // Si salió en un cambio, el minuto va junto a la foto (no ocupa alto).
        l(partido, "cambios").stream().filter(x -> n(x, "sale") == n(j, "id")).findFirst().ifPresent(x -> {
            Label salio = etiqueta("↓" + n(x, "minuto") + "′", "ficha-sale");
            salio.setMinWidth(Label.USE_PREF_SIZE);
            StackPane.setAlignment(salio, Pos.TOP_RIGHT);
            salio.setTranslateX(26);
            conDorsal.getChildren().add(salio);
        });
        Tooltip.install(v, new Tooltip("#" + n(j, "dorsal") + " " + s(j, "nombre") + " · " + s(j, "posicion")));
        return v;
    }

    /* ---------------------------------------------------------------- debajo de la cancha */

    /** Datos del equipo: formación, estado de la alineación, cuerpo técnico, cambios y suplentes. */
    private Node datosEquipo(String claveNombre, String claveId, Map<String, Object> al) {
        int idEquipo = n(partido, claveId);
        Label chip = etiqueta("Formación " + s(al, "formacion") + (b(al, "formacionElegida") ? "" : " (por defecto)"), "chip");
        chip.setMinWidth(Label.USE_PREF_SIZE);
        HBox linea = new HBox(8, chip, etiqueta(b(al, "confirmada") ? "Alineación confirmada" : "Alineación por confirmar", "sutil", "pequeno"));
        linea.setAlignment(Pos.CENTER_LEFT);
        Label dt = s(al, "dt").isEmpty() ? etiqueta("Sin director técnico registrado.", "sutil", "pequeno") : etiqueta("DT " + s(al, "dt"), "jugador-texto");
        VBox v = new VBox(6, etiqueta(s(partido, claveNombre), "h3"), linea, subtitulo("Cuerpo técnico"), dt);
        List<Map<String, Object>> cambios = l(partido, "cambios").stream().filter(x -> n(x, "equipo") == idEquipo).toList();
        if (!cambios.isEmpty()) {
            v.getChildren().add(subtitulo("Cambios"));
            for (Map<String, Object> x : cambios) {
                HBox fila = new HBox(8, etiqueta(n(x, "minuto") + "′", "entra-texto"),
                        new VBox(0, etiqueta("Entra #" + n(x, "entraDorsal") + " " + s(x, "entraNombre"), "jugador-texto"),
                                etiqueta("Sale #" + n(x, "saleDorsal") + " " + s(x, "saleNombre"), "sale-texto", "pequeno")));
                fila.setAlignment(Pos.CENTER_LEFT);
                fila.getStyleClass().addAll("fila-alineacion", "entro");
                v.getChildren().add(fila);
            }
        }
        v.getChildren().addAll(subtitulo("Suplentes"), suplentes(al));
        v.getStyleClass().addAll("vidrio", "detalle-rotulo");
        v.setPrefWidth(1);
        v.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(v, Priority.ALWAYS);
        return v;
    }

    private Node suplentes(Map<String, Object> al) {
        // Un suspendido para este partido no puede estar en el banco.
        List<Map<String, Object>> banco = l(al, "suplentes").stream()
                .filter(j -> l(partido, "suspendidos").stream().noneMatch(x -> n(x, "jugador") == n(j, "id"))).toList();
        if (banco.isEmpty()) {
            return etiqueta(b(al, "confirmada") ? "Sin suplentes en la plantilla." : "Se conocen al confirmar el XI inicial.", "sutil", "pequeno");
        }
        FlowPane caja = new FlowPane(8, 6);
        for (Map<String, Object> j : banco) {
            HBox suplente = new HBox(6, Fotos.cedula(s(j, "foto"), 21), etiqueta("#" + n(j, "dorsal") + " " + apellido(s(j, "nombre")), "pequeno"));
            suplente.setAlignment(Pos.CENTER_LEFT);
            suplente.getStyleClass().add("suplente");
            l(partido, "cambios").stream().filter(x -> n(x, "entra") == n(j, "id")).findFirst()
                    .ifPresent(x -> suplente.getChildren().add(etiqueta("↑" + n(x, "minuto") + "′", "entra-texto", "pequeno")));
            Tooltip.install(suplente, new Tooltip("#" + n(j, "dorsal") + " " + s(j, "nombre") + " · " + s(j, "posicion")));
            caja.getChildren().add(suplente);
        }
        return caja;
    }

    private static Label subtitulo(String texto) {
        return etiqueta(texto, "campo-titulo");
    }

    /** «Juan Carlos Pérez» -> «J. Pérez», para que quepa sobre la cancha. */
    private static String apellido(String nombre) {
        String[] partes = nombre.trim().split("\\s+");
        return partes.length < 2 ? nombre : partes[0].charAt(0) + ". " + partes[partes.length - 1];
    }
}
