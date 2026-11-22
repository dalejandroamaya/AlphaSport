package alphasport.service;

import alphasport.persistence.PersistenciaException;
import alphasport.persistence.RepositorioFotos;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * Fotos de los jugadores y escudos de los equipos.
 *
 * Toda foto se normaliza antes de guardarse, asi se ven ordenadas en cualquier pantalla:
 * <ul>
 * <li><b>Jugador, tipo cedula</b>: vertical 3:4, recortada al centro (un poco hacia arriba,
 * donde suele estar la cara) y reducida a 240 x 320 px en JPG.</li>
 * <li><b>Escudo</b>: se encaja completo (sin recortar) en un cuadrado de 256 x 256 px con
 * fondo transparente, en PNG.</li>
 * </ul>
 * La interfaz envia la imagen como «data URL» (igual que las fotos de noticias). Se acepta
 * JPG, PNG o GIF de hasta 5 MB y 40 megapixeles; las fotos de celular se enderezan
 * segun su orientacion EXIF.
 */
public class FotosService {

    public static final int ANCHO_CEDULA = 240;
    public static final int ALTO_CEDULA = 320;
    public static final int LADO_ESCUDO = 256;
    /** Mas pixeles que esto no se abre (evita que una imagen gigante agote la memoria). */
    public static final long MAXIMO_PIXELES = 40_000_000L;
    /** Nombre valido de una foto guardada (lo demas se rechaza: nada de rutas). */
    private static final String PATRON_NOMBRE = "(jugador|escudo)-[a-z0-9]{12}\\.(jpg|png)";

    private final RepositorioFotos repositorio;

    public FotosService(RepositorioFotos repositorio) {
        this.repositorio = repositorio;
    }

    /** true si el texto es una foto nueva enviada por la interfaz (no el nombre de una ya guardada). */
    public static boolean esNueva(String valor) {
        return valor != null && valor.startsWith("data:");
    }

    /** true si el texto es el nombre de una foto ya guardada. */
    public static boolean esNombreValido(String nombre) {
        return nombre != null && nombre.matches(PATRON_NOMBRE);
    }

    /** Recorta la foto al formato cedula, la guarda y devuelve su nombre. */
    public String guardarJugador(String dataUrl) throws PersistenciaException {
        return repositorio.guardar(tipoCedula(leer(dataUrl)), "jugador", "jpg");
    }

    /** Encaja el escudo en el cuadrado, lo guarda y devuelve su nombre. */
    public String guardarEscudo(String dataUrl) throws PersistenciaException {
        return repositorio.guardar(escudo(leer(dataUrl)), "escudo", "png");
    }

    /**
     * Resuelve el valor que manda la interfaz para una foto: una nueva (data URL) se guarda,
     * el nombre de una ya guardada se conserva y cualquier otra cosa (o "") queda sin foto.
     */
    public String resolver(String valor, boolean esEscudo) throws PersistenciaException {
        if (esNueva(valor)) {
            return esEscudo ? guardarEscudo(valor) : guardarJugador(valor);
        }
        return esNombreValido(valor) ? valor : "";
    }

    /** Bytes de una foto guardada (null si el nombre no es valido o no existe). */
    public byte[] leerGuardada(String nombre) throws PersistenciaException {
        return esNombreValido(nombre) ? repositorio.leer(nombre) : null;
    }

    /* ---------------------------------------------------------------- lectura */

