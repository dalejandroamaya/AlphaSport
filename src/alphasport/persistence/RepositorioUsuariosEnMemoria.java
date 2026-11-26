package alphasport.persistence;

import alphasport.model.CuentaUsuario;
import alphasport.model.Rol;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Cuentas en memoria, para las pruebas: se pierden al cerrar el programa. */
public class RepositorioUsuariosEnMemoria implements RepositorioUsuarios {

    private final Map<String, CuentaUsuario> cuentas = new LinkedHashMap<>();
    private final Map<String, Verificacion> verificaciones = new LinkedHashMap<>();
    /** Id que recibe la siguiente cuenta (como el SERIAL de la tabla usuarios). */
    private int siguienteId = 1;

    public RepositorioUsuariosEnMemoria() {
    }

    public RepositorioUsuariosEnMemoria(List<CuentaUsuario> cuentas) {
        cuentas.forEach(this::agregar);
    }

    /**
     * Dos cuentas de prueba: admin (organizador, clave 123) y visitante (aficionado, clave 321). Ya no se usan
     * para entrar a la aplicacion; solo en las pruebas. Se guarda el hash de las claves, no las claves.
     */
    public static RepositorioUsuariosEnMemoria demostracion() {
        return new RepositorioUsuariosEnMemoria(List.of(
                new CuentaUsuario("admin", "Administrador", Rol.ORGANIZADOR,
                        "pbkdf2_sha256$600000$ORYWJGZO2MLaUsJJD1StqA$eJmZnrPDP4/FNdM4k5l/HEFmhFxS4qPKtrYueWmiJB8"),
                new CuentaUsuario("visitante", "Visitante", Rol.AFICIONADO,
                        "pbkdf2_sha256$600000$sleF8gem07Djtx8KO31+QQ$YrnucN8AoK6aG/heRS6AhI4oRY2WAGF7eHy89aGrAc4")));
    }

    @Override
    public synchronized Optional<CuentaUsuario> buscar(String usuario) {
        return Optional.ofNullable(cuentas.get(RepositorioUsuarios.normalizar(usuario)));
    }

    @Override
    public synchronized boolean existeUsuario(String usuario) {
        return cuentas.containsKey(RepositorioUsuarios.normalizar(usuario));
    }

    @Override
    public synchronized boolean existeCorreo(String correo) {
        String buscado = RepositorioUsuarios.normalizar(correo);
        return !buscado.isEmpty() && cuentas.values().stream().anyMatch(c -> buscado.equals(RepositorioUsuarios.normalizar(c.correo())));
    }

    @Override
    public synchronized Alta crear(CuentaUsuario cuenta) {
        if (existeUsuario(cuenta.usuario())) {
            return Alta.USUARIO_EN_USO;
        }
        if (existeCorreo(cuenta.correo())) {
            return Alta.CORREO_EN_USO;
        }
        agregar(cuenta);
        return Alta.CREADA;
    }

    /** Guarda la cuenta con su propio id (el que traiga, o el siguiente). */
    @Override
    public synchronized Alta actualizarDatos(String usuario, String nombre, String correo) {
        CuentaUsuario c = cuentas.get(RepositorioUsuarios.normalizar(usuario));
        if (c == null) {
            return Alta.CREADA;
        }
        String nuevo = RepositorioUsuarios.normalizar(correo);
        boolean otroCorreo = !nuevo.equals(RepositorioUsuarios.normalizar(c.correo()));
        if (otroCorreo && existeCorreo(nuevo)) {
            return Alta.CORREO_EN_USO;
        }
        cuentas.put(RepositorioUsuarios.normalizar(usuario), new CuentaUsuario(c.usuario(), nombre, nuevo, c.rol(), c.claveHash(), c.id()));
        if (otroCorreo) {
            verificaciones.remove(RepositorioUsuarios.normalizar(usuario)); // otro correo: vuelve a «sin verificar» y sin codigo
        }
        return Alta.CREADA;
    }

    @Override
    public synchronized void cambiarClave(String usuario, String claveHash) {
        CuentaUsuario c = cuentas.get(RepositorioUsuarios.normalizar(usuario));
        if (c != null) {
            cuentas.put(RepositorioUsuarios.normalizar(usuario), new CuentaUsuario(c.usuario(), c.nombre(), c.correo(), c.rol(), claveHash, c.id()));
        }
    }

    @Override
    public synchronized void guardarCodigo(String usuario, String codigoHash, long venceEn, int intentos) {
        String clave = RepositorioUsuarios.normalizar(usuario);
        if (cuentas.containsKey(clave)) {
            boolean verificado = verificacion(clave).correoVerificado();
            verificaciones.put(clave, codigoHash == null ? new Verificacion(verificado, null, 0, 0)
                    : new Verificacion(verificado, codigoHash, venceEn, intentos));
        }
    }

    @Override
    public synchronized Verificacion verificacion(String usuario) {
        return verificaciones.getOrDefault(RepositorioUsuarios.normalizar(usuario), Verificacion.NINGUNA);
    }

    @Override
    public synchronized void marcarVerificado(String usuario) {
        String clave = RepositorioUsuarios.normalizar(usuario);
        if (cuentas.containsKey(clave)) {
            verificaciones.put(clave, new Verificacion(true, null, 0, 0));
        }
    }

    private void agregar(CuentaUsuario cuenta) {
        CuentaUsuario conId = cuenta.id() > 0 ? cuenta : cuenta.conId(siguienteId);
        siguienteId = Math.max(siguienteId, conId.id()) + 1;
        cuentas.put(RepositorioUsuarios.normalizar(conId.usuario()), conId);
    }
}
