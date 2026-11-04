package alphasport.service.formatos;

import java.util.ArrayList;
import alphasport.service.MotorCampeonato;
import alphasport.model.Campeonato;
import alphasport.model.ConfiguracionTorneo;
import alphasport.model.Fase;

/**
 * Sistema suizo (fase de liga de la Champions desde 2024).
 * Todos en una tabla, pero cada equipo juega un numero limitado de rondas.
 * Ronda 1: la mitad de arriba del sorteo contra la otra mitad. Desde la
 * ronda 2 se cruzan equipos con puntaje parecido que no se hayan enfrentado.
 * Al final: los primeros pasan directo y la zona media juega repechaje.
 */
public class FormatoSuizo extends FormatoCampeonato {

    @Override
    public String validar(Campeonato campeonato) {
        ConfiguracionTorneo cfg = campeonato.getConfiguracion();
        int n = campeonato.getEquiposActivos().size();
        int directos = cfg.getDirectosSuizo();
        int repechaje = cfg.getRepechajeSuizo();
        if (n < 3) {
            return "El sistema suizo necesita al menos 3 equipos.";
        }
        if (cfg.getRondasSuizo() < 1) {
            return "Debe haber al menos 1 ronda.";
        }
        if (directos < 0 || repechaje < 0 || repechaje % 2 != 0) {
            return "Los equipos del repechaje deben ser un numero par.";
        }
        if (directos + repechaje > n) {
            return "Directos + repechaje (" + (directos + repechaje) + ") supera los " + n + " equipos.";
        }
        if (directos + repechaje / 2 < 2) {
            return "A la fase final deben llegar al menos 2 equipos.";
        }
        return null;
    }

    @Override
    public Fase crearPrimeraFase(Campeonato campeonato, MotorCampeonato motor) {
        return motor.crearFaseSuiza("Fase de liga (suizo)", motor.sembrar(campeonato.getEquiposActivos()),
                campeonato.getConfiguracion().getRondasSuizo());
    }

    @Override
    public Fase crearSiguienteFase(Campeonato campeonato, MotorCampeonato motor, int indiceFaseTerminada) {
        ConfiguracionTorneo cfg = campeonato.getConfiguracion();
        int directos = cfg.getDirectosSuizo();
        int repechaje = cfg.getRepechajeSuizo();
        ArrayList<Integer> tabla = MotorCampeonato.ids(motor.tablaDeFase(0));

        if (indiceFaseTerminada == 0 && repechaje > 0) {
            ArrayList<Integer> zonaMedia = new ArrayList<>(tabla.subList(directos, directos + repechaje));
            return motor.crearFaseEliminatoria("Repechaje", zonaMedia, cfg.isEliminatoriaIdaYVuelta(), false, false, 1);
        }
        if (indiceFaseTerminada == 0 || (indiceFaseTerminada == 1 && repechaje > 0)) {
            ArrayList<Integer> sembrados = new ArrayList<>(tabla.subList(0, directos));
            if (repechaje > 0) {
                Fase faseRepechaje = campeonato.getFases().get(1);
                ArrayList<Integer> ganadores = motor.ganadoresDeRonda(faseRepechaje, 1, 1);
                // los ganadores del repechaje se siembran segun su puesto en la tabla
                ganadores.sort((a, b) -> Integer.compare(tabla.indexOf(a), tabla.indexOf(b)));
                sembrados.addAll(ganadores);
            }
            return motor.crearFaseEliminatoria("Fase final", sembrados, cfg.isEliminatoriaIdaYVuelta(),
                    cfg.isFinalUnica(), false, 0);
        }
        return null;
    }
}
