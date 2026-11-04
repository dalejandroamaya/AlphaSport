package alphasport.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import alphasport.model.Campeonato;
import alphasport.model.ConfiguracionTorneo;
import alphasport.model.CriterioDesempate;
import alphasport.model.EstadoPartido;
import alphasport.model.Fase;
import alphasport.model.Partido;
import alphasport.model.TipoFormato;

/**
 * Prueba automatica del motor (no necesita JUnit): juega cientos de
 * campeonatos con resultados aleatorios en los 6 formatos y revisa que
 * siempre se llegue a un campeon sin errores. Tambien prueba los
 * desempates y la edicion de partidos.
 *
 * En NetBeans: clic derecho sobre este archivo > Run File (Shift + F6).
 */
public class SimulacionFormatos {

    private static final Random azar = new Random(7);
    private static int fallos = 0;

    public static void main(String[] args) {
        int[] tamanos = {3, 4, 5, 6, 7, 8, 9, 10, 12, 13, 16, 20, 24, 32, 36};
        for (TipoFormato formato : TipoFormato.values()) {
            int completos = 0;
            int total = 0;
            for (int n : tamanos) {
                for (int variante = 0; variante < 6; variante++) {
                    Campeonato c = new Campeonato("Prueba " + n, formato, configuracion(n, variante));
                    for (int i = 1; i <= n; i++) {
                        c.agregarEquipo("Equipo " + i);
                    }
                    String resultado = jugar(new MotorCampeonato(c), n);
                    if (resultado.startsWith("INVALIDO")) {
                        continue; // esa combinacion de reglas no aplica para n equipos
                    }
                    total++;
                    if (resultado.startsWith("OK")) {
                        completos++;
                    } else {
                        fallos++;
                        System.out.println("  FALLO " + formato + " n=" + n + " variante=" + variante + ": " + resultado);
                    }
                }
            }
            System.out.println(formato.getNombre() + ": " + completos + "/" + total + " campeonatos completos");
        }
        probarDesempateDirecto();
        probarEdicion();
        probarSanciones();
        probarResumenYPalmares();
        System.out.println(fallos == 0 ? "TODO BIEN" : "FALLOS: " + fallos);
    }

    private static ConfiguracionTorneo configuracion(int n, int variante) {
        ConfiguracionTorneo cfg = new ConfiguracionTorneo();
        cfg.setIdaYVuelta(variante % 2 == 1);
        cfg.setEliminatoriaIdaYVuelta(variante % 3 == 1);
        cfg.setFinalUnica(variante != 4);
        cfg.setSorteoAleatorio(variante % 2 == 0);
        cfg.setGranFinalConReinicio(variante % 2 == 0);
        cfg.setTipoPlayoff(variante >= 3 ? ConfiguracionTorneo.PLAYOFF_CUADRANGULARES : ConfiguracionTorneo.PLAYOFF_LLAVE);
        cfg.setClasificadosPlayoff(Math.min(8, n));
        cfg.setVentajaDeportiva(variante % 2 == 0);
        cfg.setClasificadosPorGrupo(variante % 3 == 2 ? 3 : 2);
        cfg.setRondasSuizo(Math.min(8, n - 1));
        int llave = 2;
        while (llave * 2 <= n / 2) {
            llave *= 2;
        }
        boolean conRepechaje = llave + llave / 2 <= n;
        cfg.setDirectosSuizo(conRepechaje ? llave / 2 : Math.min(n, llave));
        cfg.setRepechajeSuizo(conRepechaje ? llave : 0);
        return cfg;
    }

    private static String jugar(MotorCampeonato motor, int n) {
        Campeonato c = motor.getCampeonato();
        try {
            motor.iniciar();
        } catch (IllegalStateException e) {
            return "INVALIDO " + e.getMessage();
        }
        for (int vuelta = 0; vuelta < 500; vuelta++) {
            int indice = c.getFases().size() - 1;
            for (Partido p : c.partidosDeFase(indice)) {
                if (p.isJugado()) {
                    continue;
                }
                Partido datos = new Partido();
                datos.copiarDatosDe(p);
                datos.setEstado(EstadoPartido.JUGADO);
                datos.setGolesLocal(azar.nextInt(4));
                datos.setGolesVisitante(azar.nextInt(4));
                datos.setAmarillasLocal(azar.nextInt(3));
                if (motor.requierePenales(p, datos.getGolesLocal(), datos.getGolesVisitante())) {
                    datos.setPenalesLocal(4);
                    datos.setPenalesVisitante(azar.nextBoolean() ? 3 : 5);
                }
                motor.aplicarEdicion(p, datos, false);
            }
            // ningun equipo juega dos veces en la misma ronda (la vuelta de una llave no cuenta)
            HashSet<String> vistos = new HashSet<>();
            for (Partido p : c.partidosDeFase(indice)) {
                if (p.getIdLocal() == p.getIdVisitante()) {
                    return "partido de un equipo contra si mismo";
                }
                for (int id : new int[]{p.getIdLocal(), p.getIdVisitante()}) {
                    if (p.getPierna() != 2 && !vistos.add(p.getGrupo() + "/" + p.getRonda() + "/" + id)) {
                        return "equipo " + id + " repetido en la ronda " + p.getRonda();
                    }
                }
            }
            try {
                motor.avanzar();
            } catch (IllegalStateException e) {
                return "ERROR al avanzar: " + e.getMessage();
            }
            if (c.isTerminado()) {
                if (c.getFormato() == TipoFormato.DOBLE_ELIMINACION) {
                    int partidos = c.getPartidos().size();
                    if (partidos < 2 * n - 2 || partidos > 2 * n - 1) {
                        return "doble eliminacion con " + partidos + " partidos para " + n + " equipos";
                    }
                }
                String fases = "";
                for (Fase f : c.getFases()) {
                    fases += f.getNombre() + " > ";
                }
                return "OK " + fases + c.nombreDe(c.getIdCampeon());
            }
        }
        return "el campeonato no termino";
    }

