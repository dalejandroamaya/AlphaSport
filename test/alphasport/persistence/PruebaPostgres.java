package alphasport.persistence;

import alphasport.model.Campeonato;
import alphasport.model.ConfiguracionTorneo;
import alphasport.model.Equipo;
import alphasport.model.EstadoPartido;
import alphasport.model.Gol;
import alphasport.model.Jugador;
import alphasport.model.Noticia;
import alphasport.model.Partido;
import alphasport.model.Tarjeta;
import alphasport.model.TipoFormato;
import alphasport.persistence.postgres.ConexionDB;
import alphasport.persistence.postgres.ConsultasDAO;
import alphasport.persistence.postgres.MigradorArchivos;
import alphasport.persistence.postgres.PostgresRepositorioCampeonato;
import alphasport.persistence.postgres.PostgresRepositorioHistorial;
import alphasport.persistence.postgres.PostgresRepositorioNoticias;
import alphasport.service.CalificacionesPartido;
import alphasport.service.CampeonatoService;
import alphasport.service.NoticiasService;
import alphasport.service.ReglaNegocioException;
import alphasport.ui.Presentador;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Prueba la persistencia en PostgreSQL. Usa la conexion de db.properties, pero trabaja en un esquema temporal
 * (prueba_...) que se borra al final: nunca toca los datos del programa. Termina en TODO BIEN.
 */
public final class PruebaPostgres {

    private static final ConexionDB.Configuracion CONFIGURACION = configuracion();
    private static int fallos;

    private PruebaPostgres() {
    }

    private interface Prueba {
        boolean correr(ConexionDB db) throws Exception;
    }

    public static void main(String[] args) {
        try (ConexionDB db = new ConexionDB(CONFIGURACION)) {
            db.ejecutar(con -> null);
        } catch (PersistenciaException e) {
            System.out.println("SIN CONEXION: " + e.getMessage());
            System.exit(2);
        }
        probar("Configuración y mensajes de error de la conexión", db -> probarConexion());
        probar("Esquema: tablas, vistas y restricciones", PruebaPostgres::probarEsquema);
        probar("Datos de prueba (seed.sql)", PruebaPostgres::probarSeed);
        probar("Los 6 formatos se guardan y se leen iguales", PruebaPostgres::probarFormatos);
        probar("Partido completo: XI, goles, tarjetas, cambios y votos", PruebaPostgres::probarPartido);
        probar("Consultas SQL iguales a los cálculos del programa", PruebaPostgres::probarConsultas);
        probar("Plantilla: dorsales intercambiados y jugadores retirados", PruebaPostgres::probarPlantilla);
        probar("Dos programas a la vez: sincronización y conflictos", PruebaPostgres::probarDosProgramas);
        probar("Historial: copias que no cambian con el campeonato actual", PruebaPostgres::probarHistorial);
        probar("Noticias y comentarios", PruebaPostgres::probarNoticias);
        probar("Migración de los archivos anteriores", PruebaPostgres::probarMigracion);
        System.out.println(fallos == 0 ? "TODO BIEN" : "FALLOS: " + fallos);
        System.exit(fallos == 0 ? 0 : 1);
    }

