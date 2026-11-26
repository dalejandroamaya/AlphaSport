package alphasport.service;

import alphasport.model.DirectorTecnico;
import alphasport.model.Equipo;
import alphasport.model.Jugador;
import alphasport.persistence.PersistenciaException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Equipos y plantillas del campeonato en curso: crearlos, editarlos, quitarlos, sus fotos y escudos, la division
 * de cada uno y los datos de prueba.
 */
public class EquiposService {

    private final ContextoCampeonato ctx;

    public EquiposService(ContextoCampeonato ctx) {
        this.ctx = ctx;
    }

    public void agregarEquipo(String nombre) throws PersistenciaException {
        ctx.exigirCampeonato();
        ctx.exigirNoIniciado();
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("Escriba el nombre del equipo.");
        }
        int division = ctx.campeonato().divisionConMenosEquipos();
        Equipo nuevo = ctx.campeonato().agregarEquipo(nombre.trim());
        if (nuevo == null) {
            throw new ReglaNegocioException("El equipo «" + nombre.trim() + "» ya existe.");
        }
        nuevo.setDivision(division);
        ctx.guardar();
    }

    /** Pasa un equipo a otra division (Liga A = 0). */
    public void cambiarDivision(int idEquipo, int division) throws PersistenciaException {
        ctx.exigirLigaSinIniciar();
        Equipo equipo = ctx.buscarEquipo(idEquipo);
        if (division < 0 || division >= ctx.campeonato().getConfiguracion().getDivisiones()) {
            throw new ReglaNegocioException("Esa división no existe.");
        }
        equipo.setDivision(division);
        ctx.guardar();
    }

    public void guardarEquipo(Integer idEquipo, String nombre, String estadio, DirectorTecnico dt, List<Jugador> jugadores)
            throws PersistenciaException {
        guardarEquipo(idEquipo, nombre, estadio, dt, jugadores, null);
    }

    /**
     * Crea o edita un equipo. La foto de cada jugador y el {@code escudo} pueden ser una foto nueva
     * (data URL, se normaliza y se guarda), el nombre de la que ya tenia o "" (sin foto).
     * {@code escudo} null deja el escudo como estaba.
     */
    public void guardarEquipo(Integer idEquipo, String nombre, String estadio, DirectorTecnico dt, List<Jugador> jugadores,
            String escudo) throws PersistenciaException {
        ctx.exigirCampeonato();
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty()) {
            throw new ReglaNegocioException("Debe ingresar un nombre.");
        }
        Equipo existente = idEquipo == null ? null : ctx.buscarEquipo(idEquipo);
        Equipo homonimo = ctx.campeonato().buscarEquipoPorNombre(limpio);
        if (homonimo != null && homonimo != existente) {
            throw new ReglaNegocioException("El equipo " + limpio + " ya existe.");
        }
        validarDorsales(jugadores);
        ReglasPlantilla.validarFormatoDocumentos(dt, jugadores); // solo números, de 5 a 12 dígitos
        jugadores.forEach(j -> j.setDocumento(ReglasPlantilla.soloDigitos(j.getDocumento())));
        if (dt != null) {
            dt.setDocumento(ReglasPlantilla.soloDigitos(dt.getDocumento()));
        }
        ReglasPlantilla.validarDocumentos(ctx.campeonato(), existente, jugadores); // un documento, un jugador
        Equipo equipo = existente;
        if (equipo == null) {
            ctx.exigirNoIniciado();
            equipo = ctx.campeonato().agregarEquipo(limpio);
        }
        equipo.setNombreEquipo(limpio);
        equipo.setEstadio(estadio == null ? "" : estadio.trim());
        equipo.setDirectorTecnico(dt);
        for (Jugador j : jugadores) {
            if (j.getId() == 0) {
                j.setId(ctx.campeonato().nuevoIdJugador());
            } else if (existente != null && ctx.campeonato().buscarJugador(existente.getId(), j.getId()) != null) {
                // La pantalla reconstruye el jugador: sigue siendo la misma persona.
                j.setCodigoPersona(ctx.campeonato().buscarJugador(existente.getId(), j.getId()).getCodigoPersona());
            }
            j.setFoto(ctx.fotos().resolver(j.getFoto(), false));
        }
        if (escudo != null) {
            equipo.setEscudo(ctx.fotos().resolver(escudo, true));
        }
        equipo.setJugadores(new ArrayList<>(jugadores));
        ctx.guardar();
    }

    /** Cambia (data URL) o quita ("" o null) la foto tipo cedula de un jugador. */
    public String cambiarFotoJugador(int idEquipo, int idJugador, String dataUrl) throws PersistenciaException {
        ctx.exigirCampeonato();
        Jugador j = ctx.campeonato().buscarJugador(idEquipo, idJugador);
        if (j == null) {
            throw new ReglaNegocioException("Ese jugador ya no está en la plantilla.");
        }
        boolean quitar = dataUrl == null || dataUrl.isBlank();
        j.setFoto(quitar ? "" : ctx.fotos().guardarJugador(dataUrl));
        ctx.guardar();
        return quitar ? "Foto de " + j.getNombre() + " eliminada." : "Foto de " + j.getNombre() + " actualizada.";
    }

    /** Cambia (data URL) o quita ("" o null) el escudo de un equipo. */
    public String cambiarEscudo(int idEquipo, String dataUrl) throws PersistenciaException {
        ctx.exigirCampeonato();
        Equipo e = ctx.buscarEquipo(idEquipo);
        boolean quitar = dataUrl == null || dataUrl.isBlank();
        e.setEscudo(quitar ? "" : ctx.fotos().guardarEscudo(dataUrl));
        ctx.guardar();
        return quitar ? "Escudo de " + e.getNombreEquipo() + " eliminado." : "Escudo de " + e.getNombreEquipo() + " actualizado.";
    }

    /**
     * Carga equipos de prueba con plantilla completa generados con una semilla
     * (la misma semilla produce siempre los mismos datos). Si se pide, tambien
     * llena la plantilla de los equipos que todavia no tienen jugadores.
     */
    public String cargarDatosPrueba(int semilla, int cantidad, boolean completarExistentes) throws PersistenciaException {
        ctx.exigirCampeonato();
        int pedidos = Math.max(0, Math.min(DatosPrueba.MAXIMO_EQUIPOS, cantidad));
        if (pedidos > 0) {
            ctx.exigirNoIniciado();
        }
        List<Equipo> sinPlantilla = new ArrayList<>();
        List<String> usados = new ArrayList<>();
        for (Equipo e : ctx.campeonato().getEquipos()) {
            usados.add(e.getNombreEquipo());
            if (completarExistentes && !e.isRetirado() && e.getJugadores().isEmpty()) {
                sinPlantilla.add(e);
            }
        }
        if (pedidos == 0 && sinPlantilla.isEmpty()) {
            throw new ReglaNegocioException(completarExistentes ? "Todos los equipos ya tienen plantilla. Indique cuántos equipos nuevos crear."
                    : "Indique cuántos equipos de prueba crear.");
        }
        DatosPrueba.Resultado r = DatosPrueba.generar(semilla, pedidos, usados, sinPlantilla.size());
        // La misma semilla da los mismos documentos: los que ya estan en el campeonato se cambian por otros libres.
        Set<String> documentos = ReglasPlantilla.documentosEnUso(ctx.campeonato());
        r.equipos().forEach(e -> documentosLibres(e.jugadores(), documentos));
        r.plantillas().forEach(p -> documentosLibres(p, documentos));
        for (DatosPrueba.EquipoPrueba e : r.equipos()) {
            int division = ctx.campeonato().divisionConMenosEquipos(); // reparte los equipos entre las divisiones
            Equipo equipo = ctx.campeonato().agregarEquipo(e.nombre());
            equipo.setDivision(division);
            equipo.setEstadio(e.estadio());
            equipo.setDirectorTecnico(e.dt());
            asignarIds(e.jugadores());
            equipo.setJugadores(e.jugadores());
        }
        for (int i = 0; i < sinPlantilla.size(); i++) {
            ArrayList<Jugador> jugadores = r.plantillas().get(i);
            asignarIds(jugadores);
            sinPlantilla.get(i).setJugadores(jugadores);
        }
        ctx.guardar();
        int creados = r.equipos().size();
        String texto = creados == 0 ? "" : creados + (creados == 1 ? " equipo de prueba creado" : " equipos de prueba creados");
        if (!sinPlantilla.isEmpty()) {
            texto += (texto.isEmpty() ? "Plantilla completada en " : "; plantilla completada en ") + sinPlantilla.size()
                    + (sinPlantilla.size() == 1 ? " equipo" : " equipos");
        }
        if (creados < pedidos) {
            texto += " (no hay más nombres de prueba libres)";
        }
        return texto + ". Semilla " + semilla + ".";
    }

    /** Cambia los documentos ya usados por el siguiente numero libre (y los anota como usados). */
    private static void documentosLibres(List<Jugador> jugadores, Set<String> usados) {
        for (Jugador j : jugadores) {
            String documento = j.getDocumento();
            while (!usados.add(ReglasPlantilla.claveDocumento(documento))) {
                documento = documento.matches("\\d{1,17}") ? String.valueOf(Long.parseLong(documento) + 1) : documento + "1";
            }
            j.setDocumento(documento);
        }
    }

    private void asignarIds(List<Jugador> jugadores) {
        for (Jugador j : jugadores) {
            j.setId(ctx.campeonato().nuevoIdJugador());
        }
    }

    public String quitarEquipo(int idEquipo) throws PersistenciaException {
        ctx.exigirCampeonato();
        String mensaje = ctx.motor().retirarEquipo(ctx.buscarEquipo(idEquipo));
        ctx.guardar();
        return mensaje;
    }

    private void validarDorsales(List<Jugador> jugadores) {
        Set<Integer> usados = new HashSet<>();
        for (Jugador j : jugadores) {
            if (j.getNombre() == null || j.getNombre().isBlank()) {
                throw new ReglaNegocioException("Escriba el nombre del jugador.");
            }
            if (j.getDorsal() < 1 || j.getDorsal() > 99) {
                throw new ReglaNegocioException("El dorsal debe estar entre 1 y 99.");
            }
            if (!usados.add(j.getDorsal())) {
                throw new ReglaNegocioException("El dorsal " + j.getDorsal() + " ya está en uso.");
            }
        }
    }
}
