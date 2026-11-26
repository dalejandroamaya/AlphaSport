package alphasport.service;

import alphasport.model.Permiso;
import alphasport.model.Usuario;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sesiones abiertas: cada navegador (o el escritorio) tiene la suya, identificada por un token aleatorio que no
 * se puede adivinar. Dice quien entro, con que rol y si puede hacer cada accion; validar la clave lo hace
 * {@link AutenticacionService}. Una sesion vence si no se usa durante {@link #DURACION}.
 */
public class SesionService {

    /** Tiempo sin usar la sesion tras el cual hay que volver a entrar. */
    public static final Duration DURACION = Duration.ofHours(12);

    private record Abierta(Usuario usuario, Instant vence) {
    }

    private final AutenticacionService autenticacion;
    private final Clock reloj;
    private final SecureRandom azar = new SecureRandom();
    private final Map<String, Abierta> sesiones = new ConcurrentHashMap<>();
    /** Campeonato elegido por cada sesion (token -> codigo). */
    private final Map<String, String> elegidos = new ConcurrentHashMap<>();

    public SesionService(AutenticacionService autenticacion) {
        this(autenticacion, Clock.systemUTC());
    }

    public SesionService(AutenticacionService autenticacion, Clock reloj) {
        this.autenticacion = autenticacion;
        this.reloj = reloj;
    }

    /**
     * Valida la clave y abre una sesion.
     *
     * @return el token de la sesion
     * @throws ReglaNegocioException con codigo LOGIN si el usuario o la clave no coinciden.
     */
    public String iniciar(String nombre, String clave) throws alphasport.persistence.PersistenciaException {
        return abrir(autenticacion.iniciarSesion(nombre, clave));
    }

    /**
     * Crea la cuenta y abre su sesion: tras registrarse se entra directo.
     *
     * @return el token de la sesion
     * @throws ReglaNegocioException si algun dato no sirve o el usuario o el correo ya estan en uso.
     */
    /** «Mi perfil» de las cuentas de este servicio. */
    public PerfilService perfil() {
        return autenticacion.perfil();
    }

    public String registrar(AutenticacionService.SolicitudRegistro datos) throws alphasport.persistence.PersistenciaException {
        return abrir(autenticacion.registrar(datos));
    }

    private String abrir(Usuario usuario) {
        Instant ahora = reloj.instant();
        sesiones.values().removeIf(s -> !s.vence().isAfter(ahora)); // limpia las vencidas
        elegidos.keySet().retainAll(sesiones.keySet());
        byte[] bytes = new byte[32];
        azar.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sesiones.put(token, new Abierta(usuario, ahora.plus(DURACION)));
        return token;
    }

    /**
     * Recuerda el campeonato que eligio esta sesion (su codigo): se usa cuando una peticion no dice cual. null lo
     * olvida. Sin sesion abierta no hace nada.
     */
    public void elegir(String token, String codigo) {
        if (token == null || usuario(token) == null) {
            return;
        }
        if (codigo == null) {
            elegidos.remove(token);
        } else {
            elegidos.put(token, codigo);
        }
    }

    /** Codigo del ultimo campeonato que eligio esta sesion; null si no eligio ninguno. */
    public String elegido(String token) {
        return token == null ? null : elegidos.get(token);
    }

    public void cerrar(String token) {
        if (token != null) {
            elegidos.remove(token);
            sesiones.remove(token);
        }
    }

    /** Usuario de la sesion; null si el token no existe o la sesion vencio. Usarla la renueva. */
    public Usuario usuario(String token) {
        if (token == null) {
            return null;
        }
        Instant ahora = reloj.instant();
        Abierta s = sesiones.computeIfPresent(token, (t, abierta) -> abierta.vence().isAfter(ahora)
                ? new Abierta(abierta.usuario(), ahora.plus(DURACION)) : null);
        return s == null ? null : s.usuario();
    }

    public boolean puede(String token, Permiso permiso) {
        Usuario u = usuario(token);
        return u != null && u.puede(permiso);
    }

    /** Lanza LOGIN si no hay sesion (o vencio). */
    public Usuario exigirSesion(String token) {
        Usuario u = usuario(token);
        if (u == null) {
            throw new ReglaNegocioException("LOGIN", "Inicie sesión para continuar.");
        }
        return u;
    }

    /** Lanza LOGIN si no hay sesion y PERMISO si el rol no tiene ese permiso. */
    public Usuario exigir(String token, Permiso permiso) {
        Usuario u = exigirSesion(token);
        if (!u.puede(permiso)) {
            throw new ReglaNegocioException("PERMISO", "Su rol (" + u.getRol().getNombre() + ") no puede " + permiso.getAccion() + ".");
        }
        return u;
    }
}
