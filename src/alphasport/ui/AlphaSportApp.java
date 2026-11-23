package alphasport.ui;

import alphasport.service.AutenticacionService;
import alphasport.service.CampeonatoService;
import java.awt.Desktop;
import java.net.URI;

/**
 * Version web del programa Java: inicia el servidor local y abre la interfaz en el navegador.
 */
public class AlphaSportApp {

    private static final int PUERTO = 8080;

    public static void main(String[] args) throws Exception {
        CampeonatoService servicio;
        ServidorWeb servidor;
        try {
            servicio = Arranque.servicio();
            servidor = new ServidorWeb(Arranque.CARPETA_WEB, servicio, new ApiControlador(servicio, new AutenticacionService(), Arranque.noticias()));
        } catch (alphasport.persistence.PersistenciaException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }
        int puerto = servidor.iniciar(PUERTO);
        String direccion = "http://127.0.0.1:" + puerto + "/";
        Runtime.getRuntime().addShutdownHook(new Thread(servidor::detener));
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
