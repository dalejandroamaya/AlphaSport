package alphasport.service;

import java.util.ArrayList;
import alphasport.model.Campeonato;
import alphasport.model.Partido;
import alphasport.model.Tarjeta;

/**
 * Lleva el control de las tarjetas por jugador y de las suspensiones.
 *
 * Una roja con N fechas deja al jugador fuera de los siguientes N partidos
 * de su equipo (en el orden del fixture). Una fecha se da por cumplida
 * cuando ese partido ya se jugo; los aplazados no cuentan hasta jugarse.
 */
public class Sanciones {

    /**
     * Una tarjeta junto con el partido donde se mostro y, si es roja, el
     * estado de la suspension.
     */
    public static class Registro {

        private final Partido partido;
        private final Tarjeta tarjeta;
        private final int fechasPendientes;

        Registro(Partido partido, Tarjeta tarjeta, int fechasPendientes) {
            this.partido = partido;
            this.tarjeta = tarjeta;
            this.fechasPendientes = fechasPendientes;
        }

        public Partido getPartido() {
            return partido;
        }

        public Tarjeta getTarjeta() {
            return tarjeta;
        }

        public int getFechasPendientes() {
            return fechasPendientes;
        }
    }

    private final Campeonato campeonato;

    public Sanciones(Campeonato campeonato) {
        this.campeonato = campeonato;
    }

    /**
     * Todas las tarjetas de los partidos jugados, en orden del fixture.
     */
    public ArrayList<Registro> listar() {
        ArrayList<Registro> lista = new ArrayList<>();
        for (Partido p : campeonato.getPartidos()) {
            if (!p.isJugado()) {
                continue;
            }
            for (Tarjeta t : p.getTarjetas()) {
                lista.add(new Registro(p, t, t.isRoja() ? fechasPendientes(p, t) : 0));
            }
        }
        return lista;
    }

    /**
     * Partidos que juega el equipo despues del partido indicado.
     */
    private ArrayList<Partido> partidosSiguientes(Partido desde, int idEquipo) {
        ArrayList<Partido> siguientes = new ArrayList<>();
        for (Partido p : campeonato.getPartidos()) {
            if (p.getId() > desde.getId() && p.involucra(idEquipo)) {
                siguientes.add(p);
            }
        }
        siguientes.sort((a, b) -> Integer.compare(a.getId(), b.getId()));
        return siguientes;
    }

    /**
     * Fechas que le faltan por cumplir a una roja.
     */
    public int fechasPendientes(Partido partidoRoja, Tarjeta roja) {
        ArrayList<Partido> siguientes = partidosSiguientes(partidoRoja, roja.getIdEquipo());
        int cumplidas = 0;
        for (int i = 0; i < siguientes.size() && i < roja.getFechasSuspension(); i++) {
            if (siguientes.get(i).isJugado()) {
                cumplidas++;
            }
        }
        return roja.getFechasSuspension() - cumplidas;
    }

    /**
     * Rojas (de partidos anteriores) que dejan a algun jugador fuera de este partido.
     */
    public ArrayList<Tarjeta> suspendidosEn(Partido objetivo) {
        ArrayList<Tarjeta> suspendidos = new ArrayList<>();
        for (Partido p : campeonato.getPartidos()) {
            if (!p.isJugado() || p.getId() >= objetivo.getId()) {
                continue;
            }
            for (Tarjeta t : p.getTarjetas()) {
                if (t.isRoja() && objetivo.involucra(t.getIdEquipo())) {
                    ArrayList<Partido> siguientes = partidosSiguientes(p, t.getIdEquipo());
                    for (int i = 0; i < siguientes.size() && i < t.getFechasSuspension(); i++) {
                        if (siguientes.get(i).getId() == objetivo.getId()) {
                            suspendidos.add(t);
                        }
                    }
                }
            }
        }
        return suspendidos;
    }

    public boolean estaSuspendido(Partido partido, int idJugador) {
        for (Tarjeta t : suspendidosEn(partido)) {
            if (t.getIdJugador() == idJugador) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fechas que le faltan por cumplir a un jugador (suma de todas sus rojas).
     */
    public int fechasPendientesDe(int idJugador) {
        int total = 0;
        for (Registro r : listar()) {
            if (r.getTarjeta().getIdJugador() == idJugador) {
                total += r.getFechasPendientes();
            }
        }
        return total;
    }

    public int contar(int idJugador, boolean rojas) {
        int total = 0;
        for (Registro r : listar()) {
            if (r.getTarjeta().getIdJugador() == idJugador && r.getTarjeta().isRoja() == rojas) {
                total++;
            }
        }
        return total;
    }
}
