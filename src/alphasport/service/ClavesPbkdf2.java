package alphasport.service;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Hash de claves con PBKDF2-HMAC-SHA256 (viene con Java, sin librerias). El texto guardado es
 * {@code pbkdf2_sha256$iteraciones$sal$hash} (sal y hash en Base64): lleva sus propias iteraciones, asi que
 * se pueden subir mas adelante sin invalidar las claves ya guardadas. Cabe en usuarios.password_hash.
 */
public final class ClavesPbkdf2 implements VerificadorClaves {

    public static final String PREFIJO = "pbkdf2_sha256";
    /** Iteraciones recomendadas por OWASP (2023) para PBKDF2-HMAC-SHA256. */
    public static final int ITERACIONES = 600_000;
    private static final int BYTES_SAL = 16;
    private static final int BITS_HASH = 256;

    private final int iteraciones;
    private final SecureRandom azar = new SecureRandom();

    public ClavesPbkdf2() {
        this(ITERACIONES);
    }

    /** Menos iteraciones solo para pruebas rapidas. */
    public ClavesPbkdf2(int iteraciones) {
        this.iteraciones = iteraciones;
    }

    @Override
    public String hash(String clave) {
        byte[] sal = new byte[BYTES_SAL];
        azar.nextBytes(sal);
        Base64.Encoder b64 = Base64.getEncoder().withoutPadding();
        return PREFIJO + "$" + iteraciones + "$" + b64.encodeToString(sal) + "$" + b64.encodeToString(derivar(clave, sal, iteraciones));
    }

    @Override
    public boolean coincide(String clave, String hash) {
        if (clave == null || hash == null) {
            return false;
        }
        String[] partes = hash.split("\\$");
        if (partes.length != 4 || !partes[0].equals(PREFIJO)) {
            return false;
        }
        try {
            int veces = Integer.parseInt(partes[1]);
            byte[] sal = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            if (veces < 1 || sal.length == 0 || esperado.length == 0) {
                return false;
            }
            // Comparacion en tiempo constante: no revela cuantos bytes coinciden.
            return MessageDigest.isEqual(esperado, derivar(clave, sal, veces));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derivar(String clave, byte[] sal, int veces) {
        PBEKeySpec spec = new PBEKeySpec(clave.toCharArray(), sal, veces, BITS_HASH);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Java no tiene PBKDF2WithHmacSHA256", e);
        } finally {
            spec.clearPassword();
        }
    }

    /** Genera el hash de una clave para ponerlo en la tabla usuarios: {@code java ... ClavesPbkdf2 <clave>}. */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Uso: java -cp bin alphasport.service.ClavesPbkdf2 <clave>");
            return;
        }
        System.out.println(new ClavesPbkdf2().hash(args[0]));
    }
}
