package alphasport.ui;

import alphasport.model.Campeonato;
import alphasport.model.ConfiguracionTorneo;
import alphasport.model.CriterioDesempate;
import alphasport.model.DirectorTecnico;
import alphasport.model.Equipo;
import alphasport.model.Fase;
import alphasport.model.Formacion;
import alphasport.model.Gol;
import alphasport.model.Jugador;
import alphasport.model.Partido;
import alphasport.model.Tarjeta;
import alphasport.model.TipoFase;
import alphasport.model.TipoFormato;
import alphasport.service.CalificacionesPartido;
import alphasport.service.CampeonatoService;
import alphasport.service.DetallePartido;
import alphasport.service.EstadisticasCampeonato;
import alphasport.service.FilaTabla;
import alphasport.service.GeneradorFixture;
import alphasport.service.MotorCampeonato;
import alphasport.service.Palmares;
import alphasport.service.ReglasVotacion;
import alphasport.service.ResumenCampeonato;
import alphasport.service.Sanciones;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Traduce el estado del servicio al formato JSON que dibuja la interfaz web.
 */
public class Presentador {

    private final CampeonatoService servicio;

    public Presentador(CampeonatoService servicio) {
        this.servicio = servicio;
    }

    public Map<String, Object> catalogo() {
        List<Object> formatos = new ArrayList<>();
        for (TipoFormato f : TipoFormato.values()) {
            formatos.add(mapa("clave", f.name(), "nombre", f.getNombre(), "descripcion", f.getDescripcion()));
        }
        List<Object> criterios = new ArrayList<>();
        for (CriterioDesempate c : CriterioDesempate.values()) {
            criterios.add(mapa("clave", c.name(), "texto", c.getDescripcion()));
        }
        return mapa("modo", "java", "formatos", formatos, "criterios", criterios,
                "posiciones", Arrays.asList(Jugador.POSICIONES),
                "gruposPosiciones", Arrays.stream(Jugador.GRUPOS_POSICIONES).map(g -> mapa("grupo", g[0],
                        "posiciones", Arrays.asList(g).subList(1, g.length))).collect(Collectors.toList()),
                "semillaMaximoEquipos", alphasport.service.DatosPrueba.MAXIMO_EQUIPOS, "configuracion", configuracion(new ConfiguracionTorneo()),
                "formaciones", Arrays.stream(Formacion.values()).map(Formacion::getTexto).collect(Collectors.toList()),
                "formacionPorDefecto", Formacion.POR_DEFECTO.getTexto(),
                // [REQ-07] Duracion de la votacion del publico: opciones rapidas y limites del campo de horas.
                "votacion", mapa("opciones", List.of(12, 24, 48), "minimo", ReglasVotacion.HORAS_MINIMO,
                        "maximo", ReglasVotacion.HORAS_MAXIMO, "porDefecto", ReglasVotacion.HORAS_POR_DEFECTO));
    }

    public Map<String, Object> estado() {
        if (!servicio.hayCampeonato()) {
            return mapa("hay", false);
        }
        Campeonato c = servicio.getCampeonato();
        MotorCampeonato motor = servicio.getMotor();
        Sanciones sanciones = servicio.sanciones();
        ResumenCampeonato resumen = new ResumenCampeonato(c);
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("hay", true);
        estado.put("campeonato", cabecera(c, motor));
        estado.put("equipos", c.getEquipos().stream().map(e -> equipo(e, sanciones)).collect(Collectors.toList()));
        estado.put("fases", c.getFases().stream().map(f -> mapa("nombre", f.getNombre(), "tipo", f.getTipo().name()))
                .collect(Collectors.toList()));
        estado.put("partidos", c.getPartidos().stream().map(p -> partido(c, p, sanciones)).collect(Collectors.toList()));
        estado.put("tablas", tablas(c, motor, resumen));
        estado.put("leyenda", "Orden: puntos › " + c.getConfiguracion().getCriterios().stream()
                .map(CriterioDesempate::getDescripcion).collect(Collectors.joining(" › ")) + ".");
        estado.put("llaves", llaves(c, motor));
        estado.put("sanciones", sanciones(c, sanciones));
        estado.put("estadisticas", estadisticas(c));
        estado.put("campeonFicha", c.isTerminado() ? ficha(resumen.filaDe(c.getIdCampeon())) : null);
        // Liga con divisiones: un campeon por division (Liga A, Liga B...), cada uno con las cifras de su propia tabla.
        List<Object> campeones = new ArrayList<>();
        List<Integer> porDivision = motor.campeonesPorDivision();
        for (int d = 0; d < porDivision.size(); d++) {
            int idCampeon = porDivision.get(d);
            FilaTabla fila = motor.tablaDeGrupo(0, d).stream().filter(f -> f.getIdEquipo() == idCampeon).findFirst().orElse(null);
            Map<String, Object> ficha = ficha(fila);
            if (ficha != null) {
                ficha.put("division", c.getFases().get(0).nombreGrupo(d));
                ficha.put("pts", fila.getPuntos());
                campeones.add(ficha);
            }
        }
        estado.put("campeonesDivision", campeones);
        return estado;
    }