    /**
     * A, B, C y D. C y D empatan a 6 puntos y C le gano a D.
     * A y B empatan a 3 puntos: B tiene mejor diferencia, pero A le gano a B.
     * Con "enfrentamiento directo" primero el orden debe ser C D A B.
     */
    private static void probarDesempateDirecto() {
        ConfiguracionTorneo cfg = new ConfiguracionTorneo();
        cfg.getCriterios().clear();
        cfg.getCriterios().add(CriterioDesempate.ENFRENTAMIENTO_DIRECTO);
        cfg.getCriterios().add(CriterioDesempate.DIFERENCIA_GOLES);
        Campeonato c = new Campeonato("Desempate", TipoFormato.LIGA, cfg);
        for (String nombre : new String[]{"A", "B", "C", "D"}) {
            c.agregarEquipo(nombre);
        }
        int[][] resultados = {{1, 2, 1, 0}, {2, 3, 3, 0}, {3, 1, 2, 0}, {1, 4, 0, 1}, {2, 4, 0, 1}, {3, 4, 1, 0}};
        ArrayList<Partido> partidos = new ArrayList<>();
        for (int[] r : resultados) {
            Partido p = c.crearPartido(r[0], r[1]);
            p.setEstado(EstadoPartido.JUGADO);
            p.setGolesLocal(r[2]);
            p.setGolesVisitante(r[3]);
            partidos.add(p);
        }
        String orden = "";
        for (FilaTabla f : new TablaPosiciones(c).calcular(partidos, List.of(1, 2, 3, 4))) {
            orden += f.getEquipo().getNombreEquipo() + " ";
        }
        boolean correcto = orden.trim().equals("C D A B");
        System.out.println("Desempate por enfrentamiento directo: " + orden + (correcto ? "(correcto)" : "(MAL)"));
        if (!correcto) {
            fallos++;
        }
    }

    /**
     * Cambiar la fecha de un partido de la fase regular no afecta la
     * liguilla; cambiar el marcador si, y obliga a regenerarla.
     */
    private static void probarEdicion() {
        ConfiguracionTorneo cfg = new ConfiguracionTorneo();
        cfg.setClasificadosPlayoff(4);
        Campeonato c = new Campeonato("Edicion", TipoFormato.TORNEO_CORTO, cfg);
        c.setSemilla(7); // sorteo fijo: con un sorteo al azar el partido editado a veces no cambia la tabla
        for (int i = 1; i <= 6; i++) {
            c.agregarEquipo("E" + i);
        }
        MotorCampeonato motor = new MotorCampeonato(c);
        motor.iniciar();
        for (Partido p : c.partidosDeFase(0)) {
            Partido datos = new Partido();
            datos.copiarDatosDe(p);
            datos.setEstado(EstadoPartido.JUGADO);
            datos.setGolesLocal(p.getIdLocal() < p.getIdVisitante() ? 2 : 0);
            motor.aplicarEdicion(p, datos, false);
        }
        motor.avanzar();
        Partido primero = c.partidosDeFase(0).get(0);
        Partido datos = new Partido();
        datos.copiarDatosDe(primero);
        datos.setFecha("01/01/2027");
        boolean afectaFecha = motor.afectaRondasPosteriores(primero, datos);
        datos.setGolesLocal(0);
        datos.setGolesVisitante(9);
        boolean afectaMarcador = motor.afectaRondasPosteriores(primero, datos);
        motor.aplicarEdicion(primero, datos, afectaMarcador);
        boolean correcto = !afectaFecha && afectaMarcador && c.getFases().size() == 1;
        System.out.println("Edicion de partidos: " + (correcto ? "correcto" : "MAL"));
        if (!correcto) {
            fallos++;
        }
    }

