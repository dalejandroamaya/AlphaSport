package alphasport.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON minimo para la API: escribe mapas, listas, textos, numeros y booleanos, y los lee de vuelta.
 */
public final class Json {

    private final String texto;
    private int pos;

    private Json(String texto) {
        this.texto = texto;
    }

    public static String escribir(Object valor) {
        StringBuilder sb = new StringBuilder();
        escribir(sb, valor);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> leerObjeto(String texto) {
        if (texto == null || texto.isBlank()) {
            return new LinkedHashMap<>();
        }
        Json lector = new Json(texto);
        Object valor = lector.valor();
        if (!(valor instanceof Map)) {
            throw new IllegalArgumentException("Se esperaba un objeto JSON");
        }
        return (Map<String, Object>) valor;
    }

    private static void escribir(StringBuilder sb, Object valor) {
        if (valor == null) {
            sb.append("null");
        } else if (valor instanceof String s) {
            cadena(sb, s);
        } else if (valor instanceof Number || valor instanceof Boolean) {
            sb.append(valor);
        } else if (valor instanceof Map<?, ?> mapa) {
            sb.append('{');
            boolean primero = true;
            for (Map.Entry<?, ?> e : mapa.entrySet()) {
                if (!primero) {
                    sb.append(',');
                }
                primero = false;
                cadena(sb, String.valueOf(e.getKey()));
                sb.append(':');
                escribir(sb, e.getValue());
            }
            sb.append('}');
        } else if (valor instanceof Iterable<?> lista) {
            sb.append('[');
            boolean primero = true;
            for (Object o : lista) {
                if (!primero) {
                    sb.append(',');
                }
                primero = false;
                escribir(sb, o);
            }
            sb.append(']');
        } else {
            cadena(sb, valor.toString());
        }
    }

    private static void cadena(StringBuilder sb, String s) {
        sb.append('"');
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        sb.append('"');
    }

    private Object valor() {
        espacios();
        char ch = texto.charAt(pos);
        if (ch == '{') {
            return objeto();
        }
        if (ch == '[') {
            return lista();
        }
        if (ch == '"') {
            return cadena();
        }
        if (texto.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (texto.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        if (texto.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        return numero();
    }

    private Map<String, Object> objeto() {
        Map<String, Object> mapa = new LinkedHashMap<>();
        pos++;
        espacios();
        if (texto.charAt(pos) == '}') {
            pos++;
            return mapa;
        }
        while (true) {
            espacios();
            String clave = cadena();
            espacios();
            esperar(':');
            mapa.put(clave, valor());
            espacios();
            if (texto.charAt(pos) == ',') {
                pos++;
            } else {
                esperar('}');
                return mapa;
            }
        }
    }

    private List<Object> lista() {
        List<Object> lista = new ArrayList<>();
        pos++;
        espacios();
        if (texto.charAt(pos) == ']') {
            pos++;
            return lista;
        }
        while (true) {
            lista.add(valor());
            espacios();
            if (texto.charAt(pos) == ',') {
                pos++;
            } else {
                esperar(']');
                return lista;
            }
        }
    }

    private String cadena() {
        esperar('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char ch = texto.charAt(pos++);
            if (ch == '"') {
                return sb.toString();
            }
            if (ch != '\\') {
                sb.append(ch);
                continue;
            }
            char esc = texto.charAt(pos++);
            switch (esc) {
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'u' -> {
                    sb.append((char) Integer.parseInt(texto.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                default -> sb.append(esc);
            }
        }
    }

    private Number numero() {
        int inicio = pos;
        while (pos < texto.length() && "+-0123456789.eE".indexOf(texto.charAt(pos)) >= 0) {
            pos++;
        }
        String n = texto.substring(inicio, pos);
        if (n.isEmpty()) {
            throw new IllegalArgumentException("JSON invalido en la posicion " + inicio);
        }
        return n.contains(".") || n.contains("e") || n.contains("E") ? Double.parseDouble(n) : Long.parseLong(n);
    }

    private void espacios() {
        while (pos < texto.length() && Character.isWhitespace(texto.charAt(pos))) {
            pos++;
        }
    }

    private void esperar(char ch) {
        if (pos >= texto.length() || texto.charAt(pos) != ch) {
            throw new IllegalArgumentException("JSON invalido: se esperaba '" + ch + "' en la posicion " + pos);
        }
        pos++;
    }
}
