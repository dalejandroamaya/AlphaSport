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
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

/**
 * Detalle de partido estilo Flashscore (solo lectura): cabecera con el marcador y
 * pestañas «Cara a cara», «Racha» y «Alineaciones». Dibuja el JSON que entrega
 * ApiControlador.consultar("partido/{id}"); no calcula nada.
 */
final class VistaDetallePartido {

    private static final double ANCHO_CANCHA = 520;
    private static final double ALTO_CANCHA = 720;

    private VistaDetallePartido() {
    }

    static Node construir(Map<String, Object> detalle) {
        Map<String, Object> p = m(detalle, "partido");
        TabPane pestanas = new TabPane(
                new Tab("Cara a cara", caraACara(m(detalle, "caraACara"), p)),
                new Tab("Racha", racha(m(detalle, "racha"), p)),
                new Tab("Alineaciones", alineaciones(m(detalle, "alineaciones"), p)));
        pestanas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        pestanas.getStyleClass().add("detalle-pestanas");
        return new VBox(18, cabecera(p), pestanas);
    }

    /* ---------------------------------------------------------------- cabecera */

    private static Node cabecera(Map<String, Object> p) {
        Label local = etiqueta(s(p, "localNombre"), "detalle-equipo");
        Label visitante = etiqueta(s(p, "visitanteNombre"), "detalle-equipo");
        if (n(p, "ganador") == n(p, "local")) {
            local.getStyleClass().add("ganador");
        }
        if (n(p, "ganador") == n(p, "visitante")) {
            visitante.getStyleClass().add("ganador");
        }
        boolean conMarcador = Aplicacion.conMarcador(p);
        VBox centro = new VBox(4, etiqueta(conMarcador ? n(p, "gl") + " - " + n(p, "gv") : "VS", conMarcador ? "cifras" : "vs"));
        if (b(p, "penales")) {
            centro.getChildren().add(etiqueta(n(p, "pl") + "-" + n(p, "pv") + " pen", "penales-texto"));
        }
        centro.getChildren().add(Aplicacion.etiquetaEstado(s(p, "estado")));
        centro.setAlignment(Pos.CENTER);
        centro.setMinWidth(130);
        VBox izq = new VBox(8, Aplicacion.escudo(mapa(p, "local", "localNombre"), 46), local);
        VBox der = new VBox(8, Aplicacion.escudo(mapa(p, "visitante", "visitanteNombre"), 46), visitante);
        izq.setAlignment(Pos.CENTER);
        der.setAlignment(Pos.CENTER);
        HBox.setHgrow(izq, Priority.ALWAYS);
        HBox.setHgrow(der, Priority.ALWAYS);
        izq.setMaxWidth(Double.MAX_VALUE);
        der.setMaxWidth(Double.MAX_VALUE);
        HBox marcador = new HBox(10, izq, centro, der);
        marcador.setAlignment(Pos.CENTER);
        String fecha = s(p, "fecha").matches("\\d{4}-\\d{2}-\\d{2}") ? Datos.fecha(p) : "Fecha por definir";
        String sede = s(p, "sede").isEmpty() ? "" : " · " + s(p, "sede");
        Label meta = etiqueta(fecha + sede, "sutil", "centro");
        meta.setMaxWidth(Double.MAX_VALUE);
        VBox caja = new VBox(12, marcador, meta);
        caja.getStyleClass().addAll("vidrio", "detalle-cabecera");
        return caja;
    }

    /** Mapa {id, nombre} para dibujar el escudo de un equipo del partido. */
    private static Map<String, Object> mapa(Map<String, Object> p, String id, String nombre) {
        return Datos.mapa("id", n(p, id), "nombre", s(p, nombre));
    }

    /* ---------------------------------------------------------------- cara a cara */

    private static Node caraACara(Map<String, Object> h, Map<String, Object> p) {
        List<Map<String, Object>> partidos = l(h, "partidos");
        VBox caja = new VBox(14);
        caja.getStyleClass().add("detalle-panel");
        if (partidos.isEmpty()) {
            caja.getChildren().add(etiqueta("Estos equipos no se han enfrentado antes.", "vacio"));
            return caja;
        }
        HBox resumen = new HBox(12,
                cifra(n(h, "victoriasLocal"), "Victorias " + s(p, "localNombre"), "cifra-victoria"),
                cifra(n(h, "empates"), "Empates", "cifra-empate"),
                cifra(n(h, "victoriasVisitante"), "Victorias " + s(p, "visitanteNombre"), "cifra-victoria"));
        resumen.setAlignment(Pos.CENTER);
        caja.getChildren().addAll(resumen, etiqueta("Últimos enfrentamientos", "h3"));
        for (Map<String, Object> r : partidos) {
            caja.getChildren().add(filaPartido(r, null));
        }
        return caja;
    }

