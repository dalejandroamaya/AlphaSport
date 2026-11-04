package alphasport.model;

public enum EstadoPartido {
    PENDIENTE("Pendiente"),
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
