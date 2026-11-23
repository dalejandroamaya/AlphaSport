package alphasport.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import alphasport.service.formatos.FormatoCampeonato;
import alphasport.model.Campeonato;
import alphasport.model.ConfiguracionTorneo;
import alphasport.model.Equipo;
import alphasport.model.EstadoPartido;
import alphasport.model.Fase;
import alphasport.model.Partido;
import alphasport.model.TipoFase;

/**
 * Motor del campeonato: crea las fases, genera las rondas, decide quien
 * avanza y controla la edicion de partidos.
 *
 * El formato (logica.formatos) decide QUE fases tiene el torneo; el motor
 * sabe COMO se juega cada tipo de fase (liga, grupos, suizo, eliminatoria,
 * doble eliminacion).
 */
public class MotorCampeonato {

    public static final String LLAVE_GANADORES = "Ganadores";
    public static final String LLAVE_PERDEDORES = "Perdedores";
    public static final String LLAVE_GRAN_FINAL = "Gran final";

    private final Campeonato campeonato;
    private final TablaPosiciones tabla;
    private final FormatoCampeonato formato;

    public MotorCampeonato(Campeonato campeonato) {
        this.campeonato = campeonato;
        this.tabla = new TablaPosiciones(campeonato);
        this.formato = FormatoCampeonato.crear(campeonato.getFormato());
    }

    public Campeonato getCampeonato() {
        return campeonato;
    }

    /**
     * Campeon de cada division en una liga con divisiones: el primero de la tabla de cada una
     * (posicion 0 = Liga A, que tambien es el campeon del torneo). Vacia si el torneo no ha
     * terminado o no tiene divisiones.
     */
    public ArrayList<Integer> campeonesPorDivision() {
        ArrayList<Integer> campeones = new ArrayList<>();
        if (!campeonato.isTerminado() || campeonato.getFases().isEmpty() || !campeonato.getFases().get(0).isDivisiones()) {
            return campeones;
        }
        for (int g = 0; g < campeonato.getFases().get(0).getGrupos().size(); g++) {
            ArrayList<FilaTabla> tabla = tablaDeGrupo(0, g);
            campeones.add(tabla.isEmpty() ? -1 : tabla.get(0).getIdEquipo());
        }
        return campeones;
    }

    /** «Campeon: X» o, con divisiones, «Campeones: Liga A: X · Liga B: Y · ...». */
    public String textoCampeones() {
        ArrayList<Integer> porDivision = campeonesPorDivision();
        if (porDivision.isEmpty()) {
            return "Campeón: " + campeonato.nombreDe(campeonato.getIdCampeon());
        }
        ArrayList<String> partes = new ArrayList<>();
        for (int d = 0; d < porDivision.size(); d++) {
            partes.add(campeonato.getFases().get(0).nombreGrupo(d) + ": " + campeonato.nombreDe(porDivision.get(d)));
        }
        return "Campeones: " + String.join(" · ", partes);
    }

    /** true si el formato puede tener llaves; si no, la interfaz oculta la seccion «Llaves». */
    public boolean usaLlaves() {
        return formato.usaLlaves();
    }

    public TablaPosiciones getTabla() {
        return tabla;
    }

    public ConfiguracionTorneo getConfiguracion() {
        return campeonato.getConfiguracion();
    }

    // =====================================================================
    //  INICIAR Y AVANZAR
    // =====================================================================
    /**
     * Genera la primera fase (fixture automatico). Lanza IllegalStateException
     * con un mensaje para el usuario si algo no cumple las reglas.
     */
    public String iniciar() {
        if (campeonato.isIniciado()) {
            throw new IllegalStateException("El campeonato ya fue iniciado.");
        }
        if (campeonato.getEquiposActivos().size() < 2) {
            throw new IllegalStateException("Debe registrar al menos 2 equipos.");
        }
        String error = formato.validar(campeonato);
        if (error != null) {
            throw new IllegalStateException(error);
        }
        Fase primera = formato.crearPrimeraFase(campeonato, this);
        agregarFase(primera);
        return "Campeonato iniciado. Se genero: " + primera.getNombre() + ".";
    }

    /**
     * Pasa a la siguiente ronda o fase cuando la actual esta completa.
     * Si ya no hay mas fases, declara al campeon.
     */
    public String avanzar() {
        if (!campeonato.isIniciado()) {
            return iniciar();
        }
        if (campeonato.isTerminado()) {
            throw new IllegalStateException("El campeonato ya termino. Campeon: "
                    + campeonato.nombreDe(campeonato.getIdCampeon()));
        }
        int indice = campeonato.getFases().size() - 1;
        Fase fase = campeonato.getFases().get(indice);

        int pendientes = contarPendientes(indice);
        if (pendientes > 0) {
            throw new IllegalStateException((pendientes == 1 ? "Falta 1 partido" : "Faltan " + pendientes + " partidos")
                    + " por jugar en la fase actual.\n"
                    + "Los aplazados o suspendidos deben reprogramarse y jugarse (o darse por W.O.).");
        }
        String error = validarRondaDefinida(fase, indice);
        if (error != null) {
            throw new IllegalStateException(error);
        }

        if (generarSiguienteRonda(fase, indice)) {
            return "Se genero la siguiente ronda de " + fase.getNombre() + ".";
        }

        Fase siguiente = formato.crearSiguienteFase(campeonato, this, indice);
        if (siguiente == null) {
            int ganador = ganadorDeFase(indice);
            if (ganador == -1) {
                throw new IllegalStateException("No se pudo determinar el campeon.");
            }
            campeonato.setIdCampeon(ganador);
            return "¡El campeonato termino! " + textoCampeones();
        }
        agregarFase(siguiente);
        return "Comienza la fase: " + siguiente.getNombre() + ".";
    }

