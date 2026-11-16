package alphasport.ui.escritorio;

import javafx.application.Application;

/**
 * Punto de entrada de la version de escritorio (JavaFX). Se separa de la clase
 * Application para que JavaFX funcione tambien cargado desde el classpath.
 */
public class AlphaSportEscritorio {

    public static void main(String[] args) {
        Application.launch(Aplicacion.class, args);
    }
}
