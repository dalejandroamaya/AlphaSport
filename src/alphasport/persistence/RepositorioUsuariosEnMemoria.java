package alphasport.persistence;

import alphasport.model.CuentaUsuario;
import alphasport.model.Rol;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Cuentas en memoria: las de demostracion del prototipo y las de las pruebas. */
public class RepositorioUsuariosEnMemoria implements RepositorioUsuarios {

    private final Map<String, CuentaUsuario> cuentas = new LinkedHashMap<>();

    public RepositorioUsuariosEnMemoria(List<CuentaUsuario> cuentas) {
        cuentas.forEach(c -> this.cuentas.put(clave(c.usuario()), c));
    }

    /**
     * Las cuentas de demostracion: admin (administrador) y visitante (solo lectura). Solo se guarda el hash de
     * sus claves (las del README); se generan con {@code java ... alphasport.service.ClavesPbkdf2 <clave>}.
     */
    public static RepositorioUsuariosEnMemoria demostracion() {
        return new RepositorioUsuariosEnMemoria(List.of(
                new CuentaUsuario("admin", "Administrador", Rol.ORGANIZADOR,
                        "pbkdf2_sha256$600000$ORYWJGZO2MLaUsJJD1StqA$eJmZnrPDP4/FNdM4k5l/HEFmhFxS4qPKtrYueWmiJB8"),
                new CuentaUsuario("visitante", "Visitante", Rol.AFICIONADO,
                        "pbkdf2_sha256$600000$sleF8gem07Djtx8KO31+QQ$YrnucN8AoK6aG/heRS6AhI4oRY2WAGF7eHy89aGrAc4")));
    }

    @Override
    public Optional<CuentaUsuario> buscar(String usuario) {
        return Optional.ofNullable(cuentas.get(clave(usuario)));
    }

    private static String clave(String usuario) {
        return usuario == null ? "" : usuario.trim().toLowerCase();
    }
}
