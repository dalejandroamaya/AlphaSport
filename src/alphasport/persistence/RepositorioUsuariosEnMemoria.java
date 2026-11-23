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

    public RepositorioUsuariosEnMemoria() {
    }

    public RepositorioUsuariosEnMemoria(List<CuentaUsuario> cuentas) {
        cuentas.forEach(c -> this.cuentas.put(RepositorioUsuarios.normalizar(c.usuario()), c));
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
        cuentas.put(RepositorioUsuarios.normalizar(cuenta.usuario()), cuenta);
        return Alta.CREADA;
    }
}
