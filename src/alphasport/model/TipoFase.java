package alphasport.model;

public enum TipoFase {
    LIGA,              // todos contra todos en una sola tabla
    GRUPOS,            // varias mini ligas (tambien se usa para cuadrangulares)
    SUIZO,             // una tabla, rondas emparejadas segun la clasificacion
    ELIMINATORIA,      // llave de eliminacion directa
    DOBLE_ELIMINACION  // llave de ganadores + llave de perdedores
}
