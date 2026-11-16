package alphasport.model;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Una etapa del campeonato: fase regular, fase de grupos, llave, etc.
 * Los partidos guardan el indice de la fase a la que pertenecen.
 */
public class Fase implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int LIBRE = -2; // posicion vacia en una llave (el rival pasa directo)

    private String nombre;
    private TipoFase tipo;
    private int pasoInicio;
    private boolean idaYVuelta;
    private boolean finalUnica = true;
    private boolean ventajaDeportiva;
    private int maxRondas;      // eliminatoria: 0 = hasta que quede un ganador
    private int rondasSuizo;
    private boolean conReinicio;

    // Equipos de la fase ordenados por siembra (el primero es el mejor)
    private ArrayList<Integer> participantes = new ArrayList<>();
    // Fase de grupos: ids de los equipos de cada grupo (A, B, C...)
    private ArrayList<ArrayList<Integer>> grupos = new ArrayList<>();
    // Eliminatorias: orden de la llave en la primera ronda (LIBRE = sin rival)
    private ArrayList<Integer> cuadro = new ArrayList<>();
    // true si los grupos son divisiones de una liga (Liga A, Liga B...) y no grupos de un sorteo
    private boolean divisiones;

    public Fase() {
    }

    public Fase(String nombre, TipoFase tipo) {
        this.nombre = nombre;
        this.tipo = tipo;
    }

    public static String letraGrupo(int indice) {
        return String.valueOf((char) ('A' + indice));
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public TipoFase getTipo() {
        return tipo;
    }

    public void setTipo(TipoFase tipo) {
        this.tipo = tipo;
    }

    public int getPasoInicio() {
        return pasoInicio;
    }

    public void setPasoInicio(int pasoInicio) {
        this.pasoInicio = pasoInicio;
    }

    public boolean isIdaYVuelta() {
        return idaYVuelta;
    }

    public void setIdaYVuelta(boolean idaYVuelta) {
        this.idaYVuelta = idaYVuelta;
    }

    public boolean isFinalUnica() {
        return finalUnica;
    }

    public void setFinalUnica(boolean finalUnica) {
        this.finalUnica = finalUnica;
    }

    public boolean isVentajaDeportiva() {
        return ventajaDeportiva;
    }

    public void setVentajaDeportiva(boolean ventajaDeportiva) {
        this.ventajaDeportiva = ventajaDeportiva;
    }

    public int getMaxRondas() {
        return maxRondas;
    }

    public void setMaxRondas(int maxRondas) {
        this.maxRondas = maxRondas;
    }

    public int getRondasSuizo() {
        return rondasSuizo;
    }

    public void setRondasSuizo(int rondasSuizo) {
        this.rondasSuizo = rondasSuizo;
    }

    public boolean isConReinicio() {
        return conReinicio;
    }

    public void setConReinicio(boolean conReinicio) {
        this.conReinicio = conReinicio;
    }

    public ArrayList<Integer> getParticipantes() {
        return participantes;
    }

    public void setParticipantes(ArrayList<Integer> participantes) {
        this.participantes = participantes;
    }

    public ArrayList<ArrayList<Integer>> getGrupos() {
        return grupos;
    }

    public void setGrupos(ArrayList<ArrayList<Integer>> grupos) {
        this.grupos = grupos;
    }

    public ArrayList<Integer> getCuadro() {
        return cuadro;
    }

    public void setCuadro(ArrayList<Integer> cuadro) {
        this.cuadro = cuadro;
    }

    public boolean isDivisiones() {
        return divisiones;
    }

    public void setDivisiones(boolean divisiones) {
        this.divisiones = divisiones;
    }

    /** Nombre del grupo para mostrar: «Liga B» en una liga con divisiones, «Grupo B» en los demas formatos. */
    public String nombreGrupo(int indice) {
        return (divisiones ? "Liga " : "Grupo ") + letraGrupo(indice);
    }

    @Override
    public String toString() {
        return nombre;
    }
}
