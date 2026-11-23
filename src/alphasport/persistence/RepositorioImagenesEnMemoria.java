package alphasport.persistence;

import java.util.HashMap;
import java.util.Map;

/** Imagenes en memoria, para las pruebas. */
public class RepositorioImagenesEnMemoria implements RepositorioImagenes {

    private final Map<String, byte[]> imagenes = new HashMap<>();
    private int siguiente = 1;

    @Override
    public String guardar(byte[] datos, String extension) {
        String nombre = "noticia-" + (siguiente++) + "." + extension;
        imagenes.put(nombre, datos.clone());
        return nombre;
    }

    @Override
    public byte[] leer(String nombre) {
        byte[] datos = imagenes.get(nombre);
        return datos == null ? null : datos.clone();
    }

    @Override
    public void eliminar(String nombre) {
        imagenes.remove(nombre);
    }
}
