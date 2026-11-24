package alphasport.service;

import alphasport.model.CuentaUsuario;
import alphasport.model.Rol;
import alphasport.model.Usuario;
import alphasport.persistence.PersistenciaException;
import alphasport.persistence.RepositorioUsuarios;
import alphasport.persistence.RepositorioUsuariosEnMemoria;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Registro de cuentas e inicio de sesion. Las cuentas las da un {@link RepositorioUsuarios} (el servicio no
 * sabe si estan en memoria o en la base de datos) y la clave se guarda y se compara como hash con un
 * {@link VerificadorClaves}: en ningun lado se guarda ni se muestra la clave.
 */
public class AutenticacionService {

    public static final int USUARIO_MINIMO = 3;
    public static final int USUARIO_MAXIMO = 30;
    public static final int NOMBRE_MINIMO = 2;
    public static final int NOMBRE_MAXIMO = 80;
    public static final int CORREO_MAXIMO = 120;
    public static final int CLAVE_MINIMA = 8;
    public static final int CLAVE_MAXIMA = 128;

    /** Letras sin tilde, numeros, punto, guion y guion bajo; empieza con letra o numero. */
    private static final Pattern USUARIO = Pattern.compile("[a-z0-9][a-z0-9._-]{" + (USUARIO_MINIMO - 1) + "," + (USUARIO_MAXIMO - 1) + "}");
    /** algo@dominio.ext, sin espacios. La misma regla que la pantalla de registro (web y escritorio). */
    private static final Pattern CORREO = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");

    /**
     * Lo que envia la pantalla «Registrarse».
     *
     * @param tipo ORGANIZADOR o AFICIONADO: cualquier otro valor se rechaza
     */
    public record SolicitudRegistro(String usuario, String nombre, String correo, String clave, String confirmacion, String tipo) {

        /** No muestra las claves si la solicitud se imprime en un mensaje o en un log. */
        @Override
        public String toString() {
            return "SolicitudRegistro[usuario=" + usuario + ", tipo=" + tipo + "]";
        }
    }

    private final RepositorioUsuarios usuarios;
    private final VerificadorClaves verificador;
    /** Hash de una clave que nadie usa: con un usuario que no existe se hace el mismo trabajo (no se nota en el tiempo). */
    private final String hashFicticio;

    public AutenticacionService(RepositorioUsuarios usuarios, VerificadorClaves verificador) {
        this.usuarios = usuarios;
        this.verificador = verificador;
        this.hashFicticio = verificador.hash(java.util.UUID.randomUUID().toString());
    }

    /** Solo para pruebas: las cuentas de prueba en memoria (admin y visitante) y PBKDF2. */
    public AutenticacionService() {
        this(RepositorioUsuariosEnMemoria.demostracion(), new ClavesPbkdf2());
    }

    /**
     * Inicia sesion con usuario y clave. Si falla, el mensaje es el mismo exista o no el usuario.
     *
     * @throws ReglaNegocioException con codigo LOGIN si los datos no coinciden.
     */
    public Usuario iniciarSesion(String usuario, String clave) throws PersistenciaException {
        Optional<CuentaUsuario> cuenta = usuarios.buscar(usuario);
        boolean coincide = verificador.coincide(clave == null ? "" : clave, cuenta.map(CuentaUsuario::claveHash).orElse(hashFicticio));
        if (cuenta.isEmpty() || !coincide) {
            throw new ReglaNegocioException("LOGIN", "Usuario o contraseña incorrectos.");
        }
        return cuenta.get().comoUsuario();
    }