    public int contarPendientes(int indiceFase) {
        int pendientes = 0;
        for (Partido p : campeonato.partidosDeFase(indiceFase)) {
            if (!p.isJugado()) {
                pendientes++;
            }
        }
        return pendientes;
    }

    public String describirEstado() {
        if (!campeonato.isIniciado()) {
            return "Sin iniciar: registre los equipos y pulse \"Iniciar campeonato\".";
        }
        if (campeonato.isTerminado()) {
            return "Terminado. " + textoCampeones();
        }
        int indice = campeonato.getFases().size() - 1;
        Fase fase = campeonato.getFaseActual();
        int pendientes = contarPendientes(indice);
        String texto = "Fase actual: " + fase.getNombre();
        if (fase.getTipo() != TipoFase.LIGA && fase.getTipo() != TipoFase.GRUPOS) {
            texto += " - ronda " + ultimaRonda(indice);
        }
        return texto + (pendientes > 0 ? " - " + (pendientes == 1 ? "1 partido pendiente" : pendientes + " partidos pendientes")
                : " - completa: pulse \"Cerrar ronda y avanzar\"");
    }

    private void agregarFase(Fase fase) {
        campeonato.setPasoActual(campeonato.getPasoActual() + 1);
        fase.setPasoInicio(campeonato.getPasoActual());
        campeonato.getFases().add(fase);
        int indice = campeonato.getFases().size() - 1;
        switch (fase.getTipo()) {
            case LIGA:
                generarTodosContraTodos(indice, fase.getParticipantes(), "", fase.isIdaYVuelta());
                break;
            case GRUPOS:
                for (int g = 0; g < fase.getGrupos().size(); g++) {
                    generarTodosContraTodos(indice, fase.getGrupos().get(g), Fase.letraGrupo(g), fase.isIdaYVuelta());
                }
                break;
            case SUIZO:
                generarRondaSuiza(fase, indice, 1);
                break;
            case ELIMINATORIA:
                generarRondaEliminatoria(fase, indice, 1);
                break;
            case DOBLE_ELIMINACION:
                generarRondaDoble(fase, indice, 1);
                break;
        }
    }

    private boolean generarSiguienteRonda(Fase fase, int indice) {
        int ronda = ultimaRonda(indice);
        switch (fase.getTipo()) {
            case SUIZO:
                if (ronda < fase.getRondasSuizo()) {
                    campeonato.setPasoActual(campeonato.getPasoActual() + 1);
                    generarRondaSuiza(fase, indice, ronda + 1);
                    return true;
                }
                return false;
            case ELIMINATORIA:
                if (fase.getMaxRondas() > 0 && ronda >= fase.getMaxRondas()) {
                    return false;
                }
                if (ganadoresDeRonda(fase, indice, ronda).size() < 2) {
                    return false;
                }
                campeonato.setPasoActual(campeonato.getPasoActual() + 1);
                generarRondaEliminatoria(fase, indice, ronda + 1);
                return true;
            case DOBLE_ELIMINACION:
                if (campeonDoble(fase, indice) != -1) {
                    return false;
                }
                campeonato.setPasoActual(campeonato.getPasoActual() + 1);
                generarRondaDoble(fase, indice, ronda + 1);
                return true;
            default:
                return false;
        }
    }

    public int ultimaRonda(int indiceFase) {
        int ronda = 0;
        for (Partido p : campeonato.partidosDeFase(indiceFase)) {
            ronda = Math.max(ronda, p.getRonda());
        }
        return ronda;
    }

    private String validarRondaDefinida(Fase fase, int indice) {
        int ronda = ultimaRonda(indice);
        for (Partido p : campeonato.partidosDeFase(indice)) {
            if (p.getRonda() != ronda || !p.isEliminatorio()) {
                continue;
            }
            boolean sinGanador;
            if (fase.getTipo() == TipoFase.ELIMINATORIA) {
                sinGanador = ganadorSerie(fase, indice, p.getSerie(), esFinal(fase, indice, ronda)) == -1;
            } else {
                sinGanador = p.getGanador() == -1;
            }
            if (sinGanador) {
                return "La llave " + campeonato.nombreDe(p.getIdLocal()) + " vs "
                        + campeonato.nombreDe(p.getIdVisitante()) + " termino empatada.\n"
                        + "Edite el partido y registre la definicion por penales.";
            }
        }
        return null;
    }