    private Map<String, Object> cabecera(Campeonato c, MotorCampeonato motor) {
        return mapa("nombre", c.getNombreCampeonato(), "codigo", c.getCodigo(), "formato", c.getFormato().name(),
                "formatoNombre", c.getFormato().getNombre(), "formatoDescripcion", c.getFormato().getDescripcion(),
                "iniciado", c.isIniciado(), "terminado", c.isTerminado(), "estado", motor.describirEstado(),
                "campeon", c.getIdCampeon(), "campeonNombre", c.isTerminado() ? c.nombreDe(c.getIdCampeon()) : null,
                "reglas", Arrays.stream(c.getConfiguracion().resumen(c.getFormato()).split("\n"))
                        .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList()),
                "criterios", c.getConfiguracion().getCriterios().stream().map(Enum::name).collect(Collectors.toList()),
                "equiposActivos", c.getEquiposActivos().size(),
                "puedeSiguiente", c.getFormato() == TipoFormato.TORNEO_CORTO || c.getFormato() == TipoFormato.LIGA,
                "textoSiguiente", c.getFormato() == TipoFormato.LIGA ? "Nueva temporada" + (c.tieneDivisiones() ? " (ascensos y descensos)…" : "…")
                        : "Siguiente torneo corto (Clausura)…",
                // Sin llaves (formato Liga) la interfaz oculta la seccion «Llaves».
                "usaLlaves", motor.usaLlaves(),
                "esLiga", c.getFormato() == TipoFormato.LIGA,
                "divisiones", divisiones(c), "ascensos", c.getConfiguracion().getAscensos(),
                "maximoDivisiones", ConfiguracionTorneo.MAXIMO_DIVISIONES);
    }

    /** [{indice, nombre, equipos}] de cada division; una sola si la liga no tiene divisiones. */
    private List<Object> divisiones(Campeonato c) {
        List<Object> lista = new ArrayList<>();
        int n = c.getFormato() == TipoFormato.LIGA ? c.getConfiguracion().getDivisiones() : 1;
        for (int d = 0; d < n; d++) {
            lista.add(mapa("indice", d, "nombre", ConfiguracionTorneo.nombreDivision(d), "equipos", c.equiposDeDivision(d).size()));
        }
        return lista;
    }

    private Map<String, Object> equipo(Equipo e, Sanciones sanciones) {
        DirectorTecnico dt = e.getDirectorTecnico();
        List<Object> jugadores = new ArrayList<>();
        for (Jugador j : e.getJugadores()) {
            jugadores.add(mapa("id", j.getId(), "nombre", j.getNombre(), "documento", j.getDocumento(),
                    "dorsal", j.getDorsal(), "posicion", j.getPosicion(), "foto", j.getFoto(),
                    "amarillas", j.getId() == 0 ? 0 : sanciones.contar(j.getId(), false),
                    "rojas", j.getId() == 0 ? 0 : sanciones.contar(j.getId(), true),
                    "pendientes", j.getId() == 0 ? 0 : sanciones.fechasPendientesDe(j.getId())));
        }
        return mapa("id", e.getId(), "nombre", e.getNombreEquipo(), "estadio", e.getEstadio(), "retirado", e.isRetirado(),
                "escudo", e.getEscudo(),
                "dt", dt == null ? null : mapa("nombre", dt.getNombre(), "documento", dt.getDocumento(),
                        "nacionalidad", dt.getNacionalidad()),
                "division", servicio.getCampeonato().divisionDe(e),
                "jugadores", jugadores);
    }

    private Map<String, Object> partido(Campeonato c, Partido p, Sanciones sanciones) {
        List<Object> suspendidos = new ArrayList<>();
        for (Tarjeta t : sanciones.suspendidosEn(p)) {
            suspendidos.add(mapa("jugador", t.getIdJugador(), "nombre", t.getNombreJugador(),
                    "equipo", t.getIdEquipo(), "equipoNombre", c.nombreDe(t.getIdEquipo())));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("fase", p.getIndiceFase());
        m.put("grupo", p.getGrupo());
        // «Grupo B» o «Liga B» (liga con divisiones), listo para mostrar
        Fase fase = p.getIndiceFase() < c.getFases().size() ? c.getFases().get(p.getIndiceFase()) : null;
        m.put("grupoNombre", p.getGrupo().isEmpty() || fase == null ? "" : fase.nombreGrupo(p.getGrupo().charAt(0) - 'A'));
        m.put("ronda", p.getRonda());
        m.put("nombreRonda", p.getNombreRonda());
        m.put("pierna", p.getPierna());
        m.put("llave", p.getLlave());
        m.put("eliminatorio", p.isEliminatorio());
        m.put("local", p.getIdLocal());
        m.put("visitante", p.getIdVisitante());
        m.put("localNombre", c.nombreDe(p.getIdLocal()));
        m.put("visitanteNombre", c.nombreDe(p.getIdVisitante()));
        m.put("estado", p.getEstado().name());
        m.put("jugado", p.isJugado());
        m.put("gl", p.getGolesLocal());
        m.put("gv", p.getGolesVisitante());
        m.put("pl", p.getPenalesLocal());
        m.put("pv", p.getPenalesVisitante());
        m.put("penales", p.tienePenales());
        m.put("ganador", p.isJugado() ? p.getGanador() : -1);
        m.put("fecha", p.getFecha());
        m.put("hora", p.getHora());
        m.put("sede", p.getSede());
        m.put("obs", p.getObservaciones());
        m.put("tarjetas", p.getTarjetas().stream().map(this::tarjeta).collect(Collectors.toList()));
        m.put("titularesLocal", new ArrayList<>(p.getTitularesLocal()));
        m.put("titularesVisitante", new ArrayList<>(p.getTitularesVisitante()));
        m.put("formacionLocal", p.getFormacionLocal());
        m.put("formacionVisitante", p.getFormacionVisitante());
        m.put("goles", p.getGoles().stream().map(Presentador::gol).collect(Collectors.toList()));
        m.put("cambios", p.getCambios().stream().map(x -> mapa("equipo", x.getIdEquipo(), "minuto", x.getMinuto(),
                "sale", x.getIdSale(), "saleNombre", x.getNombreSale(), "saleDorsal", x.getDorsalSale(),
                "entra", x.getIdEntra(), "entraNombre", x.getNombreEntra(), "entraDorsal", x.getDorsalEntra()))
                .collect(Collectors.toList()));
        m.put("suspendidos", suspendidos);
        // [REQ-07] Votacion del publico: horas programadas, si se puede votar ahora, cuando cierra (ms; 0 = sin hora
        // de cierre), si el administrador la cerro antes de tiempo y cuantos votos lleva.
        m.put("votacion", mapa("horas", p.getHorasVotacion(), "abierta", servicio.votacionAbierta(p),
                "cierraEn", ReglasVotacion.cierraEn(p), "cerradaAntes", ReglasVotacion.cerradaAntes(p), "votos", p.getVotos().size()));
        return m;
    }

    /** JSON de un gol: {equipo, minuto, enContra, autor, autorNombre, autorDorsal, asistente, asistenteNombre, asistenteDorsal}. */
    static Map<String, Object> gol(Gol g) {
        return mapa("equipo", g.getIdEquipo(), "minuto", g.getMinuto(), "enContra", g.isEnContra(),
                "autor", g.getIdAutor(), "autorNombre", g.getNombreAutor(), "autorDorsal", g.getDorsalAutor(),
                "asistente", g.getIdAsistente(), "asistenteNombre", g.getNombreAsistente(), "asistenteDorsal", g.getDorsalAsistente());
    }

    /* ---------------------------------------------------------------- estadisticas globales del campeonato */

    /**
     * {goleadores, asistidores, goles (registro para auditar), golesSinAutor, valla, fairPlay}.
     * Las estadisticas de un partido NO van aqui: van en el detalle del partido.
     */
    private Map<String, Object> estadisticas(Campeonato c) {
        EstadisticasCampeonato est = servicio.estadisticas();
        List<Object> goles = new ArrayList<>();
        for (EstadisticasCampeonato.RegistroGol r : est.getRegistroGoles()) {
            Partido p = r.partido();
            Map<String, Object> g = gol(r.gol());
            g.put("partido", p.getId());
            g.put("partidoTexto", c.nombreDe(p.getIdLocal()) + " " + p.getGolesLocal() + "-" + p.getGolesVisitante() + " " + c.nombreDe(p.getIdVisitante()));
            g.put("ronda", p.getNombreRonda());
            g.put("fecha", p.getFecha());
            g.put("hora", p.getHora());
            g.put("equipoNombre", c.nombreDe(r.gol().getIdEquipo()));
            goles.add(g);
        }
        return mapa("goleadores", est.getGoleadores().stream().map(this::filaJugador).collect(Collectors.toList()),
                "asistidores", est.getAsistidores().stream().map(this::filaJugador).collect(Collectors.toList()),
                "goles", goles, "golesSinAutor", est.getGolesSinAutor(),
                "valla", est.getVallaMenosVencida().stream().map(v -> mapa("id", v.fila().getIdEquipo(),
                        "nombre", v.fila().getEquipo().getNombreEquipo(), "pj", v.fila().getJugados(), "gc", v.fila().getGolesContra(),
                        "promedio", String.format(java.util.Locale.ROOT, "%.2f", v.promedio()), "invictas", v.vallasInvictas()))
                        .collect(Collectors.toList()),
                "fairPlay", est.getFairPlay().stream().map(f -> mapa("id", f.getIdEquipo(), "nombre", f.getEquipo().getNombreEquipo(),
                        "pj", f.getJugados(), "ta", f.getAmarillas(), "tr", f.getRojas(), "puntos", f.getPuntosFairPlay(),
                        "promedio", String.format(java.util.Locale.ROOT, "%.2f", f.getPuntosFairPlay() / (double) f.getJugados())))
                        .collect(Collectors.toList()));
    }

    private Map<String, Object> filaJugador(EstadisticasCampeonato.FilaJugador f) {
        return mapa("jugador", f.getIdJugador(), "nombre", f.getNombre(), "dorsal", f.getDorsal(),
                "equipo", f.getIdEquipo(), "equipoNombre", f.getEquipo(), "goles", f.getGoles(), "asistencias", f.getAsistencias());
    }

    private Map<String, Object> tarjeta(Tarjeta t) {
        return mapa("equipo", t.getIdEquipo(), "jugador", t.getIdJugador(), "nombre", t.getNombreJugador(),
                "dorsal", t.getDorsal(), "tipo", t.isRoja() ? "ROJA" : "AMARILLA", "fechas", t.getFechasSuspension(),
                "minuto", t.getMinuto());
    }

    private List<Object> tablas(Campeonato c, MotorCampeonato motor, ResumenCampeonato resumen) {
        ConfiguracionTorneo cfg = c.getConfiguracion();
        boolean corto = c.getFormato() == TipoFormato.TORNEO_CORTO;
        List<Object> tablas = new ArrayList<>();
        for (int i = 0; i < c.getFases().size(); i++) {
            Fase f = c.getFases().get(i);
            if (f.getTipo() == TipoFase.LIGA) {
                tablas.add(tabla(f.getNombre(), corto ? cfg.getClasificadosPlayoff() : 1, 0, motor.tablaDeFase(i)));
            } else if (f.getTipo() == TipoFase.SUIZO) {
                tablas.add(tabla(f.getNombre(), cfg.getDirectosSuizo(), cfg.getRepechajeSuizo(), motor.tablaDeFase(i)));
            } else if (f.getTipo() == TipoFase.GRUPOS && f.isDivisiones()) {
                // Liga con divisiones: verde = asciende (no en la Liga A), rojo = desciende (no en la ultima).
                int n = f.getGrupos().size();
                for (int g = 0; g < n; g++) {
                    List<FilaTabla> filas = motor.tablaDeGrupo(i, g);
                    int k = Math.max(0, Math.min(cfg.getAscensos(), filas.size() - 1));
                    Map<String, Object> t = tabla(f.nombreGrupo(g), g > 0 ? k : 0, 0, filas);
                    t.put("descenso", g < n - 1 ? k : 0);
                    t.put("division", true);
                    tablas.add(t);
                }
            } else if (f.getTipo() == TipoFase.GRUPOS) {
                for (int g = 0; g < f.getGrupos().size(); g++) {
                    tablas.add(tabla(f.getNombre() + " - " + f.nombreGrupo(g),
                            corto ? 1 : cfg.getClasificadosPorGrupo(), 0, motor.tablaDeGrupo(i, g)));
                }
            }
        }
        tablas.add(tabla("Estadísticas generales (todos los partidos)", 0, 0, resumen.getTablaGeneral()));
        return tablas;
    }

    private Map<String, Object> tabla(String texto, int directos, int repechaje, List<FilaTabla> filas) {
        return mapa("texto", texto, "directos", directos, "repechaje", repechaje, "descenso", 0, "division", false,
                "filas", filas.stream().map(this::fila).collect(Collectors.toList()));
    }

    private Map<String, Object> fila(FilaTabla f) {
        return mapa("id", f.getIdEquipo(), "nombre", f.getEquipo().getNombreEquipo(), "retirado", f.getEquipo().isRetirado(),
                "pj", f.getJugados(), "g", f.getGanados(), "e", f.getEmpatados(), "p", f.getPerdidos(),
                "gf", f.getGolesFavor(), "gc", f.getGolesContra(), "dg", f.getDiferenciaGoles(),
                "ta", f.getAmarillas(), "tr", f.getRojas(), "pts", f.getPuntos());
    }

    private Map<String, Object> ficha(FilaTabla f) {
        if (f == null) {
            return null;
        }
        return mapa("nombre", f.getEquipo().getNombreEquipo(), "dt", f.getEquipo().getNombreDirectorTecnico(),
                "pj", f.getJugados(), "g", f.getGanados(), "e", f.getEmpatados(), "p", f.getPerdidos(),
                "gf", f.getGolesFavor(), "gc", f.getGolesContra());
    }

    private List<Object> llaves(Campeonato c, MotorCampeonato motor) {
        List<Object> llaves = new ArrayList<>();
        for (int i = 0; i < c.getFases().size(); i++) {
            Fase f = c.getFases().get(i);
            if (f.getTipo() == TipoFase.ELIMINATORIA) {
                llaves.add(mapa("nombre", f.getNombre(), "secciones", List.of(mapa("titulo", null,
                        "columnas", columnasEliminatoria(c, motor, f, i))), "vivos", null));
            } else if (f.getTipo() == TipoFase.DOBLE_ELIMINACION) {
                List<Object> secciones = new ArrayList<>();
                String[][] partes = {{MotorCampeonato.LLAVE_GANADORES, "Llave de ganadores"},
                    {MotorCampeonato.LLAVE_PERDEDORES, "Llave de perdedores"}, {MotorCampeonato.LLAVE_GRAN_FINAL, "Gran final"}};
                for (String[] parte : partes) {
                    List<Object> columnas = columnasDoble(c, motor, i, parte[0]);
                    if (!columnas.isEmpty()) {
                        secciones.add(mapa("titulo", parte[1], "columnas", columnas));
                    }
                }
                llaves.add(mapa("nombre", f.getNombre(), "secciones", secciones, "vivos", mapa(
                        "ganadores", nombres(c, motor.vivosDoble(f, i, 0)),
                        "perdedores", nombres(c, motor.vivosDoble(f, i, 1)))));
            }
        }
        return llaves;
    }

    private List<Object> columnasEliminatoria(Campeonato c, MotorCampeonato motor, Fase f, int i) {
        List<Object> columnas = new ArrayList<>();
        for (int r = 1; r <= motor.ultimaRonda(i); r++) {
            List<Integer> entrantes = motor.entrantesDeRonda(f, i, r);
            List<Integer> ganadores = motor.ganadoresDeRonda(f, i, r);
            List<Object> cruces = new ArrayList<>();
            for (int k = 0; k + 1 < entrantes.size(); k += 2) {
                int a = entrantes.get(k);
                int b = entrantes.get(k + 1);
                if (a == Fase.LIBRE || b == Fase.LIBRE) {
                    cruces.add(List.of(lado(c.nombreDe(a == Fase.LIBRE ? b : a), "pasa directo", true)));
                    continue;
                }
                int ganador = k / 2 < ganadores.size() ? ganadores.get(k / 2) : -1;
                List<Partido> serie = motor.partidosDeSerie(MotorCampeonato.claveSerie(i, r, k / 2));
                cruces.add(List.of(lado(c.nombreDe(a), golesSerie(serie, a), ganador == a),
                        lado(c.nombreDe(b), golesSerie(serie, b), ganador == b)));
            }
            String nombre = f.getMaxRondas() == 1 ? f.getNombre() : GeneradorFixture.nombreRonda(entrantes.size());
            columnas.add(mapa("nombre", nombre, "cruces", cruces));
        }
        return columnas;
    }

    private String golesSerie(List<Partido> serie, int idEquipo) {
        String goles = serie.stream().map(p -> p.isJugado() ? String.valueOf(p.getGolesDe(idEquipo)) : "·")
                .collect(Collectors.joining(" + "));
        Partido ultimo = serie.isEmpty() ? null : serie.get(serie.size() - 1);
        if (ultimo != null && ultimo.tienePenales()) {
            goles += " (" + (ultimo.getIdLocal() == idEquipo ? ultimo.getPenalesLocal() : ultimo.getPenalesVisitante()) + ")";
        }
        return goles;
    }

    private List<Object> columnasDoble(Campeonato c, MotorCampeonato motor, int i, String llave) {
        List<Object> columnas = new ArrayList<>();
        for (int r = 1; r <= motor.ultimaRonda(i); r++) {
            final int ronda = r;
            List<Partido> partidos = c.partidosDeFase(i).stream()
                    .filter(p -> p.getRonda() == ronda && llave.equals(p.getLlave())).collect(Collectors.toList());
            if (partidos.isEmpty()) {
                continue;
            }
            List<Object> cruces = new ArrayList<>();
            for (Partido p : partidos) {
                int ganador = p.isJugado() ? p.getGanador() : -1;
                cruces.add(List.of(lado(c.nombreDe(p.getIdLocal()), golesSerie(List.of(p), p.getIdLocal()), ganador == p.getIdLocal()),
                        lado(c.nombreDe(p.getIdVisitante()), golesSerie(List.of(p), p.getIdVisitante()), ganador == p.getIdVisitante())));
            }
            columnas.add(mapa("nombre", partidos.get(0).getNombreRonda(), "cruces", cruces));
        }
        return columnas;
    }

    private Map<String, Object> lado(String nombre, String goles, boolean gana) {
        return mapa("nombre", nombre, "goles", goles, "gana", gana);
    }

    private List<String> nombres(Campeonato c, List<Integer> ids) {
        return ids.stream().map(c::nombreDe).collect(Collectors.toList());
    }

    private Map<String, Object> sanciones(Campeonato c, Sanciones sanciones) {
        List<Object> registros = new ArrayList<>();
        List<String> suspendidos = new ArrayList<>();
        for (Sanciones.Registro r : sanciones.listar()) {
            Partido p = r.getPartido();
            Tarjeta t = r.getTarjeta();
            registros.add(mapa("idPartido", p.getId(), "partido", c.nombreDe(p.getIdLocal()) + " vs " + c.nombreDe(p.getIdVisitante()),
                    "ronda", p.getNombreRonda(), "fecha", p.getFecha(), "hora", p.getHora(),
                    "equipo", c.nombreDe(t.getIdEquipo()), "jugador", t.getNombreJugador(), "dorsal", t.getDorsal(),
                    "tipo", t.isRoja() ? "ROJA" : "AMARILLA", "fechas", t.getFechasSuspension(),
                    "pendientes", r.getFechasPendientes()));
            if (r.getFechasPendientes() > 0) {
                suspendidos.add(t.getNombreJugador() + " – " + c.nombreDe(t.getIdEquipo()) + " (" + r.getFechasPendientes() + ")");
            }
        }
        return mapa("resumen", suspendidos.isEmpty() ? "No hay jugadores suspendidos."
                : "Suspendidos (fechas que faltan): " + String.join(", ", suspendidos), "registros", registros);
    }

    /* ---------------------------------------------------------------- detalle de partido */

    /**
     * JSON del detalle: {partido, caraACara:{resumen, partidos}, racha:{local, visitante}, alineaciones:{local, visitante},
     * calificaciones}. La racha de cada equipo es una lista de {resultado: V|E|D, rival, marcador, ...}.
     */
    public Map<String, Object> detallePartido(DetallePartido d, CalificacionesPartido notas) {
        Campeonato c = servicio.getCampeonato();
        DetallePartido.ResumenCaraACara r = d.getResumenCaraACara();
        return mapa("ok", true,
                "partido", partido(c, d.getPartido(), servicio.sanciones()),
                "caraACara", mapa("victoriasLocal", r.victoriasLocal(), "empates", r.empates(),
                        "victoriasVisitante", r.victoriasVisitante(),
                        "partidos", d.getCaraACara().stream().map(x -> registro(x, null)).collect(Collectors.toList())),
                "racha", mapa("local", d.getRachaLocal().stream().map(x -> registro(x, d.getLocal())).collect(Collectors.toList()),
                        "visitante", d.getRachaVisitante().stream().map(x -> registro(x, d.getVisitante())).collect(Collectors.toList())),
                "alineaciones", mapa("local", alineacion(d.getAlineacionLocal()), "visitante", alineacion(d.getAlineacionVisitante())),
                "calificaciones", calificaciones(notas));
    }

    /**
     * [REQ-07] JSON de las calificaciones: {votacion: SIN_RESULTADO|ABIERTA|CERRADA|SIN_REGISTRO, cierraEn (ms),
     * disponible, motivo, mvp (id del jugador), local: [...], visitante: [...]}. Cada jugador lleva su nota del
     * sistema (con el desglose), los votos del publico, el promedio, la nota final y su propio voto (miVoto, 0 = no voto).
     */
    public static Map<String, Object> calificaciones(CalificacionesPartido n) {
        java.util.function.Function<List<CalificacionesPartido.Calificacion>, List<Object>> jugadores = lista -> lista.stream()
                .map(x -> (Object) mapa("id", x.idJugador(), "equipo", x.idEquipo(), "nombre", x.nombre(), "dorsal", x.dorsal(),
                        "posicion", x.posicion(), "foto", x.foto(), "portero", x.portero(), "titular", x.titular(), "goles", x.goles(),
                        "asistencias", x.asistencias(), "amarillas", x.amarillas(), "rojas", x.rojas(),
                        "golesRecibidos", x.golesRecibidos(), "notaSistema", x.notaSistema(), "desglose", x.desglose(),
                        "votos", x.votos(), "promedioPublico", x.promedioPublico(), "notaFinal", x.notaFinal(),
                        "mvp", x.mvp(), "miVoto", x.miVoto()))
                .collect(Collectors.toList());
        return mapa("votacion", n.votacion().name(), "cierraEn", n.cierraEn(), "disponible", n.disponible(),
                "motivo", n.motivoSinCalificaciones(), "mvp", n.idMvp(),
                "local", jugadores.apply(n.local()), "visitante", jugadores.apply(n.visitante()));
    }

    /**
     * [REQ-07] Votaciones abiertas para el visitante: [{partido, calificaciones}], primero la que cierra antes.
     * En cada jugador, miVoto dice si este dispositivo ya lo califico.
     */
    public List<Object> votaciones(String dispositivo) {
        List<Object> lista = new ArrayList<>();
        for (Partido p : servicio.votacionesAbiertas()) {
            lista.add(mapa("partido", partido(servicio.getCampeonato(), p, servicio.sanciones()),
                    "calificaciones", calificaciones(servicio.calificaciones(p.getId(), dispositivo))));
        }
        return lista;
    }

    /** Partido previo; si se da el equipo, agrega resultado (V/E/D) y rival desde su punto de vista. */
    private Map<String, Object> registro(DetallePartido.Registro x, String equipo) {
        Map<String, Object> m = mapa("competicion", x.competicion(), "ronda", x.ronda(), "fecha", x.fecha(),
                "local", x.local(), "visitante", x.visitante(), "gl", x.golesLocal(), "gv", x.golesVisitante(),
                "ganador", x.ganador());
        if (equipo != null) {
            boolean esLocal = x.local().trim().equalsIgnoreCase(equipo.trim());
            m.put("resultado", String.valueOf(x.resultadoPara(equipo)));
            m.put("rival", esLocal ? x.visitante() : x.local());
            m.put("condicion", esLocal ? "L" : "V");
        }
        return m;
    }

    private Map<String, Object> alineacion(DetallePartido.Alineacion a) {
        List<Object> lineas = new ArrayList<>();
        for (List<DetallePartido.Puesto> linea : a.lineas()) {
            lineas.add(linea.stream().map(p -> p.jugador() == null ? null : (Object) fichaJugador(p.jugador()))
                    .collect(Collectors.toList()));
        }
        DirectorTecnico dt = a.directorTecnico();
        return mapa("formacion", a.formacion(), "formacionElegida", a.formacionElegida(), "confirmada", a.confirmada(),
                "lineas", lineas,
                "suplentes", a.suplentes().stream().map(Presentador::fichaJugador).collect(Collectors.toList()),
                "dt", dt == null ? "" : dt.getNombre());
    }

    /** Lo que se dibuja de un jugador en la cancha y en la lista de suplentes. */
    private static Map<String, Object> fichaJugador(Jugador j) {
        return mapa("id", j.getId(), "dorsal", j.getDorsal(), "nombre", j.getNombre(), "posicion", j.getPosicion(), "foto", j.getFoto());
    }

    public Map<String, Object> historial(List<Campeonato> campeonatos) {
        List<Object> lista = new ArrayList<>();
        for (Campeonato c : campeonatos) {
            lista.add(detalleHistorial(c));
        }
        List<Object> palmares = new ArrayList<>();
        for (Palmares.Fila f : Palmares.calcular(campeonatos)) {
            palmares.add(mapa("equipo", f.getEquipo(), "titulos", f.getTitulos(), "sub", f.getSubcampeonatos(),
                    "podios", f.getPodios(), "participaciones", f.getParticipaciones(), "pj", f.getJugados(),
                    "g", f.getGanados(), "e", f.getEmpatados(), "p", f.getPerdidos(), "gf", f.getGolesFavor(),
                    "gc", f.getGolesContra(), "pts", f.getPuntos()));
        }
        return mapa("lista", lista, "palmares", palmares);
    }

    private Map<String, Object> detalleHistorial(Campeonato c) {
        ResumenCampeonato r = new ResumenCampeonato(c);
        List<FilaTabla> clasificacion = r.clasificacionFinal();
        List<Object> podio = new ArrayList<>();
        for (int i = 0; i < Math.min(3, clasificacion.size()); i++) {
            Equipo e = clasificacion.get(i).getEquipo();
            podio.add(mapa("nombre", e.getNombreEquipo(), "dt", e.getNombreDirectorTecnico()));
        }
        List<Object> premios = new ArrayList<>();
        FilaTabla goleador = r.equipoMasGoleador();
        FilaTabla valla = r.vallaMenosVencida();
        FilaTabla fairPlay = r.premioFairPlay();
        Partido goleada = r.mayorGoleada();
        if (goleador != null) {
            premios.add(premio("⚽ Equipo más goleador", goleador.getEquipo().getNombreEquipo(), goleador.getGolesFavor() + " goles"));
        }
        if (valla != null) {
            premios.add(premio("🧤 Valla menos vencida", valla.getEquipo().getNombreEquipo(),
                    valla.getGolesContra() + " goles recibidos en " + valla.getJugados() + " partidos"));
        }
        if (fairPlay != null) {
            premios.add(premio("🤝 Premio Fair Play", fairPlay.getEquipo().getNombreEquipo(),
                    fairPlay.getAmarillas() + " amarillas, " + fairPlay.getRojas() + " rojas"));
        }
        if (goleada != null) {
            premios.add(premio("💥 Mayor goleada", c.nombreDe(goleada.getIdLocal()) + " " + goleada.getGolesLocal() + " - "
                    + goleada.getGolesVisitante() + " " + c.nombreDe(goleada.getIdVisitante()), goleada.getNombreRonda()));
        }
        int jugados = r.partidosJugados();
        int goles = r.golesTotales();
        premios.add(premio("📊 Cifras", jugados + " partidos · " + goles + " goles",
                jugados > 0 ? String.format("%.2f goles por partido", goles / (double) jugados) : ""));
        List<Object> resultados = new ArrayList<>();
        for (Partido p : c.getPartidos()) {
            Fase faseP = c.getFases().get(p.getIndiceFase());
            resultados.add(mapa("fase", faseP.getNombre()
                    + (p.getGrupo().isEmpty() ? "" : " · " + faseP.nombreGrupo(p.getGrupo().charAt(0) - 'A')), "ronda", p.getNombreRonda(),
                    "local", c.nombreDe(p.getIdLocal()), "marcador", p.getMarcador(),
                    "visitante", c.nombreDe(p.getIdVisitante()), "estado", p.getEstado().toString()));
        }
        List<Object> tarjetas = new ArrayList<>();
        for (Sanciones.Registro reg : new Sanciones(c).listar()) {
            Tarjeta t = reg.getTarjeta();
            Partido p = reg.getPartido();
            tarjetas.add(mapa("equipo", c.nombreDe(t.getIdEquipo()), "jugador", "#" + t.getDorsal() + " " + t.getNombreJugador(),
                    "tipo", t.isRoja() ? "ROJA" : "AMARILLA", "fechas", t.getFechasSuspension(),
                    "partido", c.nombreDe(p.getIdLocal()) + " vs " + c.nombreDe(p.getIdVisitante())));
        }
        return mapa("codigo", c.getCodigo(), "nombre", c.getNombreCampeonato(), "formato", c.getFormato().getNombre(),
                "fecha", c.getFechaArchivo(), "campeon", c.isTerminado() ? c.nombreDe(c.getIdCampeon()) : null,
                "terminado", c.isTerminado(), "podio", podio, "premios", premios,
                "clasificacion", clasificacion.stream().map(this::fila).collect(Collectors.toList()),
                "resultados", resultados, "tarjetas", tarjetas, "texto", r.texto());
    }

    private Map<String, Object> premio(String titulo, String valor, String detalle) {
        return mapa("titulo", titulo, "valor", valor, "detalle", detalle);
    }

    /* ---------------------------------------------------------------- noticias */

    /**
     * {publicadas, pendientes, puedeModerar}. Cada noticia: {id, titulo, cuerpo, autor, deVisitante, fecha,
     * imagen (nombre del archivo, "" = sin foto), aprobado, comentarios:[{id, autor, texto, fecha}]}.
     */
    static Map<String, Object> noticias(List<alphasport.model.Noticia> publicadas, List<alphasport.model.Noticia> pendientes, boolean moderar) {
        return mapa("publicadas", publicadas.stream().map(Presentador::noticia).collect(Collectors.toList()),
                "pendientes", pendientes.stream().map(Presentador::noticia).collect(Collectors.toList()),
                "puedeModerar", moderar);
    }

    private static Map<String, Object> noticia(alphasport.model.Noticia n) {
        return mapa("id", n.getId(), "titulo", n.getTitulo(), "cuerpo", n.getCuerpo(), "autor", n.getAutor(),
                "deVisitante", n.getRolAutor() == alphasport.model.Rol.VISITANTE, "fecha", n.getFecha(),
                "imagen", n.getImagenRuta(), "aprobado", n.isAprobado(),
                "comentarios", n.getComentarios().stream().map(c -> mapa("id", c.getId(), "autor", c.getAutor(),
                        "texto", c.getTexto(), "fecha", c.getFecha())).collect(Collectors.toList()));
    }

    static Map<String, Object> configuracion(ConfiguracionTorneo cfg) {
        return mapa("puntosVictoria", cfg.getPuntosVictoria(), "puntosEmpate", cfg.getPuntosEmpate(),
                "idaYVuelta", cfg.isIdaYVuelta(), "eliminatoriaIdaYVuelta", cfg.isEliminatoriaIdaYVuelta(),
                "finalUnica", cfg.isFinalUnica(), "sorteoAleatorio", cfg.isSorteoAleatorio(),
                "tamanoGrupo", cfg.getTamanoGrupo(), "clasificadosPorGrupo", cfg.getClasificadosPorGrupo(),
                "clasificadosPlayoff", cfg.getClasificadosPlayoff(), "tipoPlayoff", cfg.getTipoPlayoff(),
                "ventajaDeportiva", cfg.isVentajaDeportiva(), "rondasSuizo", cfg.getRondasSuizo(),
                "directosSuizo", cfg.getDirectosSuizo(), "repechajeSuizo", cfg.getRepechajeSuizo(),
                "granFinalConReinicio", cfg.isGranFinalConReinicio(),
                "divisiones", cfg.getDivisiones(), "ascensos", cfg.getAscensos(),
                "criterios", cfg.getCriterios().stream().map(Enum::name).collect(Collectors.toList()));
    }

    static Map<String, Object> mapa(Object... pares) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            m.put((String) pares[i], pares[i + 1]);
        }
        return m;
    }
}