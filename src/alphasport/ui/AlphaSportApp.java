package alphasport.ui;

import java.awt.Desktop;
import java.net.URI;

/**
 * Version web del programa Java: inicia el servidor local y abre la interfaz en el navegador.
 * <p>
 * En internet (Render) el proveedor da la variable PORT: entonces atiende la direccion publica
 * (RENDER_EXTERNAL_HOSTNAME, o ALPHASPORT_HOST en otro proveedor) y no abre ningun navegador.
 */
public class AlphaSportApp {

    private static final int PUERTO = 8080;

    public static void main(String[] args) throws Exception {
        FabricaAplicacion fabrica = new FabricaAplicacion();
        ServidorWeb servidor;
        try {
            servidor = fabrica.servidorWeb();
        } catch (alphasport.persistence.PersistenciaException e) {
            System.err.println(e.getMessage());
            fabrica.close();
            System.exit(1);
            return;
        }
        String puertoInternet = System.getenv("PORT");
        if (puertoInternet != null && !puertoInternet.isBlank()) {
            String anfitrion = System.getenv("RENDER_EXTERNAL_HOSTNAME");
            if (anfitrion == null || anfitrion.isBlank()) {
                anfitrion = System.getenv("ALPHASPORT_HOST");
            }
            if (anfitrion == null || anfitrion.isBlank()) {
                System.err.println("Falta la dirección pública: defina ALPHASPORT_HOST (por ejemplo alphasport.onrender.com).");
                fabrica.close();
                System.exit(1);
                return;
            }
            servidor.iniciarEnInternet(Integer.parseInt(puertoInternet.trim()), anfitrion.trim());
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                servidor.detener();
                fabrica.close();
            }));
            System.out.println("AlphaSport está publicado en https://" + anfitrion.trim() + "/ (puerto " + puertoInternet.trim() + ")");
            return;
        }
        servidor.alSalir(() -> System.exit(0)); // «Cerrar programa» en la web termina este proceso
        int puerto = servidor.iniciar(PUERTO);
        String direccion = "http://127.0.0.1:" + puerto + "/";
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            servidor.detener();
            fabrica.close();
        }));
        System.out.println("AlphaSport esta funcionando en " + direccion);
        System.out.println("Para cerrar el programa use 'Cerrar programa' en el menu Archivo o presione Ctrl+C aqui.");
        if (args.length == 0 || !args[0].equals("--sin-navegador")) {
            abrirNavegador(direccion);
        }
    }

    private static void abrirNavegador(String direccion) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(direccion));
                return;
            }
        } catch (Exception e) {
            System.err.println("No se pudo abrir el navegador automaticamente: " + e.getMessage());
        }
        System.out.println("Abra esta direccion en su navegador: " + direccion);
    }
}
