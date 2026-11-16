package alphasport.persistence;

import alphasport.model.Campeonato;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Convierte un campeonato en bytes y viceversa (serializacion de Java).
 */
public final class Serializacion {

    private Serializacion() {
    }

    public static byte[] aBytes(Campeonato campeonato) throws PersistenciaException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream salida = new ObjectOutputStream(bytes)) {
            salida.writeObject(campeonato);
        } catch (IOException e) {
            throw new PersistenciaException("No se pudo convertir el campeonato", e);
        }
        return bytes.toByteArray();
    }

    public static Campeonato desdeBytes(byte[] datos) throws PersistenciaException {
        try (ObjectInputStream entrada = new ObjectInputStream(new ByteArrayInputStream(datos))) {
            return (Campeonato) entrada.readObject();
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            throw new PersistenciaException("El archivo no es un respaldo valido de AlphaSport", e);
        }
    }

    public static Campeonato copiaProfunda(Campeonato original) throws PersistenciaException {
        return desdeBytes(aBytes(original));
    }
}