    private Partido nuevoPartido(int indiceFase, int local, int visitante, int ronda, String nombreRonda) {
        Partido p = campeonato.crearPartido(local, visitante);
        p.setIndiceFase(indiceFase);
        p.setPaso(campeonato.getPasoActual());
        p.setRonda(ronda);
        p.setNombreRonda(nombreRonda);
        Equipo equipoLocal = campeonato.buscarEquipo(local);
        if (equipoLocal != null && equipoLocal.getEstadio() != null) {
            p.setSede(equipoLocal.getEstadio());
        }
        return p;
    }

    // =====================================================================
    //  CREACION DE FASES (las usan los formatos)
    // =====================================================================
    /**
     * Ordena los equipos para el sorteo: al azar o por orden de registro
     * (el primero registrado queda como cabeza de serie).
     */
    public ArrayList<Integer> sembrar(List<Equipo> equipos) {
        ArrayList<Equipo> lista = new ArrayList<>(equipos);
        if (getConfiguracion().isSorteoAleatorio()) {
            Collections.shuffle(lista, new Random(campeonato.getSemilla()));
        }
        ArrayList<Integer> ids = new ArrayList<>();
        for (Equipo e : lista) {
            ids.add(e.getId());
        }
        return ids;
    }

    public Fase crearFaseLiga(String nombre, List<Integer> ids, boolean idaYVuelta) {
        Fase fase = new Fase(nombre, TipoFase.LIGA);
        fase.setParticipantes(new ArrayList<>(ids));
        fase.setIdaYVuelta(idaYVuelta);
        return fase;
    }

    public Fase crearFaseGrupos(String nombre, ArrayList<ArrayList<Integer>> grupos, boolean idaYVuelta) {
        Fase fase = new Fase(nombre, TipoFase.GRUPOS);
        fase.setGrupos(grupos);
        for (ArrayList<Integer> grupo : grupos) {
            fase.getParticipantes().addAll(grupo);
        }
        fase.setIdaYVuelta(idaYVuelta);
        return fase;
    }

    /**
     * Sorteo por bombos (como el Mundial): el bombo 1 tiene los mejores
     * sembrados, el bombo 2 los siguientes... y cada grupo recibe uno de cada bombo.
     */
    public ArrayList<ArrayList<Integer>> repartirPorBombos(List<Integer> sembrados, int cantidadGrupos) {
        ArrayList<ArrayList<Integer>> grupos = new ArrayList<>();
        for (int g = 0; g < cantidadGrupos; g++) {
            grupos.add(new ArrayList<>());
        }
        Random azar = new Random(campeonato.getSemilla() + 17);
        for (int inicio = 0; inicio < sembrados.size(); inicio += cantidadGrupos) {
            ArrayList<Integer> bombo = new ArrayList<>(sembrados.subList(inicio, Math.min(inicio + cantidadGrupos, sembrados.size())));
            if (getConfiguracion().isSorteoAleatorio()) {
                Collections.shuffle(bombo, azar);
            }
            for (int i = 0; i < bombo.size(); i++) {
                grupos.get(i).add(bombo.get(i));
            }
        }
        return grupos;
    }

    /**
     * Reparto en serpentina: con 8 equipos y 2 grupos queda
     * A = 1, 4, 5, 8 y B = 2, 3, 6, 7 (como los cuadrangulares).
     */
    public ArrayList<ArrayList<Integer>> repartirEnSerpentina(List<Integer> sembrados, int cantidadGrupos) {
        ArrayList<ArrayList<Integer>> grupos = new ArrayList<>();
        for (int g = 0; g < cantidadGrupos; g++) {
            grupos.add(new ArrayList<>());
        }
        for (int i = 0; i < sembrados.size(); i++) {
            int vuelta = i / cantidadGrupos;
            int posicion = i % cantidadGrupos;
            int grupo = (vuelta % 2 == 0) ? posicion : cantidadGrupos - 1 - posicion;
            grupos.get(grupo).add(sembrados.get(i));
        }
        return grupos;
    }

    public Fase crearFaseEliminatoria(String nombre, List<Integer> sembrados, boolean idaYVuelta,
            boolean finalUnica, boolean ventajaDeportiva, int maxRondas) {
        Fase fase = new Fase(nombre, TipoFase.ELIMINATORIA);
        fase.setParticipantes(new ArrayList<>(sembrados));
        fase.setCuadro(GeneradorFixture.cuadroDesdeSiembra(sembrados));
        fase.setIdaYVuelta(idaYVuelta);
        fase.setFinalUnica(finalUnica);
        fase.setVentajaDeportiva(ventajaDeportiva);
        fase.setMaxRondas(maxRondas);
        return fase;
    }

