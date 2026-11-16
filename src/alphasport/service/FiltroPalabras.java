package alphasport.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filtro anti-obscenidades de los comentarios: cada palabra de la lista negra se
 * reemplaza por «***» antes de guardar el comentario. Reconoce mayusculas, tildes,
 * letras estiradas («idiotaaa»), plurales y cambios simples de letras por numeros o
 * simbolos («1d10t4», «put@»). La misma lista y la misma regla estan en web/js/service/noticias.js.
 */
public final class FiltroPalabras {

    public static final String REEMPLAZO = "***";

    /** Lista negra (en minusculas y sin tildes). Para agregar una palabra basta con escribirla aqui y en noticias.js. */
    static final List<String> PROHIBIDAS = List.of(
            "idiota", "imbecil", "estupido", "estupida", "pendejo", "pendeja", "marica", "maricon", "malparido", "malparida",
            "hijueputa", "hijodeputa", "gonorrea", "puta", "puto", "mierda", "verga", "careverga", "cabron", "cabrona",
            "huevon", "guevon", "perra", "zorra", "tarado", "tarada", "mamon", "malnacido", "pirobo", "culo", "hp", "hdp");

    // Una «palabra»: letras, numeros y los simbolos que se usan para disfrazar letras.
    private static final Pattern PALABRA = Pattern.compile("[\\p{L}\\p{N}@$]+");

    private FiltroPalabras() {
    }

    /** Devuelve el texto con cada palabra prohibida cambiada por «***». */
    public static String sanear(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto == null ? "" : texto;
        }
        Matcher m = PALABRA.matcher(texto);
        StringBuilder salida = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(salida, Matcher.quoteReplacement(esProhibida(m.group()) ? REEMPLAZO : m.group()));
        }
        m.appendTail(salida);
        return salida.toString();
    }

    /** true si el texto tiene al menos una palabra prohibida. */
    public static boolean contieneProhibidas(String texto) {
        return !sanear(texto).equals(texto == null ? "" : texto);
    }

    /**
     * Un patron por palabra: cada letra puede repetirse pero no faltar («p+e+r+r+a+» reconoce
     * «perraaa» y nunca «pera»). Se acepta el plural y, en las palabras largas, cualquier final
     * («malparidos», «hijueputas»).
     */
    private static final List<Pattern> PATRONES = PROHIBIDAS.stream().map(raiz -> {
        StringBuilder r = new StringBuilder("^");
        for (char c : raiz.toCharArray()) {
            r.append(Pattern.quote(String.valueOf(c))).append('+');
        }
        r.append(raiz.length() >= 6 ? ".*" : "(e?s)?").append('$');
        return Pattern.compile(r.toString());
    }).toList();

    static boolean esProhibida(String palabra) {
        String p = normalizar(palabra);
        return PATRONES.stream().anyMatch(patron -> patron.matcher(p).matches());
    }

    /** Minusculas, sin tildes y con los numeros/simbolos leidos como letras («1d10t4» -> «idiota»). */
    static String normalizar(String palabra) {
        String sinTildes = Normalizer.normalize(palabra.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        StringBuilder sb = new StringBuilder();
        for (char c : sinTildes.toCharArray()) {
            sb.append(switch (c) {
                case '0' -> 'o';
                case '1' -> 'i';
                case '3' -> 'e';
                case '4', '@' -> 'a';
                case '5', '$' -> 's';
                case '7' -> 't';
                default -> c;
            });
        }
        return sb.toString();
    }
}
