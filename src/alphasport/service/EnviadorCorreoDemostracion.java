package alphasport.service;

import java.util.function.BiConsumer;

/**
 * MODO DEMOSTRACION: no envia correos. Escribe el codigo en la consola del servidor y, si el escritorio
 * registro un aviso, se lo pasa para mostrarlo en un dialogo. Sirve para probar la verificacion sin una
 * libreria ni una cuenta de correo.
 */
public class EnviadorCorreoDemostracion implements EnviadorCorreo {

    /** Lo pone el escritorio para mostrar el codigo en un dialogo (correo, codigo). La web no lo usa. */
    private static volatile BiConsumer<String, String> avisoEscritorio;

    public static void alEnviar(BiConsumer<String, String> aviso) {
        avisoEscritorio = aviso;
    }

    @Override
    public void enviarCodigo(String correo, String codigo) {
        System.out.println("[MODO DEMOSTRACIÓN] Código para verificar " + correo + ": " + codigo + " (vence en 15 minutos)");
        BiConsumer<String, String> aviso = avisoEscritorio;
        if (aviso != null) {
            aviso.accept(correo, codigo);
        }
    }

    @Override
    public boolean esDemostracion() {
        return true;
    }
}
