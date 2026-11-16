package alphasport.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import alphasport.model.Campeonato;
import alphasport.model.CriterioDesempate;
import alphasport.model.Equipo;
import alphasport.model.Partido;

/**
 * Calcula y ordena tablas de posiciones aplicando los criterios de
 * desempate configurados en el campeonato.
 *
 * Primero se ordena por puntos. Los equipos que quedan empatados se
 * desempatan con el primer criterio; los que siguen empatados pasan al
 * segundo criterio y asi sucesivamente. Esto es importante para el
 * enfrentamiento directo: si empatan 3 equipos se arma una "mini tabla"
 * solo con los partidos entre esos 3.
 */
public class TablaPosiciones {

    private final Campeonato campeonato;

    public TablaPosiciones(Campeonato campeonato) {
        this.campeonato = campeonato;
    }

    /**
     * Tabla de los equipos indicados usando solo los partidos indicados.
     */
    public ArrayList<FilaTabla> calcular(List<Partido> partidos, List<Integer> idsEquipos) {
        HashMap<Integer, FilaTabla> filas = new HashMap<>();
        ArrayList<FilaTabla> lista = new ArrayList<>();
        for (int id : idsEquipos) {
            Equipo e = campeonato.buscarEquipo(id);
            if (e != null) {
                FilaTabla fila = new FilaTabla(e);
                filas.put(id, fila);
                lista.add(fila);
            }
        }
        for (Partido p : partidos) {
            FilaTabla local = filas.get(p.getIdLocal());
            FilaTabla visitante = filas.get(p.getIdVisitante());
            if (p.isJugado() && local != null && visitante != null) {
                sumarPartido(local, visitante, p);
            }
        }
        ordenar(lista, partidos, true);
        return lista;
    }

    private void sumarPartido(FilaTabla local, FilaTabla visitante, Partido p) {
        int pv = campeonato.getConfiguracion().getPuntosVictoria();
        int pe = campeonato.getConfiguracion().getPuntosEmpate();
        int gl = p.getGolesLocal();
        int gv = p.getGolesVisitante();

        local.jugados++;
        visitante.jugados++;
        local.golesFavor += gl;
        local.golesContra += gv;
        visitante.golesFavor += gv;
        visitante.golesContra += gl;
        local.amarillas += p.getAmarillasLocal();
        local.rojas += p.getRojasLocal();
        visitante.amarillas += p.getAmarillasVisitante();
        visitante.rojas += p.getRojasVisitante();

        if (gl > gv) {
            local.ganados++;
            local.puntos += pv;
            visitante.perdidos++;
        } else if (gl < gv) {
            visitante.ganados++;
            visitante.puntos += pv;
            local.perdidos++;
        } else {
            local.empatados++;
            visitante.empatados++;
            local.puntos += pe;
            visitante.puntos += pe;
        }
    }

    /**
     * Ordena la lista. Si permitirDirecto es false se ignora el criterio de
     * enfrentamiento directo (por ejemplo al comparar terceros de distintos
     * grupos, que nunca jugaron entre si).
     */
    public void ordenar(ArrayList<FilaTabla> lista, List<Partido> partidos, boolean permitirDirecto) {
        lista.sort((a, b) -> Integer.compare(b.puntos, a.puntos));
        ArrayList<FilaTabla> resultado = new ArrayList<>();
        int i = 0;
        while (i < lista.size()) {
            int j = i;
            while (j < lista.size() && lista.get(j).puntos == lista.get(i).puntos) {
                j++;
            }
            ArrayList<FilaTabla> empatados = new ArrayList<>(lista.subList(i, j));
            resultado.addAll(desempatar(empatados, 0, partidos, permitirDirecto));
            i = j;
        }
        lista.clear();
        lista.addAll(resultado);
    }

