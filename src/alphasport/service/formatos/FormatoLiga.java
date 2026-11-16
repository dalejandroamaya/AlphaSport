package alphasport.service.formatos;

import alphasport.service.MotorCampeonato;
import alphasport.model.Campeonato;
import alphasport.model.ConfiguracionTorneo;
import alphasport.model.Equipo;
import alphasport.model.Fase;
import java.util.ArrayList;

/**
 * Todos contra todos a una o dos vueltas. El campeon es el primero de la tabla.
 *
 * Con divisiones (Liga A, Liga B, Liga C...) cada division es una liga aparte: la
 * fase es de grupos (un grupo por division) y el campeon es el primero de la Liga A.
 * Entre temporadas suben y bajan equipos (ver CampeonatoService.siguienteTemporada).
 * Una liga nunca tiene llaves.
 */
public class FormatoLiga extends FormatoCampeonato {

    @Override
    public String validar(Campeonato campeonato) {
        if (!campeonato.tieneDivisiones()) {
            return null;
        }
        for (int d = 0; d < campeonato.getConfiguracion().getDivisiones(); d++) {
            int equipos = campeonato.equiposDeDivision(d).size();
            if (equipos < 2) {
                return "La " + ConfiguracionTorneo.nombreDivision(d) + " tiene " + (equipos == 1 ? "1 equipo" : "0 equipos")
                        + ": cada división necesita al menos 2. Mueva equipos o quite divisiones en la sección Equipos.";
            }
        }
        return null;
    }

    @Override
    public Fase crearPrimeraFase(Campeonato campeonato, MotorCampeonato motor) {
        boolean idaYVuelta = campeonato.getConfiguracion().isIdaYVuelta();
        String nombre = idaYVuelta ? "Liga (ida y vuelta)" : "Liga (una vuelta)";
        if (!campeonato.tieneDivisiones()) {
            return motor.crearFaseLiga(nombre, motor.sembrar(campeonato.getEquiposActivos()), idaYVuelta);
        }
        // Una division = un grupo. Se reutiliza la fase de grupos del motor (fixture y tablas por grupo).
        ArrayList<ArrayList<Integer>> divisiones = new ArrayList<>();
        for (int d = 0; d < campeonato.getConfiguracion().getDivisiones(); d++) {
            ArrayList<Equipo> equipos = campeonato.equiposDeDivision(d);
            divisiones.add(motor.sembrar(equipos));
        }
        Fase fase = motor.crearFaseGrupos(nombre, divisiones, idaYVuelta);
        fase.setDivisiones(true);
        return fase;
    }

    @Override
    public Fase crearSiguienteFase(Campeonato campeonato, MotorCampeonato motor, int indiceFaseTerminada) {
        return null; // el ganador de la fase (primero de la Liga A si hay divisiones) es el campeon
    }

    @Override
    public boolean usaLlaves() {
        return false;
    }
}
