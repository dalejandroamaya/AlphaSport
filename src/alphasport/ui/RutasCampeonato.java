package alphasport.ui;

import static alphasport.ui.Peticion.entero;
import static alphasport.ui.Peticion.lista;
import static alphasport.ui.Peticion.texto;

import alphasport.model.ConfiguracionTorneo;
import alphasport.model.CriterioDesempate;
import alphasport.model.Permiso;
import alphasport.model.TipoFormato;
import alphasport.service.CampeonatoService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Rutas de UN campeonato (el elegido): sus reglas, avanzar, temporadas, divisiones y el estado. */
final class RutasCampeonato implements ModuloRutas {

    private final CampeonatoService servicio;
    private final Presentador presentador;

    RutasCampeonato(CampeonatoService servicio, Presentador presentador) {
        this.servicio = servicio;
        this.presentador = presentador;
    }

    @Override
    public void registrar(Rutas<Rutas.Consulta> consultas, Rutas<Rutas.Accion> acciones) {
        consultas.publica("catalogo", p -> presentador.catalogo())
                .con(Permiso.VER, "estado", p -> p.api().estado());
        Permiso gestionar = Permiso.GESTIONAR_CAMPEONATO;
        // «nuevo», «historial» e «historial/eliminar» no son de un campeonato: estan en RutasCampeonatos.
        acciones.con(gestionar, "cambiarFormato", p -> {
            servicio.cambiarFormato(p.texto("nombre"), TipoFormato.valueOf(p.texto("formato")), configuracion(p.objeto("config")));
            return p.listo(null);
        }).con(gestionar, "cancelar", p -> {
            servicio.cancelar();
            return p.listo(null);
        }).con(gestionar, "criterios", p -> {
            servicio.cambiarCriterios(criterios(p.lista("criterios")));
            return p.listo(null);
        }).con(gestionar, "avanzar", p -> p.listo(servicio.avanzar()))
                .con(gestionar, "siguiente", p -> p.listo(servicio.siguienteTemporada(p.texto("nombre"))))
                .con(gestionar, "division/agregar", p -> p.listo(servicio.agregarDivision()))
                .con(gestionar, "division/quitar", p -> p.listo(servicio.quitarDivision()))
                .con(gestionar, "archivar", p -> {
                    servicio.historial().archivarActual();
                    return p.listo(null);
                });
    }

    static ConfiguracionTorneo configuracion(Map<String, Object> c) {
        ConfiguracionTorneo cfg = new ConfiguracionTorneo();
        cfg.setPuntosVictoria(entero(c.get("puntosVictoria")));
        cfg.setPuntosEmpate(entero(c.get("puntosEmpate")));
        cfg.setIdaYVuelta(Boolean.TRUE.equals(c.get("idaYVuelta")));
        cfg.setEliminatoriaIdaYVuelta(Boolean.TRUE.equals(c.get("eliminatoriaIdaYVuelta")));
        cfg.setFinalUnica(Boolean.TRUE.equals(c.get("finalUnica")));
        cfg.setSorteoAleatorio(Boolean.TRUE.equals(c.get("sorteoAleatorio")));
        cfg.setTamanoGrupo(entero(c.get("tamanoGrupo")));
        cfg.setClasificadosPorGrupo(entero(c.get("clasificadosPorGrupo")));
        cfg.setClasificadosPlayoff(entero(c.get("clasificadosPlayoff")));
        cfg.setTipoPlayoff(texto(c, "tipoPlayoff"));
        cfg.setVentajaDeportiva(Boolean.TRUE.equals(c.get("ventajaDeportiva")));
        cfg.setRondasSuizo(entero(c.get("rondasSuizo")));
        cfg.setDirectosSuizo(entero(c.get("directosSuizo")));
        cfg.setRepechajeSuizo(entero(c.get("repechajeSuizo")));
        cfg.setGranFinalConReinicio(Boolean.TRUE.equals(c.get("granFinalConReinicio")));
        cfg.setDivisiones(c.get("divisiones") == null ? 1 : entero(c.get("divisiones")));
        cfg.setAscensos(c.get("ascensos") == null ? 2 : entero(c.get("ascensos")));
        cfg.setCriterios(criterios(lista(c, "criterios")));
        return cfg;
    }

    static ArrayList<CriterioDesempate> criterios(List<Object> claves) {
        ArrayList<CriterioDesempate> lista = new ArrayList<>();
        for (Object clave : claves) {
            lista.add(CriterioDesempate.valueOf(String.valueOf(clave)));
        }
        return lista;
    }

}
