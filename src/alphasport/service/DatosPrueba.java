package alphasport.service;

import alphasport.model.DirectorTecnico;
import alphasport.model.Jugador;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Genera equipos de prueba con plantilla completa (20 jugadores), director
 * tecnico y estadio a partir de una semilla: la misma semilla produce siempre
 * los mismos datos. La version web (web/js/service/datosPrueba.js) usa el
 * mismo algoritmo, asi que ambas generan exactamente los mismos equipos.
 * Todos los nombres y documentos son inventados.
 */
public final class DatosPrueba {

    public static final int MAXIMO_EQUIPOS = 32;
    public static final int JUGADORES_POR_EQUIPO = 20;

    static final String[] EQUIPOS = {
        "Halcones del Norte", "Tigres de la Sabana", "Lobos de Montaña", "Pumas del Valle", "Cóndores FC",
        "Toros de la Costa", "Leones Dorados", "Águilas del Sur", "Jaguares FC", "Dragones Rojos",
        "Titanes del Puerto", "Corsarios FC", "Centauros del Llano", "Vikingos de Altamira", "Búhos Nocturnos",
        "Delfines del Caribe", "Zorros del Bosque", "Osos Andinos", "Tiburones del Golfo", "Linces FC",
        "Gavilanes del Río", "Bisontes United", "Coyotes del Desierto", "Venados de la Sierra", "Caimanes del Magdalena",
        "Albatros FC", "Guepardos del Este", "Rinocerontes FC", "Mantarrayas del Mar", "Colibríes del Jardín",
        "Truenos del Oeste", "Relámpagos FC"
    };

    static final String[] ESTADIOS = {
        "Los Almendros", "La Ceiba", "El Bosque", "Las Palmas", "San Rafael", "La Colina", "El Refugio", "Santa Lucía",
        "Los Pinos", "La Floresta", "El Mirador", "Las Acacias", "Villa Real", "La Esperanza", "El Prado", "Monteverde"
    };

    static final String[] NOMBRES = {
        "Santiago", "Mateo", "Sebastián", "Nicolás", "Samuel", "Alejandro", "Daniel", "Juan Pablo", "Andrés", "Felipe",
        "Tomás", "Emiliano", "Martín", "David", "Gabriel", "Julián", "Diego", "Camilo", "Esteban", "Lucas",
        "Miguel Ángel", "Jerónimo", "Simón", "Maximiliano", "Joaquín", "Benjamín", "Cristian", "Kevin", "Brayan", "Jhon",
        "Óscar", "Luis", "Carlos", "Jorge", "Iván", "Ricardo", "Fernando", "Manuel", "Rafael", "Hernán",
        "Wilmer", "Yeison", "Duván", "Edwin", "Fabián", "Gustavo", "Héctor", "Jairo", "Leonardo", "Mauricio",
        "Néstor", "Pablo", "Rodrigo", "Sergio", "Víctor", "Álvaro", "Bruno", "Elkin", "Franco", "Thiago"
    };

    static final String[] APELLIDOS = {
        "García", "Rodríguez", "Martínez", "López", "González", "Hernández", "Pérez", "Sánchez", "Ramírez", "Torres",
        "Flores", "Rivera", "Gómez", "Díaz", "Reyes", "Morales", "Cruz", "Ortiz", "Gutiérrez", "Chávez",
        "Ramos", "Vargas", "Castillo", "Jiménez", "Moreno", "Romero", "Herrera", "Medina", "Aguilar", "Castro",
        "Ruiz", "Mendoza", "Álvarez", "Rojas", "Vásquez", "Cárdenas", "Ospina", "Restrepo", "Cuadrado", "Arango",
        "Zapata", "Mosquera", "Valencia", "Palacios", "Quintero", "Salazar", "Montoya", "Londoño", "Giraldo", "Correa",
        "Murillo", "Córdoba", "Benítez", "Navarro", "Paredes", "Sosa", "Acosta", "Méndez", "Pineda", "Cabrera"
    };

    static final String[] PAISES = {"Colombia", "Argentina", "Uruguay", "México", "Chile", "Perú", "España", "Brasil", "Ecuador", "Paraguay"};

