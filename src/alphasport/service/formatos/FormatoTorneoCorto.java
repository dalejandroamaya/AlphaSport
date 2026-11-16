package alphasport.service.formatos;

import java.util.ArrayList;
import alphasport.service.MotorCampeonato;
import alphasport.model.Campeonato;
import alphasport.model.ConfiguracionTorneo;
import alphasport.model.Fase;

/**
 * Torneo corto (Apertura o Clausura): fase regular todos contra todos y
 * luego liguilla de eliminacion directa (1 vs 8, 2 vs 7...) o
 * cuadrangulares semifinales (dos grupos de 4, los ganadores van a la final).
 */
public class FormatoTorneoCorto extends FormatoCampeonato {

    @Override
    public String validar(Campeonato campeonato) {
        ConfiguracionTorneo cfg = campeonato.getConfiguracion();
        int n = campeonato.getEquiposActivos().size();
        int clasificados = cfg.getClasificadosPlayoff();
        if (clasificados < 2 || clasificados > n) {
            return "Los clasificados a la liguilla deben ser entre 2 y " + n + ".";
        }
        if (esCuadrangulares(cfg) && clasificados < 4) {
            return "Los cuadrangulares necesitan al menos 4 clasificados (2 grupos de 2 o mas).";
        }
        return null;
    }

    private boolean esCuadrangulares(ConfiguracionTorneo cfg) {
        return ConfiguracionTorneo.PLAYOFF_CUADRANGULARES.equals(cfg.getTipoPlayoff());
    }

    @Override
    public Fase crearPrimeraFase(Campeonato campeonato, MotorCampeonato motor) {
        ConfiguracionTorneo cfg = campeonato.getConfiguracion();
        return motor.crearFaseLiga("Fase regular", motor.sembrar(campeonato.getEquiposActivos()), cfg.isIdaYVuelta());
    }

    @Override
    public Fase crearSiguienteFase(Campeonato campeonato, MotorCampeonato motor, int indiceFaseTerminada) {
        ConfiguracionTorneo cfg = campeonato.getConfiguracion();
        if (indiceFaseTerminada == 0) {
            ArrayList<Integer> tabla = MotorCampeonato.ids(motor.tablaDeFase(0));
            ArrayList<Integer> clasificados = new ArrayList<>(tabla.subList(0, cfg.getClasificadosPlayoff()));
            if (esCuadrangulares(cfg)) {
                return motor.crearFaseGrupos("Cuadrangulares", motor.repartirEnSerpentina(clasificados, 2),
                        cfg.isEliminatoriaIdaYVuelta());
            }
            return motor.crearFaseEliminatoria("Liguilla", clasificados, cfg.isEliminatoriaIdaYVuelta(),
                    cfg.isFinalUnica(), cfg.isVentajaDeportiva(), 0);
        }
        if (indiceFaseTerminada == 1 && esCuadrangulares(cfg)) {
            ArrayList<Integer> finalistas = new ArrayList<>();
            finalistas.add(motor.tablaDeGrupo(1, 0).get(0).getIdEquipo());
            finalistas.add(motor.tablaDeGrupo(1, 1).get(0).getIdEquipo());
            return motor.crearFaseEliminatoria("Final", finalistas, cfg.isEliminatoriaIdaYVuelta(),
                    cfg.isFinalUnica(), false, 0);
        }
        return null;
    }
}
