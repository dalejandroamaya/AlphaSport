package alphasport.ui.escritorio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lectura comoda del estado que entrega el Presentador (mapas y listas).
 */
final class Datos {

    private Datos() {
    }

    static String s(Map<String, Object> m, String clave) {
        Object v = m == null ? null : m.get(clave);
        return v == null ? "" : v.toString();
    }

    static int n(Map<String, Object> m, String clave) {
        Object v = m == null ? null : m.get(clave);
        return v instanceof Number numero ? numero.intValue() : 0;
    }

    static boolean b(Map<String, Object> m, String clave) {
        return m != null && Boolean.TRUE.equals(m.get(clave));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> m(Map<String, Object> m, String clave) {
        Object v = m == null ? null : m.get(clave);
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> l(Map<String, Object> m, String clave) {
        Object v = m == null ? null : m.get(clave);
        return v instanceof List ? (List<Map<String, Object>>) v : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    static List<Object> lista(Map<String, Object> m, String clave) {
        Object v = m == null ? null : m.get(clave);
        return v instanceof List ? (List<Object>) v : new ArrayList<>();
    }

    static Map<String, Object> mapa(Object... pares) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            m.put((String) pares[i], pares[i + 1]);
        }
        return m;
    }

    static String fecha(Map<String, Object> p) {
        String f = s(p, "fecha");
        String texto = f.matches("\\d{4}-\\d{2}-\\d{2}") ? f.substring(8) + "/" + f.substring(5, 7) + "/" + f.substring(0, 4)
                : (f.isEmpty() ? "Por definir" : f);
        return s(p, "hora").isEmpty() ? texto : texto + " · " + s(p, "hora");
    }
}
