package alphasport.service;

/**
 * Convierte una clave en un hash que se puede guardar y revisa si una clave coincide con el. Nunca se guarda
 * la clave: si alguien lee la tabla usuarios, no obtiene las claves.
 */
public interface VerificadorClaves {

    /** Hash nuevo de la clave (con su propia sal: dos hashes de la misma clave son distintos). */
    String hash(String clave);

    /** true si la clave corresponde al hash guardado; false si no, o si el hash no tiene un formato conocido. */
    boolean coincide(String clave, String hash);
}