    /**
     * Copa de 8 equipos: en la clasificacion final el campeon va 1.o y el
     * finalista 2.o; en una liga el orden es el de la tabla. El palmares de
     * dos copias del mismo torneo le da 2 titulos al campeon.
     */
    private static void probarResumenYPalmares() {
        boolean correcto = true;
        for (TipoFormato formato : TipoFormato.values()) {
            ConfiguracionTorneo cfg = new ConfiguracionTorneo();
            cfg.setClasificadosPlayoff(4);
            cfg.setDirectosSuizo(2);
            cfg.setRepechajeSuizo(4);
            cfg.setRondasSuizo(4);
            Campeonato c = new Campeonato("Resumen", formato, cfg);
            for (int i = 1; i <= 8; i++) {
                c.agregarEquipo("E" + i);
            }
            jugar(new MotorCampeonato(c), 8);
            ResumenCampeonato resumen = new ResumenCampeonato(c);
            ArrayList<FilaTabla> clasificacion = resumen.clasificacionFinal();
            boolean campeonPrimero = c.isTerminado() && clasificacion.get(0).getIdEquipo() == c.getIdCampeon();
            boolean todos = clasificacion.size() == 8;
            // el ultimo partido jugado es la final (o gran final): su perdedor debe ir 2.o
            boolean segundo = true;
            if (formato != TipoFormato.LIGA) {
                Partido ultimo = c.getPartidos().get(c.getPartidos().size() - 1);
                segundo = clasificacion.get(1).getIdEquipo() == ultimo.getRival(c.getIdCampeon());
            }
            ArrayList<Palmares.Fila> palmares = Palmares.calcular(List.of(c, c));
            boolean titulos = palmares.get(0).getTitulos() == 2
                    && palmares.get(0).getEquipo().equals(c.nombreDe(c.getIdCampeon()));
            boolean premios = resumen.equipoMasGoleador() != null && resumen.mayorGoleada() != null && !resumen.texto().isEmpty();
            if (!(campeonPrimero && todos && segundo && titulos && premios)) {
                System.out.println("  Resumen MAL en " + formato + ": campeon=" + campeonPrimero + " todos=" + todos
                        + " segundo=" + segundo + " titulos=" + titulos + " premios=" + premios);
                correcto = false;
            }
        }
        System.out.println("Resumen, premios y palmares: " + (correcto ? "correcto" : "MAL"));
        if (!correcto) {
            fallos++;
        }
    }

    /**
     * Liga de 4 equipos (3 jornadas). Un jugador ve roja con 2 fechas en su
     * primer partido: queda fuera de los partidos 2 y 3 de su equipo.
     */
    private static void probarSanciones() {
        Campeonato c = new Campeonato("Sanciones", TipoFormato.LIGA, new ConfiguracionTorneo());
        for (int i = 1; i <= 4; i++) {
            c.agregarEquipo("E" + i);
        }
        alphasport.model.Jugador jugador = new alphasport.model.Jugador("Jugador expulsado", "1", 10, "Delantero");
        jugador.setId(c.nuevoIdJugador());
        c.buscarEquipo(1).agregarJugador(jugador);
        MotorCampeonato motor = new MotorCampeonato(c);
        motor.iniciar();

        ArrayList<Partido> delEquipo = new ArrayList<>();
        for (Partido p : c.getPartidos()) {
            if (p.involucra(1)) {
                delEquipo.add(p);
            }
        }
        Partido primero = delEquipo.get(0);
        Partido datos = new Partido();
        datos.copiarDatosDe(primero);
        datos.setEstado(EstadoPartido.JUGADO);
        ArrayList<alphasport.model.Tarjeta> tarjetas = new ArrayList<>();
        tarjetas.add(new alphasport.model.Tarjeta(1, jugador, alphasport.model.Tarjeta.ROJA, 2));
        datos.setTarjetas(tarjetas, primero.getIdLocal());
        boolean contadaBien = primero.getIdLocal() == 1
                ? datos.getRojasLocal() == 1 && datos.getRojasVisitante() == 0
                : datos.getRojasVisitante() == 1 && datos.getRojasLocal() == 0;
        motor.aplicarEdicion(primero, datos, false);

        Sanciones s = new Sanciones(c);
        boolean fuera2 = s.estaSuspendido(delEquipo.get(1), jugador.getId());
        boolean fuera3 = s.estaSuspendido(delEquipo.get(2), jugador.getId());
        int pendientesAntes = s.fechasPendientesDe(jugador.getId());
        delEquipo.get(1).setEstado(EstadoPartido.JUGADO);
        int pendientesDespues = s.fechasPendientesDe(jugador.getId());
        boolean rojaCuenta = new TablaPosiciones(c).calcular(c.getPartidos(), List.of(1, 2, 3, 4)).stream()
                .anyMatch(f -> f.getIdEquipo() == 1 && f.getRojas() == 1);
        boolean correcto = fuera2 && fuera3 && pendientesAntes == 2 && pendientesDespues == 1 && rojaCuenta && contadaBien;
        System.out.println("Tarjetas y suspensiones: " + (correcto ? "correcto" : "MAL"));
        if (!correcto) {
            fallos++;
        }
    }
}
