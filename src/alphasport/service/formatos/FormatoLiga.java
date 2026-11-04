package alphasport.service.formatos;

import alphasport.service.MotorCampeonato;
import alphasport.model.Campeonato;
import alphasport.model.Fase;

/**
 * Todos contra todos a una o dos vueltas. El campeon es el primero de la tabla.
 */
public class FormatoLiga extends FormatoCampeonato {

    @Override
    public String validar(Campeonato campeonato) {
        return null;
    }

    @Override
    public Fase crearPrimeraFase(Campeonato campeonato, MotorCampeonato motor) {
        boolean idaYVuelta = campeonato.getConfiguracion().isIdaYVuelta();
        return motor.crearFaseLiga(idaYVuelta ? "Liga (ida y vuelta)" : "Liga (una vuelta)",
                motor.sembrar(campeonato.getEquiposActivos()), idaYVuelta);
    }

    @Override
    public Fase crearSiguienteFase(Campeonato campeonato, MotorCampeonato motor, int indiceFaseTerminada) {
        return null;
    }
}