    /**
     * En la primera ronda de una llave intenta que no se crucen dos equipos
     * del mismo grupo, intercambiando rivales entre llaves.
     */
    public void evitarCrucesMismoGrupo(Fase fase, HashMap<Integer, String> grupoDe) {
        ArrayList<Integer> cuadro = fase.getCuadro();
        for (int i = 0; i + 1 < cuadro.size(); i += 2) {
            int a = cuadro.get(i);
            int b = cuadro.get(i + 1);
            if (a == Fase.LIBRE || b == Fase.LIBRE || !grupoDe.get(a).equals(grupoDe.get(b))) {
                continue;
            }
            for (int j = 0; j + 1 < cuadro.size(); j += 2) {
                int c = cuadro.get(j);
                int d = cuadro.get(j + 1);
                if (j == i || c == Fase.LIBRE || d == Fase.LIBRE) {
                    continue;
                }
                if (!grupoDe.get(a).equals(grupoDe.get(d)) && !grupoDe.get(c).equals(grupoDe.get(b))) {
                    cuadro.set(i + 1, d);
                    cuadro.set(j + 1, b);
                    break;
                }
            }
        }
    }

    public Fase crearFaseSuiza(String nombre, List<Integer> sembrados, int rondas) {
        Fase fase = new Fase(nombre, TipoFase.SUIZO);
        fase.setParticipantes(new ArrayList<>(sembrados));
        fase.setRondasSuizo(Math.max(1, Math.min(rondas, sembrados.size() - 1)));
        return fase;
    }

    public Fase crearFaseDobleEliminacion(String nombre, List<Integer> sembrados, boolean conReinicio) {
        Fase fase = new Fase(nombre, TipoFase.DOBLE_ELIMINACION);
        fase.setParticipantes(new ArrayList<>(sembrados));
        fase.setCuadro(GeneradorFixture.cuadroDesdeSiembra(sembrados));
        fase.setConReinicio(conReinicio);
        return fase;
    }

    // =====================================================================
    //  TABLAS
    // =====================================================================
    public ArrayList<FilaTabla> tablaDeFase(int indiceFase) {
        Fase fase = campeonato.getFases().get(indiceFase);
        return tabla.calcular(campeonato.partidosDeFase(indiceFase), fase.getParticipantes());
    }

    public ArrayList<FilaTabla> tablaDeGrupo(int indiceFase, int indiceGrupo) {
        Fase fase = campeonato.getFases().get(indiceFase);
        String letra = Fase.letraGrupo(indiceGrupo);
        ArrayList<Partido> partidosGrupo = new ArrayList<>();
        for (Partido p : campeonato.partidosDeFase(indiceFase)) {
            if (letra.equals(p.getGrupo())) {
                partidosGrupo.add(p);
            }
        }
        ArrayList<FilaTabla> filas = tabla.calcular(partidosGrupo, fase.getGrupos().get(indiceGrupo));
        for (FilaTabla f : filas) {
            f.setGrupo(letra);
        }
        return filas;
    }

    public static ArrayList<Integer> ids(List<FilaTabla> filas) {
        ArrayList<Integer> ids = new ArrayList<>();
        for (FilaTabla f : filas) {
            ids.add(f.getIdEquipo());
        }
        return ids;
    }

    public int ganadorDeFase(int indiceFase) {
        Fase fase = campeonato.getFases().get(indiceFase);
        switch (fase.getTipo()) {
            case LIGA:
            case SUIZO:
                return tablaDeFase(indiceFase).get(0).getIdEquipo();
            case GRUPOS:
                return tablaDeGrupo(indiceFase, 0).get(0).getIdEquipo();
            case ELIMINATORIA:
                return ganadorEliminatoria(fase, indiceFase);
            case DOBLE_ELIMINACION:
                return campeonDoble(fase, indiceFase);
            default:
                return -1;
        }
    }

    // =====================================================================
    //  TODOS CONTRA TODOS
    // =====================================================================
    private void generarTodosContraTodos(int indiceFase, List<Integer> ids, String grupo, boolean idaYVuelta) {
        ArrayList<ArrayList<int[]>> jornadas = GeneradorFixture.todosContraTodos(ids, idaYVuelta);
        for (int j = 0; j < jornadas.size(); j++) {
            for (int[] cruce : jornadas.get(j)) {
                Partido p = nuevoPartido(indiceFase, cruce[0], cruce[1], j + 1, "Jornada " + (j + 1));
                p.setGrupo(grupo);
            }
        }
    }

    // =====================================================================
    //  ELIMINACION DIRECTA
    // =====================================================================
    public static String claveSerie(int indiceFase, int ronda, int numero) {
        return "F" + indiceFase + "-R" + ronda + "-S" + numero;
    }

    /**
     * Equipos que juegan la ronda indicada, en orden de llave
     * (posiciones 0-1 se enfrentan, 2-3 se enfrentan...).
     */
    public ArrayList<Integer> entrantesDeRonda(Fase fase, int indiceFase, int ronda) {
        if (ronda <= 1) {
            return new ArrayList<>(fase.getCuadro());
        }
        return ganadoresDeRonda(fase, indiceFase, ronda - 1);
    }

