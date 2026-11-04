package alphasport.ui;

import alphasport.model.ConfiguracionTorneo;
import alphasport.model.CriterioDesempate;
import alphasport.model.DirectorTecnico;
import alphasport.model.EstadoPartido;
import alphasport.model.Jugador;
import alphasport.model.Partido;
import alphasport.model.Tarjeta;
import alphasport.model.TipoFormato;
import alphasport.service.AutenticacionService;
import alphasport.service.CampeonatoService;
import alphasport.service.ReglaNegocioException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Atiende las acciones de la interfaz web: convierte el JSON recibido en
 * llamadas al servicio y devuelve el estado actualizado.
 */
public class ApiControlador {

    private final CampeonatoService servicio;
    private final AutenticacionService autenticacion;
    private final Presentador presentador;

    public ApiControlador(CampeonatoService servicio, AutenticacionService autenticacion) {
        this.servicio = servicio;
        this.autenticacion = autenticacion;
        this.presentador = new Presentador(servicio);
    }

    public Map<String, Object> consultar(String ruta) {
        return switch (ruta) {
            case "catalogo" -> presentador.catalogo();
            case "estado" -> presentador.estado();
            case "historial" -> presentador.historial(servicio.historial());
            default -> throw new ReglaNegocioException("Ruta desconocida: " + ruta);
        };
    }

    public Map<String, Object> ejecutar(String ruta, Map<String, Object> cuerpo) throws Exception {
        String mensaje = null;
        switch (ruta) {
            case "login" -> {
                if (!autenticacion.autenticar(texto(cuerpo, "usuario"), texto(cuerpo, "clave"))) {
                    throw new ReglaNegocioException("LOGIN", "Usuario o contraseña incorrectos.");
                }
            }
            case "nuevo" -> servicio.crear(texto(cuerpo, "nombre"), TipoFormato.valueOf(texto(cuerpo, "formato")),
                    configuracion(objeto(cuerpo, "config")), Boolean.TRUE.equals(cuerpo.get("mismosEquipos")));
            case "siguiente" -> servicio.siguienteTorneoCorto(texto(cuerpo, "nombre"));
            case "cancelar" -> servicio.cancelar();
            case "cambiarFormato" -> servicio.cambiarFormato(texto(cuerpo, "nombre"),
                    TipoFormato.valueOf(texto(cuerpo, "formato")), configuracion(objeto(cuerpo, "config")));
            case "avanzar" -> mensaje = servicio.avanzar();
            case "equipo/rapido" -> servicio.agregarEquipo(texto(cuerpo, "nombre"));
            case "equipo" -> guardarEquipo(cuerpo);
            case "equipo/quitar" -> mensaje = servicio.quitarEquipo(entero(cuerpo.get("id")));
            case "partido" -> guardarPartido(cuerpo);
            case "criterios" -> servicio.cambiarCriterios(criterios(lista(cuerpo, "criterios")));
            case "archivar" -> servicio.archivarActual();
            case "historial/eliminar" -> servicio.eliminarDelHistorial(texto(cuerpo, "codigo"));
            default -> throw new ReglaNegocioException("Ruta desconocida: " + ruta);
        }
        return respuesta(mensaje);
    }

    public Map<String, Object> respuesta(String mensaje) {
        return Presentador.mapa("ok", true, "mensaje", mensaje, "estado", presentador.estado());
    }

    private void guardarEquipo(Map<String, Object> cuerpo) throws Exception {
        Map<String, Object> dt = objeto(cuerpo, "dt");
        DirectorTecnico director = dt == null || texto(dt, "nombre").isBlank() ? null
                : new DirectorTecnico(texto(dt, "nombre").trim(), texto(dt, "documento").trim(), texto(dt, "nacionalidad").trim());
        List<Jugador> jugadores = new ArrayList<>();
        for (Object o : lista(cuerpo, "jugadores")) {
            Map<String, Object> j = mapa(o);
            Jugador jugador = new Jugador(texto(j, "nombre").trim(), texto(j, "documento").trim(),
                    entero(j.get("dorsal")), texto(j, "posicion"));
            jugador.setId(j.get("id") == null ? 0 : entero(j.get("id")));
            jugadores.add(jugador);
        }
        Object id = cuerpo.get("id");
        servicio.guardarEquipo(id == null ? null : entero(id), texto(cuerpo, "nombre"), texto(cuerpo, "estadio"),
                director, jugadores);
    }

