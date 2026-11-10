package alphasport.model;

public enum EstadoPartido {
    PENDIENTE("Pendiente"),
    EN_JUEGO("En juego"), // se esta jugando: marcador, tarjetas y cambios en vivo, aun no cuenta en la tabla
    JUGADO("Jugado"),
    APLAZADO("Aplazado"),
    SUSPENDIDO("Suspendido");

    private final String texto;

    EstadoPartido(String texto) {
        this.texto = texto;
    }

    @Override
    public String toString() {
        return texto;
    }
}
