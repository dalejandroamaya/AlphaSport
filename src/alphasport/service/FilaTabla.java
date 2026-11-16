package alphasport.service;

import alphasport.model.Equipo;

/**
 * Una fila de la tabla de posiciones. Se calcula a partir de los partidos.
 */
public class FilaTabla {

    private final Equipo equipo;
    private String grupo = "";
    int jugados;
    int ganados;
    int empatados;
    int perdidos;
    int golesFavor;
    int golesContra;
    int puntos;
    int amarillas;
    int rojas;

    public FilaTabla(Equipo equipo) {
        this.equipo = equipo;
    }

    public Equipo getEquipo() {
        return equipo;
    }

    public int getIdEquipo() {
        return equipo.getId();
    }

    public String getGrupo() {
        return grupo;
    }

    public void setGrupo(String grupo) {
        this.grupo = grupo;
    }

    public int getJugados() {
        return jugados;
    }

    public int getGanados() {
        return ganados;
    }

    public int getEmpatados() {
        return empatados;
    }

    public int getPerdidos() {
        return perdidos;
    }

    public int getGolesFavor() {
        return golesFavor;
    }

    public int getGolesContra() {
        return golesContra;
    }

    public int getDiferenciaGoles() {
        return golesFavor - golesContra;
    }

    public int getPuntos() {
        return puntos;
    }

    public int getAmarillas() {
        return amarillas;
    }

    public int getRojas() {
        return rojas;
    }

    /**
     * Puntos de juego limpio: menos es mejor.
     */
    public int getPuntosFairPlay() {
        return amarillas + 3 * rojas;
    }
}
