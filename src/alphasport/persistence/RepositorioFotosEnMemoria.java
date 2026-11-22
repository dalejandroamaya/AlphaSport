package alphasport.persistence;

import java.util.HashMap;
import java.util.Map;

/** Fotos solo en memoria (se pierden al cerrar). La usan las pruebas automaticas. */
public class RepositorioFotosEnMemoria implements RepositorioFotos {

    private final Map<String, byte[]> fotos = new HashMap<>();
    private int siguiente = 1;

    @Override
    public String guardar(byte[] datos, String prefijo, String extension) {
        String nombre = prefijo + "-" + String.format("%012d", siguiente++) + "." + extension;
        fotos.put(nombre, datos.clone());
        return nombre;
    }

    @Override
    public byte[] leer(String nombre) {
        byte[] d = fotos.get(nombre);
        return d == null ? null : d.clone();
    }
}
