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
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Ventanas emergentes de la version de escritorio.
 */
class Dialogos {

    private final Aplicacion app;

    Dialogos(Aplicacion app) {
        this.app = app;
    }

    /* ---------------------------------------------------------------- base */

    private Stage ventana(String titulo, Node contenido, double ancho) {
        Stage v = new Stage();
        v.initOwner(app.stage);
        v.initModality(Modality.APPLICATION_MODAL);
        v.setTitle(titulo);
        ScrollPane desplazable = new ScrollPane(contenido);
        desplazable.setFitToWidth(true);
        desplazable.getStyleClass().add("desplazable");
        StackPane raiz = new StackPane(desplazable);
        raiz.getStyleClass().addAll("raiz", "dialogo");
        if (app.claro) {
            raiz.getStyleClass().add("claro");
        }
        Scene escena = new Scene(raiz, ancho, -1);
        escena.getStylesheets().add(app.hojaEstilos());
        v.setScene(escena);
        v.getIcons().add(Aplicacion.imagen("logo.png"));
        v.setMaxHeight(app.stage.getHeight() - 40);
        return v;
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
        mensaje("🏆 Campeón del torneo", s(f, "nombre") + "\n\nDirector técnico: " + s(f, "dt") + "\nJugados: " + n(f, "pj")
                + "   Ganados: " + n(f, "g") + "   Empatados: " + n(f, "e") + "   Perdidos: " + n(f, "p")
                + "\nGoles a favor: " + n(f, "gf") + "   Goles en contra: " + n(f, "gc"));
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
        VBox v = new VBox(12, etiqueta(titulo.toUpperCase(), "sobretitulo"));
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
            {"Rondas del suizo", rondas, "SUIZO"}, {"Clasificados directos", directos, "SUIZO"}, {"Equipos al repechaje", repechaje, "SUIZO"}};
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
        Stage v = ventana(titulo, cuerpo(titulo, campo("Nombre del campeonato", nombre), etiqueta("FORMATO DE COMPETENCIA", "sobretitulo"),
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
        ComboBox<String> posicion = new ComboBox<>(FXCollections.observableArrayList(
                Datos.lista(app.catalogo, "posiciones").stream().map(String::valueOf).toList()));
        posicion.getSelectionModel().selectFirst();
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

    private static boolean tieneDorsal(List<Map<String, Object>> jugadores, int dorsal) {
        return jugadores.stream().anyMatch(j -> n(j, "dorsal") == dorsal);
    }

    /* ---------------------------------------------------------------- partido */

    void partido(Map<String, Object> p) {
        Spinner<Integer> gl = numero(0, 99, n(p, "gl"));
        Spinner<Integer> gv = numero(0, 99, n(p, "gv"));
        gl.getStyleClass().add("led-campo");
        gv.getStyleClass().add("led-campo");
        ComboBox<String> estado = new ComboBox<>(FXCollections.observableArrayList("Jugado", "Pendiente", "Aplazado", "Suspendido"));
        String[] claves = {"JUGADO", "PENDIENTE", "APLAZADO", "SUSPENDIDO"};
        String actual = s(p, "estado").equals("PENDIENTE") ? "JUGADO" : s(p, "estado");
        estado.getSelectionModel().select(List.of(claves).indexOf(actual));
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
        ComboBox<String> tjEquipo = new ComboBox<>(FXCollections.observableArrayList(s(p, "localNombre"), s(p, "visitanteNombre")));
        tjEquipo.getSelectionModel().selectFirst();
        ComboBox<String> tjJugador = new ComboBox<>();
        List<Map<String, Object>> opciones = new ArrayList<>();
        Runnable llenarJugadores = () -> {
            Map<String, Object> eq = app.equipoPorId(idsEquipos[Math.max(0, tjEquipo.getSelectionModel().getSelectedIndex())]);
            opciones.clear();
            l(eq, "jugadores").stream().filter(j -> n(j, "id") > 0).sorted(Comparator.comparingInt(j -> n(j, "dorsal"))).forEach(opciones::add);
            tjJugador.setItems(FXCollections.observableArrayList(opciones.isEmpty() ? List.of("(sin jugadores registrados)")
                    : opciones.stream().map(j -> "#" + n(j, "dorsal") + " " + s(j, "nombre")).toList()));
            tjJugador.getSelectionModel().selectFirst();
        };
        tjEquipo.setOnAction(e -> llenarJugadores.run());
        llenarJugadores.run();
        ComboBox<String> tjTipo = new ComboBox<>(FXCollections.observableArrayList("Amarilla", "Roja"));
        tjTipo.getSelectionModel().selectFirst();
        Spinner<Integer> tjFechas = numero(1, 20, 1);
        VBox campoFechas = campo("Fechas fuera", tjFechas);
        VBox listaTarjetas = new VBox(6);
        Runnable[] dibujarTarjetas = new Runnable[1];
        dibujarTarjetas[0] = () -> {
            listaTarjetas.getChildren().clear();
            if (tarjetas.isEmpty()) {
                listaTarjetas.getChildren().add(etiqueta("Sin tarjetas en este partido.", "sutil"));
            }
            for (Map<String, Object> t : tarjetas) {
                boolean roja = s(t, "tipo").equals("ROJA");
                Map<String, Object> eq = app.equipoPorId(n(t, "equipo"));
                Label texto = etiqueta((roja ? "Roja" : "Amarilla") + " · #" + n(t, "dorsal") + " " + s(t, "nombre") + " · " + s(eq, "nombre")
                        + (roja ? " · " + n(t, "fechas") + (n(t, "fechas") == 1 ? " fecha" : " fechas") + " fuera" : ""), "jugador-texto");
                HBox.setHgrow(texto, Priority.ALWAYS);
                texto.setMaxWidth(Double.MAX_VALUE);
                Button quitar = boton("Quitar", "mini", "peligro");
                quitar.setOnAction(e -> {
                    tarjetas.remove(t);
                    dibujarTarjetas[0].run();
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
                    "nombre", s(jugador, "nombre"), "dorsal", n(jugador, "dorsal"), "tipo", roja ? "ROJA" : "AMARILLA", "fechas", roja ? fechas : 0));
            dibujarTarjetas[0].run();
        });
        HBox filaTarjeta = new HBox(10, campo("Equipo", tjEquipo), campo("Jugador", tjJugador), campo("Tarjeta", tjTipo), campoFechas, agregarTarjeta);
        filaTarjeta.setAlignment(Pos.BOTTOM_LEFT);
        List<Map<String, Object>> suspendidos = l(p, "suspendidos");
        Label avisoSuspendidos = etiqueta(suspendidos.isEmpty() ? "Sin jugadores suspendidos para este partido."
                : "Suspendidos para este partido: " + String.join(", ", suspendidos.stream()
                        .map(x -> s(x, "nombre") + " (" + s(x, "equipoNombre") + ")").toList()), suspendidos.isEmpty() ? "sutil" : "advertencia");
        VBox grupoTarjetas = grupo("Tarjetas por jugador", filaTarjeta, listaTarjetas, avisoSuspendidos);

        DatePicker fecha = new DatePicker();
        if (s(p, "fecha").matches("\\d{4}-\\d{2}-\\d{2}")) {
            fecha.setValue(LocalDate.parse(s(p, "fecha")));
        }
        TextField hora = new TextField(s(p, "hora"));
        hora.setPromptText("15:30");
        TextField sede = new TextField(s(p, "sede"));
        TextField obs = new TextField(s(p, "obs"));

        Runnable actualizar = () -> {
            boolean jugado = estado.getSelectionModel().getSelectedIndex() == 0;
            gl.setDisable(!jugado);
            gv.setDisable(!jugado);
            hayPenales.setDisable(!jugado);
            grupoTarjetas.setDisable(!jugado);
            pl.setDisable(!jugado || !hayPenales.isSelected());
            pv.setDisable(!jugado || !hayPenales.isSelected());
            boolean roja = tjTipo.getSelectionModel().getSelectedIndex() == 1;
            campoFechas.setVisible(roja);
            campoFechas.setManaged(roja);
        };
        estado.setOnAction(e -> actualizar.run());
        hayPenales.setOnAction(e -> actualizar.run());
        tjTipo.setOnAction(e -> actualizar.run());
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
        Button cancelar = boton("Cancelar", "fantasma");
        Button guardar = boton("Guardar", "primario");
        HBox botones = new HBox(10, woLocal, woVisitante, borrar, Aplicacion.espacio(), cancelar, guardar);
        botones.setAlignment(Pos.CENTER_LEFT);
        Stage v = ventana("Partido", cuerpo(app.contexto(p), marcador, new HBox(14, campo("Estado", estado), penales), grupoTarjetas,
                new HBox(12, campo("Fecha", fecha), campo("Hora", hora), campo("Sede", sede), campo("Observaciones", obs)), err, botones), 900);
        cancelar.setOnAction(e -> v.close());

        java.util.function.Consumer<String> enviar = accion -> {
            boolean penal = conPenales && hayPenales.isSelected();
            Map<String, Object> datos = mapa("estado", claves[estado.getSelectionModel().getSelectedIndex()],
                    "gl", gl.getValue(), "gv", gv.getValue(), "pl", penal ? pl.getValue() : -1, "pv", penal ? pv.getValue() : -1,
                    "tarjetas", tarjetas, "fecha", fecha.getValue() == null ? "" : fecha.getValue().toString(),
                    "hora", hora.getText(), "sede", sede.getText(), "obs", obs.getText());
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
            app.aviso("Partido guardado.");
        };
        guardar.setOnAction(e -> enviar.accept("guardar"));
        woLocal.setOnAction(e -> enviar.accept("woLocal"));
        woVisitante.setOnAction(e -> enviar.accept("woVisitante"));
        borrar.setOnAction(e -> enviar.accept("borrar"));
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
                        + (s(h, "campeon").isEmpty() ? "Sin terminar" : "🏆 " + s(h, "campeon"))).toList()));
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
            {"🏆", "titulos", "60"}, {"2.º", "sub", "60"}, {"Podios", "podios", "75"}, {"Torneos", "participaciones", "80"},
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
            VBox premio = new VBox(2, etiqueta(s(p, "titulo").replaceAll("^[^\\p{L}]+", "").toUpperCase(), "sobretitulo"), etiqueta(s(p, "valor"), "premio-valor"),
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
        Button descargar = boton("Descargar premios (.txt)", "mini");
        descargar.setOnAction(e -> app.guardarTexto(s(h, "nombre") + "_premios", s(h, "texto")));
        detalle.getChildren().setAll(podio, premios, tablas, new HBox(10, Aplicacion.espacio(), eliminar, descargar));
    }

}
