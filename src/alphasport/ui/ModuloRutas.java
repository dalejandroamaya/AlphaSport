package alphasport.ui;

/**
 * Un modulo de la API (sesion, campeonato, equipos, partidos, votacion, noticias): registra sus consultas y
 * acciones con el permiso de cada una. Un modulo nuevo (usuarios, Fantasy) es una clase nueva, sin tocar las demas.
 */
interface ModuloRutas {

    void registrar(Rutas<Rutas.Consulta> consultas, Rutas<Rutas.Accion> acciones);
}