    private static Node cifra(int valor, String texto, String clase) {
        Label numero = etiqueta(String.valueOf(valor), "cifras");
        Label rotulo = etiqueta(texto, "sutil", "pequeno");
        rotulo.setWrapText(true);
        VBox v = new VBox(2, numero, rotulo);
        v.setAlignment(Pos.CENTER);
        v.getStyleClass().addAll("vidrio", "detalle-cifra", clase);
        v.setPrefWidth(170);
        return v;
    }

    /** Fila de un partido previo; si hay resultado (racha) se antepone el cuadro V/E/D. */
    private static Node filaPartido(Map<String, Object> r, String resultado) {
        Label contexto = etiqueta(fechaCorta(s(r, "fecha")) + s(r, "competicion")
                + (s(r, "ronda").isEmpty() ? "" : " · " + s(r, "ronda")), "sutil", "pequeno");
        Label local = etiqueta(s(r, "local"), "detalle-fila-equipo");
        Label visitante = etiqueta(s(r, "visitante"), "detalle-fila-equipo");
        if (s(r, "ganador").equalsIgnoreCase(s(r, "local"))) {
            local.getStyleClass().add("ganador");
        }
        if (s(r, "ganador").equalsIgnoreCase(s(r, "visitante"))) {
            visitante.getStyleClass().add("ganador");
        }
        local.setAlignment(Pos.CENTER_RIGHT);
        local.setPrefWidth(190);
        visitante.setPrefWidth(190);
        HBox marcador = new HBox(12, local, etiqueta(n(r, "gl") + " - " + n(r, "gv"), "detalle-fila-marcador"), visitante);
        marcador.setAlignment(Pos.CENTER_LEFT);
        HBox fila = new HBox(12);
        fila.setAlignment(Pos.CENTER_LEFT);
        if (resultado != null) {
            fila.getChildren().add(cuadroForma(resultado));
        }
        fila.getChildren().add(new VBox(2, contexto, marcador));
        fila.getStyleClass().addAll("vidrio", "detalle-fila");
        return fila;
    }

    private static String fechaCorta(String fecha) {
        return fecha.matches("\\d{4}-\\d{2}-\\d{2}")
                ? fecha.substring(8, 10) + "/" + fecha.substring(5, 7) + "/" + fecha.substring(0, 4) + " · " : "";
    }

    /* ---------------------------------------------------------------- racha */

    private static Node racha(Map<String, Object> r, Map<String, Object> p) {
        HBox columnas = new HBox(18, columnaRacha(s(p, "localNombre"), l(r, "local")),
                columnaRacha(s(p, "visitanteNombre"), l(r, "visitante")));
        columnas.getStyleClass().add("detalle-panel");
        return columnas;
    }

    private static Node columnaRacha(String equipo, List<Map<String, Object>> partidos) {
        HBox cuadros = new HBox(6);
        for (Map<String, Object> x : partidos) {
            cuadros.getChildren().add(cuadroForma(s(x, "resultado")));
        }
        VBox col = new VBox(12, etiqueta(equipo, "h3"));
        if (partidos.isEmpty()) {
            col.getChildren().add(etiqueta("Sin partidos jugados todavía.", "sutil"));
        } else {
            col.getChildren().addAll(new HBox(8, etiqueta("Últimos " + partidos.size() + ":", "sutil"), cuadros),
                    etiqueta("V = victoria · E = empate · D = derrota (el más reciente a la izquierda)", "sutil", "pequeno"));
            for (Map<String, Object> x : partidos) {
                col.getChildren().add(filaPartido(x, s(x, "resultado")));
            }
        }
        HBox.setHgrow(col, Priority.ALWAYS);
        col.setPrefWidth(420);
        return col;
    }

    /** Cuadro de color: verde victoria, amarillo empate, rojo derrota (con la letra, para no depender del color). */
    static Label cuadroForma(String resultado) {
        String letra = resultado.isEmpty() ? "?" : resultado.substring(0, 1);
        Label l = etiqueta(letra, "forma", "forma-" + letra);
        l.setMinSize(28, 28);
        l.setAlignment(Pos.CENTER);
        String nombre = switch (letra) {
            case "V" -> "Victoria";
            case "E" -> "Empate";
            case "D" -> "Derrota";
            default -> "";
        };
        l.setAccessibleText(nombre);
        return l;
    }

    /* ---------------------------------------------------------------- alineaciones */

    /** Una cancha vertical: el visitante arriba (ataca hacia abajo) y el local abajo. */
    private static Node alineaciones(Map<String, Object> a, Map<String, Object> p) {
        Map<String, Object> local = m(a, "local");
        Map<String, Object> visitante = m(a, "visitante");
        Pane cancha = new Pane();
        cancha.setPrefSize(ANCHO_CANCHA, ALTO_CANCHA);
        cancha.setMinSize(ANCHO_CANCHA, ALTO_CANCHA);
        cancha.setMaxSize(ANCHO_CANCHA, ALTO_CANCHA);
        cancha.getStyleClass().add("cancha");
        dibujarLineas(cancha);
        ubicar(cancha, visitante, true);
        ubicar(cancha, local, false);

        VBox lado = new VBox(14,
                rotuloEquipo(s(p, "visitanteNombre"), visitante, "Arriba"),
                rotuloEquipo(s(p, "localNombre"), local, "Abajo"));
        if (!b(local, "confirmada") || !b(visitante, "confirmada")) {
            Label nota = etiqueta("Las casillas con «?» son puestos por confirmar: el XI inicial se registra al abrir el partido.",
                    "sutil", "pequeno");
            nota.setWrapText(true);
            lado.getChildren().add(nota);
        }
        lado.setPrefWidth(260);
        HBox caja = new HBox(22, new StackPane(cancha), lado);
        caja.getStyleClass().add("detalle-panel");
        return caja;
    }

