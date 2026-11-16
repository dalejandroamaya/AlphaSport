package alphasport.service.formatos;

import alphasport.service.MotorCampeonato;
import alphasport.model.Campeonato;
import alphasport.model.Fase;
import alphasport.model.TipoFormato;

/**
 * Cada formato de campeonato define que fases tiene y como se pasa de una a
 * otra. Las subclases implementan esas reglas (polimorfismo): el motor solo
 * llama a estos metodos sin saber de que formato se trata.
 */
public abstract class FormatoCampeonato {

    /**
     * Revisa que la configuracion y la cantidad de equipos sean validas.
     * Devuelve un mensaje de error o null si todo esta bien.
     */
    public abstract String validar(Campeonato campeonato);

    public abstract Fase crearPrimeraFase(Campeonato campeonato, MotorCampeonato motor);

    /**
     * Crea la fase que sigue a la fase terminada, o devuelve null si esa era
     * la ultima (el ganador de esa fase es el campeon).
     */
    public abstract Fase crearSiguienteFase(Campeonato campeonato, MotorCampeonato motor, int indiceFaseTerminada);

    /**
     * true si el formato puede llegar a una fase de llaves (eliminacion directa o doble).
     * Si no, la interfaz no muestra la seccion «Llaves». Por defecto todos la usan.
     */
    public boolean usaLlaves() {
        return true;
    }

    public static FormatoCampeonato crear(TipoFormato tipo) {
        switch (tipo) {
            case LIGA:
                return new FormatoLiga();
            case ELIMINACION_DIRECTA:
                return new FormatoEliminacionDirecta();
            case GRUPOS_Y_ELIMINATORIA:
                return new FormatoGruposEliminatoria();
            case TORNEO_CORTO:
                return new FormatoTorneoCorto();
            case SUIZO:
                return new FormatoSuizo();
            case DOBLE_ELIMINACION:
                return new FormatoDobleEliminacion();
            default:
                throw new IllegalArgumentException("Formato desconocido: " + tipo);
        }
    }
}
