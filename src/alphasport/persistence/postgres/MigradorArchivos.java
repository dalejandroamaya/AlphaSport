package alphasport.persistence.postgres;

import alphasport.model.Campeonato;
import alphasport.model.Comentario;
import alphasport.model.Noticia;
import alphasport.persistence.ArchivoRepositorioCampeonato;
import alphasport.persistence.ArchivoRepositorioHistorial;
import alphasport.persistence.ArchivoRepositorioNoticias;
import alphasport.persistence.PersistenciaException;
import java.nio.file.Path;
import java.util.List;

/**
 * Pasa a PostgreSQL lo que habia en la carpeta datos/ (campeonato.dat, historial/ y noticias.dat). No borra los
 * archivos ni reemplaza lo que ya este en la base de datos: lo que existe se salta.
 */
public final class MigradorArchivos {

    private MigradorArchivos() {
    }

    public static void main(String[] args) {
        Path carpeta = Path.of(args.length > 0 ? args[0] : "datos");
        try (ConexionDB conexion = new ConexionDB(ConexionDB.leerConfiguracion(ConexionDB.ARCHIVO))) {
            conexion.verificar();
            System.out.println("Pasando " + carpeta + " a " + conexion.getConfiguracion());
            migrar(conexion, carpeta).forEach(linea -> System.out.println("  " + linea));
        } catch (PersistenciaException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    /** @return que se paso y que se salto, una linea por cada cosa */
    public static List<String> migrar(ConexionDB conexion, Path carpeta) throws PersistenciaException {
        return List.of(campeonato(conexion, carpeta), historial(conexion, carpeta), noticias(conexion, carpeta));
    }

    private static String campeonato(ConexionDB conexion, Path carpeta) throws PersistenciaException {
        ArchivoRepositorioCampeonato archivo = new ArchivoRepositorioCampeonato(carpeta);
        if (!archivo.existe()) {
            return "Campeonato: no hay archivo.";
        }
        PostgresRepositorioCampeonato base = new PostgresRepositorioCampeonato(conexion);
        if (base.existe()) {
            return "Campeonato: la base de datos ya tiene uno en curso; no se cambió.";
        }
        Campeonato c = archivo.cargar();
        base.guardar(c);
        return "Campeonato: «" + c.getNombreCampeonato() + "» (" + c.getEquipos().size() + " equipos, " + c.getPartidos().size() + " partidos).";
    }

    private static String historial(ConexionDB conexion, Path carpeta) throws PersistenciaException {
        PostgresRepositorioHistorial base = new PostgresRepositorioHistorial(conexion);
        List<String> existentes = base.listar().stream().map(Campeonato::getCodigo).toList();
        int pasados = 0;
        for (Campeonato c : new ArchivoRepositorioHistorial(carpeta).listar()) {
            if (!existentes.contains(c.getCodigo())) {
                base.archivar(c);
                pasados++;
            }
        }
        return "Historial: " + pasados + " campeonatos.";
    }

    private static String noticias(ConexionDB conexion, Path carpeta) throws PersistenciaException {
        PostgresRepositorioNoticias base = new PostgresRepositorioNoticias(conexion, carpeta.resolve("imagenes"));
        if (!base.publicadas().isEmpty() || !base.pendientes().isEmpty()) {
            return "Noticias: la base de datos ya tiene noticias; no se cambió.";
        }
        List<Noticia> todas = new ArchivoRepositorioNoticias(carpeta).todas();
        for (Noticia n : todas) {
            base.insertar(n);
            for (Comentario c : n.getComentarios()) {
                base.insertarComentario(n.getId(), c);
            }
        }
        return "Noticias: " + todas.size() + ".";
    }
}
