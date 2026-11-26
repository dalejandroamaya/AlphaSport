package alphasport.service;

import alphasport.model.CuentaUsuario;
import alphasport.model.Rol;
import alphasport.model.Usuario;
import alphasport.persistence.PersistenciaException;
import alphasport.persistence.RepositorioUsuarios;
import alphasport.persistence.RepositorioUsuariosEnMemoria;
import java.util.ArrayList;
import java.util.List;
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
    public static final int CORREO_MAXIMO = 254;
    public static final int CORREO_LOCAL_MAXIMO = 64;
    public static final int CORREO_ETIQUETA_MAXIMA = 63;
    public static final int CLAVE_MINIMA = 8;
    public static final int CLAVE_MAXIMA = 128;

    /** Letras sin tilde, numeros, punto, guion y guion bajo; empieza con letra o numero. */
    private static final Pattern USUARIO = Pattern.compile("[a-z0-9][a-z0-9._-]{" + (USUARIO_MINIMO - 1) + "," + (USUARIO_MAXIMO - 1) + "}");

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

    /**
     * Valida el correo regla por regla y explica la primera que no cumple (el mismo texto que cuentas.js):
     * una sola @ y hasta 254 caracteres; antes de la @, de 1 a 64 letras, numeros y . _ % + - sin punto al
     * inicio, al final ni dos seguidos; despues, partes separadas por punto de 1 a 63 letras, numeros o guion
     * (sin guion al inicio ni al final) y una extension final de al menos 2 letras.
     */
    static String validarCorreo(String texto) {
        String correo = RepositorioUsuarios.normalizar(texto);
        String problema = problemaCorreo(correo);
        if (problema != null) {
            throw new ReglaNegocioException(problema);
        }
        return correo;
    }

    /** Por que no sirve el correo (se pasa a minusculas y sin espacios alrededor), o null si sirve. */
    public static String problemaCorreo(String texto) {
        String correo = RepositorioUsuarios.normalizar(texto);
        if (correo.isEmpty()) {
            return "Escriba su correo.";
        }
        if (correo.length() > CORREO_MAXIMO) {
            return "El correo no puede tener más de " + CORREO_MAXIMO + " caracteres.";
        }
        int arroba = correo.indexOf('@');
        if (arroba < 0) {
            return "Al correo le falta la @, por ejemplo nombre@correo.com.";
        }
        if (correo.indexOf('@', arroba + 1) >= 0) {
            return "El correo solo puede tener una @.";
        }
        String local = correo.substring(0, arroba);
        String dominio = correo.substring(arroba + 1);
        if (local.isEmpty()) {
            return "Falta el nombre antes de la @.";
        }
        if (local.length() > CORREO_LOCAL_MAXIMO) {
            return "El nombre antes de la @ no puede tener más de " + CORREO_LOCAL_MAXIMO + " caracteres.";
        }
        for (int i = 0; i < local.length(); i++) {
            char c = local.charAt(i);
            if (!letraONumero(c) && "._%+-".indexOf(c) < 0) {
                return "Antes de la @ solo se permiten letras sin tilde, números y los signos . _ % + -";
            }
        }
        if (local.startsWith(".") || local.endsWith(".")) {
            return "El nombre antes de la @ no puede empezar ni terminar en punto.";
        }
        if (local.contains("..")) {
            return "El nombre antes de la @ no puede tener puntos seguidos.";
        }
        if (dominio.isEmpty()) {
            return "Falta el dominio después de la @, por ejemplo correo.com.";
        }
        if (dominio.startsWith(".")) {
            return "El dominio no puede empezar con punto.";
        }
        if (dominio.endsWith(".")) {
            return "El dominio no puede terminar en punto.";
        }
        if (dominio.contains("..")) {
            return "El dominio no puede tener puntos seguidos.";
        }
        String[] etiquetas = dominio.split("\\.");
        if (etiquetas.length < 2) {
            return "Al dominio le falta la extensión, por ejemplo .com.";
        }
        for (String etiqueta : etiquetas) {
            if (etiqueta.length() > CORREO_ETIQUETA_MAXIMA) {
                return "Cada parte del dominio puede tener como máximo " + CORREO_ETIQUETA_MAXIMA + " caracteres.";
            }
            for (int i = 0; i < etiqueta.length(); i++) {
                char c = etiqueta.charAt(i);
                if (!letraONumero(c) && c != '-') {
                    return "El dominio solo puede tener letras sin tilde, números, guiones y puntos.";
                }
            }
            if (etiqueta.startsWith("-") || etiqueta.endsWith("-")) {
                return "Las partes del dominio no pueden empezar ni terminar en guion.";
            }
        }
        String extension = etiquetas[etiquetas.length - 1];
        if (extension.length() < 2 || !extension.chars().allMatch(c -> c >= 'a' && c <= 'z')) {
            return "La extensión del dominio (después del último punto) debe tener solo letras y al menos 2, por ejemplo .com.";
        }
        return null;
    }

    /** Letra sin tilde (ya en minusculas) o numero. */
    private static boolean letraONumero(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    static String validarClave(String clave, String confirmacion) {
        String problema = problemaClave(clave);
        if (problema != null) {
            throw new ReglaNegocioException(problema);
        }
        if (!clave.equals(confirmacion)) {
            throw new ReglaNegocioException("Las contraseñas no coinciden.");
        }
        return clave;
    }

    /**
     * Una regla de la contraseña para la lista que se marca mientras se escribe.
     *
     * @param texto lo que muestra la lista (por ejemplo «Una letra mayúscula»)
     * @param falta como se nombra en el mensaje «Falta: …» (por ejemplo «una mayúscula»)
     */
    public record ReglaClave(String texto, String falta, boolean cumple) {
    }

    /** Lo que se encontro al recorrer la clave carácter por carácter. */
    private record RevisionClave(List<ReglaClave> reglas, boolean espacios, boolean control, boolean larga) {
    }

    /**
     * Recorre la clave carácter por carácter (igual que cuentas.js) y anota que tiene: mayuscula, minuscula,
     * numero, caracter especial (ni letra, ni numero, ni espacio), espacios y caracteres de control.
     */
    private static RevisionClave revisarClave(String clave) {
        String c = clave == null ? "" : clave;
        boolean mayuscula = false, minuscula = false, numero = false, especial = false, espacios = false, control = false;
        for (int i = 0; i < c.length(); ) {
            int cp = c.codePointAt(i);
            i += Character.charCount(cp);
            int tipo = Character.getType(cp);
            if (Character.isSpaceChar(cp) || Character.isWhitespace(cp)) {
                espacios = true;
            } else if (tipo == Character.CONTROL) {
                control = true;
            } else if (tipo == Character.UPPERCASE_LETTER) {
                mayuscula = true;
            } else if (tipo == Character.LOWERCASE_LETTER) {
                minuscula = true;
            } else if (tipo == Character.DECIMAL_DIGIT_NUMBER) {
                numero = true;
            } else if (!Character.isLetter(cp)) {
                especial = true;
            }
        }
        List<ReglaClave> reglas = List.of(
                new ReglaClave("Entre " + CLAVE_MINIMA + " y " + CLAVE_MAXIMA + " caracteres", "al menos " + CLAVE_MINIMA + " caracteres",
                        c.length() >= CLAVE_MINIMA && c.length() <= CLAVE_MAXIMA),
                new ReglaClave("Una letra mayúscula", "una mayúscula", mayuscula),
                new ReglaClave("Una letra minúscula", "una minúscula", minuscula),
                new ReglaClave("Un número", "un número", numero),
                new ReglaClave("Un carácter especial (por ejemplo ! # $ % *)", "un carácter especial", especial));
        return new RevisionClave(reglas, espacios, control, c.length() > CLAVE_MAXIMA);
    }

    /** Las 5 reglas de la contraseña y si esta clave las cumple, para marcarlas en la pantalla de registro. */
    public static List<ReglaClave> reglasClave(String clave) {
        return revisarClave(clave).reglas();
    }

    /**
     * Por que no sirve la clave, nombrando todo lo que falta (por ejemplo «Falta: un número y un carácter
     * especial.»), o null si sirve. No revisa la confirmacion. El mismo texto que cuentas.js.
     */
    public static String problemaClave(String clave) {
        RevisionClave r = revisarClave(clave);
        List<String> partes = new ArrayList<>();
        if (r.larga()) {
            partes.add("La contraseña no puede tener más de " + CLAVE_MAXIMA + " caracteres.");
        }
        if (r.espacios()) {
            partes.add("La contraseña no puede tener espacios.");
        }
        if (r.control()) {
            partes.add("La contraseña no puede tener caracteres de control.");
        }
        List<String> faltan = new ArrayList<>();
        for (ReglaClave regla : r.reglas()) {
            if (!regla.cumple() && !(r.larga() && regla == r.reglas().get(0))) {
                faltan.add(regla.falta());
            }
        }
        if (!faltan.isEmpty()) {
            partes.add("Falta: " + enLista(faltan) + ".");
        }
        return partes.isEmpty() ? null : String.join(" ", partes);
    }

    /** «a», «a y b», «a, b y c». */
    private static String enLista(List<String> cosas) {
        if (cosas.size() == 1) {
            return cosas.get(0);
        }
        return String.join(", ", cosas.subList(0, cosas.size() - 1)) + " y " + cosas.get(cosas.size() - 1);
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