    /**
     * Ganadores de cada llave de la ronda (-1 si esa llave aun no se define).
     */
    public ArrayList<Integer> ganadoresDeRonda(Fase fase, int indiceFase, int ronda) {
        ArrayList<Integer> entrantes = entrantesDeRonda(fase, indiceFase, ronda);
        ArrayList<Integer> ganadores = new ArrayList<>();
        boolean esFinal = entrantes.size() == 2;
        for (int i = 0; i + 1 < entrantes.size(); i += 2) {
            int a = entrantes.get(i);
            int b = entrantes.get(i + 1);
            if (a == Fase.LIBRE) {
                ganadores.add(b);
            } else if (b == Fase.LIBRE) {
                ganadores.add(a);
            } else if (a == -1 || b == -1) {
                ganadores.add(-1);
            } else {
                ganadores.add(ganadorSerie(fase, indiceFase, claveSerie(indiceFase, ronda, i / 2), esFinal));
            }
        }
        return ganadores;
    }

    private boolean esFinal(Fase fase, int indiceFase, int ronda) {
        return entrantesDeRonda(fase, indiceFase, ronda).size() == 2;
    }

    public int ganadorEliminatoria(Fase fase, int indiceFase) {
        ArrayList<Integer> entrantes = fase.getCuadro();
        int ronda = 1;
        while (entrantes.size() > 1) {
            entrantes = ganadoresDeRonda(fase, indiceFase, ronda);
            ronda++;
            if (fase.getMaxRondas() > 0 && ronda > fase.getMaxRondas()) {
                break;
            }
        }
        return entrantes.size() == 1 ? entrantes.get(0) : -1;
    }

    public ArrayList<Partido> partidosDeSerie(String clave) {
        ArrayList<Partido> serie = new ArrayList<>();
        for (Partido p : campeonato.getPartidos()) {
            if (clave.equals(p.getSerie())) {
                serie.add(p);
            }
        }
        serie.sort((a, b) -> Integer.compare(a.getPierna(), b.getPierna()));
        return serie;
    }

    /**
     * Ganador de una llave: marcador global, luego penales del ultimo
     * partido y, si la fase lo permite, ventaja deportiva del mejor sembrado.
     */
    public int ganadorSerie(Fase fase, int indiceFase, String clave, boolean esFinal) {
        ArrayList<Partido> serie = partidosDeSerie(clave);
        if (serie.isEmpty()) {
            return -1;
        }
        for (Partido p : serie) {
            if (!p.isJugado()) {
                return -1;
            }
        }
        int a = serie.get(0).getIdLocal();
        int b = serie.get(0).getIdVisitante();
        int golesA = 0;
        int golesB = 0;
        for (Partido p : serie) {
            golesA += p.getGolesDe(a);
            golesB += p.getGolesDe(b);
        }
        if (golesA != golesB) {
            return golesA > golesB ? a : b;
        }
        Partido ultimo = serie.get(serie.size() - 1);
        if (ultimo.tienePenales() && ultimo.getPenalesLocal() != ultimo.getPenalesVisitante()) {
            return ultimo.getPenalesLocal() > ultimo.getPenalesVisitante() ? ultimo.getIdLocal() : ultimo.getIdVisitante();
        }
        if (fase.isVentajaDeportiva() && !esFinal) {
            return mejorSembrado(fase, a, b);
        }
        return -1;
    }

    /**
     * Indica si al guardar este partido hay que pedir penales: partido
     * eliminatorio unico empatado, o vuelta con el global empatado.
     */
    public boolean requierePenales(Partido p, int golesLocal, int golesVisitante) {
        if (!p.isEliminatorio()) {
            return false;
        }
        Fase fase = campeonato.getFases().get(p.getIndiceFase());
        if (p.getPierna() == 1) {
            return false;
        }
        if (p.getPierna() == 2) {
            ArrayList<Partido> serie = partidosDeSerie(p.getSerie());
            Partido ida = serie.get(0);
            if (!ida.isJugado()) {
                return false;
            }
            int globalLocal = golesLocal + ida.getGolesDe(p.getIdLocal());
            int globalVisitante = golesVisitante + ida.getGolesDe(p.getIdVisitante());
            if (globalLocal != globalVisitante) {
                return false;
            }
        } else if (golesLocal != golesVisitante) {
            return false;
        }
        boolean esFinal = fase.getTipo() == TipoFase.ELIMINATORIA && esFinal(fase, p.getIndiceFase(), p.getRonda());
        return !(fase.isVentajaDeportiva() && !esFinal);
    }

    private int mejorSembrado(Fase fase, int a, int b) {
        int posA = fase.getParticipantes().indexOf(a);
        int posB = fase.getParticipantes().indexOf(b);
        if (posA == -1) {
            return b;
        }
        if (posB == -1) {
            return a;
        }
        return posA < posB ? a : b;
    }