    private static ConexionDB.Configuracion configuracion() {
        try {
            return ConexionDB.leerConfiguracion(ConexionDB.ARCHIVO);
        } catch (PersistenciaException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Corre la prueba en un esquema temporal recien creado y lo borra al final. */
    private static void probar(String nombre, Prueba prueba) {
        String esquema = "prueba_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        boolean correcto = false;
        try (ConexionDB admin = new ConexionDB(CONFIGURACION)) {
            sql(admin, "CREATE SCHEMA " + esquema);
            try (ConexionDB db = new ConexionDB(CONFIGURACION, esquema)) {
                script(db, "database/schema.sql");
                correcto = prueba.correr(db);
            } finally {
                sql(admin, "DROP SCHEMA " + esquema + " CASCADE");
            }
        } catch (Exception e) {
            System.out.println("  ERROR: " + e);
            e.printStackTrace(System.out);
        }
        System.out.println(nombre + ": " + (correcto ? "correcto" : "MAL"));
        if (!correcto) {
            fallos++;
        }
    }

    private static void sql(ConexionDB db, String texto) throws PersistenciaException {
        db.ejecutar(con -> {
            try (var st = con.createStatement()) {
                return st.execute(texto);
            }
        });
    }

    /** Ejecuta un archivo .sql; su BEGIN y su COMMIT sobran porque ya hay una transaccion. */
    private static void script(ConexionDB db, String archivo) throws Exception {
        sql(db, Files.readString(Path.of(archivo)).replaceAll("(?m)^(BEGIN|COMMIT);\\s*$", ""));
    }

    private static int contar(ConexionDB db, String consulta) throws PersistenciaException {
        return db.ejecutar(con -> {
            try (var st = con.createStatement(); var rs = st.executeQuery(consulta)) {
                rs.next();
                return rs.getInt(1);
            }
        });
    }

    private interface Accion {
        void hacer() throws Exception;
    }

    private static boolean falla(Class<? extends Exception> tipo, Accion accion) {
        try {
            accion.hacer();
            return false;
        } catch (Exception e) {
            return tipo.isInstance(e);
        }
    }

    private static CampeonatoService servicio(ConexionDB db) throws PersistenciaException {
        CampeonatoService s = new CampeonatoService(new PostgresRepositorioCampeonato(db), new PostgresRepositorioHistorial(db));
        s.cargarGuardado();
        return s;
    }

    /** El campeonato como lo ve la interfaz: sirve para comparar lo guardado con lo leido. */
    private static String foto(CampeonatoService s) {
        return String.valueOf(new Presentador(s).estado());
    }

    /** Otro programa que abre la misma base: lo que lee tiene que ser lo mismo. */
    private static boolean seLeeIgual(ConexionDB db, CampeonatoService s) throws PersistenciaException {
        String antes = foto(s), despues = foto(servicio(db));
        if (!antes.equals(despues)) {
            int i = 0;
            while (i < Math.min(antes.length(), despues.length()) && antes.charAt(i) == despues.charAt(i)) {
                i++;
            }
            System.out.println("  diferente en " + i + ":\n    " + antes.substring(Math.max(0, i - 60), Math.min(antes.length(), i + 80))
                    + "\n    " + despues.substring(Math.max(0, i - 60), Math.min(despues.length(), i + 80)));
        }
        return antes.equals(despues);
    }

    private static CampeonatoService liga(ConexionDB db, int equipos) throws PersistenciaException {
        CampeonatoService s = servicio(db);
        ConfiguracionTorneo cfg = new ConfiguracionTorneo();
        cfg.setSorteoAleatorio(false);
        s.crear("Liga de prueba", TipoFormato.LIGA, cfg, false);
        s.cargarDatosPrueba(4242, equipos, true);
        s.avanzar();
        return s;
    }

    private static Partido copia(Partido p) {
        Partido datos = new Partido();
        datos.copiarDatosDe(p);
        return datos;
    }

    /* ---------------------------------------------------------------- conexion y esquema */

    private static boolean probarConexion() throws Exception {
        Path vacio = Files.createTempFile("alphasport", ".properties");
        ConexionDB.Configuracion c = ConexionDB.leerConfiguracion(vacio);
        boolean porDefecto = c.host().equals("localhost") && c.puerto() == 5432 && c.base().equals("alphasport_db") && c.usuario().equals("postgres");
        Files.writeString(vacio, "db.host=otro\ndb.port=9999\ndb.name=base\ndb.user=yo\ndb.password=secreta\n");
        c = ConexionDB.leerConfiguracion(vacio);
        boolean leida = c.url().equals("jdbc:postgresql://otro:9999/base") && c.clave().equals("secreta") && !c.toString().contains("secreta");
        boolean sinBase = mensaje(new ConexionDB.Configuracion(CONFIGURACION.host(), CONFIGURACION.puerto(), "no_existe_" + System.nanoTime(),
                CONFIGURACION.usuario(), CONFIGURACION.clave())).contains("no existe");
        boolean sinServidor = mensaje(new ConexionDB.Configuracion("localhost", 1, "x", "x", "x")).contains("No hay conexión");
        // Si el trabajo falla se deshace completo y la conexion sigue sirviendo.
        boolean deshace;
        try (ConexionDB db = new ConexionDB(CONFIGURACION)) {
            deshace = falla(PersistenciaException.class, () -> sql(db, "SELECT 1/0")) && contar(db, "SELECT 1") == 1;
        }
        return porDefecto && leida && sinBase && sinServidor && deshace;
    }

    private static String mensaje(ConexionDB.Configuracion c) {
        try (ConexionDB db = new ConexionDB(c)) {
            db.verificar();
            return "";
        } catch (PersistenciaException e) {
            return e.getMessage();
        }
    }

    private static boolean probarEsquema(ConexionDB db) throws Exception {
        db.verificar();
        Set<String> tablas = new HashSet<>();
        db.ejecutar(con -> {
            try (var st = con.createStatement(); var rs = st.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema = current_schema()")) {
                while (rs.next()) {
                    tablas.add(rs.getString(1));
                }
                return null;
            }
        });
        boolean completas = tablas.containsAll(List.of("usuarios", "torneos", "divisiones", "equipos", "jugadores", "partidos", "alineaciones",
                "eventos_partido", "noticias", "comentarios", "votos_publico", "calificaciones_partido", "v_tabla_posiciones",
                "v_ultima_alineacion", "v_votaciones_activas", "v_calificaciones"));
        sql(db, "INSERT INTO usuarios (nombre, username, password_hash, rol) VALUES ('A', 'a', 'x', 'ADMIN')");
        boolean restricciones = falla(PersistenciaException.class, () -> sql(db, "INSERT INTO usuarios (nombre, username, password_hash) VALUES ('B', 'a', 'x')"))
                && falla(PersistenciaException.class, () -> sql(db, "INSERT INTO usuarios (nombre, username, password_hash, rol) VALUES ('B', 'b', 'x', 'JEFE')"))
                && falla(PersistenciaException.class, () -> sql(db, "INSERT INTO comentarios (noticia_id, autor, texto) VALUES (99, 'a', 'b')"));
        return completas && restricciones;
    }

    private static boolean probarSeed(ConexionDB db) throws Exception {
        // pgcrypto vive en public: aqui solo se prueban los datos.
        String texto = Files.readString(Path.of("database/seed.sql")).replaceAll("(?m)^(BEGIN|COMMIT);\\s*$", "");
        sql(db, texto.substring(0, texto.indexOf("-- ---------------------------------------------------------------- usuarios")));
        CampeonatoService s = servicio(db);
        boolean leido = s.getCampeonato().getEquipos().size() == 6 && s.getCampeonato().getEquipos().stream().allMatch(e -> e.getJugadores().size() == 16)
                && contar(db, "SELECT COUNT(*) FROM jugadores WHERE es_capitan") == 6;
        s.avanzar(); // el torneo de prueba se puede iniciar y seguir usando
        s.guardarEquipo(1, "Pumas del Sur", "", null, s.getCampeonato().buscarEquipo(1).getJugadores());
        boolean capitan = contar(db, "SELECT COUNT(*) FROM jugadores WHERE es_capitan") == 6; // guardar no borra el capitan
        return leido && capitan && s.getCampeonato().getPartidos().size() == 15 && seLeeIgual(db, s);
    }

    /* ---------------------------------------------------------------- campeonatos */

    private static boolean probarFormatos(ConexionDB db) throws Exception {
        boolean bien = true;
        Random azar = new Random(7);
        for (TipoFormato formato : TipoFormato.values()) {
            CampeonatoService s = servicio(db);
            ConfiguracionTorneo cfg = new ConfiguracionTorneo();
            cfg.setClasificadosPlayoff(4);
            cfg.setRondasSuizo(4);
            cfg.setDirectosSuizo(4);
            cfg.setRepechajeSuizo(0);
            if (formato == TipoFormato.LIGA) {
                cfg.setDivisiones(2);
            }
            s.crear("Torneo " + formato, formato, cfg, false);
            s.cargarDatosPrueba(99, 8, true);
            s.avanzar();
            boolean igual = seLeeIgual(db, s);
            for (int vuelta = 0; vuelta < 60 && !s.getCampeonato().isTerminado(); vuelta++) {
                for (Partido p : new ArrayList<>(s.getCampeonato().getPartidos())) {
                    if (!p.isJugado()) {
                        Partido d = copia(p);
                        d.setEstado(EstadoPartido.JUGADO);
                        d.setGolesLocal(azar.nextInt(4));
                        d.setGolesVisitante(azar.nextInt(4));
                        d.setPenalesLocal(4);
                        d.setPenalesVisitante(3);
                        s.guardarPartido(p.getId(), d, true);
                    }
                }
                s.avanzar();
                igual &= seLeeIgual(db, s);
            }
            boolean terminado = s.getCampeonato().isTerminado();
            if (!igual || !terminado) {
                System.out.println("  " + formato + ": igual=" + igual + " terminado=" + terminado);
            }
            bien &= igual && terminado;
        }
        // Solo queda uno en curso; los terminados estan en el historial.
        return bien && contar(db, "SELECT COUNT(*) FROM torneos WHERE situacion = 'ACTUAL'") == 1
                && servicio(db).historial().size() == TipoFormato.values().length;
    }

    /** Resultado 2-1 con todo: XI, gol con asistencia, autogol, amarilla, roja y cambio. */
    private static void jugarCompleto(CampeonatoService s, Partido p) throws PersistenciaException {
        Campeonato c = s.getCampeonato();
        List<Jugador> loc = c.buscarEquipo(p.getIdLocal()).getJugadores(), vis = c.buscarEquipo(p.getIdVisitante()).getJugadores();
        Partido d = copia(p);
        d.setEstado(EstadoPartido.JUGADO);
        d.setFecha("2026-11-22");
        d.setHora("18:30");
        d.setSede("Estadio de prueba");
        d.setObservaciones("Con lluvia");
        d.setFormaciones("4-4-2", "4-3-3");
        d.setGolesLocal(2);
        d.setGolesVisitante(1);
        ArrayList<Integer> xi = new ArrayList<>(loc.subList(0, 11).stream().map(Jugador::getId).toList());
        java.util.Collections.reverse(xi); // el orden en que se eligio se conserva
        d.setTitulares(xi, new ArrayList<>(vis.subList(0, 11).stream().map(Jugador::getId).toList()));
        d.setGoles(new ArrayList<>(List.of(new Gol(p.getIdLocal(), 20, loc.get(8), false, loc.get(7)), new Gol(p.getIdLocal(), 0, vis.get(2), true, null),
                new Gol(p.getIdVisitante(), 88, vis.get(9), false, null))));
        Tarjeta amarilla = new Tarjeta(p.getIdLocal(), loc.get(3), Tarjeta.AMARILLA, 0);
        amarilla.setMinuto(33);
        d.setTarjetas(new ArrayList<>(List.of(amarilla, new Tarjeta(p.getIdVisitante(), vis.get(4), Tarjeta.ROJA, 2))), p.getIdLocal());
        d.setCambios(new ArrayList<>(List.of(new alphasport.model.Cambio(p.getIdLocal(), 66, loc.get(10), loc.get(12)))));
        s.programarVotacion(d, 48);
        s.guardarPartido(p.getId(), d, false);
    }

    private static boolean probarPartido(ConexionDB db) throws Exception {
        CampeonatoService s = liga(db, 4);
        Partido p = s.getCampeonato().getPartidos().get(0);
        jugarCompleto(s, p);
        List<Jugador> loc = s.getCampeonato().buscarEquipo(p.getIdLocal()).getJugadores();
        s.votarJugadores(p.getId(), Map.of(loc.get(0).getId(), 9, loc.get(8).getId(), 10), "dispositivo-A1");
        s.votarJugador(p.getId(), loc.get(0).getId(), 6, "dispositivo-B2");
        boolean igual = seLeeIgual(db, s);
        Partido leido = servicio(db).buscarPartido(p.getId());
        igual &= leido.getTitularesLocal().equals(p.getTitularesLocal()) && leido.getFinalizadoEn() == p.getFinalizadoEn()
                && leido.getHorasVotacion() == 48 && leido.getVotos().size() == 3 && leido.getHora().equals("18:30")
                && leido.getGoles().get(1).isEnContra() && leido.getGoles().get(1).getMinuto() == 0
                && leido.getTarjetas().get(1).getFechasSuspension() == 2;
        boolean tablas = contar(db, "SELECT COUNT(*) FROM alineaciones WHERE titular") == 22 && contar(db, "SELECT COUNT(*) FROM alineaciones WHERE NOT titular") > 0
                && contar(db, "SELECT COUNT(*) FROM eventos_partido") == 6 && contar(db, "SELECT COUNT(*) FROM votos_publico") == 3;
        // Guardar otra cosa no cambia los ids del partido ni de los jugadores.
        int idFila = contar(db, "SELECT id FROM partidos WHERE numero = " + p.getId());
        Partido otro = s.getCampeonato().getPartidos().get(1);
        Partido d = copia(otro);
        d.setSede("Otra sede");
        s.guardarPartido(otro.getId(), d, false);
        boolean estable = contar(db, "SELECT id FROM partidos WHERE numero = " + p.getId()) == idFila;
        // Cerrar y borrar el resultado.
        s.cerrarVotacion(p.getId());
        boolean cerrada = servicio(db).buscarPartido(p.getId()).getVotacionCerradaEn() > 0;
        Partido borrado = copia(p);
        borrado.reiniciarResultado();
        s.guardarPartido(p.getId(), borrado, false);
        boolean limpio = contar(db, "SELECT COUNT(*) FROM votos_publico") == 0 && contar(db, "SELECT COUNT(*) FROM eventos_partido") == 0
                && contar(db, "SELECT COUNT(*) FROM calificaciones_partido") == 0 && contar(db, "SELECT COUNT(*) FROM alineaciones WHERE titular") == 22;
        return igual && tablas && estable && cerrada && limpio && seLeeIgual(db, s);
    }

    private static boolean probarConsultas(ConexionDB db) throws Exception {
        CampeonatoService s = liga(db, 6);
        ConsultasDAO consultas = new ConsultasDAO(db);
        Campeonato c = s.getCampeonato();
        Random azar = new Random(3);
        for (Partido p : c.getPartidos()) {
            Partido d = copia(p);
            d.setEstado(EstadoPartido.JUGADO);
            d.setGolesLocal(azar.nextInt(5));
            d.setGolesVisitante(azar.nextInt(5));
            s.guardarPartido(p.getId(), d, false);
        }
        // Tabla de posiciones: las mismas cifras que calcula el programa.
        Map<Integer, ConsultasDAO.Posicion> enSql = new java.util.HashMap<>();
        consultas.tablaPosiciones(0).forEach(x -> enSql.put(x.equipo(), x));
        boolean tabla = enSql.size() == 6 && s.getMotor().tablaDeFase(0).stream().allMatch(f -> {
            ConsultasDAO.Posicion x = enSql.get(f.getIdEquipo());
            return x.pts() == f.getPuntos() && x.pj() == f.getJugados() && x.pg() == f.getGanados() && x.pe() == f.getEmpatados()
                    && x.pp() == f.getPerdidos() && x.gf() == f.getGolesFavor() && x.gc() == f.getGolesContra() && x.dg() == f.getDiferenciaGoles();
        });
        // Ultima alineacion y notas.
        Partido p = c.getPartidos().get(0);
        jugarCompleto(s, p);
        List<ConsultasDAO.Titular> ultima = consultas.ultimaAlineacion(p.getIdLocal());
        boolean alineacion = ultima.stream().map(ConsultasDAO.Titular::jugador).toList().equals(p.getTitularesLocal())
                && ultima.get(0).formacion().equals("4-4-2") && ultima.get(0).partido() == p.getId()
                && consultas.ultimaAlineacion(c.getPartidos().get(1).getIdLocal()).stream().noneMatch(t -> t.partido() != p.getId());
        int portero = c.buscarEquipo(p.getIdLocal()).getJugadores().get(0).getId();
        s.votarJugador(p.getId(), portero, 10, "dispositivo-A1");
        CalificacionesPartido notas = s.calificaciones(p.getId(), null);
        List<CalificacionesPartido.Calificacion> todas = new ArrayList<>(notas.local());
        todas.addAll(notas.visitante());
        List<ConsultasDAO.Nota> guardadas = consultas.notas(p.getId());
        boolean hibrida = guardadas.size() == todas.size() && todas.stream().allMatch(n -> guardadas.stream().anyMatch(g -> g.jugador() == n.idJugador()
                && g.sistema() == n.notaSistema() && g.notaFinal() == n.notaFinal() && g.votos() == n.votos() && g.mvp() == n.mvp()));
        // Votaciones activas: las mismas que ve el visitante.
        List<Integer> activas = consultas.votacionesActivas().stream().map(ConsultasDAO.VotacionActiva::partido).toList();
        boolean votaciones = activas.equals(s.votacionesAbiertas().stream().map(Partido::getId).toList()) && activas.equals(List.of(p.getId()));
        s.cerrarVotacion(p.getId());
        votaciones &= consultas.votacionesActivas().isEmpty() && s.votacionesAbiertas().isEmpty();
        if (!(tabla && alineacion && hibrida && votaciones)) {
            System.out.println("  tabla=" + tabla + " alineacion=" + alineacion + " hibrida=" + hibrida + " votaciones=" + votaciones + " " + activas);
        }
        return tabla && alineacion && hibrida && votaciones;
    }

    private static boolean probarPlantilla(ConexionDB db) throws Exception {
        CampeonatoService s = liga(db, 4);
        Partido p = s.getCampeonato().getPartidos().get(0);
        jugarCompleto(s, p);
        Equipo e = s.getCampeonato().buscarEquipo(p.getIdLocal());
        List<Jugador> plantilla = new ArrayList<>(e.getJugadores());
        // Intercambiar dorsales no choca con «dorsal unico».
        int d0 = plantilla.get(0).getDorsal();
        plantilla.get(0).setDorsal(plantilla.get(1).getDorsal());
        plantilla.get(1).setDorsal(d0);
        s.guardarEquipo(e.getId(), e.getNombreEquipo(), "Nuevo estadio", null, plantilla);
        boolean intercambio = seLeeIgual(db, s);
        // Quien hizo el gol sale de la plantilla: su gol sigue y su id no se reutiliza.
        Jugador goleador = plantilla.remove(8);
        int antes = s.getCampeonato().getSiguienteIdJugador();
        plantilla.add(new Jugador("Nuevo Refuerzo", "X1", 98, "Delantero centro"));
        s.guardarEquipo(e.getId(), e.getNombreEquipo(), "", null, plantilla);
        CampeonatoService otro = servicio(db);
        boolean gol = otro.buscarPartido(p.getId()).getGoles().get(0).getIdAutor() == goleador.getId()
                && otro.buscarPartido(p.getId()).getGoles().get(0).getNombreAutor().equals(goleador.getNombre())
                && contar(db, "SELECT COUNT(*) FROM jugadores WHERE NOT activo") == 1;
        boolean ids = otro.getCampeonato().buscarEquipo(e.getId()).getJugadores().stream().anyMatch(j -> j.getNombre().equals("Nuevo Refuerzo") && j.getId() >= antes);
        return intercambio && gol && ids && seLeeIgual(db, s);
    }

    private static boolean probarDosProgramas(ConexionDB db) throws Exception {
        CampeonatoService escritorio = liga(db, 4);
        try (ConexionDB otraConexion = new ConexionDB(CONFIGURACION, esquemaDe(db))) {
            CampeonatoService web = servicio(otraConexion);
            Partido p = escritorio.getCampeonato().getPartidos().get(0);
            Partido d = copia(p);
            d.setSede("Cambiada en el escritorio");
            escritorio.guardarPartido(p.getId(), d, false);
            // Sin sincronizar, la web no pisa el cambio: se le avisa y se queda con los datos nuevos.
            Partido dw = copia(web.buscarPartido(p.getId()));
            dw.setSede("Cambiada en la web");
            boolean conflicto = falla(PersistenciaException.class, () -> web.guardarPartido(p.getId(), dw, false))
                    && web.buscarPartido(p.getId()).getSede().equals("Cambiada en el escritorio");
            web.guardarPartido(p.getId(), dw, false); // al reintentar si se guarda
            escritorio.sincronizar();
            boolean sincroniza = escritorio.buscarPartido(p.getId()).getSede().equals("Cambiada en la web") && foto(escritorio).equals(foto(web));
            // Uno cancela el campeonato: el otro lo nota.
            web.cancelar();
            escritorio.sincronizar();
            boolean cancelado = !escritorio.hayCampeonato() && contar(db, "SELECT COUNT(*) FROM equipos") == 0;
            return conflicto && sincroniza && cancelado;
        }
    }

    private static String esquemaDe(ConexionDB db) throws PersistenciaException {
        return db.ejecutar(con -> con.getSchema());
    }

    private static boolean probarHistorial(ConexionDB db) throws Exception {
        CampeonatoService s = liga(db, 4);
        Partido p = s.getCampeonato().getPartidos().get(0);
        jugarCompleto(s, p);
        s.archivarActual();
        String codigo = s.getCampeonato().getCodigo();
        Partido d = copia(p);
        d.setSede("Cambio posterior");
        s.guardarPartido(p.getId(), d, false);
        CampeonatoService otro = servicio(db);
        Campeonato archivado = otro.historial().get(0);
        boolean copia = otro.historial().size() == 1 && archivado.getCodigo().equals(codigo) && archivado.getPartidos().get(0).getSede().equals("Estadio de prueba")
                && archivado.getPartidos().get(0).getGoles().size() == 3 && otro.buscarPartido(p.getId()).getSede().equals("Cambio posterior");
        s.archivarActual(); // se vuelve a archivar: reemplaza la copia
        boolean reemplaza = servicio(db).historial().size() == 1 && servicio(db).historial().get(0).getPartidos().get(0).getSede().equals("Cambio posterior");
        s.siguienteTemporada("Liga 2");
        boolean siguiente = seLeeIgual(db, s) && s.getCampeonato().getPartidos().isEmpty() && servicio(db).historial().size() == 1;
        byte[] respaldo = s.respaldo();
        s.cancelar();
        s.abrirRespaldo(respaldo);
        boolean respaldado = seLeeIgual(db, s);
        s.eliminarDelHistorial(codigo);
        boolean borrado = servicio(db).historial().isEmpty() && falla(ReglaNegocioException.class, () -> s.eliminarDelHistorial(codigo));
        if (!(copia && reemplaza && siguiente && respaldado && borrado)) {
            System.out.println("  copia=" + copia + " reemplaza=" + reemplaza + " siguiente=" + siguiente + " respaldado=" + respaldado + " borrado=" + borrado);
        }
        return copia && reemplaza && siguiente && respaldado && borrado;
    }

    /* ---------------------------------------------------------------- noticias y migracion */

    private static boolean probarNoticias(ConexionDB db) throws Exception {
        Path imagenes = Files.createTempDirectory("alphasport-imagenes");
        NoticiasService n = new NoticiasService(new PostgresRepositorioNoticias(db, imagenes));
        Noticia propuesta = n.proponer("Propuesta de un hincha", "Texto de la propuesta del visitante.", "Ana", new NoticiasService.Imagen(new byte[]{1, 2, 3}, "png"));
        Noticia oficial = n.publicar(null, "Noticia oficial", "Texto oficial de la noticia.", null, false);
        NoticiasService otra = new NoticiasService(new PostgresRepositorioNoticias(db, imagenes)); // otro programa
        boolean filtro = otra.publicadas().size() == 1 && otra.pendientes().size() == 1 && otra.pendientes().get(0).getTitulo().equals(propuesta.getTitulo());
        boolean foto = otra.imagen(propuesta.getImagenRuta(), false) == null && otra.imagen(propuesta.getImagenRuta(), true).length == 3;
        otra.aprobar(propuesta.getId());
        boolean aprobada = n.publicadas().size() == 2 && n.pendientes().isEmpty() && n.imagen(propuesta.getImagenRuta(), false) != null;
        n.comentar(oficial.getId(), "Luis", "Buen partido");
        int comentario = otra.buscar(oficial.getId()).getComentarios().get(0).getId();
        boolean comentarios = otra.publicadas().stream().filter(x -> x.getId() == oficial.getId()).findFirst().orElseThrow().getComentarios().size() == 1
                && falla(ReglaNegocioException.class, () -> n.eliminarComentario(oficial.getId(), comentario + 99));
        n.publicar(oficial.getId(), "Título corregido", "Texto oficial corregido.", null, false);
        boolean editada = otra.buscar(oficial.getId()).getTitulo().equals("Título corregido") && otra.buscar(oficial.getId()).getComentarios().size() == 1;
        // Un texto con comillas o SQL se guarda tal cual.
        n.comentar(oficial.getId(), "O'Neil", "'); DROP TABLE noticias; --");
        boolean segura = otra.buscar(oficial.getId()).getComentarios().get(1).getTexto().equals("'); DROP TABLE noticias; --");
        n.eliminar(oficial.getId());
        boolean cascada = contar(db, "SELECT COUNT(*) FROM comentarios") == 0 && falla(ReglaNegocioException.class, () -> otra.buscar(oficial.getId()));
        return filtro && foto && aprobada && comentarios && editada && segura && cascada;
    }

    private static boolean probarMigracion(ConexionDB db) throws Exception {
        Path carpeta = Files.createTempDirectory("alphasport-datos");
        CampeonatoService viejo = new CampeonatoService(new ArchivoRepositorioCampeonato(carpeta), new ArchivoRepositorioHistorial(carpeta));
        ConfiguracionTorneo cfg = new ConfiguracionTorneo();
        cfg.setSorteoAleatorio(false);
        viejo.crear("Liga vieja", TipoFormato.LIGA, cfg, false);
        viejo.cargarDatosPrueba(11, 4, true);
        viejo.avanzar();
        Partido p = viejo.getCampeonato().getPartidos().get(0);
        jugarCompleto(viejo, p);
        viejo.getCampeonato().buscarEquipo(p.getIdLocal()).getJugadores().remove(8); // el goleador ya no esta en la plantilla
        viejo.archivarActual();
        NoticiasService noticias = new NoticiasService(new ArchivoRepositorioNoticias(carpeta));
        noticias.comentar(noticias.publicar(null, "Noticia vieja", "Texto de la noticia vieja.", null, false).getId(), "Ana", "Hola");
        MigradorArchivos.migrar(db, carpeta);
        CampeonatoService nuevo = servicio(db);
        boolean igual = foto(nuevo).equals(foto(viejo)) && nuevo.historial().size() == 1;
        NoticiasService migradas = new NoticiasService(new PostgresRepositorioNoticias(db, carpeta.resolve("imagenes")));
        igual &= migradas.publicadas().size() == 1 && migradas.publicadas().get(0).getComentarios().size() == 1;
        MigradorArchivos.migrar(db, carpeta); // repetirla no duplica nada
        boolean unaVez = contar(db, "SELECT COUNT(*) FROM torneos") == 2 && contar(db, "SELECT COUNT(*) FROM noticias") == 1;
        return igual && unaVez;
    }
}