    /** Decodifica la data URL y la abre como imagen (ya enderezada). */
    static BufferedImage leer(String dataUrl) {
        NoticiasService.Imagen imagen = NoticiasService.imagenDesdeDataUrl(dataUrl);
        if (imagen == null) {
            throw new ReglaNegocioException("No se recibió ninguna foto.");
        }
        if (imagen.extension().equals("webp")) {
            throw new ReglaNegocioException("Las fotos WEBP no se pueden recortar: use una JPG o PNG.");
        }
        BufferedImage img;
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(imagen.datos()))) {
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext()) {
                throw new ReglaNegocioException("No se pudo abrir la foto: use una imagen JPG o PNG.");
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(entrada, true, true);
                long pixeles = (long) lector.getWidth(0) * lector.getHeight(0);
                if (pixeles > MAXIMO_PIXELES) {
                    throw new ReglaNegocioException("La foto es demasiado grande (máximo 40 megapíxeles).");
                }
                img = lector.read(0);
            } finally {
                lector.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ReglaNegocioException r) {
                throw r;
            }
            throw new ReglaNegocioException("La foto está dañada o no se puede abrir.");
        }
        if (img == null || img.getWidth() < 1 || img.getHeight() < 1) {
            throw new ReglaNegocioException("La foto está vacía.");
        }
        return enderezar(img, orientacionExif(imagen.datos()));
    }

    /* ---------------------------------------------------------------- formatos */

    /** Vertical 3:4, recortada al centro (con la cara arriba) y reducida a 240 x 320, en JPG. */
    static byte[] tipoCedula(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        double proporcion = (double) ANCHO_CEDULA / ALTO_CEDULA;
        int cx, cy, cw, ch;
        if ((double) w / h > proporcion) { // mas ancha que una cedula: se recortan los lados
            ch = h;
            cw = Math.max(1, (int) Math.round(h * proporcion));
            cx = (w - cw) / 2;
            cy = 0;
        } else {                             // mas alta: se recorta abajo y un poco arriba
            cw = w;
            ch = Math.max(1, (int) Math.round(w / proporcion));
            cx = 0;
            cy = (int) Math.round((h - ch) * 0.3);
        }
        BufferedImage recorte = img.getSubimage(cx, cy, Math.min(cw, w - cx), Math.min(ch, h - cy));
        BufferedImage destino = new BufferedImage(ANCHO_CEDULA, ALTO_CEDULA, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        g.setColor(Color.WHITE); // las PNG con transparencia quedan sobre fondo blanco, como una cedula
        g.fillRect(0, 0, ANCHO_CEDULA, ALTO_CEDULA);
        dibujar(g, reducir(recorte, ANCHO_CEDULA, ALTO_CEDULA), 0, 0, ANCHO_CEDULA, ALTO_CEDULA);
        g.dispose();
        return jpg(destino, 0.88f);
    }

    /** El escudo completo, centrado en un cuadrado transparente de 256 x 256, en PNG. */
    static byte[] escudo(BufferedImage img) {
        int margen = 8, caja = LADO_ESCUDO - 2 * margen;
        double escala = Math.min((double) caja / img.getWidth(), (double) caja / img.getHeight());
        int w = Math.max(1, (int) Math.round(img.getWidth() * escala));
        int h = Math.max(1, (int) Math.round(img.getHeight() * escala));
        BufferedImage destino = new BufferedImage(LADO_ESCUDO, LADO_ESCUDO, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = destino.createGraphics();
        dibujar(g, reducir(img, w, h), (LADO_ESCUDO - w) / 2, (LADO_ESCUDO - h) / 2, w, h);
        g.dispose();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            ImageIO.write(destino, "png", salida);
        } catch (IOException e) {
            throw new ReglaNegocioException("No se pudo procesar el escudo.");
        }
        return salida.toByteArray();
    }

    /** Reduce a la mitad varias veces antes del ultimo paso: mas nitido que reducir de golpe. */
    private static BufferedImage reducir(BufferedImage img, int w, int h) {
        BufferedImage actual = aArgb(img);
        while (actual.getWidth() / 2 >= w && actual.getHeight() / 2 >= h) {
            BufferedImage mitad = new BufferedImage(actual.getWidth() / 2, actual.getHeight() / 2, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = mitad.createGraphics();
            dibujar(g, actual, 0, 0, mitad.getWidth(), mitad.getHeight());
            g.dispose();
            actual = mitad;
        }
        return actual;
    }

    private static BufferedImage aArgb(BufferedImage img) {
        if (img.getType() == BufferedImage.TYPE_INT_ARGB) {
            return img;
        }
        BufferedImage copia = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copia.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return copia;
    }

    private static void dibujar(Graphics2D g, BufferedImage img, int x, int y, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(img, x, y, w, h, null);
    }

    private static byte[] jpg(BufferedImage img, float calidad) {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream salida = ImageIO.createImageOutputStream(bytes)) {
            escritor.setOutput(salida);
            ImageWriteParam p = escritor.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(calidad);
            escritor.write(null, new IIOImage(img, null, null), p);
        } catch (IOException e) {
            throw new ReglaNegocioException("No se pudo procesar la foto.");
        } finally {
            escritor.dispose();
        }
        return bytes.toByteArray();
    }

    /* ---------------------------------------------------------------- orientacion de las fotos de celular */

    /** Gira la imagen segun la etiqueta EXIF (3 = 180°, 6 = 90° a la derecha, 8 = 90° a la izquierda). */
    static BufferedImage enderezar(BufferedImage img, int orientacion) {
        int grados = switch (orientacion) {
            case 3, 4 -> 180;
            case 5, 6 -> 90;
            case 7, 8 -> 270;
            default -> 0;
        };
        if (grados == 0) {
            return img;
        }
        int w = img.getWidth(), h = img.getHeight();
        boolean gira = grados != 180;
        BufferedImage destino = new BufferedImage(gira ? h : w, gira ? w : h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = destino.createGraphics();
        g.translate(destino.getWidth() / 2.0, destino.getHeight() / 2.0);
        g.rotate(Math.toRadians(grados));
        g.drawImage(img, -w / 2, -h / 2, null);
        g.dispose();
        return destino;
    }

    /** Valor de la etiqueta EXIF «Orientation» de un JPG (1 si no tiene). */
    static int orientacionExif(byte[] d) {
        try {
            if (d.length < 4 || (d[0] & 0xFF) != 0xFF || (d[1] & 0xFF) != 0xD8) {
                return 1;
            }
            int i = 2;
            while (i + 4 < d.length && (d[i] & 0xFF) == 0xFF) {
                int marca = d[i + 1] & 0xFF;
                int largo = ((d[i + 2] & 0xFF) << 8) | (d[i + 3] & 0xFF);
                if (marca == 0xE1 && i + 10 < d.length && d[i + 4] == 'E' && d[i + 5] == 'x' && d[i + 6] == 'i' && d[i + 7] == 'f') {
                    int tiff = i + 10;
                    boolean intel = d[tiff] == 'I';
                    int ifd = tiff + leerEntero(d, tiff + 4, 4, intel);
                    int entradas = leerEntero(d, ifd, 2, intel);
                    for (int k = 0; k < entradas; k++) {
                        int e = ifd + 2 + k * 12;
                        if (leerEntero(d, e, 2, intel) == 0x0112) {
                            return leerEntero(d, e + 8, 2, intel);
                        }
                    }
                    return 1;
                }
                if (marca == 0xDA) {
                    return 1; // empieza la imagen: ya no hay EXIF
                }
                i += 2 + largo;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            // EXIF incompleto: se usa la foto como viene
        }
        return 1;
    }

    private static int leerEntero(byte[] d, int pos, int bytes, boolean intel) {
        int v = 0;
        for (int k = 0; k < bytes; k++) {
            int b = d[pos + k] & 0xFF;
            v = intel ? v | (b << (8 * k)) : (v << 8) | b;
        }
        return v;
    }
}
