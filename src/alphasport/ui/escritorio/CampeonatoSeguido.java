package alphasport.ui.escritorio;

import java.util.prefs.Preferences;

/**
 * Ultimo campeonato que siguio cada Aficionado en este equipo: se guarda en las preferencias del usuario de
 * Windows (como {@link Dispositivo}), asi al volver a entrar se abre el mismo. Solo es una comodidad: si no hay
 * preferencias, no se recuerda y no pasa nada. La web hace lo mismo con localStorage.
 */
final class CampeonatoSeguido {

    private static final String PREFIJO = "seguido.";

    private CampeonatoSeguido() {
    }

    /** Codigo del campeonato que seguia ese usuario; null si ninguno. */
    static String de(String usuario) {
        try {
            return Preferences.userNodeForPackage(CampeonatoSeguido.class).get(PREFIJO + usuario, null);
        } catch (RuntimeException e) {
            return null; // sin preferencias: como si no hubiera ninguno
        }
    }

    /** Recuerda ese campeonato para el usuario; null lo olvida. */
    static void recordar(String usuario, String codigo) {
        try {
            Preferences p = Preferences.userNodeForPackage(CampeonatoSeguido.class);
            if (codigo == null) {
                p.remove(PREFIJO + usuario);
            } else if (!codigo.equals(p.get(PREFIJO + usuario, null))) {
                p.put(PREFIJO + usuario, codigo);
            }
        } catch (RuntimeException e) {
            // sin preferencias: no se recuerda
        }
    }
}
