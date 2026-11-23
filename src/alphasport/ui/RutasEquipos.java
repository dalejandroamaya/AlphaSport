package alphasport.ui;

import static alphasport.ui.Peticion.entero;
import static alphasport.ui.Peticion.lista;
import static alphasport.ui.Peticion.mapa;
import static alphasport.ui.Peticion.objeto;
import static alphasport.ui.Peticion.texto;

import alphasport.model.DirectorTecnico;
import alphasport.model.Jugador;
import alphasport.model.Permiso;
import alphasport.service.EquiposService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Rutas de equipos y plantillas: crear, editar, quitar, fotos, escudos, divisiones y datos de prueba. */
final class RutasEquipos implements ModuloRutas {

    private final EquiposService equipos;

    RutasEquipos(EquiposService equipos) {
        this.equipos = equipos;
    }

    @Override
    public void registrar(Rutas<Rutas.Consulta> consultas, Rutas<Rutas.Accion> acciones) {
        Permiso gestionar = Permiso.GESTIONAR_CAMPEONATO;
        acciones.con(gestionar, "equipo", p -> {
            guardarEquipo(p.cuerpo());
            return p.listo(null);
        }).con(gestionar, "equipo/rapido", p -> {
            equipos.agregarEquipo(p.texto("nombre"));
            return p.listo(null);
        }).con(gestionar, "equipo/division", p -> {
            equipos.cambiarDivision(p.entero("id"), p.entero("division"));
            return p.listo(null);
        }).con(gestionar, "equipo/quitar", p -> p.listo(equipos.quitarEquipo(p.entero("id"))))
                .con(gestionar, "equipo/escudo", p -> p.listo(equipos.cambiarEscudo(p.entero("id"), p.texto("imagen"))))
                .con(gestionar, "jugador/foto", p -> p.listo(equipos.cambiarFotoJugador(p.entero("equipo"), p.entero("jugador"), p.texto("imagen"))))
                .con(gestionar, "semilla", p -> p.listo(equipos.cargarDatosPrueba(p.entero("semilla"), p.entero("cantidad"),
                        !Boolean.FALSE.equals(p.cuerpo().get("completar")))));
    }

    private void guardarEquipo(Map<String, Object> cuerpo) throws Exception {
        Map<String, Object> dt = objeto(cuerpo, "dt");
        DirectorTecnico director = dt == null || texto(dt, "nombre").isBlank() ? null
                : new DirectorTecnico(texto(dt, "nombre").trim(), texto(dt, "documento").trim(), texto(dt, "nacionalidad").trim());
        List<Jugador> jugadores = new ArrayList<>();
        for (Object o : lista(cuerpo, "jugadores")) {
            Map<String, Object> j = mapa(o);
            Jugador jugador = new Jugador(texto(j, "nombre").trim(), texto(j, "documento").trim(),
                    entero(j.get("dorsal")), texto(j, "posicion"));
            jugador.setId(j.get("id") == null ? 0 : entero(j.get("id")));
            jugador.setFoto(texto(j, "foto")); // foto nueva (data URL), la que tenia o ""
            jugadores.add(jugador);
        }
        Object id = cuerpo.get("id");
        equipos.guardarEquipo(id == null ? null : entero(id), texto(cuerpo, "nombre"), texto(cuerpo, "estadio"),
                director, jugadores, cuerpo.containsKey("escudo") ? texto(cuerpo, "escudo") : null);
    }
}