    private void generarRondaEliminatoria(Fase fase, int indiceFase, int ronda) {
        ArrayList<Integer> entrantes = entrantesDeRonda(fase, indiceFase, ronda);
        int cantidad = entrantes.size();
        String nombre = fase.getMaxRondas() == 1 ? fase.getNombre() : GeneradorFixture.nombreRonda(cantidad);
        boolean idaYVuelta = fase.isIdaYVuelta() && !(cantidad == 2 && fase.isFinalUnica());

        for (int i = 0; i + 1 < cantidad; i += 2) {
            int a = entrantes.get(i);
            int b = entrantes.get(i + 1);
            if (a == Fase.LIBRE || b == Fase.LIBRE) {
                continue;
            }
            int mejor = mejorSembrado(fase, a, b);
            int peor = (mejor == a) ? b : a;
            String clave = claveSerie(indiceFase, ronda, i / 2);
            if (idaYVuelta) {
                // el mejor sembrado define de local en la vuelta
                Partido ida = nuevoPartido(indiceFase, peor, mejor, ronda, nombre + " (ida)");
                ida.setPierna(1);
                Partido vuelta = nuevoPartido(indiceFase, mejor, peor, ronda, nombre + " (vuelta)");
                vuelta.setPierna(2);
                for (Partido p : new Partido[]{ida, vuelta}) {
                    p.setSerie(clave);
                    p.setEliminatorio(true);
                }
            } else {
                Partido unico = nuevoPartido(indiceFase, mejor, peor, ronda, nombre);
                unico.setSerie(clave);
                unico.setEliminatorio(true);
            }
        }
    }

    // =====================================================================
    //  DOBLE ELIMINACION
    // =====================================================================
    private HashMap<Integer, Integer> derrotas(Fase fase, int indiceFase) {
        HashMap<Integer, Integer> derrotas = new HashMap<>();
        for (int id : fase.getParticipantes()) {
            derrotas.put(id, 0);
        }
        for (Partido p : campeonato.partidosDeFase(indiceFase)) {
            int ganador = p.getGanador();
            if (ganador != -1) {
                int perdedor = p.getRival(ganador);
                derrotas.put(perdedor, derrotas.get(perdedor) + 1);
            }
        }
        return derrotas;
    }

    /**
     * Ronda en la que cada equipo perdio por primera vez (para ordenar la
     * llave de perdedores).
     */
    private HashMap<Integer, Integer> rondaDePrimeraDerrota(int indiceFase) {
        HashMap<Integer, Integer> ronda = new HashMap<>();
        for (Partido p : campeonato.partidosDeFase(indiceFase)) {
            int ganador = p.getGanador();
            if (ganador != -1) {
                int perdedor = p.getRival(ganador);
                if (!ronda.containsKey(perdedor) || ronda.get(perdedor) > p.getRonda()) {
                    ronda.put(perdedor, p.getRonda());
                }
            }
        }
        return ronda;
    }

    /**
     * Lista los equipos vivos de una llave: perdidas = 0 llave de ganadores,
     * perdidas = 1 llave de perdedores.
     */
    public ArrayList<Integer> vivosDoble(Fase fase, int indiceFase, int perdidas) {
        HashMap<Integer, Integer> derrotas = derrotas(fase, indiceFase);
        ArrayList<Integer> lista = new ArrayList<>();
        for (int id : fase.getCuadro()) {
            if (id == Fase.LIBRE) {
                continue;
            }
            Equipo e = campeonato.buscarEquipo(id);
            if (e != null && !e.isRetirado() && derrotas.get(id) == perdidas) {
                lista.add(id);
            }
        }
        return lista;
    }

    public int campeonDoble(Fase fase, int indiceFase) {
        ArrayList<Integer> ganadores = vivosDoble(fase, indiceFase, 0);
        ArrayList<Integer> perdedores = vivosDoble(fase, indiceFase, 1);
        if (ganadores.size() + perdedores.size() == 1) {
            return ganadores.isEmpty() ? perdedores.get(0) : ganadores.get(0);
        }
        if (!fase.isConReinicio()) {
            for (Partido p : campeonato.partidosDeFase(indiceFase)) {
                if (LLAVE_GRAN_FINAL.equals(p.getLlave()) && p.getGanador() != -1) {
                    return p.getGanador();
                }
            }
        }
        return -1;
    }