    /**
     * Crea una cuenta Organizador o Aficionado y devuelve el usuario para abrirle la sesion. Primero valida
     * todos los datos; la clave se guarda solo como hash.
     *
     * @throws ReglaNegocioException con el primer dato que no sirve, o si el usuario o el correo ya estan en uso.
     */
    public Usuario registrar(SolicitudRegistro datos) throws PersistenciaException {
        String usuario = validarUsuario(datos.usuario());
        String nombre = validarNombre(datos.nombre());
        String correo = validarCorreo(datos.correo());
        String clave = validarClave(datos.clave(), datos.confirmacion());
        Rol rol = validarTipo(datos.tipo());
        CuentaUsuario cuenta = new CuentaUsuario(usuario, nombre, correo, rol, verificador.hash(clave));
        switch (usuarios.crear(cuenta)) {
            case USUARIO_EN_USO -> throw new ReglaNegocioException("Ese nombre de usuario ya está en uso. Elija otro.");
            case CORREO_EN_USO -> throw new ReglaNegocioException("Ese correo ya está registrado. Inicie sesión o use otro correo.");
            case CREADA -> {
            }
        }
        // Se vuelve a leer para tener su id (la llave de la tabla usuarios), que identifica al dueno de sus campeonatos.
        return usuarios.buscar(usuario).orElseThrow(() -> new ReglaNegocioException("No se pudo leer la cuenta recien creada.")).comoUsuario();
    }

    /* ---------------------------------------------------------------- validaciones */

    static String validarUsuario(String texto) {
        String usuario = RepositorioUsuarios.normalizar(texto);
        if (usuario.isEmpty()) {
            throw new ReglaNegocioException("Escriba un nombre de usuario.");
        }
        if (usuario.length() < USUARIO_MINIMO || usuario.length() > USUARIO_MAXIMO) {
            throw new ReglaNegocioException("El nombre de usuario debe tener entre " + USUARIO_MINIMO + " y " + USUARIO_MAXIMO + " caracteres.");
        }
        if (!USUARIO.matcher(usuario).matches()) {
            throw new ReglaNegocioException("El nombre de usuario solo puede tener letras sin tilde, números, punto, guion y guion bajo, "
                    + "y debe empezar con una letra o un número.");
        }
        return usuario;
    }

    static String validarNombre(String texto) {
        String nombre = texto == null ? "" : texto.trim().replaceAll("\\s+", " ");
        if (nombre.length() < NOMBRE_MINIMO || nombre.length() > NOMBRE_MAXIMO) {
            throw new ReglaNegocioException("Escriba su nombre (entre " + NOMBRE_MINIMO + " y " + NOMBRE_MAXIMO + " caracteres).");
        }
        return nombre;
    }

    static String validarCorreo(String texto) {
        String correo = RepositorioUsuarios.normalizar(texto);
        if (correo.isEmpty()) {
            throw new ReglaNegocioException("Escriba su correo.");
        }
        if (correo.length() > CORREO_MAXIMO || !CORREO.matcher(correo).matches()) {
            throw new ReglaNegocioException("Escriba un correo válido, por ejemplo nombre@correo.com.");
        }
        return correo;
    }

    static String validarClave(String clave, String confirmacion) {
        if (clave == null || clave.length() < CLAVE_MINIMA || clave.length() > CLAVE_MAXIMA) {
            throw new ReglaNegocioException("La contraseña debe tener entre " + CLAVE_MINIMA + " y " + CLAVE_MAXIMA + " caracteres.");
        }
        if (!clave.codePoints().anyMatch(Character::isLetter) || !clave.codePoints().anyMatch(Character::isDigit)) {
            throw new ReglaNegocioException("La contraseña debe tener letras y números.");
        }
        if (!clave.equals(confirmacion)) {
            throw new ReglaNegocioException("Las contraseñas no coinciden.");
        }
        return clave;
    }

    /** Solo ORGANIZADOR o AFICIONADO: un valor enviado por el cliente nunca puede dar otro rol. */
    static Rol validarTipo(String tipo) {
        if (Rol.ORGANIZADOR.name().equals(tipo)) {
            return Rol.ORGANIZADOR;
        }
        if (Rol.AFICIONADO.name().equals(tipo)) {
            return Rol.AFICIONADO;
        }
        throw new ReglaNegocioException("Elija el tipo de cuenta: Organizador o Aficionado.");
    }
}
