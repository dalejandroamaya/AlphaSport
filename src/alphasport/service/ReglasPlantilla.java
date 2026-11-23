package alphasport.service;

import alphasport.model.Campeonato;
import alphasport.model.Equipo;
import alphasport.model.Jugador;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reglas de las plantillas que valen para todo el campeonato. Hoy: un numero de documento identifica a un solo
 * jugador, en cualquier equipo. La base de datos lo refuerza con la restriccion jugadores_documento_unico.
 */
public final class ReglasPlantilla {

    private ReglasPlantilla() {
    }

    /**
     * Documento como se guarda: sin espacios al inicio ni al final y con un solo espacio entre sus partes
     * («  12 345 » queda «12 345»). Null queda vacio.
     */
    public static String limpiarDocumento(String documento) {
        return documento == null ? "" : documento.trim().replaceAll("\\s+", " ");
    }

    /**
     * Documento para comparar: sin ningun espacio y en mayusculas, asi «12 345», «12345» y « 12345 » son el mismo.
     * Vacio si el jugador no tiene documento (varios jugadores pueden no tenerlo).
     */
    public static String claveDocumento(String documento) {
        return documento == null ? "" : documento.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    /**
     * Revisa la plantilla que se va a guardar para {@code equipo} (null si es un equipo nuevo): ningun documento
     * se repite dentro de ella ni con los jugadores de los demas equipos del campeonato.
     *
     * @throws ReglaNegocioException con el documento repetido y a quien pertenece
     */
    public static void validarDocumentos(Campeonato c, Equipo equipo, List<Jugador> plantilla) {
        Map<String, String> enUso = new HashMap<>();
        for (Equipo otro : c.getEquipos()) {
            if (otro == equipo) {
                continue;
            }
            for (Jugador j : otro.getJugadores()) {
                String clave = claveDocumento(j.getDocumento());
                if (!clave.isEmpty()) {
                    enUso.putIfAbsent(clave, describir(j) + " de " + otro.getNombreEquipo());
                }
            }
        }
        Map<String, Jugador> enPlantilla = new HashMap<>();
        for (Jugador j : plantilla) {
            String clave = claveDocumento(j.getDocumento());
            if (clave.isEmpty()) {
                continue;
            }
            if (enUso.containsKey(clave)) {
                throw new ReglaNegocioException("El documento " + limpiarDocumento(j.getDocumento()) + " de " + j.getNombre()
                        + " ya está registrado: es de " + enUso.get(clave) + ". Revise el número.");
            }
            Jugador repetido = enPlantilla.putIfAbsent(clave, j);
            if (repetido != null) {
                throw new ReglaNegocioException("El documento " + limpiarDocumento(j.getDocumento()) + " está repetido en la plantilla: "
                        + describir(repetido) + " y " + describir(j) + ". Cada jugador debe tener su propio documento.");
            }
        }
    }

    /** Documentos (ya normalizados) de todos los jugadores del campeonato. */
    public static Set<String> documentosEnUso(Campeonato c) {
        Set<String> claves = new java.util.HashSet<>();
        c.getEquipos().forEach(e -> e.getJugadores().forEach(j -> claves.add(claveDocumento(j.getDocumento()))));
        claves.remove("");
        return claves;
    }

    private static String describir(Jugador j) {
        return "#" + j.getDorsal() + " " + j.getNombre();
    }
}