    private void generarRondaDoble(Fase fase, int indiceFase, int ronda) {
        ArrayList<Integer> ganadores = vivosDoble(fase, indiceFase, 0);
        ArrayList<Integer> perdedores = vivosDoble(fase, indiceFase, 1);

        if (ganadores.size() == 1 && perdedores.size() == 1) {
            crearPartidoDoble(indiceFase, ronda, ganadores.get(0), perdedores.get(0), "Gran final", LLAVE_GRAN_FINAL);
            return;
        }
        if (ganadores.isEmpty() && perdedores.size() == 2) {
            crearPartidoDoble(indiceFase, ronda, perdedores.get(0), perdedores.get(1),
                    "Gran final (partido de desempate)", LLAVE_GRAN_FINAL);
            return;
        }

        // Llave de ganadores
        if (ganadores.size() > 1) {
            String nombre = ganadores.size() == 2 ? "Final de ganadores" : "Ronda " + ronda + " - Ganadores";
            ArrayList<Integer> orden = (ronda == 1) ? fase.getCuadro() : ganadores;
            for (int i = 0; i + 1 < orden.size(); i += 2) {
                int a = orden.get(i);
                int b = orden.get(i + 1);
                if (a != Fase.LIBRE && b != Fase.LIBRE) {
                    crearPartidoDoble(indiceFase, ronda, a, b, nombre, LLAVE_GANADORES);
                }
            }
        }

        // Llave de perdedores: los que cayeron antes contra los que acaban de caer
        if (perdedores.size() > 1) {
            HashMap<Integer, Integer> caida = rondaDePrimeraDerrota(indiceFase);
            ArrayList<Integer> cuadro = fase.getCuadro();
            perdedores.sort((x, y) -> {
                int c = Integer.compare(caida.get(x), caida.get(y));
                return c != 0 ? c : Integer.compare(cuadro.indexOf(x), cuadro.indexOf(y));
            });
            String nombre = (perdedores.size() == 2 && ganadores.size() <= 1)
                    ? "Final de perdedores" : "Ronda " + ronda + " - Perdedores";
            int n = perdedores.size();
            for (int i = 0; i < n / 2; i++) {
                crearPartidoDoble(indiceFase, ronda, perdedores.get(i), perdedores.get(n - 1 - i), nombre, LLAVE_PERDEDORES);
            }
        }
    }

    private void crearPartidoDoble(int indiceFase, int ronda, int local, int visitante, String nombre, String llave) {
        Partido p = nuevoPartido(indiceFase, local, visitante, ronda, nombre);
        p.setLlave(llave);
        p.setEliminatorio(true);
    }

    // =====================================================================
    //  SISTEMA SUIZO
    // =====================================================================
    private void generarRondaSuiza(Fase fase, int indiceFase, int ronda) {
        ArrayList<Integer> activos = new ArrayList<>();
        for (int id : fase.getParticipantes()) {
            if (!campeonato.buscarEquipo(id).isRetirado()) {
                activos.add(id);
            }
        }
        ArrayList<Partido> partidosFase = campeonato.partidosDeFase(indiceFase);
        ArrayList<Integer> orden = (ronda == 1) ? activos : ids(tabla.calcular(partidosFase, activos));

        HashMap<Integer, Integer> jugados = new HashMap<>();
        HashMap<Integer, Integer> localias = new HashMap<>();
        HashSet<String> enfrentados = new HashSet<>();
        for (int id : orden) {
            jugados.put(id, 0);
            localias.put(id, 0);
        }
        for (Partido p : partidosFase) {
            jugados.merge(p.getIdLocal(), 1, Integer::sum);
            jugados.merge(p.getIdVisitante(), 1, Integer::sum);
            localias.merge(p.getIdLocal(), 1, Integer::sum);
            enfrentados.add(claveCruce(p.getIdLocal(), p.getIdVisitante()));
        }

        // Numero impar: descansa el peor ubicado que todavia no haya descansado
        if (orden.size() % 2 == 1) {
            int descansa = orden.get(orden.size() - 1);
            for (int i = orden.size() - 1; i >= 0; i--) {
                int id = orden.get(i);
                if (jugados.get(id) == ronda - 1) {
                    descansa = id;
                    break;
                }
            }
            orden.remove(Integer.valueOf(descansa));
        }

        ArrayList<int[]> cruces = null;
        if (ronda == 1) {
            // primera ronda: la mitad de arriba del sorteo contra la de abajo
            cruces = new ArrayList<>();
            int mitad = orden.size() / 2;
            for (int i = 0; i < mitad; i++) {
                cruces.add(new int[]{orden.get(i), orden.get(i + mitad)});
            }
        } else {
            contadorBusqueda = 0;
            cruces = emparejarSinRepetir(orden, enfrentados);
            if (cruces == null) {
                // no hay forma de evitar repetir rival: se emparejan en orden
                cruces = new ArrayList<>();
                for (int i = 0; i + 1 < orden.size(); i += 2) {
                    cruces.add(new int[]{orden.get(i), orden.get(i + 1)});
                }
            }
        }

        for (int[] cruce : cruces) {
            int local = cruce[0];
            int visitante = cruce[1];
            if (localias.get(visitante) < localias.get(local)) {
                local = cruce[1];
                visitante = cruce[0];
            }
            nuevoPartido(indiceFase, local, visitante, ronda, "Ronda " + ronda);
        }
    }

    private int contadorBusqueda;

    /**
     * Empareja a cada equipo con el mejor ubicado posible que no haya
     * enfrentado antes (busqueda con retroceso / backtracking).
     */
    private ArrayList<int[]> emparejarSinRepetir(ArrayList<Integer> pendientes, HashSet<String> enfrentados) {
        if (pendientes.isEmpty()) {
            return new ArrayList<>();
        }
        if (++contadorBusqueda > 200000) {
            return null;
        }
        int a = pendientes.get(0);
        for (int j = 1; j < pendientes.size(); j++) {
            int b = pendientes.get(j);
            if (enfrentados.contains(claveCruce(a, b))) {
                continue;
            }
            ArrayList<Integer> resto = new ArrayList<>(pendientes);
            resto.remove(Integer.valueOf(a));
            resto.remove(Integer.valueOf(b));
            ArrayList<int[]> solucion = emparejarSinRepetir(resto, enfrentados);
            if (solucion != null) {
                solucion.add(0, new int[]{a, b});
                return solucion;
            }
        }
        return null;
    }

