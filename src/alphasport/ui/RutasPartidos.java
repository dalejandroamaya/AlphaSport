package alphasport.ui;

import static alphasport.ui.Peticion.entero;
import static alphasport.ui.Peticion.lista;
import static alphasport.ui.Peticion.mapa;
import static alphasport.ui.Peticion.objeto;
import static alphasport.ui.Peticion.texto;

import alphasport.model.Cambio;
import alphasport.model.EstadoPartido;
import alphasport.model.Formacion;
import alphasport.model.Gol;
import alphasport.model.Jugador;
import alphasport.model.Partido;
import alphasport.model.Permiso;
import alphasport.model.Tarjeta;
import alphasport.service.CampeonatoService;
import alphasport.service.ReglaNegocioException;
import alphasport.service.ReglasPartido;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Rutas de los partidos: el detalle (con sus calificaciones) y guardar la previa, el resultado, el W.O. o
 * borrarlo. Aqui solo se lee el JSON; las reglas las revisa ReglasPartido al guardar.
 */
final class RutasPartidos implements ModuloRutas {

    private final CampeonatoService servicio;
    private final Presentador presentador;

    RutasPartidos(CampeonatoService servicio, Presentador presentador) {
        this.servicio = servicio;
        this.presentador = presentador;
    }

    @Override
    public void registrar(Rutas<Rutas.Consulta> consultas, Rutas<Rutas.Accion> acciones) {
        // Detalle: partido/{id} o partido/{id}/{dispositivo}. El dispositivo (anonimo) solo sirve para mostrar en
        // las calificaciones [REQ-07] lo que ya voto este navegador o equipo.
        consultas.conPrefijo(Permiso.VER, "partido/", p -> {
            String[] partes = p.resto().split("/");
            int id = entero(partes[0]);
            String dispositivo = partes.length > 1 ? partes[1] : null;
            return presentador.detallePartido(servicio.partidos().detallePartido(id), servicio.votacion().calificaciones(id, dispositivo));
        });
        acciones.con(Permiso.GESTIONAR_CAMPEONATO, "partido", p -> {
            guardarPartido(p.cuerpo());
            return p.listo(null);
        });
    }

    private void guardarPartido(Map<String, Object> cuerpo) throws Exception {
        int id = entero(cuerpo.get("id"));
        Partido original = servicio.buscarPartido(id);
        Partido datos = new Partido();
        datos.copiarDatosDe(original);
        Map<String, Object> d = objeto(cuerpo, "datos");
        EstadoPartido estado = EstadoPartido.valueOf(texto(d, "estado"));
        if (estado == EstadoPartido.JUGADO || estado == EstadoPartido.EN_JUEGO) {
            // «En juego»: el marcador, las tarjetas y los cambios se registran en vivo (sin penales).
            boolean jugado = estado == EstadoPartido.JUGADO;
            datos.setEstado(estado);
            datos.setGolesLocal(Math.max(0, entero(d.get("gl"))));
            datos.setGolesVisitante(Math.max(0, entero(d.get("gv"))));
            datos.setPenalesLocal(jugado ? entero(d.get("pl")) : Partido.SIN_PENALES);
            datos.setPenalesVisitante(jugado ? entero(d.get("pv")) : Partido.SIN_PENALES);
            datos.setTarjetas(tarjetas(lista(d, "tarjetas")), original.getIdLocal());
            leerTitulares(d, datos);
            if (d.containsKey("cambios")) {
                datos.setCambios(cambios(lista(d, "cambios")));
            }
            if (d.containsKey("goles")) { // la carga rapida no envia los goles: se conservan
                datos.setGoles(goles(lista(d, "goles")));
            }
        } else {
            datos.reiniciarResultado();
            datos.setEstado(estado);
            leerTitulares(d, datos); // «Previa del partido»: el XI se elige antes de jugar
        }
        // La formacion se elige antes del partido: se guarda en cualquier estado.
        if (d.containsKey("formacionLocal")) {
            datos.setFormaciones(formacion(texto(d, "formacionLocal")), formacion(texto(d, "formacionVisitante")));
        }
        datos.setFecha(fecha(texto(d, "fecha")));
        datos.setHora(hora(texto(d, "hora")));
        datos.setSede(texto(d, "sede").trim());
        datos.setObservaciones(texto(d, "obs").trim());
        boolean confirmado = Boolean.TRUE.equals(cuerpo.get("confirmado"));
        switch (texto(cuerpo, "accion")) {
            case "woLocal" -> servicio.partidos().darWalkover(id, datos, original.getIdLocal(), confirmado);
            case "woVisitante" -> servicio.partidos().darWalkover(id, datos, original.getIdVisitante(), confirmado);
            case "borrar" -> {
                datos.reiniciarResultado();
                servicio.partidos().guardarPartido(id, datos, confirmado);
            }
            default -> {
                if (d.containsKey("horasVotacion")) { // [REQ-07] cuanto dura la votacion (la carga rapida no lo envia)
                    servicio.votacion().programarVotacion(datos, entero(d.get("horasVotacion")));
                }
                servicio.partidos().guardarPartido(id, datos, confirmado);
            }
        }
    }

