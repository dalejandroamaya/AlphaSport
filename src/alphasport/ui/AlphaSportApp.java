package alphasport.ui;

import alphasport.persistence.ArchivoRepositorioCampeonato;
import alphasport.persistence.ArchivoRepositorioHistorial;
import alphasport.service.AutenticacionService;
import alphasport.service.CampeonatoService;
import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Punto de entrada: arma las capas (persistence, service, ui), inicia el
 * servidor local y abre la interfaz en el navegador.
 */
public class AlphaSportApp {

    private static final int PUERTO = 8080;

    public static void main(String[] args) throws Exception {
        Path carpetaWeb = Path.of("web");
        if (!Files.isRegularFile(carpetaWeb.resolve("index.html"))) {
            System.err.println("No se encontro la carpeta web/. Ejecute el programa desde la carpeta del proyecto.");
            System.exit(1);
        }
        Path carpetaDatos = Path.of("datos");
        CampeonatoService servicio = new CampeonatoService(
                new ArchivoRepositorioCampeonato(carpetaDatos), new ArchivoRepositorioHistorial(carpetaDatos));
        servicio.cargarGuardado();
        ServidorWeb servidor = new ServidorWeb(carpetaWeb, servicio, new ApiControlador(servicio, new AutenticacionService()));
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
