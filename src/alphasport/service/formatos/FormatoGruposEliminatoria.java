package alphasport.service.formatos;

import java.util.ArrayList;
import java.util.HashMap;
import alphasport.service.FilaTabla;
import alphasport.service.GeneradorFixture;
import alphasport.service.MotorCampeonato;
import alphasport.model.Campeonato;
import alphasport.model.ConfiguracionTorneo;
import alphasport.model.Fase;

/**
 * Fase de grupos (sorteo por bombos) + llave de eliminacion directa.
 * Si los clasificados no completan una llave (8, 16, 32...) se completa con
 * los mejores equipos de la siguiente posicion, como los "mejores terceros"
 * del Mundial 2026.
 */
public class FormatoGruposEliminatoria extends FormatoCampeonato {

    @Override
    public String validar(Campeonato campeonato) {
        ConfiguracionTorneo cfg = campeonato.getConfiguracion();
        int n = campeonato.getEquiposActivos().size();
        if (cfg.getTamanoGrupo() < 2) {
            return "Cada grupo debe tener al menos 2 equipos.";
        }
        int grupos = cantidadGrupos(n, cfg.getTamanoGrupo());
        int minimoPorGrupo = n / grupos;
        if (minimoPorGrupo < 2) {
            return "Con " + n + " equipos no alcanza para grupos de " + cfg.getTamanoGrupo() + ".";
        }
        if (cfg.getClasificadosPorGrupo() < 1 || cfg.getClasificadosPorGrupo() >= minimoPorGrupo) {
            return "Los clasificados por grupo deben ser entre 1 y " + (minimoPorGrupo - 1)
                    + " (hay grupos de " + minimoPorGrupo + " equipos).";
        }
        if (grupos * cfg.getClasificadosPorGrupo() < 2) {
            return "Deben clasificar al menos 2 equipos a la fase final.";
        }
        return null;
    }

    public static int cantidadGrupos(int equipos, int tamanoGrupo) {
        return Math.max(1, (int) Math.ceil(equipos / (double) tamanoGrupo));
    }

    @Override
    public Fase crearPrimeraFase(Campeonato campeonato, MotorCampeonato motor) {
        ConfiguracionTorneo cfg = campeonato.getConfiguracion();
        int grupos = cantidadGrupos(campeonato.getEquiposActivos().size(), cfg.getTamanoGrupo());
        ArrayList<ArrayList<Integer>> reparto = motor.repartirPorBombos(motor.sembrar(campeonato.getEquiposActivos()), grupos);
        return motor.crearFaseGrupos("Fase de grupos", reparto, cfg.isIdaYVuelta());
    }

    @Override
    public Fase crearSiguienteFase(Campeonato campeonato, MotorCampeonato motor, int indiceFaseTerminada) {
        if (indiceFaseTerminada != 0) {
            return null;
        }
        ConfiguracionTorneo cfg = campeonato.getConfiguracion();
        Fase faseGrupos = campeonato.getFases().get(0);
        int cantidad = faseGrupos.getGrupos().size();
        int k = cfg.getClasificadosPorGrupo();

        ArrayList<ArrayList<FilaTabla>> tablas = new ArrayList<>();
        HashMap<Integer, String> grupoDe = new HashMap<>();
        for (int g = 0; g < cantidad; g++) {
            ArrayList<FilaTabla> t = motor.tablaDeGrupo(0, g);
            tablas.add(t);
            for (FilaTabla f : t) {
                grupoDe.put(f.getIdEquipo(), f.getGrupo());
            }
        }

        // Primeros de todos los grupos, luego segundos... cada nivel ordenado entre si
        ArrayList<Integer> sembrados = new ArrayList<>();
        for (int posicion = 0; posicion < k; posicion++) {
            sembrados.addAll(nivel(campeonato, motor, tablas, posicion));
        }
        int llave = GeneradorFixture.siguientePotenciaDeDos(sembrados.size());
        if (sembrados.size() < llave) {
            ArrayList<Integer> siguientes = nivel(campeonato, motor, tablas, k);
            for (int i = 0; i < siguientes.size() && sembrados.size() < llave; i++) {
                sembrados.add(siguientes.get(i));
            }
        }

        Fase fase = motor.crearFaseEliminatoria("Fase final", sembrados,
                cfg.isEliminatoriaIdaYVuelta(), cfg.isFinalUnica(), false, 0);
        motor.evitarCrucesMismoGrupo(fase, grupoDe);
        return fase;
    }

    private ArrayList<Integer> nivel(Campeonato campeonato, MotorCampeonato motor,
            ArrayList<ArrayList<FilaTabla>> tablas, int posicion) {
        ArrayList<FilaTabla> filas = new ArrayList<>();
        for (ArrayList<FilaTabla> t : tablas) {
            if (posicion < t.size()) {
                filas.add(t.get(posicion));
            }
        }
        motor.getTabla().ordenar(filas, campeonato.partidosDeFase(0), false);
        return MotorCampeonato.ids(filas);
    }
}
