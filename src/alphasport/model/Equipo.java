package alphasport.model;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Un equipo ya no guarda puntos ni goles: esas estadisticas se calculan
 * a partir de los partidos (ver alphasport.service.TablaPosiciones). Asi, al editar o
 * borrar un partido la tabla siempre queda correcta.
 */
public class Equipo implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private String nombreEquipo;
    private String estadio;
    private boolean retirado;
    private DirectorTecnico directorTecnico;
    private ArrayList<Jugador> jugadores = new ArrayList<>();
    // Division de una liga con divisiones: 0 = Liga A, 1 = Liga B... (0 en archivos anteriores)
    private int division;

    public Equipo() {
    }

    public int getDivision() {
        return division;
    }

    public void setDivision(int division) {
        this.division = Math.max(0, division);
    }

    public Equipo(int id, String nombreEquipo) {
        this.id = id;
        this.nombreEquipo = nombreEquipo;
        this.estadio = "";
    }

    public boolean agregarJugador(Jugador jugador) {
        if (buscarJugadorPorDorsal(jugador.getDorsal()) != null) {
            return false;
        }
        jugadores.add(jugador);
        return true;
    }

    public boolean eliminarJugador(int dorsal) {
        Jugador j = buscarJugadorPorDorsal(dorsal);
        if (j == null) {
            return false;
        }
        jugadores.remove(j);
        return true;
    }

    public Jugador buscarJugadorPorDorsal(int dorsal) {
        for (Jugador j : jugadores) {
            if (j.getDorsal() == dorsal) {
                return j;
            }
        }
        return null;
    }

    public String getNombreDirectorTecnico() {
        if (directorTecnico == null || directorTecnico.getNombre() == null) {
            return "Sin asignar";
        }
        return directorTecnico.getNombre();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNombreEquipo() {
        return nombreEquipo;
    }

    public void setNombreEquipo(String nombreEquipo) {
        this.nombreEquipo = nombreEquipo;
    }

    public String getEstadio() {
        return estadio;
    }

    public void setEstadio(String estadio) {
        this.estadio = estadio;
    }

    public boolean isRetirado() {
        return retirado;
    }

    public void setRetirado(boolean retirado) {
        this.retirado = retirado;
    }

    public DirectorTecnico getDirectorTecnico() {
        return directorTecnico;
    }

    public void setDirectorTecnico(DirectorTecnico directorTecnico) {
        this.directorTecnico = directorTecnico;
    }

    public ArrayList<Jugador> getJugadores() {
        return jugadores;
    }

    public void setJugadores(ArrayList<Jugador> jugadores) {
        this.jugadores = jugadores;
    }

    @Override
    public String toString() {
        return nombreEquipo;
    }
}
