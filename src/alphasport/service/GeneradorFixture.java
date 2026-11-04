package alphasport.service;

import java.util.ArrayList;
import java.util.List;
import alphasport.model.Fase;

/**
 * Utilidades para armar calendarios y llaves.
 */
public class GeneradorFixture {

    /**
     * Calendario todos contra todos con el metodo del circulo (el mismo que
     * usaba el prototipo de P2, ahora con local/visitante alternado).
     * Cada elemento de la lista es una jornada; cada jornada es una lista de
     * cruces {local, visitante}. Si el numero de equipos es impar, en cada
     * jornada descansa un equipo.
     */
    public static ArrayList<ArrayList<int[]>> todosContraTodos(List<Integer> ids, boolean idaYVuelta) {
        ArrayList<Integer> equipos = new ArrayList<>(ids);
        if (equipos.size() % 2 != 0) {
            equipos.add(Fase.LIBRE);
        }
        int n = equipos.size();
        ArrayList<ArrayList<int[]>> jornadas = new ArrayList<>();

        for (int ronda = 0; ronda < n - 1; ronda++) {
            ArrayList<int[]> jornada = new ArrayList<>();
            for (int i = 0; i < n / 2; i++) {
                int a = equipos.get(i);
                int b = equipos.get(n - 1 - i);
                if (a == Fase.LIBRE || b == Fase.LIBRE) {
                    continue;
                }
                boolean aEsLocal = (i == 0) ? (ronda % 2 == 0) : (i % 2 == 0);
                jornada.add(aEsLocal ? new int[]{a, b} : new int[]{b, a});
            }
            jornadas.add(jornada);
            int ultimo = equipos.remove(n - 1);
            equipos.add(1, ultimo);
        }

        if (idaYVuelta) {
            int primeraVuelta = jornadas.size();
            for (int r = 0; r < primeraVuelta; r++) {
                ArrayList<int[]> vuelta = new ArrayList<>();
                for (int[] cruce : jornadas.get(r)) {
                    vuelta.add(new int[]{cruce[1], cruce[0]});
                }
                jornadas.add(vuelta);
            }
        }
        return jornadas;
    }

    public static int siguientePotenciaDeDos(int n) {
        int p = 1;
        while (p < n) {
            p *= 2;
        }
        return p;
    }

    /**
     * Orden de siembra de una llave de tamano P (potencia de 2), para que el
     * 1 y el 2 solo se puedan cruzar en la final. Para P = 8 devuelve
     * [1, 8, 4, 5, 2, 7, 3, 6].
     */
    public static ArrayList<Integer> ordenSembrado(int p) {
        ArrayList<Integer> orden = new ArrayList<>();
        orden.add(1);
        if (p == 1) {
            return orden;
        }
        orden.add(2);
        int tamano = 2;
        while (tamano < p) {
            tamano *= 2;
            ArrayList<Integer> nuevo = new ArrayList<>();
            for (int semilla : orden) {
                nuevo.add(semilla);
                nuevo.add(tamano + 1 - semilla);
            }
            orden = nuevo;
        }
        return orden;
    }

    /**
     * Llave de la primera ronda a partir de los equipos ordenados por siembra.
     * Si no son potencia de 2, los mejores sembrados quedan con rival LIBRE
     * (pasan directo a la segunda ronda).
     */
    public static ArrayList<Integer> cuadroDesdeSiembra(List<Integer> sembrados) {
        int p = siguientePotenciaDeDos(Math.max(2, sembrados.size()));
        ArrayList<Integer> cuadro = new ArrayList<>();
        for (int semilla : ordenSembrado(p)) {
            cuadro.add(semilla <= sembrados.size() ? sembrados.get(semilla - 1) : Fase.LIBRE);
        }
        return cuadro;
    }

    public static String nombreRonda(int equiposEnRonda) {
        switch (equiposEnRonda) {
            case 2:
                return "Final";
            case 4:
                return "Semifinales";
            case 8:
                return "Cuartos de final";
            case 16:
                return "Octavos de final";
            case 32:
                return "Dieciseisavos de final";
            default:
                return "Ronda de " + equiposEnRonda;
        }
    }
}
