package alphasport.ui;

import static alphasport.ui.Peticion.entero;
import static alphasport.ui.Peticion.mapa;

import alphasport.model.Permiso;
import alphasport.service.CalificacionesPartido;
import alphasport.service.VotacionService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** [REQ-07] Rutas de la votacion del publico: partidos abiertos, votar y cerrar la votacion. */
final class RutasVotacion implements ModuloRutas {

    private final VotacionService votacion;
    private final Presentador presentador;

    RutasVotacion(VotacionService votacion, Presentador presentador) {
        this.votacion = votacion;
        this.presentador = presentador;
    }

    @Override
    public void registrar(Rutas<Rutas.Consulta> consultas, Rutas<Rutas.Accion> acciones) {
        // votaciones/{dispositivo}: los partidos que este navegador o equipo puede votar ahora.
        consultas.conPrefijo(Permiso.VER, "votaciones/", p -> Presentador.mapa("ok", true, "votaciones", presentador.votaciones(p.resto())));
        acciones.con(Permiso.VOTAR_JUGADORES, "calificacion/votar", this::votar)
                .con(Permiso.GESTIONAR_CAMPEONATO, "votacion/cerrar", p -> {
                    votacion.cerrarVotacion(p.entero("partido"));
                    return p.listo("Votación cerrada: las notas finales quedaron definitivas.");
                });
    }

    /**
     * Voto del publico: {partido, dispositivo, votos: [{jugador, valor (1 a 10)}]} o, para un solo jugador,
     * {partido, jugador, valor, dispositivo}. Solo votan los visitantes (permiso VOTAR_JUGADORES): el
     * administrador ya influye en la nota del sistema al registrar goles y tarjetas.
     */
    private Map<String, Object> votar(Peticion p) throws Exception {
        Map<Integer, Integer> notas = new LinkedHashMap<>();
        for (Object o : p.tiene("votos") ? p.lista("votos") : List.<Object>of(p.cuerpo())) {
            notas.put(entero(mapa(o).get("jugador")), entero(mapa(o).get("valor")));
        }
        CalificacionesPartido calificaciones = votacion.votarJugadores(p.entero("partido"), notas, p.texto("dispositivo"));
        Map<String, Object> r = p.listo(notas.size() == 1 ? "¡Gracias! Su calificación quedó registrada."
                : "¡Gracias! Sus " + notas.size() + " calificaciones quedaron registradas.");
        r.put("calificaciones", Presentador.calificaciones(calificaciones));
        return r;
    }
}
