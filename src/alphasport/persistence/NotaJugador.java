package alphasport.persistence;

/**
 * Nota de un jugador en un partido, ya calculada por el servicio, para guardarla.
 */
public record NotaJugador(int idJugador, double notaSistema, int votos, double promedioPublico, double notaFinal, boolean mvp) {
}