    /* Lectura del JSON del partido: solo convierte. Las reglas (XI, minutos, cambios, goles y tarjetas) las
       revisa ReglasPartido al guardar, igual para el escritorio y la web. */

    private ArrayList<Tarjeta> tarjetas(List<Object> lista) {
        ArrayList<Tarjeta> tarjetas = new ArrayList<>();
        for (Object o : lista) {
            Map<String, Object> t = mapa(o);
            boolean roja = "ROJA".equals(texto(t, "tipo"));
            Tarjeta tarjeta = new Tarjeta(entero(t.get("equipo")), jugador(t.get("jugador"), "Una de las tarjetas"),
                    roja ? Tarjeta.ROJA : Tarjeta.AMARILLA, roja ? Math.max(1, entero(t.get("fechas"))) : 0);
            tarjeta.setMinuto(t.get("minuto") == null ? 0 : entero(t.get("minuto")));
            tarjetas.add(tarjeta);
        }
        return tarjetas;
    }

    /** XI inicial de los dos equipos, si viene en la petición (la carga rápida no lo envía: se conserva). */
    private void leerTitulares(Map<String, Object> d, Partido datos) {
        if (d.containsKey("titularesLocal")) {
            datos.setTitulares(enteros(lista(d, "titularesLocal")), enteros(lista(d, "titularesVisitante")));
        }
    }

    private static ArrayList<Integer> enteros(List<Object> lista) {
        ArrayList<Integer> ids = new ArrayList<>();
        lista.forEach(o -> ids.add(entero(o)));
        return ids;
    }

    private ArrayList<Cambio> cambios(List<Object> lista) {
        ArrayList<Cambio> cambios = new ArrayList<>();
        for (Object o : lista) {
            Map<String, Object> c = mapa(o);
            cambios.add(new Cambio(entero(c.get("equipo")), entero(c.get("minuto")),
                    jugador(c.get("sale"), "Uno de los cambios"), jugador(c.get("entra"), "Uno de los cambios")));
        }
        cambios.sort(Comparator.comparingInt(Cambio::getMinuto));
        return cambios;
    }

    private ArrayList<Gol> goles(List<Object> lista) {
        ArrayList<Gol> goles = new ArrayList<>();
        for (Object o : lista) {
            Map<String, Object> g = mapa(o);
            boolean enContra = Boolean.TRUE.equals(g.get("enContra"));
            int idAsistente = g.get("asistente") == null || enContra ? Gol.SIN_ASISTENCIA : entero(g.get("asistente")); // un autogol no tiene
            goles.add(new Gol(entero(g.get("equipo")), g.get("minuto") == null ? 0 : entero(g.get("minuto")),
                    jugador(g.get("autor"), "Uno de los goles"), enContra,
                    idAsistente == Gol.SIN_ASISTENCIA ? null : jugador(idAsistente, "La asistencia de un gol")));
        }
        goles.sort(Comparator.comparingInt(x -> x.getMinuto() == 0 ? Integer.MAX_VALUE : x.getMinuto()));
        return goles;
    }

    /** Jugador por id; si ya no esta en ninguna plantilla, el dato no se puede leer. */
    private Jugador jugador(Object id, String que) {
        Jugador j = ReglasPartido.buscarJugador(servicio.getCampeonato(), entero(id));
        if (j == null) {
            throw new ReglaNegocioException(que + " es de un jugador que ya no existe.");
        }
        return j;
    }

    /** La fecha se guarda como DATE: aaaa-mm-dd o «Por definir». */
    private static String fecha(String texto) {
        if (texto.isBlank() || texto.equals("Por definir")) {
            return "Por definir";
        }
        try {
            return java.time.LocalDate.parse(texto.trim()).toString();
        } catch (java.time.format.DateTimeParseException e) {
            throw new ReglaNegocioException("La fecha «" + texto + "» no existe. Use el formato año-mes-día.");
        }
    }

    /** La hora se guarda como TIME: hh:mm en 24 horas o vacia. */
    private static String hora(String texto) {
        String t = texto.trim();
        if (t.isEmpty()) {
            return "";
        }
        if (!t.matches("([01]?\\d|2[0-3]):[0-5]\\d")) {
            throw new ReglaNegocioException("Escriba la hora en formato de 24 horas, por ejemplo 15:30.");
        }
        return t.length() == 4 ? "0" + t : t;
    }

    /** Valida el texto de una formacion ("" = sin definir). */
    private static String formacion(String texto) {
        if (texto.isBlank()) {
            return "";
        }
        if (Formacion.desdeTexto(texto) == null) {
            throw new ReglaNegocioException("La formación «" + texto + "» no existe.");
        }
        return texto;
    }

}
