package alphasport.ui.escritorio;

import java.util.UUID;
import java.util.prefs.Preferences;

/**
 * [REQ-07] Identificador anonimo de este equipo para las votaciones del publico: se crea al azar la
 * primera vez y se guarda en las preferencias del usuario de Windows (no en datos/), asi sigue siendo
 * el mismo aunque se cierre el programa o se cambie de campeonato. No contiene datos personales.
 */
final class Dispositivo {

    private static final String CLAVE = "dispositivo";
    private static String id;

    private Dispositivo() {
    }

    static synchronized String id() {
        if (id == null) {
            try {
                Preferences preferencias = Preferences.userNodeForPackage(Dispositivo.class);
                id = preferencias.get(CLAVE, null);
                if (id == null || !id.matches("[A-Za-z0-9-]{8,64}")) {
                    id = UUID.randomUUID().toString();
                    preferencias.put(CLAVE, id);
                }
            } catch (RuntimeException | Error e) {
                id = UUID.randomUUID().toString(); // sin preferencias: vale solo mientras el programa este abierto
            }
        }
        return id;
    }
}