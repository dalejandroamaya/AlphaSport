package alphasport.service.formatos;

import alphasport.service.MotorCampeonato;
import alphasport.model.Campeonato;
import alphasport.model.ConfiguracionTorneo;
import alphasport.model.Fase;

/**
 * Llave de eliminacion directa. Si los equipos no son potencia de 2
 * (4, 8, 16, 32...) los mejores sembrados pasan directo a la segunda ronda.
 */
public class FormatoEliminacionDirecta extends FormatoCampeonato {

    @Override
    public String validar(Campeonato campeonato) {
        return null;
    }

    @Override
    public Fase crearPrimeraFase(Campeonato campeonato, MotorCampeonato motor) {
        ConfiguracionTorneo cfg = campeonato.getConfiguracion();
        return motor.crearFaseEliminatoria("Eliminacion directa", motor.sembrar(campeonato.getEquiposActivos()),
                cfg.isEliminatoriaIdaYVuelta(), cfg.isFinalUnica(), false, 0);
    }

    @Override
    public Fase crearSiguienteFase(Campeonato campeonato, MotorCampeonato motor, int indiceFaseTerminada) {
        return null;
    }
}
