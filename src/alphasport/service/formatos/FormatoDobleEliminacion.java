package alphasport.service.formatos;

import alphasport.service.MotorCampeonato;
import alphasport.model.Campeonato;
import alphasport.model.Fase;

/**
 * Doble eliminacion: llave de ganadores y llave de perdedores. Un equipo
 * queda fuera al perder dos veces. Todos los partidos son a partido unico.
 */
public class FormatoDobleEliminacion extends FormatoCampeonato {

    @Override
    public String validar(Campeonato campeonato) {
        if (campeonato.getEquiposActivos().size() < 3) {
            return "La doble eliminacion necesita al menos 3 equipos.";
        }
        return null;
    }

    @Override
    public Fase crearPrimeraFase(Campeonato campeonato, MotorCampeonato motor) {
        return motor.crearFaseDobleEliminacion("Doble eliminacion", motor.sembrar(campeonato.getEquiposActivos()),
                campeonato.getConfiguracion().isGranFinalConReinicio());
    }

    @Override
    public Fase crearSiguienteFase(Campeonato campeonato, MotorCampeonato motor, int indiceFaseTerminada) {
        return null;
    }
}
