package alphasport.service;

/**
 * Operacion rechazada por una regla del campeonato; el mensaje se muestra al usuario.
 */
public class ReglaNegocioException extends RuntimeException {

    private final String codigo;

    public ReglaNegocioException(String mensaje) {
        this("REGLA", mensaje);
    }

    public ReglaNegocioException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