    private ArrayList<FilaTabla> desempatar(ArrayList<FilaTabla> empatados, int indiceCriterio,
            List<Partido> partidos, boolean permitirDirecto) {
        if (empatados.size() <= 1) {
            return empatados;
        }
        ArrayList<CriterioDesempate> criterios = campeonato.getConfiguracion().getCriterios();
        if (indiceCriterio >= criterios.size()) {
            // Ya no quedan criterios: se usa el sorteo para que el orden sea estable
            empatados.sort((a, b) -> Integer.compare(valorSorteo(b), valorSorteo(a)));
            return empatados;
        }
        CriterioDesempate criterio = criterios.get(indiceCriterio);
        if (criterio == CriterioDesempate.ENFRENTAMIENTO_DIRECTO && !permitirDirecto) {
            return desempatar(empatados, indiceCriterio + 1, partidos, permitirDirecto);
        }

        HashMap<Integer, int[]> claves = new HashMap<>();
        if (criterio == CriterioDesempate.ENFRENTAMIENTO_DIRECTO) {
            claves = clavesEnfrentamientoDirecto(empatados, partidos);
        } else {
            for (FilaTabla f : empatados) {
                claves.put(f.getIdEquipo(), new int[]{valorCriterio(f, criterio)});
            }
        }
        final HashMap<Integer, int[]> c = claves;
        empatados.sort((a, b) -> compararClaves(c.get(b.getIdEquipo()), c.get(a.getIdEquipo())));

        ArrayList<FilaTabla> resultado = new ArrayList<>();
        int i = 0;
        while (i < empatados.size()) {
            int j = i;
            while (j < empatados.size()
                    && compararClaves(c.get(empatados.get(j).getIdEquipo()), c.get(empatados.get(i).getIdEquipo())) == 0) {
                j++;
            }
            ArrayList<FilaTabla> subgrupo = new ArrayList<>(empatados.subList(i, j));
            resultado.addAll(desempatar(subgrupo, indiceCriterio + 1, partidos, permitirDirecto));
            i = j;
        }
        return resultado;
    }

    private int valorCriterio(FilaTabla f, CriterioDesempate criterio) {
        switch (criterio) {
            case DIFERENCIA_GOLES:
                return f.getDiferenciaGoles();
            case GOLES_A_FAVOR:
                return f.getGolesFavor();
            case PARTIDOS_GANADOS:
                return f.getGanados();
            case FAIR_PLAY:
                return -f.getPuntosFairPlay(); // negativo: menos tarjetas es mejor
            case SORTEO:
                return valorSorteo(f);
            default:
                return 0;
        }
    }

    /**
     * Mini tabla con los partidos jugados solo entre los equipos empatados:
     * puntos, diferencia de goles y goles a favor en esos partidos.
     */
    private HashMap<Integer, int[]> clavesEnfrentamientoDirecto(ArrayList<FilaTabla> empatados, List<Partido> partidos) {
        int pv = campeonato.getConfiguracion().getPuntosVictoria();
        int pe = campeonato.getConfiguracion().getPuntosEmpate();
        HashSet<Integer> ids = new HashSet<>();
        HashMap<Integer, int[]> claves = new HashMap<>();
        for (FilaTabla f : empatados) {
            ids.add(f.getIdEquipo());
            claves.put(f.getIdEquipo(), new int[3]); // puntos, diferencia, goles
        }
        for (Partido p : partidos) {
            if (!p.isJugado() || !ids.contains(p.getIdLocal()) || !ids.contains(p.getIdVisitante())) {
                continue;
            }
            int[] local = claves.get(p.getIdLocal());
            int[] visitante = claves.get(p.getIdVisitante());
            int gl = p.getGolesLocal();
            int gv = p.getGolesVisitante();
            local[1] += gl - gv;
            local[2] += gl;
            visitante[1] += gv - gl;
            visitante[2] += gv;
            if (gl > gv) {
                local[0] += pv;
            } else if (gv > gl) {
                visitante[0] += pv;
            } else {
                local[0] += pe;
                visitante[0] += pe;
            }
        }
        return claves;
    }

    private int compararClaves(int[] a, int[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return Integer.compare(a[i], b[i]);
            }
        }
        return 0;
    }

    /**
     * Numero "aleatorio" fijo para cada equipo dentro de este campeonato,
     * asi el sorteo no cambia cada vez que se refresca la tabla.
     */
    private int valorSorteo(FilaTabla f) {
        return new Random(campeonato.getSemilla() * 31 + f.getIdEquipo() * 7919L).nextInt(1000000);
    }
}
