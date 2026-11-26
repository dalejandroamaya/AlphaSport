package alphasport.service;

/**
 * Envia el codigo para verificar el correo. El servicio no sabe como se envia: hoy la unica version es
 * {@link EnviadorCorreoDemostracion} (sin libreria de correo); una version real solo tendria que implementar esto.
 */
public interface EnviadorCorreo {

    /** Envia el codigo de 6 digitos al correo. */
    void enviarCodigo(String correo, String codigo);

    /** true si es la version de demostracion (no envia correos de verdad): la pantalla lo dice. */
    default boolean esDemostracion() {
        return false;
    }
}
