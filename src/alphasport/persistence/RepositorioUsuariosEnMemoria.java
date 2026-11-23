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

    /** Las cuentas de demostracion: admin (administrador) y visitante (solo lectura). */
    public static RepositorioUsuariosEnMemoria demostracion() {
        return new RepositorioUsuariosEnMemoria(List.of(
                new CuentaUsuario("admin", "Administrador", Rol.ADMINISTRADOR, "123"),
                new CuentaUsuario("visitante", "Visitante", Rol.VISITANTE, "321")));
    }

    @Override
    public Optional<CuentaUsuario> buscar(String usuario) {
        return Optional.ofNullable(cuentas.get(clave(usuario)));
    }

    private static String clave(String usuario) {
        return usuario == null ? "" : usuario.trim().toLowerCase();
    }
}