    private static String claveCruce(int a, int b) {
        return Math.min(a, b) + "-" + Math.max(a, b);
    }

    // =====================================================================
    //  EDICION DE PARTIDOS Y EQUIPOS
    // =====================================================================
    /**
     * "Huella" de lo que decide un partido: el orden de su tabla o el
     * ganador de su llave. Si cambia, las rondas generadas despues quedan mal.
     */
    private String huella(Partido p) {
        int indice = p.getIndiceFase();
        Fase fase = campeonato.getFases().get(indice);
        switch (fase.getTipo()) {
            case LIGA:
            case SUIZO:
                return ids(tablaDeFase(indice)).toString();
            case GRUPOS:
                String texto = "";
                for (int g = 0; g < fase.getGrupos().size(); g++) {
                    texto += ids(tablaDeGrupo(indice, g)).toString();
                }
                return texto;
            case ELIMINATORIA:
                return "" + ganadorSerie(fase, indice, p.getSerie(), esFinal(fase, indice, p.getRonda()));
            default:
                return "" + p.getGanador();
        }
    }

    /**
     * true si aplicar los datos nuevos cambia quien avanza y ya existen
     * rondas generadas despues de este partido.
     */
    public boolean afectaRondasPosteriores(Partido partido, Partido datosNuevos) {
        if (partido.getPaso() >= campeonato.getPasoActual()) {
            return false;
        }
        Partido respaldo = new Partido();
        respaldo.copiarDatosDe(partido);
        String antes = huella(partido);
        partido.copiarDatosDe(datosNuevos);
        String despues = huella(partido);
        partido.copiarDatosDe(respaldo);
        return !antes.equals(despues);
    }

    /**
     * Guarda los cambios de un partido. Si deshacerPosteriores es true se
     * borran las rondas/fases generadas despues para volver a generarlas.
     */
    public void aplicarEdicion(Partido partido, Partido datosNuevos, boolean deshacerPosteriores) {
        if (deshacerPosteriores) {
            deshacerDesdePaso(partido.getPaso());
        }
        partido.copiarDatosDe(datosNuevos);
        if (campeonato.isTerminado() && partido.getIndiceFase() == campeonato.getFases().size() - 1) {
            campeonato.setIdCampeon(Campeonato.SIN_CAMPEON); // se confirma de nuevo con "Avanzar"
        }
    }

    public void deshacerDesdePaso(int paso) {
        campeonato.getPartidos().removeIf(p -> p.getPaso() > paso);
        campeonato.getFases().removeIf(f -> f.getPasoInicio() > paso);
        campeonato.setPasoActual(paso);
        campeonato.setIdCampeon(Campeonato.SIN_CAMPEON);
    }

    public static void darWalkover(Partido p, int idGanador, String motivo) {
        darWalkover(p, p.getIdLocal(), idGanador, motivo);
    }

    /**
     * W.O. 3-0 a favor de {@code idGanador}. Se pasa el id del local aparte porque el editor trabaja
     * sobre una COPIA de los datos del partido que no trae los ids de los equipos (antes se comparaba
     * con el id 0 de la copia y los 3 goles siempre iban al visitante).
     */
    public static void darWalkover(Partido p, int idLocal, int idGanador, String motivo) {
        p.reiniciarResultado();
        p.setEstado(EstadoPartido.JUGADO);
        p.setWalkover(true);
        if (idLocal == idGanador) {
            p.setGolesLocal(3);
        } else {
            p.setGolesVisitante(3);
        }
        p.setObservaciones(motivo);
    }

    /**
     * Antes de iniciar el equipo se borra. Ya iniciado, el equipo se retira:
     * sus partidos pendientes de la fase actual se dan por perdidos 0-3 (W.O.)
     * y no se tiene en cuenta para nuevos emparejamientos.
     */
    public String retirarEquipo(Equipo equipo) {
        if (!campeonato.isIniciado()) {
            campeonato.getEquipos().remove(equipo);
            return "Equipo '" + equipo.getNombreEquipo() + "' eliminado.";
        }
        equipo.setRetirado(true);
        int indiceActual = campeonato.getFases().size() - 1;
        int cantidad = 0;
        for (Partido p : campeonato.getPartidos()) {
            if (!p.isJugado() && p.involucra(equipo.getId()) && p.getIndiceFase() == indiceActual) {
                darWalkover(p, p.getRival(equipo.getId()), "W.O. por retiro de " + equipo.getNombreEquipo());
                cantidad++;
            }
        }
        return "Equipo '" + equipo.getNombreEquipo() + "' retirado. " + (cantidad == 0 ? "No tenía partidos pendientes."
                : (cantidad == 1 ? "1 partido pendiente se dio" : cantidad + " partidos pendientes se dieron")
                        + " por W.O. 3-0 a favor del rival.");
    }
}
