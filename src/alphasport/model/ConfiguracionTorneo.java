package alphasport.model;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Reglas configurables de un campeonato. No todas aplican a todos los
 * formatos; el dialogo de nuevo campeonato solo muestra las que sirven.
 */
public class ConfiguracionTorneo implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String PLAYOFF_LLAVE = "LLAVE";
    public static final String PLAYOFF_CUADRANGULARES = "CUADRANGULARES";

    private int puntosVictoria = 3;
    private int puntosEmpate = 1;

    // Liga, grupos y fase regular
    private boolean idaYVuelta = false;

    // Eliminatorias
    private boolean eliminatoriaIdaYVuelta = false;
    private boolean finalUnica = true;
    private boolean sorteoAleatorio = true;

    // Grupos + eliminatoria
    private int tamanoGrupo = 4;
    private int clasificadosPorGrupo = 2;

    // Torneo corto
    private int clasificadosPlayoff = 8;
    private String tipoPlayoff = PLAYOFF_LLAVE;
    private boolean ventajaDeportiva = true;

    // Sistema suizo
    private int rondasSuizo = 8;
    private int directosSuizo = 8;
    private int repechajeSuizo = 16;

    // Doble eliminacion
    private boolean granFinalConReinicio = true;

    private ArrayList<CriterioDesempate> criterios = new ArrayList<>();

    public ConfiguracionTorneo() {
        criterios.add(CriterioDesempate.DIFERENCIA_GOLES);
        criterios.add(CriterioDesempate.GOLES_A_FAVOR);
        criterios.add(CriterioDesempate.ENFRENTAMIENTO_DIRECTO);
        criterios.add(CriterioDesempate.FAIR_PLAY);
        criterios.add(CriterioDesempate.SORTEO);
    }

    public String resumen(TipoFormato formato) {
        String texto = "Puntos: victoria " + puntosVictoria + ", empate " + puntosEmpate + "\n";
        switch (formato) {
            case LIGA:
                texto += idaYVuelta ? "A ida y vuelta (doble round-robin)\n" : "A una sola vuelta\n";
                break;
            case ELIMINACION_DIRECTA:
                texto += textoEliminatoria();
                break;
            case GRUPOS_Y_ELIMINATORIA:
                texto += "Grupos de " + tamanoGrupo + " equipos, clasifican " + clasificadosPorGrupo
                        + " por grupo\n";
                texto += idaYVuelta ? "Grupos a ida y vuelta\n" : "Grupos a una vuelta\n";
                texto += textoEliminatoria();
                break;
            case TORNEO_CORTO:
                texto += idaYVuelta ? "Fase regular a ida y vuelta\n" : "Fase regular a una vuelta\n";
                texto += "Clasifican " + clasificadosPlayoff + " a "
                        + (PLAYOFF_CUADRANGULARES.equals(tipoPlayoff) ? "cuadrangulares" : "liguilla (llave)") + "\n";
                texto += textoEliminatoria();
                if (ventajaDeportiva) {
                    texto += "Empate en el global: avanza el mejor ubicado en la tabla\n";
                }
                break;
            case SUIZO:
                texto += rondasSuizo + " rondas. Pasan directo los " + directosSuizo + " primeros";
                texto += repechajeSuizo > 0 ? "; del " + (directosSuizo + 1) + " al "
                        + (directosSuizo + repechajeSuizo) + " juegan repechaje\n" : "\n";
                texto += textoEliminatoria();
                break;
            case DOBLE_ELIMINACION:
                texto += granFinalConReinicio
                        ? "Gran final con reinicio (si gana el de perdedores se juega otro partido)\n"
                        : "Gran final a un solo partido\n";
                break;
        }
        texto += "Desempate (despues de los puntos):";
        for (int i = 0; i < criterios.size(); i++) {
            texto += "\n   " + (i + 1) + ". " + criterios.get(i).getDescripcion();
        }
        return texto;
    }

    private String textoEliminatoria() {
        String texto = eliminatoriaIdaYVuelta ? "Eliminatorias a ida y vuelta" : "Eliminatorias a partido unico";
        if (eliminatoriaIdaYVuelta) {
            texto += finalUnica ? " (final a partido unico)" : " (final tambien a ida y vuelta)";
        }
        texto += sorteoAleatorio ? "\nCruces por sorteo\n" : "\nCruces segun orden de registro\n";
        return texto;
    }

    // ---------------- getters y setters ----------------
    public int getPuntosVictoria() {
        return puntosVictoria;
    }

    public void setPuntosVictoria(int puntosVictoria) {
        this.puntosVictoria = puntosVictoria;
    }

    public int getPuntosEmpate() {
        return puntosEmpate;
    }

    public void setPuntosEmpate(int puntosEmpate) {
        this.puntosEmpate = puntosEmpate;
    }

    public boolean isIdaYVuelta() {
        return idaYVuelta;
    }

    public void setIdaYVuelta(boolean idaYVuelta) {
        this.idaYVuelta = idaYVuelta;
    }

    public boolean isEliminatoriaIdaYVuelta() {
        return eliminatoriaIdaYVuelta;
    }

    public void setEliminatoriaIdaYVuelta(boolean eliminatoriaIdaYVuelta) {
        this.eliminatoriaIdaYVuelta = eliminatoriaIdaYVuelta;
    }

    public boolean isFinalUnica() {
        return finalUnica;
    }

    public void setFinalUnica(boolean finalUnica) {
        this.finalUnica = finalUnica;
    }

    public boolean isSorteoAleatorio() {
        return sorteoAleatorio;
    }

    public void setSorteoAleatorio(boolean sorteoAleatorio) {
        this.sorteoAleatorio = sorteoAleatorio;
    }

    public int getTamanoGrupo() {
        return tamanoGrupo;
    }

    public void setTamanoGrupo(int tamanoGrupo) {
        this.tamanoGrupo = tamanoGrupo;
    }

    public int getClasificadosPorGrupo() {
        return clasificadosPorGrupo;
    }

    public void setClasificadosPorGrupo(int clasificadosPorGrupo) {
        this.clasificadosPorGrupo = clasificadosPorGrupo;
    }

    public int getClasificadosPlayoff() {
        return clasificadosPlayoff;
    }

    public void setClasificadosPlayoff(int clasificadosPlayoff) {
        this.clasificadosPlayoff = clasificadosPlayoff;
    }

    public String getTipoPlayoff() {
        return tipoPlayoff;
    }

    public void setTipoPlayoff(String tipoPlayoff) {
        this.tipoPlayoff = tipoPlayoff;
    }

    public boolean isVentajaDeportiva() {
        return ventajaDeportiva;
    }

    public void setVentajaDeportiva(boolean ventajaDeportiva) {
        this.ventajaDeportiva = ventajaDeportiva;
    }

    public int getRondasSuizo() {
        return rondasSuizo;
    }

    public void setRondasSuizo(int rondasSuizo) {
        this.rondasSuizo = rondasSuizo;
    }

    public int getDirectosSuizo() {
        return directosSuizo;
    }

    public void setDirectosSuizo(int directosSuizo) {
        this.directosSuizo = directosSuizo;
    }

    public int getRepechajeSuizo() {
        return repechajeSuizo;
    }

    public void setRepechajeSuizo(int repechajeSuizo) {
        this.repechajeSuizo = repechajeSuizo;
    }

    public boolean isGranFinalConReinicio() {
        return granFinalConReinicio;
    }

    public void setGranFinalConReinicio(boolean granFinalConReinicio) {
        this.granFinalConReinicio = granFinalConReinicio;
    }

    public ArrayList<CriterioDesempate> getCriterios() {
        return criterios;
    }

    public void setCriterios(ArrayList<CriterioDesempate> criterios) {
        this.criterios = criterios;
    }
}