    /** Once titular con los dorsales clasicos y nueve suplentes. */
    static final String[] TITULARES = {
        "Portero", "Lateral derecho", "Lateral izquierdo", "Defensa central", "Mediocampista defensivo", "Defensa central",
        "Extremo derecho", "Mediocampista central", "Delantero centro", "Mediocampista ofensivo", "Extremo izquierdo"
    };
    static final String[] SUPLENTES = {
        "Portero", "Defensa central", "Carrilero derecho", "Carrilero izquierdo", "Interior izquierdo",
        "Volante derecho", "Mediapunta", "Segundo delantero", "Delantero centro"
    };

    private DatosPrueba() {
    }

    /** Equipo generado (sin id: lo asigna el campeonato al guardarlo). */
    public record EquipoPrueba(String nombre, String estadio, DirectorTecnico dt, ArrayList<Jugador> jugadores) {
    }

    /** Generador pseudoaleatorio mulberry32 (identico al de la version web). */
    static final class Azar {

        private int estado;

        Azar(long semilla) {
            this.estado = (int) semilla;
        }

        double siguiente() {
            estado += 0x6D2B79F5;
            int t = estado;
            t = (t ^ (t >>> 15)) * (t | 1);
            t ^= t + (t ^ (t >>> 7)) * (t | 61);
            return ((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296.0;
        }

        int entero(int limite) {
            return (int) Math.floor(siguiente() * limite);
        }

        String elegir(String[] opciones) {
            return opciones[entero(opciones.length)];
        }

        <T> void mezclar(List<T> lista) {
            for (int i = lista.size() - 1; i > 0; i--) {
                int j = entero(i + 1);
                T tmp = lista.get(i);
                lista.set(i, lista.get(j));
                lista.set(j, tmp);
            }
        }
    }

    /**
     * Crea {@code cantidad} equipos nuevos (sin repetir nombres ya usados) y una
     * plantilla para cada uno de los {@code porCompletar} equipos existentes.
     * Devuelve primero los equipos nuevos y despues las plantillas extra.
     */
    public static Resultado generar(long semilla, int cantidad, Collection<String> nombresUsados, int porCompletar) {
        Azar azar = new Azar(semilla);
        Set<String> usados = new HashSet<>();
        for (String n : nombresUsados) {
            usados.add(n.trim().toLowerCase(Locale.ROOT));
        }
        List<String> nombres = new ArrayList<>(List.of(EQUIPOS));
        azar.mezclar(nombres);
        ArrayList<EquipoPrueba> nuevos = new ArrayList<>();
        for (String nombre : nombres) {
            if (nuevos.size() >= cantidad) {
                break;
            }
            if (usados.contains(nombre.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String estadio = "Estadio " + azar.elegir(ESTADIOS);
            nuevos.add(new EquipoPrueba(nombre, estadio, director(azar), plantilla(azar)));
        }
        ArrayList<ArrayList<Jugador>> plantillas = new ArrayList<>();
        for (int i = 0; i < porCompletar; i++) {
            plantillas.add(plantilla(azar));
        }
        return new Resultado(nuevos, plantillas);
    }

    public record Resultado(ArrayList<EquipoPrueba> equipos, ArrayList<ArrayList<Jugador>> plantillas) {
    }

    private static DirectorTecnico director(Azar azar) {
        String nombre = azar.elegir(NOMBRES) + " " + azar.elegir(APELLIDOS);
        return new DirectorTecnico(nombre, documento(azar), azar.elegir(PAISES));
    }

    private static String documento(Azar azar) {
        return "1" + (azar.entero(900000000) + 100000000);
    }

    static ArrayList<Jugador> plantilla(Azar azar) {
        List<Integer> dorsales = new ArrayList<>();
        for (int d = 12; d <= 30; d++) {
            dorsales.add(d);
        }
        azar.mezclar(dorsales);
        Set<String> nombres = new HashSet<>();
        ArrayList<Jugador> jugadores = new ArrayList<>();
        for (int i = 0; i < TITULARES.length + SUPLENTES.length; i++) {
            String nombre;
            do {
                nombre = azar.elegir(NOMBRES) + " " + azar.elegir(APELLIDOS);
            } while (!nombres.add(nombre));
            boolean titular = i < TITULARES.length;
            int dorsal = titular ? i + 1 : dorsales.get(i - TITULARES.length);
            String posicion = titular ? TITULARES[i] : SUPLENTES[i - TITULARES.length];
            jugadores.add(new Jugador(nombre, documento(azar), dorsal, posicion));
        }
        return jugadores;
    }
}
