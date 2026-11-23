package alphasport.ui;

import alphasport.service.ReglaNegocioException;
import java.util.List;
import java.util.Map;

/**
 * Lo que llega a un manejador de ruta: el cliente que la pide (su sesion), lo que sigue al prefijo de la ruta y
 * el cuerpo JSON. Trae los metodos para leer el JSON sin repetir conversiones en cada modulo.
 */
record Peticion(ApiControlador api, String resto, Map<String, Object> cuerpo) {

    /** Respuesta normal de una accion: el mensaje, el estado actualizado y la sesion. */
    Map<String, Object> listo(String mensaje) {
        return api.respuesta(mensaje);
    }

    String texto(String clave) {
        return texto(cuerpo, clave);
    }

    int entero(String clave) {
        return entero(cuerpo.get(clave));
    }

    boolean si(String clave) {
        return Boolean.TRUE.equals(cuerpo.get(clave));
    }

    boolean tiene(String clave) {
        return cuerpo.containsKey(clave);
    }

    List<Object> lista(String clave) {
        return lista(cuerpo, clave);
    }

    Map<String, Object> objeto(String clave) {
        return objeto(cuerpo, clave);
    }

    /* ---------------------------------------------------------------- lectura del JSON */

    static String texto(Map<String, Object> m, String clave) {
        Object v = m.get(clave);
        return v == null ? "" : v.toString();
    }

    static int entero(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException("Valor numérico inválido: " + v);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> mapa(Object o) {
        return (Map<String, Object>) o;
    }

    static Map<String, Object> objeto(Map<String, Object> m, String clave) {
        Object v = m.get(clave);
        return v == null ? null : mapa(v);
    }

    @SuppressWarnings("unchecked")
    static List<Object> lista(Map<String, Object> m, String clave) {
        Object v = m.get(clave);
        return v == null ? List.of() : (List<Object>) v;
    }
}
