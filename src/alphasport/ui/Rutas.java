package alphasport.ui;

import alphasport.model.Permiso;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Tabla de rutas de la API: cada ruta tiene el {@link Permiso} que exige y lo que hace. La llenan los
 * {@link ModuloRutas} (uno por modulo: sesion, campeonato, equipos, partidos, votacion, noticias); agregar una
 * ruta es agregar una linea en su modulo, con su permiso al lado.
 *
 * @param <A> tipo de manejador: {@link Consulta} o {@link Accion}
 */
final class Rutas<A> {

    /** Atiende una consulta (GET): solo lee. */
    @FunctionalInterface
    interface Consulta {
        Map<String, Object> atender(Peticion p);
    }

    /** Atiende una accion (POST): puede cambiar datos. */
    @FunctionalInterface
    interface Accion {
        Map<String, Object> atender(Peticion p) throws Exception;
    }

    /** @param permiso lo que exige; null = ruta publica (sin sesion) */
    record Ruta<A>(Permiso permiso, A manejador) {
    }

    /** Ruta encontrada y lo que sigue al prefijo (por ejemplo, el id en «partido/12»). */
    record Encontrada<A>(Ruta<A> ruta, String resto) {
    }

    private final Map<String, Ruta<A>> exactas = new LinkedHashMap<>();
    private final Map<String, Ruta<A>> prefijos = new LinkedHashMap<>();

    /** Ruta que no exige sesion (entrar, salir, catalogo). */
    Rutas<A> publica(String ruta, A manejador) {
        return poner(exactas, ruta, new Ruta<>(null, manejador));
    }

    Rutas<A> con(Permiso permiso, String ruta, A manejador) {
        return poner(exactas, ruta, new Ruta<>(Objects.requireNonNull(permiso, ruta), manejador));
    }

    /** Ruta con datos al final: «partido/» atiende «partido/12» y «partido/12/dispositivo». */
    Rutas<A> conPrefijo(Permiso permiso, String prefijo, A manejador) {
        return poner(prefijos, prefijo, new Ruta<>(Objects.requireNonNull(permiso, prefijo), manejador));
    }

    private Rutas<A> poner(Map<String, Ruta<A>> tabla, String ruta, Ruta<A> r) {
        if (exactas.containsKey(ruta) || prefijos.containsKey(ruta)) {
            throw new IllegalStateException("Ruta repetida: " + ruta);
        }
        tabla.put(ruta, r);
        return this;
    }

    Optional<Encontrada<A>> buscar(String ruta) {
        Ruta<A> exacta = exactas.get(ruta);
        if (exacta != null) {
            return Optional.of(new Encontrada<>(exacta, ""));
        }
        return prefijos.entrySet().stream().filter(e -> ruta.startsWith(e.getKey())).findFirst()
                .map(e -> new Encontrada<>(e.getValue(), ruta.substring(e.getKey().length())));
    }

    /** Rutas registradas con su permiso (null = publica), para revisarlas en las pruebas. */
    Map<String, Permiso> permisos() {
        Map<String, Permiso> todas = new LinkedHashMap<>();
        exactas.forEach((k, v) -> todas.put(k, v.permiso()));
        prefijos.forEach((k, v) -> todas.put(k + "…", v.permiso()));
        return Collections.unmodifiableMap(todas);
    }

    Set<String> nombres() {
        return permisos().keySet();
    }
}
