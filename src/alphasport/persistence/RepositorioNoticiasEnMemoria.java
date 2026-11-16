package alphasport.persistence;

import alphasport.model.Noticia;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Noticias y fotos solo en memoria (listas y un mapa). Todo se pierde al cerrar el
 * programa. Sirve para pruebas o si se quiere el modulo sin ningun archivo.
 */
public class RepositorioNoticiasEnMemoria implements RepositorioNoticias {

    private final List<Noticia> noticias = new ArrayList<>();
    private final Map<String, byte[]> imagenes = new HashMap<>();
    private int siguienteImagen = 1;

    @Override
    public void guardar(List<Noticia> lista) {
        noticias.clear();
        noticias.addAll(lista);
    }

    @Override
    public List<Noticia> cargar() {
        return new ArrayList<>(noticias);
    }

    @Override
    public String guardarImagen(byte[] datos, String extension) {
        String nombre = "noticia-" + (siguienteImagen++) + "." + extension;
        imagenes.put(nombre, datos.clone());
        return nombre;
    }

    @Override
    public byte[] leerImagen(String nombre) {
        byte[] datos = imagenes.get(nombre);
        return datos == null ? null : datos.clone();
    }

    @Override
    public void eliminarImagen(String nombre) {
        imagenes.remove(nombre);
    }
}
