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
import alphasport.service.MotorCampeonato;
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
        probar("Identidad: los ids no cambian al guardar ni al archivar", PruebaPostgres::probarIdentidad);
        probar("Temporadas: el campeonato anterior pasa al historial con sus ids", PruebaPostgres::probarTemporadas);
        probar("W.O.: sin notas ni votación, y vuelve a la normalidad al borrarlo", PruebaPostgres::probarWalkover);
        probar("Dos programas a la vez: sincronización y conflictos", PruebaPostgres::probarDosProgramas);
        probar("Una transacción: el partido no queda guardado sin sus notas", PruebaPostgres::probarUnaTransaccion);
        probar("Historial: copias que no cambian con el campeonato actual", PruebaPostgres::probarHistorial);
        probar("Noticias y comentarios", PruebaPostgres::probarNoticias);
        probar("Migración de los archivos anteriores", PruebaPostgres::probarMigracion);
        probar("Contratos: PostgreSQL, archivo y memoria responden igual", PruebaPostgres::probarContratos);
        probar("Documento único también en la base de datos", PruebaPostgres::probarDocumentoUnico);
        probar("Ascensos y descensos al pasar de temporada", PruebaPostgres::probarAscensos);
        probar("Migraciones del esquema (database/migraciones)", PruebaPostgres::probarMigraciones);
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
        CampeonatoService s = new CampeonatoService(new PostgresRepositorioCampeonato(db), new PostgresRepositorioHistorial(db), db);
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
        s.equipos().cargarDatosPrueba(4242, equipos, true);
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
        sql(db, "INSERT INTO usuarios (nombre, username, password_hash, rol) VALUES ('A', 'a', 'x', 'ORGANIZADOR')");
        boolean restricciones = falla(PersistenciaException.class, () -> sql(db, "INSERT INTO usuarios (nombre, username, password_hash) VALUES ('B', 'a', 'x')"))
                && falla(PersistenciaException.class, () -> sql(db, "INSERT INTO usuarios (nombre, username, password_hash, rol) VALUES ('B', 'b', 'x', 'JEFE')"))
                && falla(PersistenciaException.class, () -> sql(db, "INSERT INTO usuarios (nombre, username, password_hash, rol) VALUES ('B', 'b', 'x', 'ADMIN')"))
                && falla(PersistenciaException.class, () -> sql(db, "INSERT INTO usuarios (nombre, username, password_hash) VALUES ('B', 'Bea', 'x')"))
                && !falla(PersistenciaException.class, () -> sql(db, "INSERT INTO usuarios (nombre, username, password_hash) VALUES ('C', 'c', 'x')"))
                && contar(db, "SELECT COUNT(*) FROM usuarios WHERE username = 'c' AND rol = 'AFICIONADO'") == 1 // rol por defecto
                && falla(PersistenciaException.class, () -> sql(db, "INSERT INTO comentarios (noticia_id, autor, texto) VALUES (99, 'a', 'b')"));
        return completas && restricciones;
    }

    private static boolean probarSeed(ConexionDB db) throws Exception {
        script(db, "database/seed.sql");
        CampeonatoService s = servicio(db);
        // Sin cuentas de demostracion: cada persona se registra desde la app.
        boolean usuarios = contar(db, "SELECT COUNT(*) FROM usuarios") == 0;
        boolean leido = s.getCampeonato().getEquipos().size() == 6 && s.getCampeonato().getEquipos().stream().allMatch(e -> e.getJugadores().size() == 16)
                && contar(db, "SELECT COUNT(*) FROM jugadores WHERE es_capitan") == 6;
        s.avanzar(); // el torneo de prueba se puede iniciar y seguir usando
        s.equipos().guardarEquipo(1, "Pumas del Sur", "", null, s.getCampeonato().buscarEquipo(1).getJugadores());
        boolean capitan = contar(db, "SELECT COUNT(*) FROM jugadores WHERE es_capitan") == 6; // guardar no borra el capitan
        return usuarios && leido && capitan && s.getCampeonato().getPartidos().size() == 15 && seLeeIgual(db, s);
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
            s.equipos().cargarDatosPrueba(99, 8, true);
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
                        s.partidos().guardarPartido(p.getId(), d, true);
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
                && servicio(db).historial().listar().size() == TipoFormato.values().length;
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
        s.votacion().programarVotacion(d, 48);
        s.partidos().guardarPartido(p.getId(), d, false);
    }

    /** El partido (o el voto) y sus notas se guardan juntos: si las notas fallan, no queda nada a medias. */
    private static boolean probarUnaTransaccion(ConexionDB db) throws Exception {
        liga(db, 4);
        boolean[] fallar = {true};
        PostgresRepositorioCampeonato repositorio = new PostgresRepositorioCampeonato(db) {
            @Override
            public void guardarNotas(int idPartido, List<NotaJugador> notas) throws PersistenciaException {
                super.guardarNotas(idPartido, notas); // escribe y despues falla: tambien se deshace
                if (fallar[0]) {
                    throw new PersistenciaException("Falla simulada al guardar las notas", null);
                }
            }
        };
        CampeonatoService s = new CampeonatoService(repositorio, new PostgresRepositorioHistorial(db), db);
        s.cargarGuardado();
        int idPartido = s.getCampeonato().getPartidos().get(0).getId();
        String antes = foto(s);
        int version = contar(db, "SELECT version FROM torneos WHERE situacion = 'ACTUAL'");
        boolean falla = falla(PersistenciaException.class, () -> jugarCompleto(s, s.buscarPartido(idPartido)));
        // Ni el resultado ni las notas: la base y el programa quedan como antes.
        boolean nada = contar(db, "SELECT version FROM torneos WHERE situacion = 'ACTUAL'") == version
                && contar(db, "SELECT COUNT(*) FROM eventos_partido") == 0 && contar(db, "SELECT COUNT(*) FROM alineaciones") == 0
                && contar(db, "SELECT COUNT(*) FROM calificaciones_partido") == 0
                && foto(servicio(db)).equals(antes) && foto(s).equals(antes) && !s.buscarPartido(idPartido).isJugado();
        // La siguiente accion funciona: el programa no se quedo con datos que la base no tiene.
        fallar[0] = false;
        jugarCompleto(s, s.buscarPartido(idPartido));
        int notas = contar(db, "SELECT COUNT(*) FROM calificaciones_partido");
        boolean despues = servicio(db).buscarPartido(idPartido).isJugado() && notas > 0 && seLeeIgual(db, s);
        // Igual con el voto del publico: no queda sin su nota.
        int portero = s.buscarPartido(idPartido).getTitularesVisitante().get(0);
        String notasAntes = notasDe(db);
        fallar[0] = true;
        boolean voto = falla(PersistenciaException.class, () -> s.votacion().votarJugador(idPartido, portero, 7, "dispositivo-U1"))
                && contar(db, "SELECT COUNT(*) FROM votos_publico") == 0 && notasDe(db).equals(notasAntes)
                && s.votacion().calificaciones(idPartido, null).local().stream().allMatch(n -> n.votos() == 0)
                && s.votacion().calificaciones(idPartido, null).visitante().stream().allMatch(n -> n.votos() == 0);
        fallar[0] = false;
        s.votacion().votarJugador(idPartido, portero, 7, "dispositivo-U1");
        voto &= contar(db, "SELECT COUNT(*) FROM votos_publico") == 1 && !notasDe(db).equals(notasAntes) && seLeeIgual(db, s);
        if (!(falla && nada && despues && voto)) {
            System.out.println("  falla=" + falla + " nada=" + nada + " despues=" + despues + " voto=" + voto);
        }
        return falla && nada && despues && voto;
    }

    private static String notasDe(ConexionDB db) throws PersistenciaException {
        return db.ejecutar(con -> {
            try (var st = con.createStatement(); var rs = st.executeQuery("SELECT jugador_id, votos, nota_final FROM calificaciones_partido ORDER BY jugador_id")) {
                StringBuilder texto = new StringBuilder();
                while (rs.next()) {
                    texto.append(rs.getInt(1)).append(':').append(rs.getInt(2)).append(':').append(rs.getString(3)).append(' ');
                }
                return texto.toString();
            }
        });
    }

    private static boolean probarPartido(ConexionDB db) throws Exception {
        CampeonatoService s = liga(db, 4);
        Partido p = s.getCampeonato().getPartidos().get(0);
        jugarCompleto(s, p);
        List<Jugador> loc = s.getCampeonato().buscarEquipo(p.getIdLocal()).getJugadores();
        s.votacion().votarJugadores(p.getId(), Map.of(loc.get(0).getId(), 9, loc.get(8).getId(), 10), "dispositivo-A1");
        s.votacion().votarJugador(p.getId(), loc.get(0).getId(), 6, "dispositivo-B2");
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
        s.partidos().guardarPartido(otro.getId(), d, false);
        boolean estable = contar(db, "SELECT id FROM partidos WHERE numero = " + p.getId()) == idFila;
        // Cerrar y borrar el resultado.
        s.votacion().cerrarVotacion(p.getId());
        boolean cerrada = servicio(db).buscarPartido(p.getId()).getVotacionCerradaEn() > 0;
        Partido borrado = copia(p);
        borrado.reiniciarResultado();
        s.partidos().guardarPartido(p.getId(), borrado, false);
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
            s.partidos().guardarPartido(p.getId(), d, false);
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
        s.votacion().votarJugador(p.getId(), portero, 10, "dispositivo-A1");
        CalificacionesPartido notas = s.votacion().calificaciones(p.getId(), null);
        List<CalificacionesPartido.Calificacion> todas = new ArrayList<>(notas.local());
        todas.addAll(notas.visitante());
        List<ConsultasDAO.Nota> guardadas = consultas.notas(p.getId());
        boolean hibrida = guardadas.size() == todas.size() && todas.stream().allMatch(n -> guardadas.stream().anyMatch(g -> g.jugador() == n.idJugador()
                && g.sistema() == n.notaSistema() && g.notaFinal() == n.notaFinal() && g.votos() == n.votos() && g.mvp() == n.mvp()));
        // Votaciones activas: las mismas que ve el visitante.
        List<Integer> activas = consultas.votacionesActivas().stream().map(ConsultasDAO.VotacionActiva::partido).toList();
        boolean votaciones = activas.equals(s.votacion().votacionesAbiertas().stream().map(Partido::getId).toList()) && activas.equals(List.of(p.getId()));
        s.votacion().cerrarVotacion(p.getId());
        votaciones &= consultas.votacionesActivas().isEmpty() && s.votacion().votacionesAbiertas().isEmpty();
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
        s.equipos().guardarEquipo(e.getId(), e.getNombreEquipo(), "Nuevo estadio", null, plantilla);
        boolean intercambio = seLeeIgual(db, s);
        // Quien hizo el gol sale de la plantilla: su gol sigue y su id no se reutiliza.
        Jugador goleador = plantilla.remove(8);
        int antes = s.getCampeonato().getSiguienteIdJugador();
        plantilla.add(new Jugador("Nuevo Refuerzo", "X1", 98, "Delantero centro"));
        s.equipos().guardarEquipo(e.getId(), e.getNombreEquipo(), "", null, plantilla);
        CampeonatoService otro = servicio(db);
        boolean gol = otro.buscarPartido(p.getId()).getGoles().get(0).getIdAutor() == goleador.getId()
                && otro.buscarPartido(p.getId()).getGoles().get(0).getNombreAutor().equals(goleador.getNombre())
                && contar(db, "SELECT COUNT(*) FROM jugadores WHERE NOT activo") == 1;
        boolean ids = otro.getCampeonato().buscarEquipo(e.getId()).getJugadores().stream().anyMatch(j -> j.getNombre().equals("Nuevo Refuerzo") && j.getId() >= antes);
        return intercambio && gol && ids && seLeeIgual(db, s);
    }

    /** Numero -> id de las filas del campeonato en curso, como texto para comparar. */
    private static String idsActuales(ConexionDB db, String tabla) throws PersistenciaException {
        return db.ejecutar(con -> {
            try (var st = con.createStatement(); var rs = st.executeQuery("SELECT x.numero, x.id FROM " + tabla
                    + " x JOIN torneos t ON t.id = x.torneo_id WHERE t.situacion = 'ACTUAL' ORDER BY x.numero")) {
                StringBuilder texto = new StringBuilder();
                while (rs.next()) {
                    texto.append(rs.getInt(1)).append('=').append(rs.getInt(2)).append(' ');
                }
                return texto.toString();
            }
        });
    }

    /** Plantillas sin ids: nombre, documento y dorsal de cada jugador, por equipo. */
    private static String plantillas(Campeonato c) {
        StringBuilder texto = new StringBuilder();
        for (Equipo e : c.getEquipos()) {
            texto.append(e.getNombreEquipo()).append(':');
            e.getJugadores().forEach(j -> texto.append(j.getNombre()).append('/').append(j.getDocumento()).append('/').append(j.getDorsal()).append(' '));
        }
        return texto.toString();
    }

    private static boolean probarIdentidad(ConexionDB db) throws Exception {
        CampeonatoService s = liga(db, 4);
        Partido p = s.getCampeonato().getPartidos().get(0);
        String jugadores = idsActuales(db, "jugadores"), partidos = idsActuales(db, "partidos"), equipos = idsActuales(db, "equipos");
        String personas = personasActuales(db);
        int plantilla = contar(db, "SELECT COUNT(*) FROM jugadores j JOIN torneos t ON t.id = j.torneo_id WHERE t.situacion = 'ACTUAL'");
        jugarCompleto(s, p);
        int portero = p.getTitularesVisitante().get(0);
        s.votacion().votarJugador(p.getId(), portero, 8, "dispositivo-A1");
        // El editor de equipos reconstruye cada jugador a partir de lo que envia la pantalla.
        alphasport.ui.ApiControlador api = new alphasport.ui.ApiControlador(s, new alphasport.service.AutenticacionService());
        api.ejecutar("login", Map.of("usuario", "admin", "clave", "123"));
        Equipo e = s.getCampeonato().getEquipos().get(0);
        List<Object> enviados = new ArrayList<>();
        e.getJugadores().forEach(j -> enviados.add(Map.of("id", j.getId(), "nombre", j.getNombre() + " Jr.", "documento", j.getDocumento(),
                "dorsal", j.getDorsal(), "posicion", j.getPosicion(), "foto", j.getFoto())));
        api.ejecutar("equipo", Map.of("id", e.getId(), "nombre", e.getNombreEquipo(), "estadio", "", "jugadores", enviados));
        s.historial().archivarActual();
        boolean estables = idsActuales(db, "jugadores").equals(jugadores) && idsActuales(db, "partidos").equals(partidos)
                && idsActuales(db, "equipos").equals(equipos) && servicio(db).getCampeonato().getEquipos().get(0).getJugadores().get(0).getNombre().endsWith(" Jr.");
        // Lo que apunta al jugador sigue apuntando al mismo.
        boolean votos = contar(db, "SELECT COUNT(*) FROM votos_publico v JOIN jugadores j ON j.id = v.jugador_id JOIN torneos t ON t.id = j.torneo_id"
                + " WHERE t.situacion = 'ACTUAL' AND j.numero = " + portero) == 1;
        // Cada jugador es una persona; editarlo o archivarlo no la cambia y la copia del historial es la misma persona.
        boolean unaPersona = personasActuales(db).equals(personas) && contar(db, "SELECT COUNT(*) FROM personas") == plantilla
                && contar(db, "SELECT COUNT(*) FROM personas WHERE nombre LIKE '% Jr.'") == enviados.size()
                && contar(db, "SELECT COUNT(*) FROM jugadores a JOIN torneos ta ON ta.id = a.torneo_id AND ta.situacion = 'ARCHIVADO'"
                        + " JOIN torneos tb ON tb.codigo = ta.codigo AND tb.situacion = 'ACTUAL'"
                        + " JOIN jugadores b ON b.torneo_id = tb.id AND b.numero = a.numero WHERE a.persona_id = b.persona_id") == plantilla;
        // La siguiente temporada y «mismos equipos» conservan las plantillas y las personas.
        String antes = plantillas(s.getCampeonato()), quienes = codigosPersonas(db);
        s.siguienteTemporada("Temporada 2");
        boolean temporada = plantillas(servicio(db).getCampeonato()).equals(antes) && codigosPersonas(db).equals(quienes);
        s.crear("Otro torneo", TipoFormato.LIGA, new ConfiguracionTorneo(), true);
        boolean mismos = plantillas(servicio(db).getCampeonato()).equals(antes) && codigosPersonas(db).equals(quienes)
                && contar(db, "SELECT COUNT(*) FROM personas") == plantilla;
        // Equipos nuevos son personas nuevas, aunque se llamen igual.
        s.crear("Desde cero", TipoFormato.LIGA, new ConfiguracionTorneo(), false);
        s.equipos().cargarDatosPrueba(7, 2, false);
        boolean nuevas = contar(db, "SELECT COUNT(*) FROM personas") == plantilla + 2 * s.getCampeonato().getEquipos().get(0).getJugadores().size();
        if (!(estables && votos && unaPersona && temporada && mismos && nuevas)) {
            System.out.println("  estables=" + estables + " votos=" + votos + " unaPersona=" + unaPersona + " temporada=" + temporada
                    + " mismos=" + mismos + " nuevas=" + nuevas);
        }
        return estables && votos && unaPersona && temporada && mismos && nuevas;
    }

    /** numero del jugador -> id de su persona, en el campeonato en curso. */
    private static String personasActuales(ConexionDB db) throws PersistenciaException {
        return db.ejecutar(con -> {
            try (var st = con.createStatement(); var rs = st.executeQuery("SELECT j.numero, j.persona_id FROM jugadores j"
                    + " JOIN torneos t ON t.id = j.torneo_id WHERE t.situacion = 'ACTUAL' ORDER BY j.numero")) {
                StringBuilder texto = new StringBuilder();
                while (rs.next()) {
                    texto.append(rs.getInt(1)).append('=').append(rs.getInt(2)).append(' ');
                }
                return texto.toString();
            }
        });
    }

    /** Codigos de las personas de la plantilla en curso, por equipo y dorsal (los numeros cambian de una temporada a otra). */
    private static String codigosPersonas(ConexionDB db) throws PersistenciaException {
        return db.ejecutar(con -> {
            try (var st = con.createStatement(); var rs = st.executeQuery("SELECT e.nombre, j.dorsal, p.codigo FROM jugadores j"
                    + " JOIN torneos t ON t.id = j.torneo_id AND t.situacion = 'ACTUAL' JOIN equipos e ON e.id = j.equipo_id"
                    + " JOIN personas p ON p.id = j.persona_id WHERE j.activo ORDER BY e.nombre, j.dorsal")) {
                StringBuilder texto = new StringBuilder();
                while (rs.next()) {
                    texto.append(rs.getString(1)).append('#').append(rs.getInt(2)).append('=').append(rs.getString(3)).append(' ');
                }
                return texto.toString();
            }
        });
    }

    private static boolean probarTemporadas(ConexionDB db) throws Exception {
        CampeonatoService s = liga(db, 4);
        Partido p = s.getCampeonato().getPartidos().get(0);
        jugarCompleto(s, p);
        int portero = p.getTitularesVisitante().get(0);
        s.votacion().votarJugador(p.getId(), portero, 8, "dispositivo-T1");
        String codigo = s.getCampeonato().getCodigo();
        String jugadores = idsActuales(db, "jugadores"), partidos = idsActuales(db, "partidos"), equipos = idsActuales(db, "equipos");
        int notas = contar(db, "SELECT COUNT(*) FROM calificaciones_partido");
        // Una tabla que apunta a los jugadores, como lo hara el modulo Fantasy.
        sql(db, "CREATE TABLE fantasy_prueba (jugador_id INTEGER NOT NULL REFERENCES jugadores (id) ON DELETE CASCADE)");
        sql(db, "INSERT INTO fantasy_prueba SELECT j.id FROM jugadores j JOIN torneos t ON t.id = j.torneo_id AND t.situacion = 'ACTUAL'"
                + " WHERE j.numero = " + portero);
        s.siguienteTemporada("Temporada 2");
        // El campeonato anterior es el del historial, con los mismos ids, sus notas y sus votos.
        boolean conserva = ids(db, codigo, "jugadores").equals(jugadores) && ids(db, codigo, "partidos").equals(partidos)
                && ids(db, codigo, "equipos").equals(equipos) && notas > 0 && contar(db, "SELECT COUNT(*) FROM calificaciones_partido") == notas
                && contar(db, "SELECT COUNT(*) FROM votos_publico") == 1 && contar(db, "SELECT COUNT(*) FROM torneos WHERE situacion = 'ARCHIVADO'") == 1;
        Campeonato archivado = servicio(db).historial().listar().get(0);
        conserva &= archivado.getCodigo().equals(codigo) && archivado.getFechaArchivo() != null
                && archivado.getPartidos().stream().filter(Partido::isJugado).count() == 1 && !s.getCampeonato().getCodigo().equals(codigo);
        // Lo que apunta al jugador lo sigue encontrando, y es la misma persona que juega la nueva temporada.
        boolean fantasy = contar(db, "SELECT COUNT(*) FROM fantasy_prueba f JOIN jugadores j ON j.id = f.jugador_id"
                + " JOIN jugadores n ON n.persona_id = j.persona_id JOIN torneos t ON t.id = n.torneo_id AND t.situacion = 'ACTUAL'") == 1;
        // Lo mismo al crear otro campeonato y al abrir un respaldo; cambiar el formato o cancelar descartan el actual.
        s.avanzar();
        jugarCompleto(s, s.getCampeonato().getPartidos().get(0));
        String segunda = s.getCampeonato().getCodigo(), jugadores2 = idsActuales(db, "jugadores");
        s.crear("Otro torneo", TipoFormato.LIGA, new ConfiguracionTorneo(), true);
        boolean otros = ids(db, segunda, "jugadores").equals(jugadores2) && servicio(db).historial().listar().size() == 2;
        byte[] respaldo = s.historial().respaldo();
        s.cambiarFormato("Otro torneo 2", TipoFormato.LIGA, new ConfiguracionTorneo());
        otros &= servicio(db).historial().listar().size() == 2 && contar(db, "SELECT COUNT(*) FROM torneos") == 3;
        s.cancelar();
        s.historial().abrirRespaldo(respaldo);
        otros &= servicio(db).historial().listar().size() == 2 && seLeeIgual(db, s) && contar(db, "SELECT COUNT(*) FROM fantasy_prueba") == 1;
        if (!(conserva && fantasy && otros)) {
            System.out.println("  conserva=" + conserva + " fantasy=" + fantasy + " otros=" + otros);
        }
        return conserva && fantasy && otros;
    }

    /** numero -> id de una tabla en la copia archivada del torneo. */
    private static String ids(ConexionDB db, String codigo, String tabla) throws PersistenciaException {
        return db.ejecutar(con -> {
            try (var st = con.prepareStatement("SELECT x.numero, x.id FROM " + tabla
                    + " x JOIN torneos t ON t.id = x.torneo_id WHERE t.situacion = 'ARCHIVADO' AND t.codigo = ? ORDER BY x.numero")) {
                st.setString(1, codigo);
                try (var rs = st.executeQuery()) {
                    StringBuilder texto = new StringBuilder();
                    while (rs.next()) {
                        texto.append(rs.getInt(1)).append('=').append(rs.getInt(2)).append(' ');
                    }
                    return texto.toString();
                }
            }
        });
    }

    private static void walkover(CampeonatoService s, Partido p, int ganador) throws PersistenciaException {
        s.partidos().darWalkover(p.getId(), copia(p), ganador, true);
    }

    private static boolean probarWalkover(ConexionDB db) throws Exception {
        CampeonatoService s = liga(db, 4);
        Partido p = s.getCampeonato().getPartidos().get(0);
        List<Integer> xi = new ArrayList<>(s.getCampeonato().buscarEquipo(p.getIdLocal()).getJugadores().subList(0, 11).stream().map(Jugador::getId).toList());
        Partido previa = copia(p);
        previa.setTitulares(new ArrayList<>(xi), new ArrayList<>(s.getCampeonato().buscarEquipo(p.getIdVisitante()).getJugadores()
                .subList(0, 11).stream().map(Jugador::getId).toList()));
        s.partidos().guardarPartido(p.getId(), previa, false);
        walkover(s, p, p.getIdVisitante());
        CampeonatoService otro = servicio(db);
        CalificacionesPartido notas = otro.votacion().calificaciones(p.getId(), null);
        boolean sinNotas = otro.buscarPartido(p.getId()).getGolesVisitante() == 3 && !notas.disponible() && notas.motivoSinCalificaciones().contains("W.O.")
                && contar(db, "SELECT COUNT(*) FROM calificaciones_partido") == 0;
        boolean sinVotos = falla(ReglaNegocioException.class, () -> s.votacion().votarJugador(p.getId(), xi.get(0), 8, "dispositivo-A1"))
                && s.votacion().votacionesAbiertas().isEmpty() && new ConsultasDAO(db).votacionesActivas().isEmpty();
        // Lo decide el campo, no el texto: cambiar la observacion de un W.O. no lo convierte en partido jugado...
        Partido reclamo = copia(p);
        reclamo.setObservaciones("Reclamo en estudio");
        s.partidos().guardarPartido(p.getId(), reclamo, false);
        boolean porCampo = servicio(db).buscarPartido(p.getId()).isWalkover() && !s.votacion().calificaciones(p.getId(), null).disponible()
                && contar(db, "SELECT COUNT(*) FROM partidos WHERE walkover") == 1;
        // ...y registrar un gol si: el partido se jugo.
        Partido conGol = copia(p);
        conGol.setGoles(new ArrayList<>(List.of(new Gol(p.getIdVisitante(), 10, s.getCampeonato().buscarEquipo(p.getIdVisitante()).getJugadores().get(9), false, null))));
        s.partidos().guardarPartido(p.getId(), conGol, false);
        porCampo &= !servicio(db).buscarPartido(p.getId()).isWalkover() && s.votacion().calificaciones(p.getId(), null).disponible();
        // Un partido jugado cuya observacion empieza por «W.O.» tiene notas y votacion.
        Partido otroPartido = s.getCampeonato().getPartidos().get(1);
        jugarCompleto(s, otroPartido);
        Partido conTexto = copia(otroPartido);
        conTexto.setObservaciones("W.O. solicitado, pero el partido se jugó");
        s.partidos().guardarPartido(otroPartido.getId(), conTexto, false);
        porCampo &= s.votacion().calificaciones(otroPartido.getId(), null).disponible() && s.votacion().votacionesAbiertas().contains(otroPartido)
                && new ConsultasDAO(db).votacionesActivas().size() == 2;
        s.votacion().cerrarVotacion(otroPartido.getId());
        // Se borra el W.O. y el partido se juega: ahora si tiene notas y votacion.
        Partido borrado = copia(p);
        borrado.reiniciarResultado();
        s.partidos().guardarPartido(p.getId(), borrado, false);
        Partido jugado = copia(p);
        jugado.setObservaciones("");
        jugado.setEstado(EstadoPartido.JUGADO);
        jugado.setGolesLocal(1);
        s.partidos().guardarPartido(p.getId(), jugado, false);
        boolean normal = s.votacion().calificaciones(p.getId(), null).disponible() && s.votacion().votacionesAbiertas().equals(List.of(p))
                && new ConsultasDAO(db).votacionesActivas().size() == 1;
        // Retirar un equipo da por W.O. sus partidos pendientes.
        Partido pendiente = s.getCampeonato().getPartidos().stream().filter(x -> !x.isJugado()).findFirst().orElseThrow();
        s.equipos().quitarEquipo(pendiente.getIdLocal());
        boolean retiro = pendiente.isJugado() && pendiente.getGolesVisitante() == 3
                && servicio(db).votacion().calificaciones(pendiente.getId(), null).motivoSinCalificaciones().contains("W.O.");
        if (!(sinNotas && sinVotos && porCampo && normal && retiro)) {
            System.out.println("  sinNotas=" + sinNotas + " sinVotos=" + sinVotos + " porCampo=" + porCampo + " normal=" + normal + " retiro=" + retiro);
        }
        return sinNotas && sinVotos && porCampo && normal && retiro && seLeeIgual(db, s);
    }

    /** Un respaldo anterior no trae el campo: se deduce una vez del texto que escribia el programa. */
    private static boolean walkoverAnterior() throws Exception {
        Partido p = new Partido(1, 1, 2);
        MotorCampeonato.darWalkover(p, 1, "W.O. a favor de A");
        java.lang.reflect.Field campo = Partido.class.getDeclaredField("walkover");
        campo.setAccessible(true);
        campo.set(p, null);
        Partido jugado = new Partido(2, 1, 2);
        jugado.setEstado(EstadoPartido.JUGADO);
        jugado.setObservaciones("W.O. solicitado, pero se jugó");
        campo.set(jugado, null);
        return p.isWalkover() && !jugado.isWalkover();
    }

    /** Las migraciones llevan una base anterior a la misma estructura que schema.sql y se pueden repetir. */
    private static boolean probarMigraciones(ConexionDB db) throws Exception {
        boolean anterior = walkoverAnterior();
        CampeonatoService s = liga(db, 4);
        Partido p = s.getCampeonato().getPartidos().get(0);
        walkover(s, p, p.getIdLocal());
        jugarCompleto(s, s.getCampeonato().getPartidos().get(1));
        s.historial().archivarActual(); // la copia del historial debe quedar con las mismas personas
        String antes = foto(s);
        volverAVersionCero(db);
        boolean avisa = falla(PersistenciaException.class, db::verificar);
        List<Path> archivos = migraciones();
        for (Path m : archivos) {
            script(db, m.toString());
        }
        db.verificar();
        // 005: las cuentas siguen ahi con el rol nuevo, y en minusculas.
        boolean cuentas = contar(db, "SELECT COUNT(*) FROM usuarios") == 2
                && contar(db, "SELECT COUNT(*) FROM usuarios WHERE username = 'admin' AND rol = 'ORGANIZADOR'") == 1
                && contar(db, "SELECT COUNT(*) FROM usuarios WHERE username = 'ana' AND email = 'ana@correo.com' AND rol = 'AFICIONADO' AND password_hash = 'y'") == 1;
        boolean igual = cuentas && foto(servicio(db)).equals(antes) && contar(db, "SELECT COUNT(*) FROM partidos WHERE walkover") == 2; // el campeonato y su copia
        // Una persona por jugador, compartida por el campeonato en curso y su copia del historial.
        int jugadores = contar(db, "SELECT COUNT(*) FROM jugadores j JOIN torneos t ON t.id = j.torneo_id WHERE t.situacion = 'ACTUAL'");
        boolean personas = jugadores > 0 && contar(db, "SELECT COUNT(*) FROM personas") == jugadores
                && contar(db, "SELECT COUNT(DISTINCT persona_id) FROM jugadores") == jugadores
                && contar(db, "SELECT COUNT(*) FROM jugadores j JOIN torneos t ON t.id = j.torneo_id WHERE t.situacion = 'ARCHIVADO'") == jugadores;
        // El programa sigue guardando sobre la base migrada.
        CampeonatoService migrado = servicio(db);
        Equipo e = migrado.getCampeonato().getEquipos().get(0);
        migrado.equipos().guardarEquipo(e.getId(), e.getNombreEquipo() + " 2", e.getEstadio(), e.getDirectorTecnico(), new ArrayList<>(e.getJugadores()));
        personas &= contar(db, "SELECT COUNT(*) FROM personas") == jugadores;
        antes = foto(migrado);
        for (Path m : archivos) {
            script(db, m.toString()); // repetirlas no cambia nada
        }
        igual &= foto(servicio(db)).equals(antes) && contar(db, "SELECT COUNT(*) FROM usuarios") == 2
                && contar(db, "SELECT MAX(version) FROM esquema_version") == ConexionDB.VERSION_ESQUEMA
                && archivos.size() == ConexionDB.VERSION_ESQUEMA;
        if (!(anterior && avisa && igual && personas)) {
            System.out.println("  anterior=" + anterior + " avisa=" + avisa + " igual=" + igual + " personas=" + personas);
        }
        return anterior && avisa && igual && personas;
    }

    private static List<Path> migraciones() throws Exception {
        try (var archivos = Files.list(Path.of("database/migraciones"))) {
            return archivos.filter(f -> f.toString().endsWith(".sql")).sorted().toList();
        }
    }

    /** Deja la base como la creaba la primera version de schema.sql. */
    private static void volverAVersionCero(ConexionDB db) throws Exception {
        // usuarios como antes de la 005: roles ADMIN y VISITANTE, y usuario o correo con mayusculas.
        sql(db, "ALTER TABLE usuarios DROP CONSTRAINT usuarios_minusculas");
        sql(db, "ALTER TABLE usuarios DROP CONSTRAINT usuarios_rol_check");
        sql(db, "ALTER TABLE usuarios ALTER COLUMN rol TYPE VARCHAR(10)");
        sql(db, "ALTER TABLE usuarios ALTER COLUMN rol SET DEFAULT 'VISITANTE'");
        sql(db, "ALTER TABLE usuarios ADD CONSTRAINT usuarios_rol_check CHECK (rol IN ('ADMIN', 'VISITANTE'))");
        sql(db, "INSERT INTO usuarios (nombre, username, email, password_hash, rol) VALUES ('Administrador', 'admin', 'admin@alphasport.local', 'x', 'ADMIN'),"
                + " ('Ana', 'Ana', 'Ana@Correo.COM', 'y', 'VISITANTE')");
        sql(db, "DROP TABLE movimientos_division");
        sql(db, "ALTER TABLE jugadores DROP CONSTRAINT jugadores_documento_unico");
        sql(db, "ALTER TABLE jugadores DROP COLUMN persona_id");
        sql(db, "DROP TABLE personas");
        sql(db, "DROP VIEW v_votaciones_activas");
        sql(db, "ALTER TABLE partidos DROP COLUMN walkover");
        sql(db, "DROP TABLE esquema_version");
        sql(db, "CREATE VIEW v_votaciones_activas AS SELECT p.id AS partido_id, p.torneo_id, p.numero AS partido_numero, ''::varchar(200) AS local,"
                + " ''::varchar(200) AS visitante, p.goles_local, p.goles_visitante, p.horas_votacion, p.finalizado_en AS cierra_en, 0::bigint AS votos FROM partidos p");
    }

    private static boolean probarDosProgramas(ConexionDB db) throws Exception {
        CampeonatoService escritorio = liga(db, 4);
        try (ConexionDB otraConexion = new ConexionDB(CONFIGURACION, esquemaDe(db))) {
            CampeonatoService web = servicio(otraConexion);
            Partido p = escritorio.getCampeonato().getPartidos().get(0);
            Partido d = copia(p);
            d.setSede("Cambiada en el escritorio");
            escritorio.partidos().guardarPartido(p.getId(), d, false);
            // Sin sincronizar, la web no pisa el cambio: se le avisa y se queda con los datos nuevos.
            Partido dw = copia(web.buscarPartido(p.getId()));
            dw.setSede("Cambiada en la web");
            boolean conflicto = falla(PersistenciaException.class, () -> web.partidos().guardarPartido(p.getId(), dw, false))
                    && web.buscarPartido(p.getId()).getSede().equals("Cambiada en el escritorio");
            web.partidos().guardarPartido(p.getId(), dw, false); // al reintentar si se guarda
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
        s.historial().archivarActual();
        String codigo = s.getCampeonato().getCodigo();
        Partido d = copia(p);
        d.setSede("Cambio posterior");
        s.partidos().guardarPartido(p.getId(), d, false);
        CampeonatoService otro = servicio(db);
        Campeonato archivado = otro.historial().listar().get(0);
        boolean copia = otro.historial().listar().size() == 1 && archivado.getCodigo().equals(codigo) && archivado.getPartidos().get(0).getSede().equals("Estadio de prueba")
                && archivado.getPartidos().get(0).getGoles().size() == 3 && otro.buscarPartido(p.getId()).getSede().equals("Cambio posterior");
        s.historial().archivarActual(); // se vuelve a archivar: reemplaza la copia
        boolean reemplaza = servicio(db).historial().listar().size() == 1 && servicio(db).historial().listar().get(0).getPartidos().get(0).getSede().equals("Cambio posterior");
        s.siguienteTemporada("Liga 2");
        boolean siguiente = seLeeIgual(db, s) && s.getCampeonato().getPartidos().isEmpty() && servicio(db).historial().listar().size() == 1;
        byte[] respaldo = s.historial().respaldo();
        s.cancelar();
        s.historial().abrirRespaldo(respaldo);
        boolean respaldado = seLeeIgual(db, s);
        s.historial().eliminarDelHistorial(codigo);
        boolean borrado = servicio(db).historial().listar().isEmpty() && falla(ReglaNegocioException.class, () -> s.historial().eliminarDelHistorial(codigo));
        if (!(copia && reemplaza && siguiente && respaldado && borrado)) {
            System.out.println("  copia=" + copia + " reemplaza=" + reemplaza + " siguiente=" + siguiente + " respaldado=" + respaldado + " borrado=" + borrado);
        }
        return copia && reemplaza && siguiente && respaldado && borrado;
    }

    /* ---------------------------------------------------------------- ascensos y descensos */

    /** Liga de 8 equipos en 2 divisiones con 1 ascenso, jugada hasta tener campeon. */
    private static CampeonatoService ligaConDivisionesTerminada(ConexionDB db) throws Exception {
        CampeonatoService s = servicio(db);
        ConfiguracionTorneo cfg = new ConfiguracionTorneo();
        cfg.setSorteoAleatorio(false);
        cfg.setDivisiones(2);
        cfg.setAscensos(1);
        s.crear("Temporada 1", TipoFormato.LIGA, cfg, false);
        s.equipos().cargarDatosPrueba(11, 8, true);
        s.avanzar();
        Random azar = new Random(3);
        while (!s.getCampeonato().isTerminado()) {
            for (Partido p : new ArrayList<>(s.getCampeonato().getPartidos())) {
                if (!p.isJugado()) {
                    Partido d = copia(p);
                    d.setEstado(EstadoPartido.JUGADO);
                    d.setGolesLocal(azar.nextInt(4));
                    d.setGolesVisitante(azar.nextInt(4));
                    s.partidos().guardarPartido(p.getId(), d, true);
                }
            }
            s.avanzar();
        }
        return s;
    }

    /**
     * Al pasar de temporada (por «Siguiente temporada» o creando el nuevo campeonato con los mismos equipos) suben
     * los primeros y bajan los ultimos de cada division; queda guardado por que, y los jugadores siguen siendo
     * las mismas personas.
     */
    private static boolean probarAscensos(ConexionDB db) throws Exception {
        boolean bien = true;
        for (String camino : List.of("siguiente", "nuevo")) {
            sql(db, "DELETE FROM torneos");
            CampeonatoService s = ligaConDivisionesTerminada(db);
            Campeonato antes = s.getCampeonato();
            int primeroB = MotorCampeonato.ids(s.getMotor().tablaDeGrupo(0, 1)).get(0);
            List<Integer> tablaA = MotorCampeonato.ids(s.getMotor().tablaDeGrupo(0, 0));
            String sube = antes.nombreDe(primeroB), baja = antes.nombreDe(tablaA.get(tablaA.size() - 1));
            String personas = personasPorDocumento(db, "ACTUAL");
            String resumen = camino.equals("siguiente") ? s.siguienteTemporada("Temporada 2")
                    : s.crear("Temporada 2", TipoFormato.LIGA, antes.getConfiguracion(), true);
            CampeonatoService leido = servicio(db); // lo que quedo guardado
            Campeonato c = leido.getCampeonato();
            boolean movidos = c.getNombreCampeonato().equals("Temporada 2") && c.divisionDe(c.buscarEquipoPorNombre(sube)) == 0
                    && c.divisionDe(c.buscarEquipoPorNombre(baja)) == 1 && resumen.contains("Suben a la Liga A: " + sube)
                    && resumen.contains("Bajan a la Liga B: " + baja);
            boolean constancia = c.getMovimientos().size() == 2 && c.getMovimientos().stream().anyMatch(m -> m.isAscenso()
                    && m.nombreEquipo().equals(sube) && m.puesto() == 1 && m.temporadaAnterior().equals("Temporada 1")
                    && m.idEquipo() == c.buscarEquipoPorNombre(sube).getId())
                    && contar(db, "SELECT COUNT(*) FROM movimientos_division m JOIN torneos t ON t.id = m.torneo_id AND t.situacion = 'ACTUAL'") == 2;
            boolean identidad = !personas.isEmpty() && personasPorDocumento(db, "ACTUAL").equals(personas)
                    && personasPorDocumento(db, "ARCHIVADO").equals(personas)
                    && leido.historial().listar().size() == 1;
            if (!(movidos && constancia && identidad)) {
                System.out.println("  " + camino + ": movidos=" + movidos + " constancia=" + constancia + " identidad=" + identidad + " " + resumen);
            }
            bien &= movidos && constancia && identidad;
        }
        return bien;
    }

    /** documento -> codigo de persona de los jugadores activos del torneo ACTUAL o ARCHIVADO. */
    private static String personasPorDocumento(ConexionDB db, String situacion) throws PersistenciaException {
        return db.ejecutar(con -> {
            try (var st = con.createStatement(); var rs = st.executeQuery("SELECT j.documento, p.codigo FROM jugadores j"
                    + " JOIN torneos t ON t.id = j.torneo_id AND t.situacion = '" + situacion + "' JOIN personas p ON p.id = j.persona_id"
                    + " WHERE j.activo ORDER BY j.documento")) {
                StringBuilder texto = new StringBuilder();
                while (rs.next()) {
                    texto.append(rs.getString(1)).append('=').append(rs.getString(2)).append(' ');
                }
                return texto.toString();
            }
        });
    }

    /* ---------------------------------------------------------------- documento unico */

    /**
     * La base de datos tampoco acepta dos jugadores activos del torneo con el mismo documento (aunque alguien se
     * salte el servicio), con un mensaje claro; y la migracion 003 no se aplica si ya hay repetidos: dice cuales.
     */
    private static boolean probarDocumentoUnico(ConexionDB db) throws Exception {
        CampeonatoService s = liga(db, 4);
        String doc = s.getCampeonato().getEquipos().get(0).getJugadores().get(0).getDocumento();
        int otro = s.getCampeonato().getEquipos().get(1).getJugadores().get(0).getId();
        String error = "";
        try {
            sql(db, "UPDATE jugadores SET documento = ' " + doc.toLowerCase() + " ' WHERE numero = " + otro
                    + " AND torneo_id = (SELECT id FROM torneos WHERE situacion = 'ACTUAL')");
        } catch (PersistenciaException e) {
            error = e.getMessage();
        }
        boolean base = error.contains("mismo documento");
        // Un jugador que salio de la plantilla (inactivo) no cuenta, y en otro torneo (el historial) tampoco.
        sql(db, "UPDATE jugadores SET activo = FALSE WHERE numero = " + otro);
        sql(db, "UPDATE jugadores SET documento = '" + doc + "' WHERE numero = " + otro);
        // La migracion avisa con nombres si encuentra repetidos.
        sql(db, "ALTER TABLE jugadores DROP CONSTRAINT jugadores_documento_unico");
        sql(db, "UPDATE jugadores SET activo = TRUE WHERE numero = " + otro);
        String aviso = "";
        try {
            script(db, "database/migraciones/003_documento_unico.sql");
        } catch (PersistenciaException e) {
            aviso = e.getMessage();
        }
        boolean migracion = aviso.contains("mismo documento") && aviso.contains(doc);
        sql(db, "UPDATE jugadores SET documento = 'OTRO-" + otro + "' WHERE numero = " + otro);
        script(db, "database/migraciones/003_documento_unico.sql");
        migracion &= contar(db, "SELECT COUNT(*) FROM pg_constraint WHERE conname = 'jugadores_documento_unico'") == 1;
        if (!(base && migracion)) {
            System.out.println("  base=" + base + " (" + error + ") migracion=" + migracion + " (" + aviso + ")");
        }
        return base && migracion;
    }

    /* ---------------------------------------------------------------- contratos (LSP) */

    /**
     * Cualquier version de un repositorio se puede usar en lugar de otra: sin campeonato, cargar() da vacio (no
     * null ni una excepcion); buscar una noticia que no existe da vacio, quitar uno de sus comentarios da false y
     * comentarla es un error.
     */
    private static boolean probarContratos(ConexionDB db) throws Exception {
        Path carpeta = Files.createTempDirectory("alphasport-contrato");
        List<alphasport.persistence.RepositorioCampeonato> campeonatos = List.of(new PostgresRepositorioCampeonato(db),
                new alphasport.persistence.ArchivoRepositorioCampeonato(carpeta));
        boolean vacios = true;
        for (var r : campeonatos) {
            vacios &= r.cargar().isEmpty() && !r.existe();
        }
        CampeonatoService s = liga(db, 4);
        var archivo = new alphasport.persistence.ArchivoRepositorioCampeonato(carpeta);
        archivo.guardar(s.getCampeonato());
        for (var r : List.of(new PostgresRepositorioCampeonato(db), archivo)) {
            vacios &= r.existe() && r.cargar().orElseThrow().getEquipos().size() == 4;
        }
        List<alphasport.persistence.RepositorioNoticias> noticias = List.of(new PostgresRepositorioNoticias(db),
                new alphasport.persistence.RepositorioNoticiasEnMemoria(), new alphasport.persistence.ArchivoRepositorioNoticias(carpeta));
        boolean iguales = true;
        for (var r : noticias) {
            iguales &= r.buscar(999).isEmpty() && !r.eliminarComentario(999, 1)
                    && falla(PersistenciaException.class, () -> r.insertarComentario(999, new alphasport.model.Comentario(0, "Ana", "Hola", "2026-11-22 10:00")));
        }
        if (!(vacios && iguales)) {
            System.out.println("  vacios=" + vacios + " iguales=" + iguales);
        }
        return vacios && iguales;
    }

    /* ---------------------------------------------------------------- noticias y migracion */

    private static boolean probarNoticias(ConexionDB db) throws Exception {
        Path imagenes = Files.createTempDirectory("alphasport-imagenes");
        NoticiasService n = new NoticiasService(new PostgresRepositorioNoticias(db), new alphasport.persistence.ImagenesEnDisco(imagenes));
        Noticia propuesta = n.proponer("Propuesta de un hincha", "Texto de la propuesta del visitante.", "Ana", new NoticiasService.Imagen(new byte[]{1, 2, 3}, "png"));
        Noticia oficial = n.publicar(null, "Noticia oficial", "Texto oficial de la noticia.", null, false);
        NoticiasService otra = new NoticiasService(new PostgresRepositorioNoticias(db), new alphasport.persistence.ImagenesEnDisco(imagenes)); // otro programa
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
        viejo.equipos().cargarDatosPrueba(11, 4, true);
        viejo.avanzar();
        Partido p = viejo.getCampeonato().getPartidos().get(0);
        jugarCompleto(viejo, p);
        viejo.getCampeonato().buscarEquipo(p.getIdLocal()).getJugadores().remove(8); // el goleador ya no esta en la plantilla
        viejo.historial().archivarActual();
        NoticiasService noticias = new NoticiasService(new ArchivoRepositorioNoticias(carpeta), new alphasport.persistence.ImagenesEnDisco(carpeta.resolve("imagenes")));
        noticias.comentar(noticias.publicar(null, "Noticia vieja", "Texto de la noticia vieja.", null, false).getId(), "Ana", "Hola");
        MigradorArchivos.migrar(db, carpeta);
        CampeonatoService nuevo = servicio(db);
        boolean igual = foto(nuevo).equals(foto(viejo)) && nuevo.historial().listar().size() == 1;
        NoticiasService migradas = new NoticiasService(new PostgresRepositorioNoticias(db), new alphasport.persistence.ImagenesEnDisco(carpeta.resolve("imagenes")));
        igual &= migradas.publicadas().size() == 1 && migradas.publicadas().get(0).getComentarios().size() == 1;
        MigradorArchivos.migrar(db, carpeta); // repetirla no duplica nada
        boolean unaVez = contar(db, "SELECT COUNT(*) FROM torneos") == 2 && contar(db, "SELECT COUNT(*) FROM noticias") == 1;
        return igual && unaVez;
    }
}
