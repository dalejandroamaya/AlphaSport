package alphasport.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import alphasport.model.Campeonato;

/**
 * Palmares historico: suma lo que cada equipo logro en todos los
 * campeonatos del historial (titulos, subcampeonatos, partidos, puntos...).
 * Los equipos se reconocen por su nombre, porque cada campeonato tiene sus
 * propios ids.
 */
public class Palmares {

    public static class Fila {

        private final String equipo;
        int titulos;
        int subcampeonatos;
        int podios;
        int participaciones;
        int jugados;
        int ganados;
        int empatados;
        int perdidos;
        int golesFavor;
        int golesContra;
        int puntos;

        Fila(String equipo) {
            this.equipo = equipo;
        }

        public String getEquipo() {
            return equipo;
        }

        public int getTitulos() {
            return titulos;
        }

        public int getSubcampeonatos() {
            return subcampeonatos;
        }

        public int getPodios() {
            return podios;
        }

        public int getParticipaciones() {
            return participaciones;
        }

        public int getJugados() {
            return jugados;
        }

        public int getGanados() {
            return ganados;
        }

        public int getEmpatados() {
            return empatados;
        }

        public int getPerdidos() {
            return perdidos;
        }

        public int getGolesFavor() {
            return golesFavor;
        }

        public int getGolesContra() {
            return golesContra;
        }

        public int getPuntos() {
            return puntos;
        }
    }

    public static ArrayList<Fila> calcular(List<Campeonato> campeonatos) {
        LinkedHashMap<String, Fila> filas = new LinkedHashMap<>();
        for (Campeonato c : campeonatos) {
            ResumenCampeonato resumen = new ResumenCampeonato(c);
            ArrayList<FilaTabla> clasificacion = resumen.clasificacionFinal();
            for (int i = 0; i < clasificacion.size(); i++) {
                FilaTabla f = clasificacion.get(i);
                String clave = f.getEquipo().getNombreEquipo().trim().toLowerCase();
                Fila fila = filas.computeIfAbsent(clave, k -> new Fila(f.getEquipo().getNombreEquipo()));
                fila.participaciones++;
                fila.jugados += f.getJugados();
                fila.ganados += f.getGanados();
                fila.empatados += f.getEmpatados();
                fila.perdidos += f.getPerdidos();
                fila.golesFavor += f.getGolesFavor();
                fila.golesContra += f.getGolesContra();
                fila.puntos += f.getPuntos();
                if (c.isTerminado()) { // solo los torneos terminados reparten titulos
                    if (i == 0) {
                        fila.titulos++;
                    } else if (i == 1) {
                        fila.subcampeonatos++;
                    }
                    if (i < 3) {
                        fila.podios++;
                    }
                }
            }
        }
        ArrayList<Fila> lista = new ArrayList<>(filas.values());
        lista.sort((a, b) -> a.titulos != b.titulos ? b.titulos - a.titulos
                : a.subcampeonatos != b.subcampeonatos ? b.subcampeonatos - a.subcampeonatos
                : a.podios != b.podios ? b.podios - a.podios
                : b.puntos - a.puntos);
        return lista;
    }
}