    private static Node rotuloEquipo(String equipo, Map<String, Object> al, String donde) {
        String formacion = s(al, "formacion") + (b(al, "formacionElegida") ? "" : " (por defecto)");
        VBox v = new VBox(4, etiqueta(donde, "sutil", "pequeno"), etiqueta(equipo, "h3"),
                etiqueta("Formación " + formacion, "chip"),
                etiqueta(b(al, "confirmada") ? "Alineación confirmada" : "Alineación por confirmar", "sutil", "pequeno"));
        v.getStyleClass().addAll("vidrio", "detalle-rotulo");
        return v;
    }

    private static void dibujarLineas(Pane cancha) {
        double w = ANCHO_CANCHA, h = ALTO_CANCHA, m = 14;
        Rectangle borde = rect(m, m, w - 2 * m, h - 2 * m);
        Line medio = new Line(m, h / 2, w - m, h / 2);
        medio.getStyleClass().add("cancha-linea");
        Circle circulo = new Circle(w / 2, h / 2, 62);
        circulo.getStyleClass().add("cancha-linea");
        double areaW = w * 0.56, areaH = h * 0.14, chicaW = w * 0.28, chicaH = h * 0.055;
        cancha.getChildren().addAll(borde, medio, circulo,
                rect((w - areaW) / 2, m, areaW, areaH), rect((w - areaW) / 2, h - m - areaH, areaW, areaH),
                rect((w - chicaW) / 2, m, chicaW, chicaH), rect((w - chicaW) / 2, h - m - chicaH, chicaW, chicaH));
    }

    private static Rectangle rect(double x, double y, double w, double h) {
        Rectangle r = new Rectangle(x, y, w, h);
        r.getStyleClass().add("cancha-linea");
        return r;
    }

    /**
     * Coloca las lineas del equipo en su mitad: el portero junto a su arco y la ultima
     * linea cerca del medio campo. El de arriba se refleja para que ataque hacia abajo.
     */
    private static void ubicar(Pane cancha, Map<String, Object> al, boolean arriba) {
        List<Object> lineas = lista(al, "lineas");
        double arco = 0.93, frente = 0.55; // fraccion de alto (equipo de abajo)
        int ultima = Math.max(1, lineas.size() - 1);
        for (int i = 0; i < lineas.size(); i++) {
            @SuppressWarnings("unchecked")
            List<Object> linea = (List<Object>) lineas.get(i);
            double y = arco - (arco - frente) * i / ultima;
            for (int k = 0; k < linea.size(); k++) {
                double x = (k + 1.0) / (linea.size() + 1);
                @SuppressWarnings("unchecked")
                Map<String, Object> j = (Map<String, Object>) linea.get(k);
                Node ficha = ficha(j, arriba);
                double fx = (arriba ? 1 - x : x) * ANCHO_CANCHA;
                double fy = (arriba ? 1 - y : y) * ALTO_CANCHA;
                ficha.setLayoutX(fx - 45);
                ficha.setLayoutY(fy - 17);
                cancha.getChildren().add(ficha);
            }
        }
    }

    private static Node ficha(Map<String, Object> j, boolean visitante) {
        Label dorsal = etiqueta(j == null ? "?" : String.valueOf(n(j, "dorsal")), "ficha-dorsal",
                visitante ? "ficha-visitante" : "ficha-local");
        dorsal.setMinSize(34, 34);
        dorsal.setMaxSize(34, 34);
        dorsal.setAlignment(Pos.CENTER);
        VBox v = new VBox(3, dorsal);
        if (j != null) { // las casillas por confirmar solo llevan «?»
            Label nombre = etiqueta(apellido(s(j, "nombre")), "ficha-nombre");
            nombre.setMaxWidth(90);
            v.getChildren().add(nombre);
        }
        v.setAlignment(Pos.TOP_CENTER);
        v.setPrefWidth(90);
        if (j != null) {
            javafx.scene.control.Tooltip.install(v, new javafx.scene.control.Tooltip(
                    "#" + n(j, "dorsal") + " " + s(j, "nombre") + " · " + s(j, "posicion")));
        }
        return v;
    }

    /** «Juan Carlos Pérez» -> «J. Pérez», para que quepa sobre la cancha. */
    static String apellido(String nombre) {
        String[] partes = nombre.trim().split("\\s+");
        return partes.length < 2 ? nombre : partes[0].charAt(0) + ". " + partes[partes.length - 1];
    }
}
