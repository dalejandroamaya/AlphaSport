package alphasport.service;

import alphasport.model.CuentaUsuario;
import alphasport.persistence.PersistenciaException;
import alphasport.persistence.RepositorioUsuarios;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * «Mi perfil»: ver y editar nombre y correo, cambiar la contraseña y verificar el correo con un codigo. La
 * cuenta es siempre la de la sesion (la pantalla nunca dice de quien es). El usuario no se cambia. Usa las
 * mismas reglas que el registro (AutenticacionService).
 */
public class PerfilService {

    public static final Duration VIGENCIA_CODIGO = Duration.ofMinutes(15);
    public static final int MAXIMO_INTENTOS = 5;

    private final RepositorioUsuarios usuarios;
    private final VerificadorClaves verificador;
    private final EnviadorCorreo enviador;
    private final Clock reloj;
    private final SecureRandom azar = new SecureRandom();

    public PerfilService(RepositorioUsuarios usuarios, VerificadorClaves verificador, EnviadorCorreo enviador, Clock reloj) {
        this.usuarios = usuarios;
        this.verificador = verificador;
        this.enviador = enviador;
        this.reloj = reloj;
    }

    /** Los datos del perfil: usuario, nombre, correo, rol, si el correo esta verificado y si el envio es de demostracion. */
    public Map<String, Object> ver(String usuario) throws PersistenciaException {
        CuentaUsuario c = cuenta(usuario);
        RepositorioUsuarios.Verificacion v = usuarios.verificacion(c.usuario());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("usuario", c.usuario());
        m.put("nombre", c.nombre());
        m.put("correo", c.correo() == null ? "" : c.correo());
        m.put("rol", c.rol().getNombre());
        m.put("correoVerificado", v.correoVerificado());
        m.put("codigoPendiente", v.codigoHash() != null && v.venceEn() > reloj.millis());
        m.put("demostracion", enviador.esDemostracion());
        return m;
    }

    /**
     * Cambia nombre y correo. Cambiar el correo pide la contraseña actual y deja el correo sin verificar.
     *
     * @return el mensaje para mostrar
     */
    public String actualizarDatos(String usuario, String nombre, String correo, String claveActual) throws PersistenciaException {
        CuentaUsuario c = cuenta(usuario);
        String nombreLimpio = AutenticacionService.validarNombre(nombre);
        String correoLimpio = AutenticacionService.validarCorreo(correo);
        boolean otroCorreo = !correoLimpio.equals(RepositorioUsuarios.normalizar(c.correo()));
        if (otroCorreo) {
            exigirClaveActual(c, claveActual, "Para cambiar el correo escriba su contraseña actual.");
        }
        if (usuarios.actualizarDatos(c.usuario(), nombreLimpio, correoLimpio) == RepositorioUsuarios.Alta.CORREO_EN_USO) {
            throw new ReglaNegocioException("Ese correo ya está registrado en otra cuenta.");
        }
        return otroCorreo ? "Datos guardados. Su correo nuevo queda sin verificar: pida un código para verificarlo." : "Datos guardados.";
    }

    /** Cambia la contraseña: pide la actual, y la nueva cumple las 5 reglas y coincide con la confirmacion. */
    public void cambiarClave(String usuario, String claveActual, String nueva, String confirmacion) throws PersistenciaException {
        CuentaUsuario c = cuenta(usuario);
        exigirClaveActual(c, claveActual, "Escriba su contraseña actual.");
        String limpia = AutenticacionService.validarClave(nueva, confirmacion);
        usuarios.cambiarClave(c.usuario(), verificador.hash(limpia));
    }

    /** Crea un codigo de 6 digitos que vence en 15 minutos, guarda su hash y lo envia (en demostracion, a la consola). */
    public String enviarCodigo(String usuario) throws PersistenciaException {
        CuentaUsuario c = cuenta(usuario);
        if (c.correo() == null || c.correo().isBlank()) {
            throw new ReglaNegocioException("Primero escriba su correo en «Mis datos».");
        }
        if (usuarios.verificacion(c.usuario()).correoVerificado()) {
            throw new ReglaNegocioException("Su correo ya está verificado.");
        }
        String codigo = String.format("%06d", azar.nextInt(1_000_000));
        usuarios.guardarCodigo(c.usuario(), verificador.hash(codigo), reloj.millis() + VIGENCIA_CODIGO.toMillis(), 0);
        enviador.enviarCodigo(c.correo(), codigo);
        return enviador.esDemostracion()
                ? "Modo demostración: no se envió un correo real. El código está en la consola del servidor (en el escritorio, en una ventana). Vence en 15 minutos."
                : "Le enviamos un código a " + c.correo() + ". Vence en 15 minutos.";
    }

    /** Revisa el codigo: debe existir, no estar vencido ni con los 5 intentos agotados, y coincidir. */
    public void verificarCodigo(String usuario, String codigo) throws PersistenciaException {
        CuentaUsuario c = cuenta(usuario);
        RepositorioUsuarios.Verificacion v = usuarios.verificacion(c.usuario());
        if (v.correoVerificado()) {
            throw new ReglaNegocioException("Su correo ya está verificado.");
        }
        if (v.codigoHash() == null) {
            throw new ReglaNegocioException("Primero pida un código.");
        }
        if (reloj.millis() > v.venceEn()) {
            usuarios.guardarCodigo(c.usuario(), null, 0, 0);
            throw new ReglaNegocioException("El código venció. Pida uno nuevo.");
        }
        if (v.intentos() >= MAXIMO_INTENTOS) {
            usuarios.guardarCodigo(c.usuario(), null, 0, 0);
            throw new ReglaNegocioException("Agotó los " + MAXIMO_INTENTOS + " intentos. Pida un código nuevo.");
        }
        String escrito = codigo == null ? "" : codigo.trim();
        if (!escrito.matches("\\d{6}") || !verificador.coincide(escrito, v.codigoHash())) {
            int intentos = v.intentos() + 1;
            if (intentos >= MAXIMO_INTENTOS) {
                usuarios.guardarCodigo(c.usuario(), null, 0, 0);
                throw new ReglaNegocioException("Código incorrecto. Agotó los " + MAXIMO_INTENTOS + " intentos: pida un código nuevo.");
            }
            usuarios.guardarCodigo(c.usuario(), v.codigoHash(), v.venceEn(), intentos);
            int quedan = MAXIMO_INTENTOS - intentos;
            throw new ReglaNegocioException("Código incorrecto. Le " + (quedan == 1 ? "queda 1 intento." : "quedan " + quedan + " intentos."));
        }
        usuarios.marcarVerificado(c.usuario());
    }

    private CuentaUsuario cuenta(String usuario) throws PersistenciaException {
        return usuarios.buscar(usuario).orElseThrow(() -> new ReglaNegocioException("LOGIN", "Inicie sesión para continuar."));
    }

    private void exigirClaveActual(CuentaUsuario c, String clave, String siFalta) {
        if (clave == null || clave.isEmpty()) {
            throw new ReglaNegocioException(siFalta);
        }
        if (!verificador.coincide(clave, c.claveHash())) {
            throw new ReglaNegocioException("La contraseña actual no es correcta.");
        }
    }
}