    private void guardarPartido(Map<String, Object> cuerpo) throws Exception {
        int id = entero(cuerpo.get("id"));
        Partido original = servicio.buscarPartido(id);
        Partido datos = new Partido();
        datos.copiarDatosDe(original);
        Map<String, Object> d = objeto(cuerpo, "datos");
        EstadoPartido estado = EstadoPartido.valueOf(texto(d, "estado"));
        if (estado == EstadoPartido.JUGADO) {
            datos.setEstado(estado);
            datos.setGolesLocal(Math.max(0, entero(d.get("gl"))));
            datos.setGolesVisitante(Math.max(0, entero(d.get("gv"))));
            datos.setPenalesLocal(entero(d.get("pl")));
            datos.setPenalesVisitante(entero(d.get("pv")));
            datos.setTarjetas(tarjetas(lista(d, "tarjetas")), original.getIdLocal());
        } else {
            datos.reiniciarResultado();
            datos.setEstado(estado);
        }
        String fecha = texto(d, "fecha");
        datos.setFecha(fecha.isBlank() ? "Por definir" : fecha);
        datos.setHora(texto(d, "hora"));
        datos.setSede(texto(d, "sede").trim());
        datos.setObservaciones(texto(d, "obs").trim());
        boolean confirmado = Boolean.TRUE.equals(cuerpo.get("confirmado"));
        switch (texto(cuerpo, "accion")) {
            case "woLocal" -> servicio.darWalkover(id, datos, original.getIdLocal(), confirmado);
            case "woVisitante" -> servicio.darWalkover(id, datos, original.getIdVisitante(), confirmado);
            case "borrar" -> {
                datos.reiniciarResultado();
                servicio.guardarPartido(id, datos, confirmado);
            }
            default -> servicio.guardarPartido(id, datos, confirmado);
        }
    }

    private ArrayList<Tarjeta> tarjetas(List<Object> lista) {
        ArrayList<Tarjeta> tarjetas = new ArrayList<>();
        for (Object o : lista) {
            Map<String, Object> t = mapa(o);
            int idEquipo = entero(t.get("equipo"));
            Jugador jugador = servicio.getCampeonato().buscarJugador(idEquipo, entero(t.get("jugador")));
            if (jugador == null) {
                throw new ReglaNegocioException("Una de las tarjetas es de un jugador que ya no existe.");
            }
            boolean roja = "ROJA".equals(texto(t, "tipo"));
            tarjetas.add(new Tarjeta(idEquipo, jugador, roja ? Tarjeta.ROJA : Tarjeta.AMARILLA,
                    roja ? Math.max(1, entero(t.get("fechas"))) : 0));
        }
        return tarjetas;
    }

    private ConfiguracionTorneo configuracion(Map<String, Object> c) {
        ConfiguracionTorneo cfg = new ConfiguracionTorneo();
        cfg.setPuntosVictoria(entero(c.get("puntosVictoria")));
        cfg.setPuntosEmpate(entero(c.get("puntosEmpate")));
        cfg.setIdaYVuelta(Boolean.TRUE.equals(c.get("idaYVuelta")));
        cfg.setEliminatoriaIdaYVuelta(Boolean.TRUE.equals(c.get("eliminatoriaIdaYVuelta")));
        cfg.setFinalUnica(Boolean.TRUE.equals(c.get("finalUnica")));
        cfg.setSorteoAleatorio(Boolean.TRUE.equals(c.get("sorteoAleatorio")));
        cfg.setTamanoGrupo(entero(c.get("tamanoGrupo")));
        cfg.setClasificadosPorGrupo(entero(c.get("clasificadosPorGrupo")));
        cfg.setClasificadosPlayoff(entero(c.get("clasificadosPlayoff")));
        cfg.setTipoPlayoff(texto(c, "tipoPlayoff"));
        cfg.setVentajaDeportiva(Boolean.TRUE.equals(c.get("ventajaDeportiva")));
        cfg.setRondasSuizo(entero(c.get("rondasSuizo")));
        cfg.setDirectosSuizo(entero(c.get("directosSuizo")));
        cfg.setRepechajeSuizo(entero(c.get("repechajeSuizo")));
        cfg.setGranFinalConReinicio(Boolean.TRUE.equals(c.get("granFinalConReinicio")));
        cfg.setCriterios(criterios(lista(c, "criterios")));
        return cfg;
    }

    private ArrayList<CriterioDesempate> criterios(List<Object> claves) {
        ArrayList<CriterioDesempate> lista = new ArrayList<>();
        for (Object clave : claves) {
            lista.add(CriterioDesempate.valueOf(String.valueOf(clave)));
        }
        return lista;
    }

    private static String texto(Map<String, Object> m, String clave) {
        Object v = m.get(clave);
        return v == null ? "" : v.toString();
    }

    private static int entero(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException("Valor numérico inválido: " + v);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object o) {
        return (Map<String, Object>) o;
    }

    private static Map<String, Object> objeto(Map<String, Object> m, String clave) {
        Object v = m.get(clave);
        return v == null ? null : mapa(v);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> lista(Map<String, Object> m, String clave) {
        Object v = m.get(clave);
        return v == null ? List.of() : (List<Object>) v;
    }
}
