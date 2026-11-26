package alphasport.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import alphasport.model.Campeonato;
import alphasport.model.ConfiguracionTorneo;
import alphasport.model.CriterioDesempate;
import alphasport.model.EstadoPartido;
import alphasport.model.Fase;
import alphasport.model.Partido;
import alphasport.model.TipoFormato;

/**
 * Prueba automatica del motor (no necesita JUnit): juega cientos de
 * campeonatos con resultados aleatorios en los 6 formatos y revisa que
 * siempre se llegue a un campeon sin errores. Tambien prueba los
 * desempates y la edicion de partidos.
 *
 * En NetBeans: clic derecho sobre este archivo > Run File (Shift + F6).
 */
public class SimulacionFormatos {

    private static final Random azar = new Random(7);
    private static int fallos = 0;

    public static void main(String[] args) {
        int[] tamanos = {3, 4, 5, 6, 7, 8, 9, 10, 12, 13, 16, 20, 24, 32, 36};
        for (TipoFormato formato : TipoFormato.values()) {
            int completos = 0;
            int total = 0;
            for (int n : tamanos) {
                for (int variante = 0; variante < 6; variante++) {
                    Campeonato c = new Campeonato("Prueba " + n, formato, configuracion(n, variante));
                    for (int i = 1; i <= n; i++) {
                        c.agregarEquipo("Equipo " + i);
                    }
                    String resultado = jugar(new MotorCampeonato(c), n);
                    if (resultado.startsWith("INVALIDO")) {
                        continue; // esa combinacion de reglas no aplica para n equipos
                    }
                    total++;
                    if (resultado.startsWith("OK")) {
                        completos++;
                    } else {
                        fallos++;
                        System.out.println("  FALLO " + formato + " n=" + n + " variante=" + variante + ": " + resultado);
                    }
                }
            }
            System.out.println(formato.getNombre() + ": " + completos + "/" + total + " campeonatos completos");
        }
        probarDesempateDirecto();
        probarEdicion();
        probarSanciones();
        probarResumenYPalmares();
        probarEstadisticas();
        probarDivisiones();
        probarWalkoverYCampeones();
        probarCronograma();
        probarNoticias();
        probarPreviaYResultado();
        probarReglasPartido();
        probarPermisos();
        probarServidorWeb();
        probarClaves();
        probarRegistro();
        probarVariosCampeonatos();
        probarPerfil();
        probarOrdenPorLinea();
        probarDocumentoUnico();
        probarCalificaciones();
        probarFotos();
        System.out.println(fallos == 0 ? "TODO BIEN" : "FALLOS: " + fallos);
    }

    /* ---------------------------------------------------------------- fotos de jugadores y escudos */

    /** Imagen de prueba como «data URL» (la forma en que la interfaz envia las fotos). */
    private static String dataUrl(java.awt.image.BufferedImage img, String formato) throws Exception {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, formato, b);
        return "data:image/" + formato + ";base64," + java.util.Base64.getEncoder().encodeToString(b.toByteArray());
    }

    private static java.awt.image.BufferedImage abrir(byte[] datos) throws Exception {
        return javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(datos));
    }

    /** Mitad izquierda roja y derecha azul (para ver hacia donde quedo girada). */
    private static java.awt.image.BufferedImage dosColores(int w, int h, int tipo) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, tipo);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.RED);
        g.fillRect(0, 0, w / 2, h);
        g.setColor(java.awt.Color.BLUE);
        g.fillRect(w / 2, 0, w - w / 2, h);
        g.dispose();
        return img;
    }

    private static boolean rojo(int rgb) {
        return ((rgb >> 16) & 0xFF) > 180 && (rgb & 0xFF) < 90;
    }

    private static boolean azul(int rgb) {
        return (rgb & 0xFF) > 180 && ((rgb >> 16) & 0xFF) < 90;
    }

    /**
     * Fotos tipo cedula y escudos: formato final, recorte, transparencia, fotos de celular giradas,
     * archivos que no son imagenes o demasiado grandes, permisos y que la foto viaje con el equipo
     * al crear otro campeonato con los mismos equipos. Todo por ApiControlador, como la web y el escritorio.
     */
    private static void probarFotos() {
        boolean correcto;
        try {
            alphasport.persistence.RepositorioFotosEnMemoria repo = new alphasport.persistence.RepositorioFotosEnMemoria();
            FotosService fotos = new FotosService(repo);

            // 1) Formatos: cualquier foto queda en 240x320 (jugador) o 256x256 con transparencia (escudo).
            String apaisada = dataUrl(dosColores(900, 300, java.awt.image.BufferedImage.TYPE_INT_RGB), "png");
            String alargada = dataUrl(dosColores(200, 1200, java.awt.image.BufferedImage.TYPE_INT_RGB), "jpg");
            java.awt.image.BufferedImage c1 = abrir(repo.leer(fotos.guardarJugador(apaisada)));
            java.awt.image.BufferedImage c2 = abrir(repo.leer(fotos.guardarJugador(alargada)));
            java.awt.image.BufferedImage chica = abrir(repo.leer(fotos.guardarJugador(
                    dataUrl(dosColores(30, 20, java.awt.image.BufferedImage.TYPE_INT_RGB), "png"))));
            boolean formatos = c1.getWidth() == 240 && c1.getHeight() == 320 && c2.getWidth() == 240 && c2.getHeight() == 320
                    && chica.getWidth() == 240 && chica.getHeight() == 320;
            // la apaisada se recorta al centro: queda mitad roja y mitad azul
            formatos &= rojo(c1.getRGB(20, 160)) && azul(c1.getRGB(220, 160));
            java.awt.image.BufferedImage transparente = new java.awt.image.BufferedImage(400, 200, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = transparente.createGraphics();
            g.setColor(java.awt.Color.GREEN);
            g.fillOval(0, 0, 400, 200);
            g.dispose();
            java.awt.image.BufferedImage esc = abrir(repo.leer(fotos.guardarEscudo(dataUrl(transparente, "png"))));
            boolean escudo = esc.getWidth() == 256 && esc.getHeight() == 256 && ((esc.getRGB(2, 2) >>> 24) == 0) // esquina transparente
                    && ((esc.getRGB(128, 128) >> 8) & 0xFF) > 180;                                                   // centro verde

            // 2) Foto de celular con EXIF «girar 90°»: rojo (izquierda) debe quedar arriba.
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(dosColores(400, 300, java.awt.image.BufferedImage.TYPE_INT_RGB), "jpg", b);
            byte[] jpg = b.toByteArray();
            byte[] exif = {(byte) 0xFF, (byte) 0xE1, 0, 34, 'E', 'x', 'i', 'f', 0, 0,
                'I', 'I', 42, 0, 8, 0, 0, 0, 1, 0, 0x12, 0x01, 3, 0, 1, 0, 0, 0, 6, 0, 0, 0, 0, 0, 0, 0};
            byte[] conExif = new byte[jpg.length + exif.length];
            System.arraycopy(jpg, 0, conExif, 0, 2);
            System.arraycopy(exif, 0, conExif, 2, exif.length);
            System.arraycopy(jpg, 2, conExif, 2 + exif.length, jpg.length - 2);
            java.awt.image.BufferedImage girada = abrir(repo.leer(fotos.guardarJugador(
                    "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(conExif))));
            boolean exifBien = FotosService.orientacionExif(conExif) == 6 && rojo(girada.getRGB(120, 20)) && azul(girada.getRGB(120, 300));

            // 3) Lo que no es una foto valida se rechaza.
            String gigante = gigantePng(12000, 12000);
            String noImagen = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString("no soy una imagen".getBytes());
            String webp = "data:image/webp;base64," + java.util.Base64.getEncoder().encodeToString(
                    new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0, 0});
            boolean rechazos = rechaza(() -> fotos.guardarJugador("hola"))
                    && rechaza(() -> fotos.guardarJugador("data:image/png;base64,AAAA"))
                    && rechaza(() -> fotos.guardarJugador(noImagen))
                    && rechaza(() -> fotos.guardarJugador(webp))
                    && rechaza(() -> fotos.guardarJugador(gigante))
                    && FotosService.esNombreValido("jugador-abcdef123456.jpg") && !FotosService.esNombreValido("../datos/campeonato.dat")
                    && fotos.leerGuardada("../campeonato.dat") == null;

            // 4) Por la API, como la web y el escritorio.
            CampeonatoService servicio = new CampeonatoService(
                    new alphasport.persistence.ArchivoRepositorioCampeonato(java.nio.file.Files.createTempDirectory("alphasport-fotos")),
                    new alphasport.persistence.ArchivoRepositorioHistorial(java.nio.file.Files.createTempDirectory("alphasport-fotos")));
            servicio.usarFotos(fotos);
            alphasport.ui.ApiControlador api = new alphasport.ui.ApiControlador(servicio, new AutenticacionService());
            api.ejecutar("login", java.util.Map.of("usuario", "admin", "clave", "123"));
            ConfiguracionTorneo cfg = new ConfiguracionTorneo();
            cfg.setSorteoAleatorio(false);
            servicio.crear("Fotos", TipoFormato.LIGA, cfg, false);
            java.util.Map<String, Object> j1 = new java.util.HashMap<>(java.util.Map.of("id", 0, "nombre", "Ana", "documento", "10001",
                    "dorsal", 1, "posicion", "Portero", "foto", alargada));
            java.util.Map<String, Object> j2 = new java.util.HashMap<>(java.util.Map.of("id", 0, "nombre", "Beto", "documento", "10002",
                    "dorsal", 2, "posicion", "Defensa central", "foto", ""));
            java.util.Map<String, Object> cuerpo = new java.util.HashMap<>(java.util.Map.of("nombre", "Lobos", "estadio", "",
                    "jugadores", new ArrayList<Object>(List.of(j1, j2)), "escudo", dataUrl(transparente, "png")));
            api.ejecutar("equipo", cuerpo);
            alphasport.model.Equipo lobos = servicio.getCampeonato().buscarEquipoPorNombre("Lobos");
            String fotoAna = lobos.getJugadores().get(0).getFoto();
            String escudoLobos = lobos.getEscudo();
            boolean api1 = FotosService.esNombreValido(fotoAna) && lobos.getJugadores().get(1).getFoto().isEmpty()
                    && FotosService.esNombreValido(escudoLobos) && api.foto(fotoAna) != null && api.foto(escudoLobos) != null;
            // editar el equipo reenviando los nombres conserva las fotos; un nombre inventado no se acepta
            j1.put("id", lobos.getJugadores().get(0).getId());
            j1.put("foto", fotoAna);
            j2.put("id", lobos.getJugadores().get(1).getId());
            j2.put("foto", "../../campeonato.dat");
            cuerpo.put("id", lobos.getId());
            cuerpo.remove("escudo"); // sin «escudo» se deja como estaba
            api.ejecutar("equipo", cuerpo);
            boolean api2 = lobos.getJugadores().get(0).getFoto().equals(fotoAna) && lobos.getJugadores().get(1).getFoto().isEmpty()
                    && lobos.getEscudo().equals(escudoLobos);
            // cambiar y quitar desde la alineacion o el equipo
            int idEquipo = lobos.getId();
            int idBeto = lobos.getJugadores().get(1).getId();
            api.ejecutar("jugador/foto", java.util.Map.of("equipo", idEquipo, "jugador", idBeto, "imagen", apaisada));
            boolean api3 = FotosService.esNombreValido(lobos.getJugadores().get(1).getFoto());
            api.ejecutar("jugador/foto", java.util.Map.of("equipo", idEquipo, "jugador", idBeto, "imagen", ""));
            api.ejecutar("equipo/escudo", java.util.Map.of("id", idEquipo, "imagen", ""));
            api3 &= lobos.getJugadores().get(1).getFoto().isEmpty() && lobos.getEscudo().isEmpty();
            api.ejecutar("equipo/escudo", java.util.Map.of("id", idEquipo, "imagen", dataUrl(transparente, "png")));
            api3 &= rechaza(() -> api.ejecutar("jugador/foto", java.util.Map.of("equipo", idEquipo, "jugador", 9999, "imagen", apaisada)))
                    && rechaza(() -> api.ejecutar("jugador/foto", java.util.Map.of("equipo", idEquipo, "jugador", idBeto, "imagen", "data:image/png;base64,xx")));
            // el JSON lleva la foto y el escudo
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> eqJson = ((List<java.util.Map<String, Object>>) api.consultar("estado").get("equipos")).get(0);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> anaJson = ((List<java.util.Map<String, Object>>) eqJson.get("jugadores")).get(0);
            boolean json = fotoAna.equals(anaJson.get("foto")) && FotosService.esNombreValido((String) eqJson.get("escudo"));
            // el visitante no puede cambiar fotos, pero si verlas; sin sesion no se ven
            api.ejecutar("logout", java.util.Map.of());
            api.ejecutar("login", java.util.Map.of("usuario", "visitante", "clave", "321")); // cuenta de prueba (aficionado)
            boolean permisos = rechaza(() -> api.ejecutar("jugador/foto", java.util.Map.of("equipo", idEquipo, "jugador", idBeto, "imagen", apaisada)))
                    && rechaza(() -> api.ejecutar("equipo/escudo", java.util.Map.of("id", idEquipo, "imagen", "")))
                    && api.foto(fotoAna) != null;
            api.ejecutar("logout", java.util.Map.of());
            boolean sinSesion = rechaza(() -> api.foto(fotoAna));
            // un campeonato nuevo con los mismos equipos conserva fotos y escudos
            api.ejecutar("login", java.util.Map.of("usuario", "admin", "clave", "123"));
            servicio.crear("Fotos 2", TipoFormato.LIGA, cfg, true);
            alphasport.model.Equipo copia = servicio.getCampeonato().buscarEquipoPorNombre("Lobos");
            boolean copiaBien = copia.getJugadores().get(0).getFoto().equals(fotoAna) && FotosService.esNombreValido(copia.getEscudo());

            correcto = formatos && escudo && exifBien && rechazos && api1 && api2 && api3 && json && permisos && sinSesion && copiaBien;
            if (!correcto) {
                System.out.println("  formatos=" + formatos + " escudo=" + escudo + " exif=" + exifBien + " rechazos=" + rechazos
                        + " api1=" + api1 + " api2=" + api2 + " api3=" + api3 + " json=" + json + " permisos=" + permisos
                        + " sinSesion=" + sinSesion + " copia=" + copiaBien);
            }
        } catch (Exception e) {
            e.printStackTrace();
            correcto = false;
        }
        if (!correcto) {
            fallos++;
        }
        System.out.println("Fotos de jugadores y escudos: " + (correcto ? "correcto" : "FALLO"));
    }

    /** Una PNG que dice medir ancho x alto (solo la cabecera): no se debe intentar abrir. */
    private static String gigantePng(int ancho, int alto) throws Exception {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream d = new java.io.DataOutputStream(b);
        d.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
        byte[] ihdr = java.nio.ByteBuffer.allocate(17).put("IHDR".getBytes()).putInt(ancho).putInt(alto)
                .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0).array();
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(ihdr);
        d.writeInt(13);
        d.write(ihdr);
        d.writeInt((int) crc.getValue());
        return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(b.toByteArray());
    }

    /** Reloj que la prueba puede adelantar (para el cierre de la votacion). */
    private static final class RelojPrueba extends java.time.Clock {
        long ahora = 1_800_000_000_000L;

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zona) {
            return this;
        }

        @Override
        public java.time.Instant instant() {
            return java.time.Instant.ofEpochMilli(ahora);
        }

        @Override
        public long millis() {
            return ahora;
        }
    }

    /**
     * [REQ-07] Calificaciones: nota del sistema (base, goles, asistencias, tarjetas, valla invicta y goles
     * recibidos del portero), limites 1.0 y 10.0, votos del publico solo mientras la votacion esta abierta y una
     * vez por dispositivo, tiempo de votacion configurable y cierre manual, nota final hibrida redondeada a 1
     * decimal, MVP, y que borrar el resultado descarte la votacion.
     */
    private static void probarCalificaciones() {
        boolean correcto;
        try {
            // Reglas sueltas y limites.
            List<String> d = new ArrayList<>();
            ReglaPuntuacion nota = PuntuacionNotaPartido.REGLAS;
            boolean reglas = nota.puntuar(new DesempenoJugador(false, true, 0, 0, 0, 0, 0), d) == 6.0
                    && nota.puntuar(new DesempenoJugador(false, true, 2, 1, 1, 0, 0), d) == 8.0     // 6 + 2 + 0.5 - 0.5
                    && nota.puntuar(new DesempenoJugador(true, true, 0, 0, 0, 0, 0), d) == 7.5      // valla invicta
                    && nota.puntuar(new DesempenoJugador(true, true, 0, 0, 0, 0, 3), d) == 5.1      // 6 - 0.9
                    && nota.puntuar(new DesempenoJugador(false, true, 0, 0, 0, 3, 0), d) == 1.0     // 6 - 6 -> minimo 1.0
                    && nota.puntuar(new DesempenoJugador(false, true, 6, 0, 0, 0, 0), d) == 10.0    // 6 + 6 -> maximo 10.0
                    && PuntuacionNotaPartido.redondear((7.0 + 9.5) / 2) == 8.3;                    // 8.25 -> 8.3

            RelojPrueba reloj = new RelojPrueba();
            CampeonatoService servicio = new CampeonatoService(
                    new alphasport.persistence.ArchivoRepositorioCampeonato(java.nio.file.Files.createTempDirectory("alphasport-notas")),
                    new alphasport.persistence.ArchivoRepositorioHistorial(java.nio.file.Files.createTempDirectory("alphasport-notas")), reloj);
            ConfiguracionTorneo cfg = new ConfiguracionTorneo();
            cfg.setSorteoAleatorio(false);
            servicio.crear("Notas", TipoFormato.LIGA, cfg, false);
            for (int i = 1; i <= 4; i++) {
                servicio.equipos().agregarEquipo("N" + i);
            }
            Campeonato c = servicio.getCampeonato();
            for (alphasport.model.Equipo e : new ArrayList<>(c.getEquipos())) {
                List<alphasport.model.Jugador> plantilla = new ArrayList<>();
                for (int dorsal = 1; dorsal <= 14; dorsal++) {
                    plantilla.add(new alphasport.model.Jugador(e.getNombreEquipo() + " J" + dorsal, String.valueOf(100000 + e.getId() * 100 + dorsal),
                            dorsal, dorsal == 1 ? "Portero" : "Mediocampista central"));
                }
                servicio.equipos().guardarEquipo(e.getId(), e.getNombreEquipo(), "", null, plantilla);
            }
            servicio.avanzar();
            Partido p = c.getPartidos().get(0);
            List<alphasport.model.Jugador> loc = c.buscarEquipo(p.getIdLocal()).getJugadores();
            List<alphasport.model.Jugador> vis = c.buscarEquipo(p.getIdVisitante()).getJugadores();
            // Local 2-0: el #9 marca los dos (el #8 asiste en uno), amarilla al #4, roja al #5.
            Partido datos = new Partido();
            datos.copiarDatosDe(p);
            datos.setEstado(EstadoPartido.JUGADO);
            datos.setGolesLocal(2);
            datos.setGolesVisitante(0);
            datos.setTitulares(new ArrayList<>(loc.subList(0, 11).stream().map(alphasport.model.Jugador::getId).toList()),
                    new ArrayList<>(vis.subList(0, 11).stream().map(alphasport.model.Jugador::getId).toList()));
            datos.setGoles(new ArrayList<>(List.of(new alphasport.model.Gol(p.getIdLocal(), 20, loc.get(8), false, loc.get(7)),
                    new alphasport.model.Gol(p.getIdLocal(), 70, loc.get(8), false, null))));
            datos.setTarjetas(new ArrayList<>(List.of(new alphasport.model.Tarjeta(p.getIdLocal(), loc.get(3), alphasport.model.Tarjeta.AMARILLA, 0),
                    new alphasport.model.Tarjeta(p.getIdLocal(), loc.get(4), alphasport.model.Tarjeta.ROJA, 1))), p.getIdLocal());
            servicio.partidos().guardarPartido(p.getId(), datos, false);
            long cierre = reloj.ahora;
            CalificacionesPartido n = servicio.votacion().calificaciones(p.getId(), null);
            java.util.function.BiFunction<List<CalificacionesPartido.Calificacion>, Integer, CalificacionesPartido.Calificacion> de =
                    (lista, dorsal) -> lista.stream().filter(x -> x.dorsal() == dorsal).findFirst().orElseThrow();
            boolean sistema = p.getFinalizadoEn() == cierre && n.votacion() == CalificacionesPartido.Votacion.ABIERTA
                    && n.local().size() == 11 && n.visitante().size() == 11
                    && de.apply(n.local(), 1).notaSistema() == 7.5        // portero, valla invicta
                    && de.apply(n.local(), 9).notaSistema() == 8.0        // 6 + 2 goles
                    && de.apply(n.local(), 8).notaSistema() == 6.5        // asistencia
                    && de.apply(n.local(), 4).notaSistema() == 5.5        // amarilla
                    && de.apply(n.local(), 5).notaSistema() == 4.0        // roja
                    && de.apply(n.visitante(), 1).notaSistema() == 5.4    // portero: 2 goles recibidos
                    && otrasReglas(c, p)
                    && n.idMvp() == loc.get(8).getId();                   // el #9 (8.0) supera al portero (7.5)
            if (!sistema) {
                System.out.println("  cierre=" + (p.getFinalizadoEn() == cierre) + " votacion=" + n.votacion() + " local="
                        + n.local().stream().map(x -> x.dorsal() + ":" + x.notaSistema()).toList() + " visitante="
                        + n.visitante().stream().map(x -> x.dorsal() + ":" + x.notaSistema()).toList() + " mvp=" + n.idMvp());
            }

            // Votos: el #1 local recibe 10 y 9 desde dos dispositivos -> (7.5 + 9.5) / 2 = 8.5 y pasa a ser el MVP.
            int portero = loc.get(0).getId();
            servicio.votacion().votarJugador(p.getId(), portero, 10, "dispositivo-A1");
            boolean repetido;
            try {
                servicio.votacion().votarJugador(p.getId(), portero, 3, "dispositivo-A1");
                repetido = false;
            } catch (ReglaNegocioException e) {
                repetido = true; // mismo dispositivo, mismo jugador, mismo partido
            }
            CalificacionesPartido conVotos = servicio.votacion().votarJugador(p.getId(), portero, 9, "dispositivo-B2");
            CalificacionesPartido.Calificacion arquero = de.apply(conVotos.local(), 1);
            boolean hibrida = repetido && arquero.votos() == 2 && arquero.promedioPublico() == 9.5 && arquero.notaFinal() == 8.5
                    && conVotos.idMvp() == portero && de.apply(conVotos.local(), 9).notaFinal() == 8.0 // sin votos = sistema
                    && de.apply(servicio.votacion().calificaciones(p.getId(), "dispositivo-A1").local(), 1).miVoto() == 10;

            // Valores fuera de rango y jugadores que no jugaron.
            boolean rango = rechaza(() -> servicio.votacion().votarJugador(p.getId(), portero, 11, "dispositivo-C3"))
                    && rechaza(() -> servicio.votacion().votarJugador(p.getId(), portero, 0, "dispositivo-C3"))
                    && rechaza(() -> servicio.votacion().votarJugador(p.getId(), loc.get(13).getId(), 7, "dispositivo-C3")); // suplente sin jugar

            // Varios votos a la vez: si uno no se acepta no se guarda ninguno.
            boolean varios = rechaza(() -> servicio.votacion().votarJugadores(p.getId(), java.util.Map.of(loc.get(8).getId(), 7, loc.get(13).getId(), 7), "dispositivo-D4"))
                    && rechaza(() -> servicio.votacion().votarJugadores(p.getId(), java.util.Map.of(), "dispositivo-D4"))
                    && p.getVotos().size() == 2
                    && servicio.votacion().votacionesAbiertas().equals(List.of(p)); // el unico partido terminado

            // Tiempo configurable: 48 horas mueve el cierre; cerrar a mano lo adelanta; otro tiempo lo reabre.
            final long hora = 60L * 60 * 1000;
            java.util.function.IntConsumer programar = horas -> {
                Partido copia = new Partido();
                copia.copiarDatosDe(p);
                servicio.votacion().programarVotacion(copia, horas);
                try {
                    servicio.partidos().guardarPartido(p.getId(), copia, false);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            };
            boolean tiempo = servicio.votacion().calificaciones(p.getId(), null).cierraEn() == cierre + ReglasVotacion.HORAS_POR_DEFECTO * hora;
            programar.accept(48);
            tiempo &= p.getHorasVotacion() == 48 && servicio.votacion().calificaciones(p.getId(), null).cierraEn() == cierre + 48 * hora
                    && rechaza(() -> programar.accept(0)) && rechaza(() -> programar.accept(ReglasVotacion.HORAS_MAXIMO + 1));
            reloj.ahora = cierre + 30 * hora;
            tiempo &= servicio.votacion().calificaciones(p.getId(), null).votacion() == CalificacionesPartido.Votacion.ABIERTA
                    && rechaza(() -> programar.accept(12)) && p.getHorasVotacion() == 48; // 12 h ya habrian pasado
            servicio.votacion().cerrarVotacion(p.getId());
            tiempo &= servicio.votacion().calificaciones(p.getId(), null).votacion() == CalificacionesPartido.Votacion.CERRADA
                    && servicio.votacion().calificaciones(p.getId(), null).cierraEn() == cierre + 30 * hora
                    && servicio.votacion().votacionesAbiertas().isEmpty()
                    && rechaza(() -> servicio.votacion().cerrarVotacion(p.getId()))
                    && rechaza(() -> servicio.votacion().votarJugador(p.getId(), loc.get(8).getId(), 8, "dispositivo-C3"));
            programar.accept(48); // el mismo tiempo no la reabre
            tiempo &= servicio.votacion().votacionesAbiertas().isEmpty();
            programar.accept(36);
            tiempo &= servicio.votacion().votacionesAbiertas().equals(List.of(p)) && p.getVotos().size() == 2 && p.getFinalizadoEn() == cierre;

            // Vencido el tiempo la votacion se cierra sola y la nota queda como estaba.
            reloj.ahora = cierre + 36 * hora;
            tiempo &= servicio.votacion().votacionesAbiertas().isEmpty();
            CalificacionesPartido cerrada = servicio.votacion().calificaciones(p.getId(), null);
            boolean ventana = rechaza(() -> servicio.votacion().votarJugador(p.getId(), loc.get(8).getId(), 8, "dispositivo-C3"))
                    && cerrada.votacion() == CalificacionesPartido.Votacion.CERRADA && de.apply(cerrada.local(), 1).notaFinal() == 8.5;

            // Corregir el resultado conserva la hora de cierre y los votos; borrarlo los descarta.
            Partido correccion = new Partido();
            correccion.copiarDatosDe(p);
            correccion.setGolesLocal(3);
            servicio.partidos().guardarPartido(p.getId(), correccion, false);
            boolean conserva = p.getFinalizadoEn() == cierre && p.getVotos().size() == 2;
            Partido borrado = new Partido();
            borrado.copiarDatosDe(p);
            borrado.reiniciarResultado();
            servicio.partidos().guardarPartido(p.getId(), borrado, false);
            boolean descarta = p.getFinalizadoEn() == 0 && p.getVotos().isEmpty() && p.getVotacionCerradaEn() == 0
                    && p.getHorasVotacion() == 36 // la duracion se decide antes de jugar: no se borra
                    && servicio.votacion().calificaciones(p.getId(), null).votacion() == CalificacionesPartido.Votacion.SIN_RESULTADO;

            correcto = reglas && sistema && hibrida && rango && varios && tiempo && ventana && conserva && descarta;
            if (!correcto) {
                System.out.println("  reglas=" + reglas + " sistema=" + sistema + " hibrida=" + hibrida + " rango=" + rango
                        + " varios=" + varios + " tiempo=" + tiempo + " ventana=" + ventana + " conserva=" + conserva + " descarta=" + descarta);
            }
        } catch (Exception e) {
            correcto = false;
            System.out.println("  ERROR: " + e);
            e.printStackTrace();
        }
        System.out.println("Calificaciones de jugadores (sistema, público y MVP): " + (correcto ? "correcto" : "MAL"));
        if (!correcto) {
            fallos++;
        }
    }

    /**
     * [A5] Las reglas del partido las aplica el servicio, no la pantalla ni el controlador: un partido
     * guardado directo por CampeonatoService tambien se revisa.
     */
    private static void probarReglasPartido() {
        boolean correcto;
        try {
            CampeonatoService servicio = new CampeonatoService(
                    new alphasport.persistence.ArchivoRepositorioCampeonato(java.nio.file.Files.createTempDirectory("alphasport-reglas")),
                    new alphasport.persistence.ArchivoRepositorioHistorial(java.nio.file.Files.createTempDirectory("alphasport-reglas")));
            ConfiguracionTorneo cfg = new ConfiguracionTorneo();
            cfg.setSorteoAleatorio(false);
            servicio.crear("Reglas", TipoFormato.LIGA, cfg, false);
            servicio.equipos().cargarDatosPrueba(7, 4, true);
            servicio.avanzar();
            Campeonato c = servicio.getCampeonato();
            Partido p = c.getPartidos().get(0);
            List<alphasport.model.Jugador> loc = c.buscarEquipo(p.getIdLocal()).getJugadores();
            List<alphasport.model.Jugador> vis = c.buscarEquipo(p.getIdVisitante()).getJugadores();
            java.util.function.Supplier<Partido> base = () -> {
                Partido d = new Partido();
                d.copiarDatosDe(p);
                d.setEstado(EstadoPartido.JUGADO);
                d.setGolesLocal(1);
                d.setGolesVisitante(0);
                d.setTitulares(ids(loc.subList(0, 11)), ids(vis.subList(0, 11)));
                return d;
            };
            Partido corto = base.get();
            corto.setTitulares(ids(loc.subList(0, 10)), ids(vis.subList(0, 11)));
            Partido titularEntra = base.get();
            titularEntra.setCambios(new ArrayList<>(List.of(new alphasport.model.Cambio(p.getIdLocal(), 60, loc.get(0), loc.get(1)))));
            Partido golAjeno = base.get();
            golAjeno.setGoles(new ArrayList<>(List.of(new alphasport.model.Gol(p.getIdLocal(), 10, vis.get(3), false, null))));
            Partido demasiados = base.get();
            demasiados.setGoles(new ArrayList<>(List.of(new alphasport.model.Gol(p.getIdLocal(), 10, loc.get(9), false, null),
                    new alphasport.model.Gol(p.getIdLocal(), 20, loc.get(9), false, null))));
            Partido minuto = base.get();
            alphasport.model.Tarjeta tarde = new alphasport.model.Tarjeta(p.getIdLocal(), loc.get(2), alphasport.model.Tarjeta.AMARILLA, 0);
            tarde.setMinuto(ReglasPartido.MINUTO_MAXIMO + 1);
            minuto.setTarjetas(new ArrayList<>(List.of(tarde)), p.getIdLocal());
            // Doble amarilla: la segunda es expulsion, en el servicio y no solo en la pantalla.
            java.util.function.Function<String[], Partido> conTarjetas = tipos -> {
                Partido d = base.get();
                ArrayList<alphasport.model.Tarjeta> lista = new ArrayList<>();
                for (String tipo : tipos) {
                    lista.add(new alphasport.model.Tarjeta(p.getIdLocal(), loc.get(4), tipo, tipo.equals(alphasport.model.Tarjeta.ROJA) ? 1 : 0));
                }
                d.setTarjetas(lista, p.getIdLocal());
                return d;
            };
            String a = alphasport.model.Tarjeta.AMARILLA, r = alphasport.model.Tarjeta.ROJA;
            boolean dobleAmarilla = rechaza(() -> servicio.partidos().guardarPartido(p.getId(), conTarjetas.apply(new String[]{a, a}), false))
                    && rechaza(() -> servicio.partidos().guardarPartido(p.getId(), conTarjetas.apply(new String[]{a, a, a, r}), false))
                    && rechaza(() -> servicio.partidos().guardarPartido(p.getId(), conTarjetas.apply(new String[]{r, r}), false));
            servicio.partidos().guardarPartido(p.getId(), conTarjetas.apply(new String[]{a, r}), false); // asi la registran las pantallas
            dobleAmarilla &= servicio.buscarPartido(p.getId()).getTarjetas().size() == 2;
            servicio.partidos().guardarPartido(p.getId(), conTarjetas.apply(new String[]{a, a, r}), false);
            dobleAmarilla &= servicio.buscarPartido(p.getId()).getTarjetas().size() == 3;
            Partido limpio = base.get();
            limpio.reiniciarResultado();
            servicio.partidos().guardarPartido(p.getId(), limpio, false);
            boolean rechazos = rechaza(() -> servicio.partidos().guardarPartido(p.getId(), corto, false))
                    && rechaza(() -> servicio.partidos().guardarPartido(p.getId(), titularEntra, false))
                    && rechaza(() -> servicio.partidos().guardarPartido(p.getId(), golAjeno, false))
                    && rechaza(() -> servicio.partidos().guardarPartido(p.getId(), demasiados, false))
                    && rechaza(() -> servicio.partidos().guardarPartido(p.getId(), minuto, false))
                    && !servicio.buscarPartido(p.getId()).isJugado();
            // Un partido correcto se guarda, y sigue pudiendo editarse aunque su goleador deje el equipo.
            Partido bueno = base.get();
            bueno.setGoles(new ArrayList<>(List.of(new alphasport.model.Gol(p.getIdLocal(), 10, loc.get(9), false, loc.get(8)))));
            bueno.setCambios(new ArrayList<>(List.of(new alphasport.model.Cambio(p.getIdLocal(), 60, loc.get(9), loc.get(14)))));
            servicio.partidos().guardarPartido(p.getId(), bueno, false);
            alphasport.model.Equipo local = c.buscarEquipo(p.getIdLocal());
            List<alphasport.model.Jugador> sinGoleador = new ArrayList<>(local.getJugadores());
            sinGoleador.remove(9);
            servicio.equipos().guardarEquipo(local.getId(), local.getNombreEquipo(), local.getEstadio(), local.getDirectorTecnico(), sinGoleador);
            Partido sede = new Partido();
            sede.copiarDatosDe(servicio.buscarPartido(p.getId()));
            sede.setSede("Otra sede");
            servicio.partidos().guardarPartido(p.getId(), sede, false);
            boolean guarda = servicio.buscarPartido(p.getId()).getGoles().size() == 1 && servicio.buscarPartido(p.getId()).getSede().equals("Otra sede");
            correcto = dobleAmarilla && rechazos && guarda;
            if (!correcto) {
                System.out.println("  dobleAmarilla=" + dobleAmarilla + " rechazos=" + rechazos + " guarda=" + guarda);
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
            correcto = false;
        }
        System.out.println("Reglas del partido en el servicio: " + (correcto ? "correcto" : "MAL"));
        if (!correcto) {
            fallos++;
        }
    }

    private static ArrayList<Integer> ids(List<alphasport.model.Jugador> jugadores) {
        ArrayList<Integer> ids = new ArrayList<>();
        jugadores.forEach(j -> ids.add(j.getId()));
        return ids;
    }

    /**
     * [A7] Otro conjunto de puntos (como el del modo Fantasy) se enchufa sin tocar la calculadora: sin base,
     * sin minimo ni maximo y con su propio desglose.
     */
    private static boolean otrasReglas(Campeonato c, Partido p) {
        ReglaPuntuacion fantasy = (x, desglose) -> PuntuacionNotaPartido.sumar(desglose, "Gol", x.goles(), 4)
                + PuntuacionNotaPartido.sumar(desglose, "Asistencia", x.asistencias(), 3)
                + PuntuacionNotaPartido.sumar(desglose, "Tarjeta roja", x.rojas(), -3)
                + (x.titular() ? 2 : 1);
        CalificacionesPartido f = CalculadoraCalificaciones.calcular(c, p, p.getFinalizadoEn(), null, fantasy);
        java.util.function.ToDoubleFunction<Integer> local = dorsal -> f.local().stream().filter(x -> x.dorsal() == dorsal)
                .findFirst().orElseThrow().notaSistema();
        return local.applyAsDouble(9) == 10.0 && local.applyAsDouble(8) == 5.0 && local.applyAsDouble(5) == -1.0
                && f.local().stream().filter(x -> x.dorsal() == 9).findFirst().orElseThrow().desglose().contains("Gol ×2: +8.0")
                && f.idMvp() == f.local().stream().filter(x -> x.dorsal() == 9).findFirst().orElseThrow().idJugador();
    }

    /** [A3] Cada ruta exige un permiso y cada rol es un conjunto de permisos. */
    private static void probarPermisos() {
        boolean correcto;
        try {
            alphasport.model.Rol admin = alphasport.model.Rol.ORGANIZADOR, visitante = alphasport.model.Rol.AFICIONADO;
            boolean roles = admin.puede(alphasport.model.Permiso.GESTIONAR_CAMPEONATO) && !admin.puede(alphasport.model.Permiso.VOTAR_JUGADORES)
                    && visitante.puede(alphasport.model.Permiso.VOTAR_JUGADORES) && !visitante.puede(alphasport.model.Permiso.MODERAR_NOTICIAS)
                    && admin.puedeEditar() && !visitante.puedeEditar();
            CampeonatoService servicio = new CampeonatoService(
                    new alphasport.persistence.ArchivoRepositorioCampeonato(java.nio.file.Files.createTempDirectory("alphasport-permisos")),
                    new alphasport.persistence.ArchivoRepositorioHistorial(java.nio.file.Files.createTempDirectory("alphasport-permisos")));
            alphasport.ui.ApiControlador api = new alphasport.ui.ApiControlador(servicio, new AutenticacionService());
            java.util.function.Function<Accion, String> codigo = accion -> {
                try {
                    accion.correr();
                    return "OK";
                } catch (ReglaNegocioException e) {
                    return e.getCodigo();
                } catch (Exception e) {
                    return e.toString();
                }
            };
            boolean sinSesion = codigo.apply(() -> api.consultar("estado")).equals("LOGIN")
                    && codigo.apply(() -> api.ejecutar("noticia/comentar", java.util.Map.of("id", 1, "autor", "A", "texto", "Hola"))).equals("LOGIN");
            // [L1] Respaldo y exportacion pasan por el controlador: tambien exigen sesion y permiso.
            boolean archivos = codigo.apply(api::respaldo).equals("LOGIN") && codigo.apply(api::historialEnTexto).equals("LOGIN");
            api.ejecutar("login", java.util.Map.of("usuario", "visitante", "clave", "321")); // cuenta de prueba (aficionado)
            @SuppressWarnings("unchecked")
            List<String> permisosVisitante = (List<String>) api.sesion().get("permisos");
            archivos &= codigo.apply(() -> api.abrirRespaldo(new byte[0])).equals("PERMISO");
            boolean comoVisitante = codigo.apply(() -> api.ejecutar("nuevo", java.util.Map.of())).equals("PERMISO")
                    && codigo.apply(() -> api.ejecutar("noticia/aprobar", java.util.Map.of("id", 1))).equals("PERMISO")
                    && codigo.apply(() -> api.ejecutar("ruta/que/no/existe", java.util.Map.of())).equals("PERMISO") // lista blanca
                    && codigo.apply(() -> api.ejecutar("noticia/proponer", java.util.Map.of("titulo", "Propuesta de prueba",
                            "cuerpo", "Texto de la propuesta.", "autor", "Ana"))).equals("OK")
                    && permisosVisitante.contains("VOTAR_JUGADORES") && !permisosVisitante.contains("GESTIONAR_CAMPEONATO");
            api.ejecutar("login", java.util.Map.of("usuario", "admin", "clave", "123"));
            boolean comoAdmin = codigo.apply(() -> api.ejecutar("calificacion/votar", java.util.Map.of("partido", 1, "jugador", 1, "valor", 7,
                    "dispositivo", "x"))).equals("PERMISO") && api.getNoticias().pendientes().size() == 1
                    && ((java.util.Map<?, ?>) api.consultar("noticias").get("noticias")).toString().contains("Propuesta de prueba");
            // [M2] Cada modulo registra sus rutas con su permiso: solo registrarse, entrar y salir son publicas.
            java.util.Map<String, alphasport.model.Permiso> acciones = api.permisosDeAcciones();
            boolean rutas = acciones.size() >= 29 && acciones.entrySet().stream().filter(e -> e.getValue() == null).map(java.util.Map.Entry::getKey)
                    .sorted().toList().equals(List.of("login", "logout", "registro"))
                    && acciones.get("calificacion/votar") == alphasport.model.Permiso.VOTAR_JUGADORES
                    && acciones.get("comentario/eliminar") == alphasport.model.Permiso.MODERAR_NOTICIAS
                    && acciones.get("partido") == alphasport.model.Permiso.GESTIONAR_CAMPEONATO
                    && api.permisosDeConsultas().get("partido/…") == alphasport.model.Permiso.VER
                    && api.permisosDeConsultas().get("catalogo") == null;
            correcto = roles && sinSesion && comoVisitante && comoAdmin && rutas && archivos;
            if (!correcto) {
                System.out.println("  roles=" + roles + " sinSesion=" + sinSesion + " comoVisitante=" + comoVisitante + " comoAdmin=" + comoAdmin
                        + " rutas=" + rutas + " archivos=" + archivos + " " + acciones);
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
            correcto = false;
        }
        System.out.println("Permisos por rol: " + (correcto ? "correcto" : "MAL"));
        if (!correcto) {
            fallos++;
        }
    }

    /**
     * [A2] El servidor web da una sesion a cada navegador (cookie HttpOnly y SameSite) y rechaza las acciones
     * que no vienen de su propia pagina. Usa un puerto de pruebas (desde 18300), nunca el 8080 del programa.
     */
    private static void probarServidorWeb() {
        boolean correcto;
        alphasport.ui.ServidorWeb servidor = null;
        try {
            CampeonatoService servicio = new CampeonatoService(
                    new alphasport.persistence.ArchivoRepositorioCampeonato(java.nio.file.Files.createTempDirectory("alphasport-web")),
                    new alphasport.persistence.ArchivoRepositorioHistorial(java.nio.file.Files.createTempDirectory("alphasport-web")));
            servidor = new alphasport.ui.ServidorWeb(java.nio.file.Path.of("web"),
                    new alphasport.ui.ApiControlador(servicio, new AutenticacionService()));
            int puerto = servidor.iniciar(18300);
            String base = "http://127.0.0.1:" + puerto;
            java.net.http.HttpClient cliente = java.net.http.HttpClient.newHttpClient();
            // Sin cookie no hay sesion.
            boolean sinSesion = pedir(cliente, base, "GET", "estado", null, null, base).body().contains("\"LOGIN\"");
            // Dos navegadores: el administrador y un visitante, cada uno con su cookie.
            var admin = pedir(cliente, base, "POST", "login", "{\"usuario\":\"admin\",\"clave\":\"123\"}", null, base);
            String cookieAdmin = admin.headers().firstValue("Set-Cookie").orElse("");
            var visita = pedir(cliente, base, "POST", "login", "{\"usuario\":\"visitante\",\"clave\":\"321\"}", null, base);
            String cookieVisita = visita.headers().firstValue("Set-Cookie").orElse("");
            String tokenAdmin = cookieAdmin.replaceFirst(";.*$", ""), tokenVisita = cookieVisita.replaceFirst(";.*$", "");
            boolean cookies = cookieAdmin.startsWith(alphasport.ui.ServidorWeb.COOKIE + "=") && cookieAdmin.contains("HttpOnly")
                    && cookieAdmin.contains("SameSite=Strict") && !tokenAdmin.equals(tokenVisita) && tokenAdmin.length() > 30;
            boolean separadas = pedir(cliente, base, "GET", "sesion", null, tokenAdmin, base).body().contains("\"ORGANIZADOR\"")
                    && pedir(cliente, base, "GET", "sesion", null, tokenVisita, base).body().contains("\"AFICIONADO\"")
                    && pedir(cliente, base, "POST", "division/agregar", "{}", tokenVisita, base).body().contains("\"PERMISO\"");
            // Otra pagina no puede usar la sesion del administrador, y solo se atiende con el Host de esta maquina.
            var ajena = pedir(cliente, base, "POST", "nuevo", "{}", tokenAdmin, "http://pagina-ajena.example");
            var sinOrigen = pedir(cliente, base, "POST", "nuevo", "{}", tokenAdmin, null);
            boolean origen = ajena.statusCode() == 403 && ajena.body().contains("\"ORIGEN\"") && sinOrigen.statusCode() == 403
                    && hostAjeno(puerto).startsWith("HTTP/1.1 403");
            // Al salir se borra la cookie y el token deja de servir.
            var salir = pedir(cliente, base, "POST", "logout", "{}", tokenAdmin, base);
            boolean cerrada = salir.headers().firstValue("Set-Cookie").orElse("").contains("Max-Age=0")
                    && pedir(cliente, base, "GET", "estado", null, tokenAdmin, base).body().contains("\"LOGIN\"")
                    && pedir(cliente, base, "GET", "sesion", null, tokenVisita, base).body().contains("\"AFICIONADO\"")
                    && cliente.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/")).build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
            correcto = sinSesion && cookies && separadas && origen && cerrada;
            if (!correcto) {
                System.out.println("  sinSesion=" + sinSesion + " cookies=" + cookies + " separadas=" + separadas + " origen=" + origen + " cerrada=" + cerrada);
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
            correcto = false;
        } finally {
            if (servidor != null) {
                servidor.detener();
            }
        }
        System.out.println("Servidor web: una sesión por navegador y control de origen: " + (correcto ? "correcto" : "MAL"));
        if (!correcto) {
            fallos++;
        }
    }

    private static java.net.http.HttpResponse<String> pedir(java.net.http.HttpClient cliente, String base, String metodo, String ruta,
            String cuerpo, String cookie, String origen) throws Exception {
        var pedido = java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/api/" + ruta));
        if (cookie != null) {
            pedido.header("Cookie", cookie.contains("=") ? cookie : alphasport.ui.ServidorWeb.COOKIE + "=" + cookie);
        }
        if (origen != null) {
            pedido.header("Origin", origen);
        }
        pedido.method(metodo, cuerpo == null ? java.net.http.HttpRequest.BodyPublishers.noBody()
                : java.net.http.HttpRequest.BodyPublishers.ofString(cuerpo));
        return cliente.send(pedido.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    /** Primera linea de la respuesta a una peticion con otro Host (como en un ataque de «DNS rebinding»). */
    private static String hostAjeno(int puerto) throws Exception {
        try (java.net.Socket s = new java.net.Socket("127.0.0.1", puerto)) {
            s.getOutputStream().write(("GET /api/catalogo HTTP/1.1\r\nHost: pagina-ajena.example\r\nConnection: close\r\n\r\n")
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return new java.io.BufferedReader(new java.io.InputStreamReader(s.getInputStream(), java.nio.charset.StandardCharsets.US_ASCII)).readLine();
        }
    }

    /** [A8] Las claves se guardan y se comparan como hash; las cuentas vienen de un RepositorioUsuarios. */
    private static void probarClaves() {
        boolean correcto;
        try {
            ClavesPbkdf2 rapido = new ClavesPbkdf2(1000); // pocas iteraciones: solo para que la prueba sea rapida
            String h = rapido.hash("secreta"), otro = rapido.hash("secreta");
            String alterado = h.substring(0, h.length() - 2) + (h.endsWith("AA") ? "BB" : "AA");
            boolean hash = h.startsWith(ClavesPbkdf2.PREFIJO + "$1000$") && !h.contains("secreta") && !h.equals(otro)
                    && rapido.coincide("secreta", h) && rapido.coincide("secreta", otro) && !rapido.coincide("Secreta", h)
                    && !rapido.coincide("secreta", alterado) && !rapido.coincide("secreta", "secreta") && !rapido.coincide(null, h)
                    && !rapido.coincide("secreta", "$2a$06$bcrypt-no-soportado") && h.length() <= 255;
            // Otro repositorio de cuentas (como sera la tabla usuarios) sin cambiar el servicio.
            AutenticacionService propia = new AutenticacionService(new alphasport.persistence.RepositorioUsuariosEnMemoria(List.of(
                    new alphasport.model.CuentaUsuario("ana", "Ana", alphasport.model.Rol.ORGANIZADOR, rapido.hash("clave-de-ana")))), rapido);
            boolean repositorio = propia.iniciarSesion("  ANA ", "clave-de-ana").puedeEditar()
                    && rechaza(() -> propia.iniciarSesion("ana", "otra")) && rechaza(() -> propia.iniciarSesion("admin", "123"));
            // Las cuentas de prueba (solo en memoria, para las pruebas) entran con sus claves.
            AutenticacionService demo = new AutenticacionService();
            boolean demostracion = demo.iniciarSesion("admin", "123").puedeEditar() && !demo.iniciarSesion("visitante", "321").puedeEditar()
                    && rechaza(() -> demo.iniciarSesion("admin", "321")) && rechaza(() -> demo.iniciarSesion("nadie", "123"));
            correcto = hash && repositorio && demostracion;
            if (!correcto) {
                System.out.println("  hash=" + hash + " repositorio=" + repositorio + " demostracion=" + demostracion);
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
            correcto = false;
        }
        System.out.println("Claves con hash y cuentas por repositorio: " + (correcto ? "correcto" : "MAL"));
        if (!correcto) {
            fallos++;
        }
    }

    /**
     * Registro de cuentas Organizador y Aficionado: validaciones, usuario o correo repetidos, rol que no se puede
     * forzar, inicio de sesion y permisos, por el servicio y por la ruta «registro» de ApiControlador.
     */
    private static void probarRegistro() {
        boolean correcto;
        try {
            ClavesPbkdf2 rapido = new ClavesPbkdf2(1000);
            alphasport.persistence.RepositorioUsuariosEnMemoria cuentas = new alphasport.persistence.RepositorioUsuariosEnMemoria();
            AutenticacionService auth = new AutenticacionService(cuentas, rapido);
            java.util.function.BiFunction<String, String[], AutenticacionService.SolicitudRegistro> solicitud = (tipo, d) ->
                    new AutenticacionService.SolicitudRegistro(d[0], d[1], d[2], d[3], d[4], tipo);
            String[] ana = {"  Ana.Torres ", "  Ana   Torres ", " Ana@Correo.COM ", "Clave#1234", "Clave#1234"};
            // Registro valido: usuario y correo en minusculas, nombre sin espacios de mas, la clave solo como hash.
            alphasport.model.Usuario u = auth.registrar(solicitud.apply("ORGANIZADOR", ana));
            alphasport.model.CuentaUsuario guardada = cuentas.buscar("ANA.TORRES").orElseThrow();
            boolean valido = u.getNombre().equals("ana.torres") && u.getRol() == alphasport.model.Rol.ORGANIZADOR && u.puedeEditar()
                    && u.getId() > 0 && u.getId() == cuentas.buscar("ana.torres").orElseThrow().id() // su id, para ser duena de sus campeonatos
                    && guardada.correo().equals("ana@correo.com") && guardada.nombre().equals("Ana Torres")
                    && !guardada.claveHash().contains("Clave#1234") && rapido.coincide("Clave#1234", guardada.claveHash())
                    && !guardada.toString().contains(guardada.claveHash()) && !solicitud.apply("AFICIONADO", ana).toString().contains("Clave#1234");
            // Usuario o correo repetidos, sin importar mayusculas ni espacios.
            boolean repetidos = mensaje(() -> auth.registrar(solicitud.apply("AFICIONADO",
                    new String[]{"ANA.TORRES", "Otra", "otra@correo.com", "Clave#1234", "Clave#1234"}))).contains("usuario ya está en uso")
                    && mensaje(() -> auth.registrar(solicitud.apply("AFICIONADO",
                    new String[]{"otra", "Otra", " ANA@correo.com", "Clave#1234", "Clave#1234"}))).contains("correo ya está registrado")
                    && cuentas.existeUsuario("Ana.Torres") && cuentas.existeCorreo("ANA@CORREO.COM") && !cuentas.existeCorreo("");
            // Contraseña debil o mal confirmada, y datos que no sirven.
            java.util.function.Function<String[], String> error = d -> mensaje(() -> auth.registrar(solicitud.apply("AFICIONADO", d)));
            java.util.function.Function<String, String> clave = c -> error.apply(new String[]{"luis", "Luis", "luis@x.co", c, c});
            // Cada regla de la contraseña por separado, y el mensaje nombra todo lo que falta (no solo lo primero).
            boolean debil = clave.apply("abc123").equals("Falta: al menos 8 caracteres, una mayúscula y un carácter especial.")
                    && clave.apply("Cl#1a").equals("Falta: al menos 8 caracteres.")
                    && clave.apply("clave#1234").equals("Falta: una mayúscula.")
                    && clave.apply("CLAVE#1234").equals("Falta: una minúscula.")
                    && clave.apply("Clave#abcd").equals("Falta: un número.")
                    && clave.apply("Clave12345").equals("Falta: un carácter especial.")
                    && clave.apply("solamenteletras").equals("Falta: una mayúscula, un número y un carácter especial.")
                    && clave.apply("Clave #1234").equals("La contraseña no puede tener espacios.")
                    && clave.apply("Clave#12\u00073").equals("La contraseña no puede tener caracteres de control.")
                    && clave.apply("Aa1#" + "x".repeat(130)).equals("La contraseña no puede tener más de 128 caracteres.")
                    && AutenticacionService.problemaClave("Ñandú#2026") == null // letras con tilde y Ñ cuentan como mayúscula y minúscula
                    && AutenticacionService.reglasClave("clave#1234").stream().filter(r -> !r.cumple()).map(AutenticacionService.Regla::texto)
                            .toList().equals(List.of("Una letra mayúscula"))
                    && AutenticacionService.reglasClave("").size() == 5
                    && error.apply(new String[]{"luis", "Luis", "luis@x.co", "Clave#1234", "Clave#12345"}).contains("no coinciden")
                    && error.apply(new String[]{"lu", "Luis", "luis@x.co", "Clave#1234", "Clave#1234"}).contains("de 4 a 20 caracteres")
                    && error.apply(new String[]{"josé", "Luis", "luis@x.co", "Clave#1234", "Clave#1234"}).contains("letras a-z sin tilde")
                    && error.apply(new String[]{".luis", "Luis", "luis@x.co", "Clave#1234", "Clave#1234"}).contains("empezar con una letra")
                    && error.apply(new String[]{"luis", "L", "luis@x.co", "Clave#1234", "Clave#1234"}).contains("Escriba su nombre")
                    && error.apply(new String[]{"luis", "Luis", "luis@x", "Clave#1234", "Clave#1234"}).equals("Al dominio le falta la extensión, por ejemplo .com.")
                    && error.apply(new String[]{"luis", "Luis", "", "Clave#1234", "Clave#1234"}).contains("Escriba su correo")
                    && !cuentas.existeUsuario("luis");
            // El rol solo puede ser ORGANIZADOR o AFICIONADO: nunca otro valor enviado por el cliente.
            String[] luis = {"luis", "Luis", "luis@x.co", "Futbol#2026", "Futbol#2026"};
            boolean rolInvalido = true;
            for (String tipo : new String[]{"ADMINISTRADOR", "ADMIN", "VISITANTE", "organizador", "", null, "ORGANIZADOR "}) {
                rolInvalido &= mensaje(() -> auth.registrar(solicitud.apply(tipo, luis))).contains("tipo de cuenta");
            }
            rolInvalido &= !cuentas.existeUsuario("luis");
            // Inicio de sesion: correcto, y el mismo mensaje con clave mala o usuario inexistente.
            String malaClave = mensaje(() -> auth.iniciarSesion("ana.torres", "otra1234"));
            boolean login = auth.iniciarSesion(" ANA.torres ", "Clave#1234").getRol() == alphasport.model.Rol.ORGANIZADOR
                    && malaClave.equals("Usuario o contraseña incorrectos.") && malaClave.equals(mensaje(() -> auth.iniciarSesion("nadie", "otra1234")))
                    && mensaje(() -> auth.iniciarSesion("ana.torres", null)).equals(malaClave);
            // Por la ruta «registro»: abre la sesion, la respuesta no trae claves ni hash, y un Aficionado no gestiona torneos.
            CampeonatoService servicio = new CampeonatoService(new alphasport.persistence.ArchivoRepositorioCampeonato(
                    java.nio.file.Files.createTempDirectory("alphasport-registro")), new alphasport.persistence.ArchivoRepositorioHistorial(
                    java.nio.file.Files.createTempDirectory("alphasport-registro")));
            alphasport.ui.ApiControlador api = new alphasport.ui.ApiControlador(servicio, auth);
            java.util.Map<String, Object> r = api.ejecutar("registro", java.util.Map.of("tipo", "AFICIONADO", "nombre", "Luis Pérez",
                    "usuario", "Luis", "correo", "luis@x.co", "clave", "Futbol#2026", "confirmacion", "Futbol#2026"));
            String json = alphasport.ui.Json.escribir(r);
            boolean ruta = r.get("sesion").toString().contains("rol=AFICIONADO") && !json.contains("Futbol#2026") && !json.contains("pbkdf2")
                    && api.getUsuarioActual().getNombre().equals("luis")
                    && "PERMISO".equals(codigoDe(() -> api.ejecutar("nuevo", java.util.Map.of("nombre", "Copa", "formato", "LIGA"))))
                    && !servicio.hayCampeonato();
            api.ejecutar("logout", java.util.Map.of());
            boolean forzado = mensaje(() -> api.ejecutar("registro", java.util.Map.of("tipo", "ADMINISTRADOR", "nombre", "Hack",
                    "usuario", "hack", "correo", "h@x.co", "clave", "Clave#1234", "confirmacion", "Clave#1234"))).contains("tipo de cuenta")
                    && api.getUsuarioActual() == null && !cuentas.existeUsuario("hack");
            api.ejecutar("login", java.util.Map.of("usuario", "ana.torres", "clave", "Clave#1234"));
            // El Organizador sí pasa el permiso de gestionar (la ruta lo deja seguir) y puede crear el torneo.
            boolean organizador = !"PERMISO".equals(codigoDe(() -> api.ejecutar("nuevo", java.util.Map.of("nombre", "Copa", "formato", "LIGA"))))
                    && api.getUsuarioActual().puede(alphasport.model.Permiso.GESTIONAR_CAMPEONATO);
            servicio.crear("Copa de Ana", TipoFormato.LIGA, new ConfiguracionTorneo(), false);
            organizador &= servicio.hayCampeonato();
            // Correo: cada regla con su mensaje concreto (el mismo texto que cuentas.js).
            java.util.function.Function<String, String> correo = AutenticacionService::problemaCorreo;
            // Usuario: cada regla con su mensaje; varias a la vez se nombran todas.
            java.util.function.Function<String, String> usuarioMal = AutenticacionService::problemaUsuario;
            boolean usuarios = "El usuario debe tener de 4 a 20 caracteres.".equals(usuarioMal.apply("ana"))
                    && "El usuario debe tener de 4 a 20 caracteres.".equals(usuarioMal.apply("a".repeat(21)))
                    && "El usuario solo puede tener letras a-z sin tilde, números y los signos . _ -".equals(usuarioMal.apply("josé1"))
                    && "El usuario debe empezar con una letra.".equals(usuarioMal.apply("1ana"))
                    && "El usuario debe empezar con una letra.".equals(usuarioMal.apply("_ana"))
                    && "El usuario no puede terminar en punto, guion bajo ni guion.".equals(usuarioMal.apply("ana."))
                    && "El usuario no puede tener dos signos seguidos (por ejemplo «..» o «_-»).".equals(usuarioMal.apply("ana._b"))
                    && "El usuario debe empezar con una letra. El usuario no puede ser solo números.".equals(usuarioMal.apply("12345"))
                    && "Ese nombre de usuario está reservado. Elija otro.".equals(usuarioMal.apply(" ADMIN "))
                    && "Ese nombre de usuario está reservado. Elija otro.".equals(usuarioMal.apply("soporte"))
                    && "Escriba un nombre de usuario.".equals(usuarioMal.apply("  "))
                    && usuarioMal.apply("Ana.Torres-9") == null && usuarioMal.apply("a_b.c-d1") == null
                    && AutenticacionService.reglasUsuario("").size() == 7;
            // Reproduce lo que vio el profesor: con el tipo Aficionado (y Organizador) una clave debil daba el error
            // generico «letras y números» o se aceptaba. Ahora ambos tipos reciben el nombre de lo que falta.
            boolean ambosRoles = true;
            for (String tipo : new String[]{"AFICIONADO", "ORGANIZADOR"}) {
                java.util.function.Function<String, String> conTipo = c -> mensaje(() -> auth.registrar(
                        solicitud.apply(tipo, new String[]{"pedro.p", "Pedro", "pedro@x.co", c, c})));
                ambosRoles &= conTipo.apply("solamenteletras").equals("Falta: una mayúscula, un número y un carácter especial.")
                        && conTipo.apply("clave#1234").equals("Falta: una mayúscula.")
                        && conTipo.apply("Clave12345").equals("Falta: un carácter especial.")
                        && mensaje(() -> auth.registrar(solicitud.apply(tipo, new String[]{"admin", "Pedro", "pedro@x.co", "Clave#1234", "Clave#1234"})))
                        .equals("Ese nombre de usuario está reservado. Elija otro.")
                        && mensaje(() -> auth.registrar(solicitud.apply(tipo, new String[]{"pedro.p", "Pedro", "a@.com", "Clave#1234", "Clave#1234"})))
                        .equals("El dominio no puede empezar con punto.");
            }
            ambosRoles &= !cuentas.existeUsuario("pedro.p");
            boolean correos = usuarios && ambosRoles && "El dominio no puede empezar con punto.".equals(correo.apply("a@.com"))
                    && "El dominio no puede empezar con punto.".equals(correo.apply("a@..com"))
                    && "El dominio no puede terminar en punto.".equals(correo.apply("a@com."))
                    && "El dominio no puede tener puntos seguidos.".equals(correo.apply("a@x..com"))
                    && "El nombre antes de la @ no puede tener puntos seguidos.".equals(correo.apply("a..b@x.com"))
                    && "El nombre antes de la @ no puede empezar ni terminar en punto.".equals(correo.apply(".a@x.com"))
                    && "Las partes del dominio no pueden empezar ni terminar en guion.".equals(correo.apply("a@-x.com"))
                    && "El correo solo puede tener una @.".equals(correo.apply("a@b@x.com"))
                    && "Al correo le falta la @, por ejemplo nombre@correo.com.".equals(correo.apply("ax.com"))
                    && "Falta el nombre antes de la @.".equals(correo.apply("@x.com"))
                    && correo.apply("a@x.c1") != null && correo.apply("a@x.c") != null && correo.apply("a b@x.com") != null
                    && correo.apply("a".repeat(65) + "@x.com") != null && correo.apply("a@" + "x".repeat(64) + ".com") != null
                    && correo.apply("a@" + "x".repeat(250) + ".com") != null
                    && correo.apply("ana.perez+1@mail.example.co") == null && correo.apply(" Ana_P%9-x@Sub-1.Dominio.ORG ") == null
                    && correo.apply("a@" + "x".repeat(63) + ".com") == null;
            // Una cuenta antigua con una contraseña que hoy no cumple las reglas sigue entrando: el login no mide la fuerza.
            cuentas.crear(new alphasport.model.CuentaUsuario("viejo", "Viejo", "viejo@x.co", alphasport.model.Rol.AFICIONADO, rapido.hash("abc123")));
            boolean antigua = auth.iniciarSesion("viejo", "abc123").getRol() == alphasport.model.Rol.AFICIONADO;
            correcto = valido && repetidos && debil && correos && antigua && rolInvalido && login && ruta && forzado && organizador;
            if (!correcto) {
                System.out.println("  valido=" + valido + " repetidos=" + repetidos + " debil=" + debil + " correos=" + correos + " antigua=" + antigua
                        + " rolInvalido=" + rolInvalido + " login=" + login + " ruta=" + ruta + " forzado=" + forzado + " organizador=" + organizador);
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
            correcto = false;
        }
        System.out.println("Registro de cuentas Organizador y Aficionado: " + (correcto ? "correcto" : "MAL"));
        if (!correcto) {
            fallos++;
        }
    }

    /**
     * Mi perfil: editar datos (el usuario no cambia y la cuenta sale de la sesion), cambiar la clave con la
     * actual correcta e incorrecta, y el codigo de verificacion correcto, incorrecto, vencido y con los 5
     * intentos agotados; cambiar el correo lo deja sin verificar.
     */
    private static void probarPerfil() {
        boolean correcto;
        try {
            ClavesPbkdf2 rapido = new ClavesPbkdf2(1000);
            alphasport.persistence.RepositorioUsuariosEnMemoria cuentas = new alphasport.persistence.RepositorioUsuariosEnMemoria();
            AutenticacionService auth = new AutenticacionService(cuentas, rapido);
            auth.registrar(new AutenticacionService.SolicitudRegistro("ana.torres", "Ana", "ana@x.co", "Clave#1234", "Clave#1234", "AFICIONADO"));
            auth.registrar(new AutenticacionService.SolicitudRegistro("beto.org", "Beto", "beto@x.co", "Clave#1234", "Clave#1234", "ORGANIZADOR"));
            java.util.concurrent.atomic.AtomicLong ahora = new java.util.concurrent.atomic.AtomicLong(1_000_000L);
            java.time.Clock reloj = new java.time.Clock() {
                @Override
                public java.time.ZoneId getZone() {
                    return java.time.ZoneOffset.UTC;
                }

                @Override
                public java.time.Clock withZone(java.time.ZoneId z) {
                    return this;
                }

                @Override
                public java.time.Instant instant() {
                    return java.time.Instant.ofEpochMilli(ahora.get());
                }
            };
            List<String> enviados = new ArrayList<>();
            PerfilService perfil = new PerfilService(cuentas, rapido, new EnviadorCorreo() {
                @Override
                public void enviarCodigo(String correo, String codigo) {
                    enviados.add(codigo);
                }

                @Override
                public boolean esDemostracion() {
                    return true;
                }
            }, reloj);
            // Datos: el nombre cambia; el correo nuevo pide la clave actual y queda sin verificar.
            boolean datos = perfil.actualizarDatos("ana.torres", "  Ana   María ", "ana@x.co", null).equals("Datos guardados.")
                    && cuentas.buscar("ana.torres").orElseThrow().nombre().equals("Ana María")
                    && mensaje(() -> perfil.actualizarDatos("ana.torres", "Ana", "nueva@x.co", "")).equals("Para cambiar el correo escriba su contraseña actual.")
                    && mensaje(() -> perfil.actualizarDatos("ana.torres", "Ana", "nueva@x.co", "Mala#1234")).equals("La contraseña actual no es correcta.")
                    && mensaje(() -> perfil.actualizarDatos("ana.torres", "Ana", "beto@x.co", "Clave#1234")).equals("Ese correo ya está registrado en otra cuenta.")
                    && mensaje(() -> perfil.actualizarDatos("ana.torres", "Ana", "a@.com", "Clave#1234")).equals("El dominio no puede empezar con punto.")
                    && mensaje(() -> perfil.actualizarDatos("ana.torres", "A", "ana@x.co", null)).startsWith("Escriba su nombre")
                    && Boolean.FALSE.equals(perfil.ver("ana.torres").get("correoVerificado"))
                    && perfil.ver("ana.torres").get("usuario").equals("ana.torres");
            // Clave: actual incorrecta, nueva debil, confirmacion distinta y bien; la nueva sirve para entrar y la vieja ya no.
            boolean clave = mensaje(() -> perfil.cambiarClave("ana.torres", "Mala#1234", "Nueva#5678", "Nueva#5678")).equals("La contraseña actual no es correcta.")
                    && mensaje(() -> perfil.cambiarClave("ana.torres", "Clave#1234", "nueva5678", "nueva5678")).equals("Falta: una mayúscula y un carácter especial.")
                    && mensaje(() -> perfil.cambiarClave("ana.torres", "Clave#1234", "Nueva#5678", "Nueva#5679")).equals("Las contraseñas no coinciden.")
                    && mensaje(() -> perfil.cambiarClave("ana.torres", "Clave#1234", "Nueva#5678", "Nueva#5678")).isEmpty()
                    && auth.iniciarSesion("ana.torres", "Nueva#5678") != null
                    && mensaje(() -> auth.iniciarSesion("ana.torres", "Clave#1234")).equals("Usuario o contraseña incorrectos.");
            // Codigo: sin pedir, incorrecto (cuenta intentos) y correcto; se guarda solo su hash.
            boolean codigo = mensaje(() -> perfil.verificarCodigo("ana.torres", "123456")).equals("Primero pida un código.")
                    && perfil.enviarCodigo("ana.torres").startsWith("Modo demostración") && enviados.size() == 1
                    && !cuentas.verificacion("ana.torres").codigoHash().contains(enviados.get(0));
            String bueno = enviados.get(0), malo = bueno.equals("000000") ? "111111" : "000000";
            codigo &= mensaje(() -> perfil.verificarCodigo("ana.torres", malo)).equals("Código incorrecto. Le quedan 4 intentos.")
                    && mensaje(() -> perfil.verificarCodigo("ana.torres", bueno)).isEmpty()
                    && Boolean.TRUE.equals(perfil.ver("ana.torres").get("correoVerificado"))
                    && mensaje(() -> perfil.enviarCodigo("ana.torres")).equals("Su correo ya está verificado.");
            // Vencido: pasan 15 minutos y un segundo.
            perfil.enviarCodigo("beto.org");
            String deBeto = enviados.get(enviados.size() - 1);
            ahora.addAndGet(PerfilService.VIGENCIA_CODIGO.toMillis() + 1000);
            boolean vencido = mensaje(() -> perfil.verificarCodigo("beto.org", deBeto)).equals("El código venció. Pida uno nuevo.")
                    && mensaje(() -> perfil.verificarCodigo("beto.org", deBeto)).equals("Primero pida un código.");
            // Intentos agotados: 5 errores y despues ni el codigo correcto sirve.
            perfil.enviarCodigo("beto.org");
            String otro = enviados.get(enviados.size() - 1), mal = otro.equals("000000") ? "111111" : "000000";
            for (int i = 0; i < 4; i++) {
                mensaje(() -> perfil.verificarCodigo("beto.org", mal));
            }
            boolean agotado = mensaje(() -> perfil.verificarCodigo("beto.org", mal)).equals("Código incorrecto. Agotó los 5 intentos: pida un código nuevo.")
                    && mensaje(() -> perfil.verificarCodigo("beto.org", otro)).equals("Primero pida un código.")
                    && Boolean.FALSE.equals(perfil.ver("beto.org").get("correoVerificado"));
            // Cambiar el correo vuelve a «sin verificar».
            boolean vuelve = perfil.actualizarDatos("ana.torres", "Ana", "ana.nueva@x.co", "Nueva#5678").contains("sin verificar")
                    && Boolean.FALSE.equals(perfil.ver("ana.torres").get("correoVerificado"));
            // Por la ruta: la cuenta es la de la sesion aunque el cuerpo diga otro usuario.
            CampeonatoService servicio = new CampeonatoService(new alphasport.persistence.ArchivoRepositorioCampeonato(
                    java.nio.file.Files.createTempDirectory("alphasport-perfil")), new alphasport.persistence.ArchivoRepositorioHistorial(
                    java.nio.file.Files.createTempDirectory("alphasport-perfil")));
            alphasport.ui.ApiControlador api = new alphasport.ui.ApiControlador(servicio, auth);
            boolean sinSesion = "LOGIN".equals(codigoDe(() -> api.consultar("perfil")));
            api.ejecutar("login", java.util.Map.of("usuario", "beto.org", "clave", "Clave#1234"));
            api.ejecutar("perfil/datos", java.util.Map.of("usuario", "ana.torres", "nombre", "Beto Ruiz", "correo", "beto@x.co"));
            boolean ruta = sinSesion && cuentas.buscar("beto.org").orElseThrow().nombre().equals("Beto Ruiz")
                    && cuentas.buscar("ana.torres").orElseThrow().nombre().equals("Ana")
                    && ((java.util.Map<?, ?>) api.consultar("perfil").get("perfil")).get("usuario").equals("beto.org");
            correcto = datos && clave && codigo && vencido && agotado && vuelve && ruta;
            if (!correcto) {
                System.out.println("  datos=" + datos + " clave=" + clave + " codigo=" + codigo + " vencido=" + vencido + " agotado=" + agotado
                        + " vuelve=" + vuelve + " ruta=" + ruta);
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
            correcto = false;
        }
        System.out.println("Mi perfil: datos, contraseña y verificación del correo: " + (correcto ? "correcto" : "MAL"));
        if (!correcto) {
            fallos++;
        }
    }

    /**
     * Varios campeonatos en curso a la vez, por la API como el escritorio y la web: cada Organizador ve y gestiona
     * solo los suyos, el Aficionado ve todos, y equipos, partidos, tabla, votos y noticias no se mezclan.
     */
    private static void probarVariosCampeonatos() {
        boolean correcto;
        try {
            ClavesPbkdf2 rapido = new ClavesPbkdf2(1000);
            alphasport.persistence.RepositorioUsuariosEnMemoria cuentas = new alphasport.persistence.RepositorioUsuariosEnMemoria(List.of(
                    new alphasport.model.CuentaUsuario("ana", "Ana", alphasport.model.Rol.ORGANIZADOR, rapido.hash("clave-ana1")),
                    new alphasport.model.CuentaUsuario("beto", "Beto", alphasport.model.Rol.ORGANIZADOR, rapido.hash("clave-beto1")),
                    new alphasport.model.CuentaUsuario("luis", "Luis", alphasport.model.Rol.AFICIONADO, rapido.hash("clave-luis1")),
                    new alphasport.model.CuentaUsuario("maria", "María", alphasport.model.Rol.AFICIONADO, rapido.hash("clave-maria1"))));
            alphasport.persistence.RepositorioCampeonatosEnMemoria repos = new alphasport.persistence.RepositorioCampeonatosEnMemoria(
                    id -> id == 1 ? "Ana" : id == 2 ? "Beto" : null);
            alphasport.persistence.RepositorioHistorial historial = new alphasport.persistence.ArchivoRepositorioHistorial(
                    java.nio.file.Files.createTempDirectory("alphasport-varios"));
            GestorCampeonatos gestor = new GestorCampeonatos(repos, historial, r -> new CampeonatoService(r, historial));
            alphasport.ui.ApiControlador base = new alphasport.ui.ApiControlador(gestor, new SesionService(new AutenticacionService(cuentas, rapido)),
                    new NoticiasService(new alphasport.persistence.RepositorioNoticiasEnMemoria()),
                    new FotosService(new alphasport.persistence.RepositorioFotosEnMemoria()));
            java.util.function.BiFunction<String, String, alphasport.ui.ApiControlador> entrar = (usuario, clave) -> {
                alphasport.ui.ApiControlador api = base.cliente(null);
                try {
                    api.ejecutar("login", java.util.Map.of("usuario", usuario, "clave", clave));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                return api;
            };
            alphasport.ui.ApiControlador ana = entrar.apply("ana", "clave-ana1"), beto = entrar.apply("beto", "clave-beto1"),
                    luis = entrar.apply("luis", "clave-luis1");
            // Sin campeonatos: no hay nada que elegir.
            boolean vacio = Boolean.FALSE.equals(ana.consultar("estado").get("hay")) && Boolean.FALSE.equals(ana.consultar("estado").get("elegir"))
                    && "CAMPEONATO".equals(codigoDe(() -> luis.ejecutar("calificacion/votar", java.util.Map.of("partido", 1, "jugador", 1, "valor", 7,
                            "dispositivo", "dispositivo-L1"))));
            // Ana crea dos y Beto uno, cada uno con sus equipos (las rutas de equipos van al campeonato recien creado).
            String ligaAna = crearPorApi(ana, "Liga Ana", TipoFormato.LIGA, "Ana FC", "Ana United", "Ana City", "Ana Real");
            String copaAna = crearPorApi(ana, "Copa Ana", TipoFormato.ELIMINACION_DIRECTA, "Copa Uno", "Copa Dos");
            String ligaBeto = crearPorApi(beto, "Liga Beto", TipoFormato.LIGA, "Beto FC", "Beto United", "Beto City", "Beto Real");
            java.util.function.Function<alphasport.ui.ApiControlador, List<String>> nombres = api -> ((List<?>) api.consultar("campeonatos")
                    .get("campeonatos")).stream().map(x -> String.valueOf(((java.util.Map<?, ?>) x).get("nombre"))).toList();
            String organizadorDe = ((List<?>) luis.consultar("campeonatos").get("campeonatos")).stream().map(x -> (java.util.Map<?, ?>) x)
                    .filter(x -> x.get("nombre").equals("Liga Beto")).map(x -> String.valueOf(x.get("organizador"))).findFirst().orElse("");
            boolean listado = nombres.apply(ana).equals(List.of("Liga Ana", "Copa Ana")) && nombres.apply(beto).equals(List.of("Liga Beto"))
                    && nombres.apply(luis).equals(List.of("Liga Ana", "Copa Ana", "Liga Beto")) && organizadorDe.equals("Beto");
            // Equipos de cada uno, sin mezclarse.
            java.util.function.Function<String, List<String>> equipos = codigo -> gestor.paraLeer(codigo, luis.getUsuarioActual()).getCampeonato()
                    .getEquipos().stream().map(alphasport.model.Equipo::getNombreEquipo).toList();
            boolean separados = equipos.apply(ligaAna).equals(List.of("Ana FC", "Ana United", "Ana City", "Ana Real"))
                    && equipos.apply(copaAna).equals(List.of("Copa Uno", "Copa Dos"))
                    && equipos.apply(ligaBeto).equals(List.of("Beto FC", "Beto United", "Beto City", "Beto Real"));
            // Permisos en el servicio: Ana no ve ni toca lo de Beto, ni siquiera pidiendolo por la cabecera; Luis no cambia nada.
            alphasport.ui.ApiControlador anaEnBeto = base.cliente(ana.getToken(), ligaBeto);
            boolean permisos = "PERMISO".equals(codigoDe(() -> ana.ejecutar("campeonato/elegir", java.util.Map.of("codigo", ligaBeto))))
                    && "PERMISO".equals(codigoDe(() -> anaEnBeto.ejecutar("equipo/rapido", java.util.Map.of("nombre", "Intruso"))))
                    && "PERMISO".equals(codigoDe(() -> anaEnBeto.consultar("partido/1")))
                    && "PERMISO".equals(codigoDe(() -> base.cliente(luis.getToken(), ligaAna).ejecutar("equipo/rapido", java.util.Map.of("nombre", "X"))))
                    && "PERMISO".equals(codigoDe(() -> ana.ejecutar("campeonato/terminar", java.util.Map.of("codigo", ligaBeto))))
                    && equipos.apply(ligaBeto).size() == 4 && equipos.apply(ligaAna).size() == 4
                    && !"PERMISO".equals(codigoDe(() -> anaEnBeto.consultar("estado"))); // ver el estado no falla: vuelve a lo suyo
            // Respaldo: solo el Organizador dueño. El Aficionado y otro Organizador reciben PERMISO aunque llamen la ruta
            // directamente; abrir respaldos tampoco es del Aficionado; y el archivo no lleva cuentas, correos ni claves.
            alphasport.ui.ApiControlador anaEnSuLiga = base.cliente(ana.getToken(), ligaAna);
            byte[] respaldoAna = anaEnSuLiga.respaldo().datos();
            String textoRespaldo = new String(respaldoAna, java.nio.charset.StandardCharsets.ISO_8859_1);
            permisos &= "PERMISO".equals(codigoDe(() -> base.cliente(luis.getToken(), ligaAna).respaldo()))
                    && "PERMISO".equals(codigoDe(() -> base.cliente(ana.getToken(), ligaBeto).respaldo())) // otro Organizador
                    && "PERMISO".equals(codigoDe(() -> base.cliente(luis.getToken(), ligaAna).abrirRespaldo(respaldoAna)))
                    && respaldoAna.length > 0 && !textoRespaldo.contains("pbkdf2") && !textoRespaldo.contains("clave-ana1")
                    && !textoRespaldo.contains("@") && !textoRespaldo.contains("CuentaUsuario")
                    // el historial en texto sí lo puede pedir (sin partidos jugados responde que está vacío, no PERMISO)
                    && !"PERMISO".equals(codigoDe(() -> base.cliente(luis.getToken(), ligaAna).historialEnTexto()));
            // Elegir: Luis elige la de Beto y la ve; Beto, con uno solo, lo tiene sin elegir; Maria, con tres, tiene que elegir.
            luis.ejecutar("campeonato/elegir", java.util.Map.of("codigo", ligaBeto));
            alphasport.ui.ApiControlador betoOtraVez = entrar.apply("beto", "clave-beto1"), maria = entrar.apply("maria", "clave-maria1");
            java.util.function.Function<java.util.Map<String, Object>, String> nombreDe = estado ->
                    String.valueOf(((java.util.Map<?, ?>) estado.get("campeonato")).get("nombre"));
            boolean elegir = nombreDe.apply(luis.consultar("estado")).equals("Liga Beto") && nombreDe.apply(betoOtraVez.consultar("estado")).equals("Liga Beto")
                    && Boolean.FALSE.equals(maria.consultar("estado").get("hay")) && Boolean.TRUE.equals(maria.consultar("estado").get("elegir"))
                    && nombreDe.apply(base.cliente(maria.getToken(), copaAna).consultar("estado")).equals("Copa Ana")
                    && "CAMPEONATO".equals(codigoDe(() -> maria.consultar("partido/1")));
            // Partidos, tabla y votos: se juega en la liga de Ana y la de Beto no cambia.
            for (String codigo : List.of(ligaAna, ligaBeto)) {
                CampeonatoService s = gestor.paraGestionar(codigo, (codigo.equals(ligaAna) ? ana : beto).getUsuarioActual());
                for (alphasport.model.Equipo e : s.getCampeonato().getEquipos()) {
                    List<alphasport.model.Jugador> plantilla = new ArrayList<>();
                    for (int dorsal = 1; dorsal <= 12; dorsal++) {
                        plantilla.add(new alphasport.model.Jugador(e.getNombreEquipo() + " J" + dorsal, String.valueOf(200000 + e.getId() * 100 + dorsal), dorsal,
                                dorsal == 1 ? "Portero" : "Mediocampista central"));
                    }
                    s.equipos().guardarEquipo(e.getId(), e.getNombreEquipo(), "", null, plantilla);
                }
                s.avanzar();
            }
            CampeonatoService sAna = gestor.paraLeer(ligaAna, luis.getUsuarioActual()), sBeto = gestor.paraLeer(ligaBeto, luis.getUsuarioActual());
            Partido p = sAna.getCampeonato().getPartidos().get(0);
            List<alphasport.model.Jugador> loc = sAna.getCampeonato().buscarEquipo(p.getIdLocal()).getJugadores();
            List<alphasport.model.Jugador> vis = sAna.getCampeonato().buscarEquipo(p.getIdVisitante()).getJugadores();
            Partido datos = new Partido();
            datos.copiarDatosDe(p);
            datos.setEstado(EstadoPartido.JUGADO);
            datos.setGolesLocal(1);
            datos.setTitulares(new ArrayList<>(loc.subList(0, 11).stream().map(alphasport.model.Jugador::getId).toList()),
                    new ArrayList<>(vis.subList(0, 11).stream().map(alphasport.model.Jugador::getId).toList()));
            datos.setGoles(new ArrayList<>(List.of(new alphasport.model.Gol(p.getIdLocal(), 20, loc.get(8), false, null))));
            gestor.paraGestionar(ligaAna, ana.getUsuarioActual()).partidos().guardarPartido(p.getId(), datos, false);
            alphasport.ui.ApiControlador luisEnAna = base.cliente(luis.getToken(), ligaAna);
            luisEnAna.ejecutar("calificacion/votar", java.util.Map.of("partido", p.getId(), "jugador", loc.get(8).getId(), "valor", 9,
                    "dispositivo", "dispositivo-L1"));
            boolean juego = sAna.getCampeonato().getPartidos().stream().filter(Partido::isJugado).count() == 1
                    && sBeto.getCampeonato().getPartidos().stream().noneMatch(Partido::isJugado)
                    && sAna.getCampeonato().buscarPartido(p.getId()).getVotos().size() == 1
                    && sBeto.getCampeonato().getPartidos().stream().allMatch(x -> x.getVotos().isEmpty())
                    && sAna.getMotor().tablaDeFase(0).stream().mapToInt(FilaTabla::getPuntos).sum() == 3
                    && sBeto.getMotor().tablaDeFase(0).stream().mapToInt(FilaTabla::getPuntos).sum() == 0
                    // El voto pedido sobre la liga de Beto con el id del partido de Ana no llega a la de Ana.
                    && "REGLA".equals(codigoDe(() -> base.cliente(luis.getToken(), ligaBeto).ejecutar("calificacion/votar", java.util.Map.of(
                            "partido", p.getId(), "jugador", loc.get(8).getId(), "valor", 9, "dispositivo", "dispositivo-L2"))))
                    && sAna.getCampeonato().buscarPartido(p.getId()).getVotos().size() == 1;
            // Noticias: cada campeonato tiene las suyas y solo su Organizador las modera.
            base.cliente(luis.getToken(), ligaAna).ejecutar("noticia/proponer", java.util.Map.of("titulo", "Propuesta para Ana", "cuerpo", "Cuerpo de la propuesta"));
            alphasport.ui.ApiControlador anaEnLiga = base.cliente(ana.getToken(), ligaAna);
            java.util.function.Function<alphasport.ui.ApiControlador, Integer> pendientes = api -> ((List<?>) ((java.util.Map<?, ?>) api.consultar("noticias")
                    .get("noticias")).get("pendientes")).size();
            int idPropuesta = base.getNoticias().pendientes().get(0).getId();
            boolean noticias = pendientes.apply(anaEnLiga) == 1 && pendientes.apply(betoOtraVez) == 0
                    // Para Beto la noticia de Ana ni existe: en su campeonato no se ve.
                    && "REGLA".equals(codigoDe(() -> betoOtraVez.ejecutar("noticia/aprobar", java.util.Map.of("id", idPropuesta))))
                    && !base.getNoticias().pendientes().get(0).isAprobado();
            anaEnLiga.ejecutar("noticia/aprobar", java.util.Map.of("id", idPropuesta));
            noticias &= base.getNoticias().publicadas().get(0).getCampeonato().equals(ligaAna)
                    && ((List<?>) ((java.util.Map<?, ?>) base.cliente(luis.getToken(), ligaBeto).consultar("noticias").get("noticias")).get("publicadas")).isEmpty();
            // Terminar uno no afecta a los demas: pasa al historial de Ana y deja de estar en curso.
            anaEnLiga.ejecutar("campeonato/terminar", java.util.Map.of());
            boolean terminar = nombres.apply(ana).equals(List.of("Copa Ana")) && nombres.apply(luis).equals(List.of("Copa Ana", "Liga Beto"))
                    && equipos.apply(ligaBeto).size() == 4 && equipos.apply(copaAna).size() == 2
                    && gestor.historial(ana.getUsuarioActual()).stream().map(Campeonato::getNombreCampeonato).toList().equals(List.of("Liga Ana"))
                    && gestor.historial(beto.getUsuarioActual()).isEmpty() && gestor.historial(luis.getUsuarioActual()).size() == 1
                    && "CAMPEONATO".equals(codigoDe(() -> luisEnAna.ejecutar("calificacion/votar", java.util.Map.of("partido", p.getId(),
                            "jugador", loc.get(9).getId(), "valor", 9, "dispositivo", "dispositivo-L3"))));
            correcto = vacio && listado && separados && permisos && elegir && juego && noticias && terminar;
            if (!correcto) {
                System.out.println("  vacio=" + vacio + " listado=" + listado + " separados=" + separados + " permisos=" + permisos + " elegir=" + elegir
                        + " juego=" + juego + " noticias=" + noticias + " terminar=" + terminar);
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
            correcto = false;
        }
        System.out.println("Varios campeonatos a la vez (dueños, elegir y sin mezclar datos): " + (correcto ? "correcto" : "MAL"));
        if (!correcto) {
            fallos++;
        }
    }

    /** Crea un campeonato por la API (ruta «nuevo») con sus equipos y devuelve su codigo. */
    private static String crearPorApi(alphasport.ui.ApiControlador api, String nombre, TipoFormato formato, String... equipos) throws Exception {
        ConfiguracionTorneo cfg = new ConfiguracionTorneo();
        java.util.Map<String, Object> config = new java.util.HashMap<>();
        config.put("puntosVictoria", cfg.getPuntosVictoria());
        config.put("puntosEmpate", cfg.getPuntosEmpate());
        config.put("tamanoGrupo", cfg.getTamanoGrupo());
        config.put("clasificadosPorGrupo", cfg.getClasificadosPorGrupo());
        config.put("clasificadosPlayoff", cfg.getClasificadosPlayoff());
        config.put("tipoPlayoff", cfg.getTipoPlayoff());
        config.put("rondasSuizo", cfg.getRondasSuizo());
        config.put("directosSuizo", cfg.getDirectosSuizo());
        config.put("repechajeSuizo", cfg.getRepechajeSuizo());
        config.put("criterios", cfg.getCriterios().stream().map(Enum::name).toList());
        java.util.Map<String, Object> r = api.ejecutar("nuevo", java.util.Map.of("nombre", nombre, "formato", formato.name(), "config", config));
        for (String equipo : equipos) {
            api.ejecutar("equipo/rapido", java.util.Map.of("nombre", equipo));
        }
        return String.valueOf(((java.util.Map<?, ?>) ((java.util.Map<?, ?>) r.get("estado")).get("campeonato")).get("codigo"));
    }

    /** Codigo de la ReglaNegocioException que lanza la accion (null si no lanza ninguna). */
    private static String codigoDe(Accion accion) {
        try {
            accion.correr();
            return null;
        } catch (ReglaNegocioException e) {
            return e.getCodigo();
        } catch (Exception e) {
            return "otra excepcion: " + e;
        }
    }

    /**
     * Las plantillas se muestran por linea (Porteria, Defensa, Mediocampo, Ataque) y dentro de cada una por dorsal,
     * sin tocar el catalogo de posiciones.
     */
    private static void probarOrdenPorLinea() {
        boolean correcto;
        try {
            boolean catalogo = alphasport.model.Jugador.POSICIONES.length == 19 && alphasport.model.Jugador.GRUPOS_POSICIONES.length == 4
                    && alphasport.model.Jugador.GRUPOS_POSICIONES[1][0].equals("Defensa")
                    && List.of(alphasport.model.Jugador.POSICIONES).containsAll(List.of("Líbero", "Carrilero izquierdo", "Mediapunta", "Volante derecho"))
                    && alphasport.model.Jugador.lineaDe(" portero ") == 0 && alphasport.model.Jugador.lineaDe("Mediapunta") == 3
                    && alphasport.model.Jugador.lineaDe("Utilero") == alphasport.model.Jugador.SIN_LINEA;
            CampeonatoService servicio = new CampeonatoService(
                    new alphasport.persistence.ArchivoRepositorioCampeonato(java.nio.file.Files.createTempDirectory("alphasport-orden")),
                    new alphasport.persistence.ArchivoRepositorioHistorial(java.nio.file.Files.createTempDirectory("alphasport-orden")));
            servicio.crear("Orden", TipoFormato.LIGA, new ConfiguracionTorneo(), false);
            servicio.equipos().agregarEquipo("Orden FC");
            alphasport.model.Equipo e = servicio.getCampeonato().getEquipos().get(0);
            String[][] datos = {{"Nueve", "9", "Delantero centro"}, {"Uno", "1", "Portero"}, {"Ocho", "8", "Mediocampista central"},
                {"Cuatro", "4", "Defensa central"}, {"Doce", "12", "Portero"}, {"Dos", "2", "Lateral derecho"}, {"Once", "11", "Extremo izquierdo"}};
            List<alphasport.model.Jugador> plantilla = new ArrayList<>();
            for (String[] d : datos) {
                plantilla.add(new alphasport.model.Jugador(d[0], String.valueOf(50000 + Integer.parseInt(d[1])), Integer.parseInt(d[1]), d[2]));
            }
            servicio.equipos().guardarEquipo(e.getId(), "Orden FC", "", null, plantilla);
            alphasport.ui.ApiControlador api = new alphasport.ui.ApiControlador(servicio, new AutenticacionService());
            api.ejecutar("login", java.util.Map.of("usuario", "admin", "clave", "123"));
            @SuppressWarnings("unchecked")
            List<java.util.Map<String, Object>> enPantalla = (List<java.util.Map<String, Object>>) ((List<java.util.Map<String, Object>>) api.consultar("estado")
                    .get("equipos")).get(0).get("jugadores");
            List<String> nombres = enPantalla.stream().map(j -> (String) j.get("nombre")).toList();
            boolean orden = nombres.equals(List.of("Uno", "Doce", "Dos", "Cuatro", "Ocho", "Nueve", "Once"))
                    && enPantalla.get(0).get("linea").equals(0) && enPantalla.get(6).get("linea").equals(3);
            correcto = catalogo && orden;
            if (!correcto) {
                System.out.println("  catalogo=" + catalogo + " orden=" + nombres);
            }
        } catch (Exception ex) {
            ex.printStackTrace(System.out);
            correcto = false;
        }
        System.out.println("Plantillas ordenadas por línea (Portería a Ataque): " + (correcto ? "correcto" : "MAL"));
        if (!correcto) {
            fallos++;
        }
    }

    /** Un numero de documento es de un solo jugador del campeonato (sin contar espacios ni mayusculas). */
    private static void probarDocumentoUnico() {
        boolean correcto;
        try {
            CampeonatoService servicio = new CampeonatoService(
                    new alphasport.persistence.ArchivoRepositorioCampeonato(java.nio.file.Files.createTempDirectory("alphasport-documento")),
                    new alphasport.persistence.ArchivoRepositorioHistorial(java.nio.file.Files.createTempDirectory("alphasport-documento")));
            servicio.crear("Documentos", TipoFormato.LIGA, new ConfiguracionTorneo(), false);
            servicio.equipos().agregarEquipo("Rojos");
            servicio.equipos().agregarEquipo("Azules");
            int rojos = servicio.getCampeonato().getEquipos().get(0).getId(), azules = servicio.getCampeonato().getEquipos().get(1).getId();
            java.util.function.Function<String[][], List<alphasport.model.Jugador>> plantilla = filas -> {
                List<alphasport.model.Jugador> l = new ArrayList<>();
                for (String[] f : filas) {
                    l.add(new alphasport.model.Jugador(f[0], f[1], Integer.parseInt(f[2]), "Portero"));
                }
                return l;
            };
            servicio.equipos().guardarEquipo(rojos, "Rojos", "", null, plantilla.apply(new String[][]{{"Ana", "  10 20  30 ", "1"}, {"Bea", "", "2"}, {"Cris", "", "3"}}));
            boolean limpia = servicio.buscarEquipo(rojos).getJugadores().get(0).getDocumento().equals("102030"); // se guardan solo los dígitos
            // Mismo documento en la misma plantilla, en otro equipo (con otros espacios y mayusculas) o en un equipo nuevo.
            String mensajeRepetido = mensaje(() -> servicio.equipos().guardarEquipo(rojos, "Rojos", "", null,
                    plantilla.apply(new String[][]{{"Ana", "102030", "1"}, {"Dani", "10 2030", "4"}})));
            String mensajeOtroEquipo = mensaje(() -> servicio.equipos().guardarEquipo(azules, "Azules", "", null,
                    plantilla.apply(new String[][]{{"Eva", "1020 30", "1"}})));
            // Documentos con letras o muy cortos: se rechazan (solo números, de 5 a 12 dígitos).
            boolean letras = servicio.getCampeonato().getEquipos().size() == 2
                    && rechaza(() -> servicio.equipos().guardarEquipo(azules, "Azules", "", null, plantilla.apply(new String[][]{{"Eva", "ab-1", "1"}})))
                    && rechaza(() -> servicio.equipos().guardarEquipo(azules, "Azules", "", null, plantilla.apply(new String[][]{{"Eva", "123", "1"}})));
            letras &= rechaza(() -> servicio.equipos().guardarEquipo(null, "Verdes", "", null, plantilla.apply(new String[][]{{"Fer", "AB-1", "1"}})))
                    && servicio.getCampeonato().buscarEquipoPorNombre("Verdes") == null;
            // Sin documento se permite, y el mismo jugador puede volver a guardarse con su documento.
            servicio.equipos().guardarEquipo(rojos, "Rojos", "", null, new ArrayList<>(servicio.buscarEquipo(rojos).getJugadores()));
            // Los datos de prueba con la misma semilla no repiten documentos.
            servicio.equipos().cargarDatosPrueba(5, 2, false);
            servicio.equipos().cargarDatosPrueba(5, 2, false);
            java.util.Set<String> documentos = new java.util.HashSet<>();
            boolean semilla = servicio.getCampeonato().getEquipos().stream().flatMap(e -> e.getJugadores().stream())
                    .map(j -> ReglasPlantilla.claveDocumento(j.getDocumento())).filter(d -> !d.isEmpty()).allMatch(documentos::add)
                    && servicio.getCampeonato().getEquipos().size() >= 5;
            boolean mensajes = mensajeRepetido.contains("repetido en la plantilla") && mensajeRepetido.contains("#1 Ana")
                    && mensajeOtroEquipo.contains("ya está registrado") && mensajeOtroEquipo.contains("#1 Ana de Rojos");
            correcto = limpia && mensajes && letras && semilla;
            if (!correcto) {
                System.out.println("  limpia=" + limpia + " letras=" + letras + " semilla=" + semilla + "\n  " + mensajeRepetido + "\n  " + mensajeOtroEquipo);
            }
        } catch (Exception ex) {
            ex.printStackTrace(System.out);
            correcto = false;
        }
        System.out.println("Documento único por jugador: " + (correcto ? "correcto" : "MAL"));
        if (!correcto) {
            fallos++;
        }
    }

    /** Mensaje de la ReglaNegocioException que lanza la accion ("" si no la lanza). */
    private static String mensaje(Accion accion) {
        try {
            accion.correr();
            return "";
        } catch (ReglaNegocioException e) {
            return e.getMessage();
        } catch (Exception e) {
            return e.toString();
        }
    }

    private interface Accion {
        void correr() throws Exception;
    }

    /** true si la accion lanza una ReglaNegocioException (la regla se cumplio). */
    private static boolean rechaza(Accion accion) {
        try {
            accion.correr();
            return false;
        } catch (ReglaNegocioException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * «Gestionar partidos»: la previa (XI, formación, fecha y sede) se guarda en un partido PENDIENTE,
     * el resultado se registra después con cambios sobre ese mismo XI, y al borrar el resultado la
     * previa se conserva. Todo a través de ApiControlador, igual que el escritorio y la web.
     */
    private static void probarPreviaYResultado() {
        boolean correcto;
        try {
            CampeonatoService servicio = new CampeonatoService(
                    new alphasport.persistence.ArchivoRepositorioCampeonato(java.nio.file.Files.createTempDirectory("alphasport-previa")),
                    new alphasport.persistence.ArchivoRepositorioHistorial(java.nio.file.Files.createTempDirectory("alphasport-previa")));
            ConfiguracionTorneo cfg = new ConfiguracionTorneo();
            cfg.setSorteoAleatorio(false);
            servicio.crear("Previa", TipoFormato.LIGA, cfg, false);
            for (int i = 1; i <= 4; i++) {
                servicio.equipos().agregarEquipo("P" + i);
            }
            Campeonato c = servicio.getCampeonato();
            for (alphasport.model.Equipo e : new ArrayList<>(c.getEquipos())) {
                List<alphasport.model.Jugador> plantilla = new ArrayList<>();
                for (int d = 1; d <= 14; d++) {
                    plantilla.add(new alphasport.model.Jugador(e.getNombreEquipo() + " J" + d, String.valueOf(300000 + e.getId() * 100 + d), d, "Mediocampista"));
                }
                servicio.equipos().guardarEquipo(e.getId(), e.getNombreEquipo(), "", null, plantilla);
            }
            servicio.avanzar();
            alphasport.ui.ApiControlador api = new alphasport.ui.ApiControlador(servicio, new AutenticacionService(),
                    new NoticiasService(new alphasport.persistence.RepositorioNoticiasEnMemoria()));
            api.ejecutar("login", java.util.Map.of("usuario", "admin", "clave", "123"));
            Partido p = c.getPartidos().get(0);
            java.util.function.Function<Integer, List<Integer>> once = idEq -> c.buscarEquipo(idEq).getJugadores().stream()
                    .limit(11).map(alphasport.model.Jugador::getId).toList();
            List<Integer> xiLocal = once.apply(p.getIdLocal()), xiVisitante = once.apply(p.getIdVisitante());
            // 1) Previa: partido pendiente con XI, formación, fecha, hora y sede.
            java.util.Map<String, Object> previa = new java.util.HashMap<>(java.util.Map.of("estado", "PENDIENTE",
                    "titularesLocal", xiLocal, "titularesVisitante", xiVisitante, "formacionLocal", "4-4-2", "formacionVisitante", "4-3-3",
                    "fecha", java.time.LocalDate.now().toString(), "hora", "18:00", "sede", "Estadio Norte", "obs", ""));
            previa.putAll(java.util.Map.of("gl", 0, "gv", 0, "pl", -1, "pv", -1, "tarjetas", List.of(), "cambios", List.of(), "goles", List.of()));
            api.ejecutar("partido", java.util.Map.of("id", p.getId(), "datos", previa, "accion", "guardar", "confirmado", false));
            boolean previaGuardada = !p.isJugado() && p.getTitularesLocal().equals(xiLocal) && p.getTitularesVisitante().equals(xiVisitante)
                    && p.getFormacionLocal().equals("4-4-2") && p.getSede().equals("Estadio Norte");
            // La alineación lleva los 11 de la previa y, como suplentes, los 3 que sobran de la plantilla (por dorsal).
            DetallePartido.Alineacion alineacion = servicio.partidos().detallePartido(p.getId()).getAlineacionLocal();
            previaGuardada &= alineacion.confirmada() && alineacion.lineas().stream().mapToInt(List::size).sum() == 11
                    && alineacion.suplentes().stream().map(alphasport.model.Jugador::getDorsal).toList().equals(List.of(12, 13, 14));
            // 2) Resultado: el cambio usa el XI de la previa (sale un titular, entra un suplente).
            int sale = xiLocal.get(10);
            int entra = c.buscarEquipo(p.getIdLocal()).getJugadores().get(12).getId();
            java.util.Map<String, Object> resultado = new java.util.HashMap<>(previa);
            resultado.putAll(java.util.Map.of("estado", "JUGADO", "gl", 2, "gv", 1,
                    "cambios", List.of(java.util.Map.of("equipo", p.getIdLocal(), "minuto", 60, "sale", sale, "entra", entra))));
            api.ejecutar("partido", java.util.Map.of("id", p.getId(), "datos", resultado, "accion", "guardar", "confirmado", false));
            boolean resultadoGuardado = p.isJugado() && p.getGolesLocal() == 2 && p.getCambios().size() == 1
                    && p.getTitularesLocal().equals(xiLocal);
            // 3) Borrar el resultado no borra la previa.
            api.ejecutar("partido", java.util.Map.of("id", p.getId(), "datos", resultado, "accion", "borrar", "confirmado", false));
            boolean previaConservada = !p.isJugado() && p.getCambios().isEmpty() && p.getTitularesLocal().equals(xiLocal)
                    && p.getFormacionVisitante().equals("4-3-3");
            // 4) [REQ-07] Votacion por la API: el admin programa las horas y cierra; el visitante la ve, vota y la deja de ver.
            resultado.put("horasVotacion", 48);
            api.ejecutar("partido", java.util.Map.of("id", p.getId(), "datos", resultado, "accion", "guardar", "confirmado", false));
            boolean adminNoVota = rechaza(() -> api.ejecutar("calificacion/votar", java.util.Map.of("partido", p.getId(), "jugador", xiLocal.get(0), "valor", 8, "dispositivo", "dispositivo-A1")));
            api.ejecutar("logout", java.util.Map.of());
            api.ejecutar("login", java.util.Map.of("usuario", "visitante", "clave", "321")); // cuenta de prueba (aficionado)
            java.util.function.Supplier<Integer> abiertas = () -> ((List<?>) api.consultar("votaciones/dispositivo-A1").get("votaciones")).size();
            boolean votacion = adminNoVota && p.getHorasVotacion() == 48 && abiertas.get() == 1;
            java.util.Map<String, Object> votado = api.ejecutar("calificacion/votar", java.util.Map.of("partido", p.getId(), "dispositivo", "dispositivo-A1",
                    "votos", List.of(java.util.Map.of("jugador", xiLocal.get(0), "valor", 8), java.util.Map.of("jugador", entra, "valor", 9))));
            votacion &= Boolean.TRUE.equals(votado.get("ok")) && p.getVotos().size() == 2 && abiertas.get() == 1
                    && rechaza(() -> api.ejecutar("votacion/cerrar", java.util.Map.of("partido", p.getId()))); // el visitante no la cierra
            api.ejecutar("login", java.util.Map.of("usuario", "admin", "clave", "123"));
            api.ejecutar("votacion/cerrar", java.util.Map.of("partido", p.getId()));
            votacion &= abiertas.get() == 0;

            correcto = previaGuardada && resultadoGuardado && previaConservada && votacion;
            if (!correcto) {
                System.out.println("  previa=" + previaGuardada + " resultado=" + resultadoGuardado + " conservada=" + previaConservada + " votacion=" + votacion);
            }
        } catch (Exception e) {
            correcto = false;
            System.out.println("  ERROR: " + e);
        }
        System.out.println("Previa del partido y registro de resultados: " + (correcto ? "correcto" : "MAL"));
        if (!correcto) {
            fallos++;
        }
    }

    private static ConfiguracionTorneo configuracion(int n, int variante) {
        ConfiguracionTorneo cfg = new ConfiguracionTorneo();
        cfg.setIdaYVuelta(variante % 2 == 1);
        cfg.setEliminatoriaIdaYVuelta(variante % 3 == 1);
        cfg.setFinalUnica(variante != 4);
        cfg.setSorteoAleatorio(variante % 2 == 0);
        cfg.setGranFinalConReinicio(variante % 2 == 0);
        cfg.setTipoPlayoff(variante >= 3 ? ConfiguracionTorneo.PLAYOFF_CUADRANGULARES : ConfiguracionTorneo.PLAYOFF_LLAVE);
        cfg.setClasificadosPlayoff(Math.min(8, n));
        cfg.setVentajaDeportiva(variante % 2 == 0);
        cfg.setClasificadosPorGrupo(variante % 3 == 2 ? 3 : 2);
        cfg.setRondasSuizo(Math.min(8, n - 1));
        int llave = 2;
        while (llave * 2 <= n / 2) {
            llave *= 2;
        }
        boolean conRepechaje = llave + llave / 2 <= n;
        cfg.setDirectosSuizo(conRepechaje ? llave / 2 : Math.min(n, llave));
        cfg.setRepechajeSuizo(conRepechaje ? llave : 0);
        return cfg;
    }

    private static String jugar(MotorCampeonato motor, int n) {
        Campeonato c = motor.getCampeonato();
        try {
            motor.iniciar();
        } catch (IllegalStateException e) {
            return "INVALIDO " + e.getMessage();
        }
        for (int vuelta = 0; vuelta < 500; vuelta++) {
            int indice = c.getFases().size() - 1;
            for (Partido p : c.partidosDeFase(indice)) {
                if (p.isJugado()) {
                    continue;
                }
                Partido datos = new Partido();
                datos.copiarDatosDe(p);
                datos.setEstado(EstadoPartido.JUGADO);
                datos.setGolesLocal(azar.nextInt(4));
                datos.setGolesVisitante(azar.nextInt(4));
                datos.setAmarillasLocal(azar.nextInt(3));
                if (motor.requierePenales(p, datos.getGolesLocal(), datos.getGolesVisitante())) {
                    datos.setPenalesLocal(4);
                    datos.setPenalesVisitante(azar.nextBoolean() ? 3 : 5);
                }
                motor.aplicarEdicion(p, datos, false);
            }
            // ningun equipo juega dos veces en la misma ronda (la vuelta de una llave no cuenta)
            HashSet<String> vistos = new HashSet<>();
            for (Partido p : c.partidosDeFase(indice)) {
                if (p.getIdLocal() == p.getIdVisitante()) {
                    return "partido de un equipo contra si mismo";
                }
                for (int id : new int[]{p.getIdLocal(), p.getIdVisitante()}) {
                    if (p.getPierna() != 2 && !vistos.add(p.getGrupo() + "/" + p.getRonda() + "/" + id)) {
                        return "equipo " + id + " repetido en la ronda " + p.getRonda();
                    }
                }
            }
            try {
                motor.avanzar();
            } catch (IllegalStateException e) {
                return "ERROR al avanzar: " + e.getMessage();
            }
            if (c.isTerminado()) {
                if (c.getFormato() == TipoFormato.DOBLE_ELIMINACION) {
                    int partidos = c.getPartidos().size();
                    if (partidos < 2 * n - 2 || partidos > 2 * n - 1) {
                        return "doble eliminacion con " + partidos + " partidos para " + n + " equipos";
                    }
                }
                String fases = "";
                for (Fase f : c.getFases()) {
                    fases += f.getNombre() + " > ";
                }
                return "OK " + fases + c.nombreDe(c.getIdCampeon());
            }
        }
        return "el campeonato no termino";
    }

    /**
     * A, B, C y D. C y D empatan a 6 puntos y C le gano a D.
     * A y B empatan a 3 puntos: B tiene mejor diferencia, pero A le gano a B.
     * Con "enfrentamiento directo" primero el orden debe ser C D A B.
     */
    private static void probarDesempateDirecto() {
        ConfiguracionTorneo cfg = new ConfiguracionTorneo();
        cfg.getCriterios().clear();
        cfg.getCriterios().add(CriterioDesempate.ENFRENTAMIENTO_DIRECTO);
        cfg.getCriterios().add(CriterioDesempate.DIFERENCIA_GOLES);
        Campeonato c = new Campeonato("Desempate", TipoFormato.LIGA, cfg);
        for (String nombre : new String[]{"A", "B", "C", "D"}) {
            c.agregarEquipo(nombre);
        }
        int[][] resultados = {{1, 2, 1, 0}, {2, 3, 3, 0}, {3, 1, 2, 0}, {1, 4, 0, 1}, {2, 4, 0, 1}, {3, 4, 1, 0}};
        ArrayList<Partido> partidos = new ArrayList<>();
        for (int[] r : resultados) {
            Partido p = c.crearPartido(r[0], r[1]);
            p.setEstado(EstadoPartido.JUGADO);
            p.setGolesLocal(r[2]);
            p.setGolesVisitante(r[3]);
            partidos.add(p);
        }
        String orden = "";
        for (FilaTabla f : new TablaPosiciones(c).calcular(partidos, List.of(1, 2, 3, 4))) {
            orden += f.getEquipo().getNombreEquipo() + " ";
        }
        boolean correcto = orden.trim().equals("C D A B");
        System.out.println("Desempate por enfrentamiento directo: " + orden + (correcto ? "(correcto)" : "(MAL)"));
        if (!correcto) {
            fallos++;
        }
    }

    /**
     * Cambiar la fecha de un partido de la fase regular no afecta la
     * liguilla; cambiar el marcador si, y obliga a regenerarla.
     */
    private static void probarEdicion() {
        ConfiguracionTorneo cfg = new ConfiguracionTorneo();
        cfg.setClasificadosPlayoff(4);
        Campeonato c = new Campeonato("Edicion", TipoFormato.TORNEO_CORTO, cfg);
        c.setSemilla(7); // sorteo fijo: con un sorteo al azar el partido editado a veces no cambia la tabla
        for (int i = 1; i <= 6; i++) {
            c.agregarEquipo("E" + i);
        }
        MotorCampeonato motor = new MotorCampeonato(c);
        motor.iniciar();
        for (Partido p : c.partidosDeFase(0)) {
            Partido datos = new Partido();
            datos.copiarDatosDe(p);
            datos.setEstado(EstadoPartido.JUGADO);
            datos.setGolesLocal(p.getIdLocal() < p.getIdVisitante() ? 2 : 0);
            motor.aplicarEdicion(p, datos, false);
        }
        motor.avanzar();
        Partido primero = c.partidosDeFase(0).get(0);
        Partido datos = new Partido();
        datos.copiarDatosDe(primero);
        datos.setFecha("01/01/2027");
        boolean afectaFecha = motor.afectaRondasPosteriores(primero, datos);
        datos.setGolesLocal(0);
        datos.setGolesVisitante(9);
        boolean afectaMarcador = motor.afectaRondasPosteriores(primero, datos);
        motor.aplicarEdicion(primero, datos, afectaMarcador);
        boolean correcto = !afectaFecha && afectaMarcador && c.getFases().size() == 1;
        System.out.println("Edicion de partidos: " + (correcto ? "correcto" : "MAL"));
        if (!correcto) {
            fallos++;
        }
    }

    /**
     * Copa de 8 equipos: en la clasificacion final el campeon va 1.o y el
     * finalista 2.o; en una liga el orden es el de la tabla. El palmares de
     * dos copias del mismo torneo le da 2 titulos al campeon.
     */
    private static void probarResumenYPalmares() {
        boolean correcto = true;
        for (TipoFormato formato : TipoFormato.values()) {
            ConfiguracionTorneo cfg = new ConfiguracionTorneo();
            cfg.setClasificadosPlayoff(4);
            cfg.setDirectosSuizo(2);
            cfg.setRepechajeSuizo(4);
            cfg.setRondasSuizo(4);
            Campeonato c = new Campeonato("Resumen", formato, cfg);
            for (int i = 1; i <= 8; i++) {
                c.agregarEquipo("E" + i);
            }
            jugar(new MotorCampeonato(c), 8);
            ResumenCampeonato resumen = new ResumenCampeonato(c);
            ArrayList<FilaTabla> clasificacion = resumen.clasificacionFinal();
            boolean campeonPrimero = c.isTerminado() && clasificacion.get(0).getIdEquipo() == c.getIdCampeon();
            boolean todos = clasificacion.size() == 8;
            // el ultimo partido jugado es la final (o gran final): su perdedor debe ir 2.o
            boolean segundo = true;
            if (formato != TipoFormato.LIGA) {
                Partido ultimo = c.getPartidos().get(c.getPartidos().size() - 1);
                segundo = clasificacion.get(1).getIdEquipo() == ultimo.getRival(c.getIdCampeon());
            }
            ArrayList<Palmares.Fila> palmares = Palmares.calcular(List.of(c, c));
            boolean titulos = palmares.get(0).getTitulos() == 2
                    && palmares.get(0).getEquipo().equals(c.nombreDe(c.getIdCampeon()));
            boolean premios = resumen.equipoMasGoleador() != null && resumen.mayorGoleada() != null && !resumen.texto().isEmpty();
            if (!(campeonPrimero && todos && segundo && titulos && premios)) {
                System.out.println("  Resumen MAL en " + formato + ": campeon=" + campeonPrimero + " todos=" + todos
                        + " segundo=" + segundo + " titulos=" + titulos + " premios=" + premios);
                correcto = false;
            }
        }
        System.out.println("Resumen, premios y palmares: " + (correcto ? "correcto" : "MAL"));
        if (!correcto) {
            fallos++;
        }
    }

    /**
     * Liga de 4 equipos (3 jornadas). Un jugador ve roja con 2 fechas en su
     * primer partido: queda fuera de los partidos 2 y 3 de su equipo.
     */
    private static void probarSanciones() {
        Campeonato c = new Campeonato("Sanciones", TipoFormato.LIGA, new ConfiguracionTorneo());
        for (int i = 1; i <= 4; i++) {
            c.agregarEquipo("E" + i);
        }
        alphasport.model.Jugador jugador = new alphasport.model.Jugador("Jugador expulsado", "10001", 10, "Delantero");
        jugador.setId(c.nuevoIdJugador());
        c.buscarEquipo(1).agregarJugador(jugador);
        MotorCampeonato motor = new MotorCampeonato(c);
        motor.iniciar();

        ArrayList<Partido> delEquipo = new ArrayList<>();
        for (Partido p : c.getPartidos()) {
            if (p.involucra(1)) {
                delEquipo.add(p);
            }
        }
        Partido primero = delEquipo.get(0);
        Partido datos = new Partido();
        datos.copiarDatosDe(primero);
        datos.setEstado(EstadoPartido.JUGADO);
        ArrayList<alphasport.model.Tarjeta> tarjetas = new ArrayList<>();
        tarjetas.add(new alphasport.model.Tarjeta(1, jugador, alphasport.model.Tarjeta.ROJA, 2));
        datos.setTarjetas(tarjetas, primero.getIdLocal());
        boolean contadaBien = primero.getIdLocal() == 1
                ? datos.getRojasLocal() == 1 && datos.getRojasVisitante() == 0
                : datos.getRojasVisitante() == 1 && datos.getRojasLocal() == 0;
        motor.aplicarEdicion(primero, datos, false);

        Sanciones s = new Sanciones(c);
        boolean fuera2 = s.estaSuspendido(delEquipo.get(1), jugador.getId());
        boolean fuera3 = s.estaSuspendido(delEquipo.get(2), jugador.getId());
        int pendientesAntes = s.fechasPendientesDe(jugador.getId());
        delEquipo.get(1).setEstado(EstadoPartido.JUGADO);
        int pendientesDespues = s.fechasPendientesDe(jugador.getId());
        boolean rojaCuenta = new TablaPosiciones(c).calcular(c.getPartidos(), List.of(1, 2, 3, 4)).stream()
                .anyMatch(f -> f.getIdEquipo() == 1 && f.getRojas() == 1);
        boolean correcto = fuera2 && fuera3 && pendientesAntes == 2 && pendientesDespues == 1 && rojaCuenta && contadaBien;
        System.out.println("Tarjetas y suspensiones: " + (correcto ? "correcto" : "MAL"));
        if (!correcto) {
            fallos++;
        }
    }

    /**
     * Goleadores, asistencias, valla y juego limpio: un 2-1 con un autogol. El autogol no
     * suma al goleador, la asistencia si, y un partido «En juego» todavia no cuenta.
     */
    private static void probarEstadisticas() {
        Campeonato c = new Campeonato("Estadisticas", TipoFormato.LIGA, new ConfiguracionTorneo());
        for (int i = 1; i <= 4; i++) {
            c.agregarEquipo("E" + i);
        }
        alphasport.model.Jugador nueve = new alphasport.model.Jugador("Nueve", "10001", 9, "Delantero centro");
        alphasport.model.Jugador diez = new alphasport.model.Jugador("Diez", "10002", 10, "Mediapunta");
        alphasport.model.Jugador central = new alphasport.model.Jugador("Central", "10003", 4, "Defensa central");
        nueve.setId(c.nuevoIdJugador());
        diez.setId(c.nuevoIdJugador());
        central.setId(c.nuevoIdJugador());
        c.buscarEquipo(1).agregarJugador(nueve);
        c.buscarEquipo(1).agregarJugador(diez);
        c.buscarEquipo(2).agregarJugador(central);
        MotorCampeonato motor = new MotorCampeonato(c);
        motor.iniciar();
        Partido p = c.getPartidos().stream().filter(x -> x.involucra(1) && x.involucra(2)).findFirst().orElseThrow();
        Partido datos = new Partido();
        datos.copiarDatosDe(p);
        datos.setEstado(EstadoPartido.EN_JUEGO);
        boolean unoEsLocal = p.getIdLocal() == 1;
        datos.setGolesLocal(unoEsLocal ? 2 : 1);
        datos.setGolesVisitante(unoEsLocal ? 1 : 2);
        ArrayList<alphasport.model.Gol> goles = new ArrayList<>();
        goles.add(new alphasport.model.Gol(1, 10, nueve, false, diez));    // gol de 9 con asistencia de 10
        goles.add(new alphasport.model.Gol(1, 50, central, true, null));   // autogol del central (equipo 2) a favor del 1
        goles.add(new alphasport.model.Gol(2, 80, central, false, null));  // gol del central para el 2
        datos.setGoles(goles);
        motor.aplicarEdicion(p, datos, false);
        boolean enJuegoNoCuenta = new EstadisticasCampeonato(c).getGoleadores().isEmpty();

        p.setEstado(EstadoPartido.JUGADO);
        EstadisticasCampeonato est = new EstadisticasCampeonato(c);
        List<EstadisticasCampeonato.FilaJugador> goleadores = est.getGoleadores();
        boolean goleadoresBien = goleadores.size() == 2 && goleadores.stream().allMatch(f -> f.getGoles() == 1)
                && goleadores.stream().anyMatch(f -> f.getIdJugador() == central.getId() && f.getIdEquipo() == 2);
        boolean asistenciaBien = est.getAsistidores().size() == 1 && est.getAsistidores().get(0).getIdJugador() == diez.getId();
        boolean vallaBien = est.getVallaMenosVencida().size() == 2 && est.getGolesSinAutor() == 0
                && est.getVallaMenosVencida().get(0).fila().getIdEquipo() == 1;
        boolean copiaBien = copiaConGoles(p);
        boolean correcto = enJuegoNoCuenta && goleadoresBien && asistenciaBien && vallaBien && copiaBien;
        System.out.println("Goleadores, asistencias y valla: " + (correcto ? "correcto" : "MAL"));
        if (!correcto) {
            fallos++;
        }
    }

    /** La copia que usa el editor conserva los goles, y borrar el resultado los quita. */
    private static boolean copiaConGoles(Partido p) {
        Partido copia = new Partido();
        copia.copiarDatosDe(p);
        boolean conserva = copia.getGoles().size() == 3;
        copia.reiniciarResultado();
        return conserva && copia.getGoles().isEmpty() && p.getGoles().size() == 3;
    }

    /**
     * Liga con 3 divisiones de 4 equipos: se juega completa, el campeon sale de la
     * Liga A, no hay llaves y en la siguiente temporada suben y bajan 1 por division.
     */
    private static void probarDivisiones() {
        boolean correcto;
        try {
            java.nio.file.Path carpeta = java.nio.file.Files.createTempDirectory("alphasport-prueba");
            CampeonatoService servicio = new CampeonatoService(
                    new alphasport.persistence.ArchivoRepositorioCampeonato(carpeta),
                    new alphasport.persistence.ArchivoRepositorioHistorial(carpeta));
            ConfiguracionTorneo cfg = new ConfiguracionTorneo();
            cfg.setSorteoAleatorio(false);
            cfg.setDivisiones(2);
            cfg.setAscensos(1);
            servicio.crear("Liga por divisiones", TipoFormato.LIGA, cfg, false);
            servicio.agregarDivision();                       // la liga se expande: Liga C
            for (int i = 1; i <= 12; i++) {
                servicio.equipos().agregarEquipo("D" + i);              // se reparten: 4 por division
            }
            Campeonato c = servicio.getCampeonato();
            boolean reparto = c.equiposDeDivision(0).size() == 4 && c.equiposDeDivision(1).size() == 4
                    && c.equiposDeDivision(2).size() == 4;
            String resultado = jugar(servicio.getMotor(), 12);
            Fase fase = c.getFases().get(0);
            boolean campeonDeA = c.equiposDeDivision(0).stream().anyMatch(e -> e.getId() == c.getIdCampeon());
            boolean sinLlaves = !servicio.getMotor().usaLlaves() && fase.isDivisiones() && fase.getGrupos().size() == 3
                    && c.getPartidos().size() == 3 * 6 && fase.nombreGrupo(1).equals("Liga B");
            // ultimo de la A y primero de la B antes de la nueva temporada
            int ultimoA = servicio.getMotor().tablaDeGrupo(0, 0).get(3).getIdEquipo();
            int primeroB = servicio.getMotor().tablaDeGrupo(0, 1).get(0).getIdEquipo();
            String movimientos = servicio.siguienteTemporada("Temporada 2");
            Campeonato nueva = servicio.getCampeonato();
            boolean ascenso = nueva.divisionDe(nueva.buscarEquipo(primeroB)) == 0
                    && nueva.divisionDe(nueva.buscarEquipo(ultimoA)) == 1
                    && nueva.equiposDeDivision(0).size() == 4 && nueva.equiposDeDivision(2).size() == 4
                    && !nueva.isIniciado() && movimientos.contains("Suben a la Liga A");
            boolean otrosConLlaves = new MotorCampeonato(new Campeonato("x", TipoFormato.TORNEO_CORTO, new ConfiguracionTorneo())).usaLlaves();
            correcto = reparto && resultado.startsWith("OK") && campeonDeA && sinLlaves && ascenso && otrosConLlaves;
            if (!correcto) {
                System.out.println("  reparto=" + reparto + " resultado=" + resultado + " campeonDeA=" + campeonDeA
                        + " sinLlaves=" + sinLlaves + " ascenso=" + ascenso + " " + movimientos);
            }
        } catch (Exception e) {
            correcto = false;
            System.out.println("  ERROR: " + e);
        }
        System.out.println("Liga con divisiones (A, B, C): " + (correcto ? "correcto" : "MAL"));
        if (!correcto) {
            fallos++;
        }
    }

    /**
     * W.O. desde el editor (que trabaja sobre una copia sin ids): gana el local y gana el visitante,
     * cada uno con sus 3 goles y sus 3 puntos. Y en una liga con divisiones hay un campeon por division.
     */
    /** Deja cada equipo con al menos el mínimo de jugadores para iniciar (ReglasPlantilla.MINIMO_JUGADORES). */
    static void completarPlantillas(Campeonato c) {
        for (alphasport.model.Equipo e : c.getEquipos()) {
            for (int dorsal = e.getJugadores().size() + 1; e.getJugadores().size() < ReglasPlantilla.MINIMO_JUGADORES; dorsal++) {
                alphasport.model.Jugador j = new alphasport.model.Jugador(e.getNombreEquipo() + " J" + dorsal, String.valueOf(700000 + e.getId() * 100 + dorsal),
                        dorsal, dorsal == 1 ? "Portero" : "Mediocampista");
                j.setId(c.nuevoIdJugador());
                e.getJugadores().add(j);
            }
        }
    }

    /**
     * Cronograma automático: reparte los partidos pendientes en los días y horas pedidos, sin que un equipo juegue
     * dos veces el mismo día ni más partidos por día que horas por canchas; rechaza un inicio antes de hoy. Además,
     * un partido no puede quedar antes de la creación del campeonato ni jugado en el futuro.
     */
    private static void probarCronograma() {
        boolean correcto;
        try {
            java.nio.file.Path carpeta = java.nio.file.Files.createTempDirectory("alphasport-cronograma");
            CampeonatoService servicio = new CampeonatoService(new alphasport.persistence.ArchivoRepositorioCampeonato(carpeta),
                    new alphasport.persistence.ArchivoRepositorioHistorial(carpeta));
            ConfiguracionTorneo cfg = new ConfiguracionTorneo();
            cfg.setSorteoAleatorio(false);
            servicio.crear("Cronograma", TipoFormato.LIGA, cfg, false);
            for (int i = 1; i <= 6; i++) {
                servicio.equipos().agregarEquipo("C" + i);
            }
            completarPlantillas(servicio.getCampeonato());
            servicio.avanzar();
            java.time.LocalDate hoy = java.time.LocalDate.now();
            java.time.LocalDate inicio = hoy.plusDays(1);
            java.util.Set<java.time.DayOfWeek> dias = java.util.EnumSet.of(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY);
            boolean rechazaPasado = rechaza(() -> servicio.partidos().programar(new Cronograma.Pedido(hoy.minusDays(1), dias, List.of("15:00"), List.of(), false)))
                    && rechaza(() -> servicio.partidos().programar(new Cronograma.Pedido(inicio, java.util.Set.of(), List.of("15:00"), List.of(), false)))
                    && rechaza(() -> servicio.partidos().programar(new Cronograma.Pedido(inicio, dias, List.of("25:00"), List.of(), false)));
            String mensaje = servicio.partidos().programar(new Cronograma.Pedido(inicio, dias, List.of("17:00", "15:00"), List.of("Cancha 1"), false));
            List<Partido> ps = servicio.getCampeonato().getPartidos();
            java.util.Map<String, List<Partido>> porDia = new java.util.HashMap<>();
            ps.forEach(x -> porDia.computeIfAbsent(x.getFecha(), k -> new ArrayList<>()).add(x));
            boolean todos = ps.stream().allMatch(x -> x.getFecha().length() == 10 && java.time.LocalDate.parse(x.getFecha()) != null && "Cancha 1".equals(x.getSede()));
            boolean diasBien = porDia.keySet().stream().map(java.time.LocalDate::parse)
                    .allMatch(d -> !d.isBefore(inicio) && dias.contains(d.getDayOfWeek()));
            boolean cupo = porDia.values().stream().allMatch(l -> l.size() <= 2
                    && l.stream().map(Partido::getHora).distinct().count() == l.size());
            boolean sinRepetir = porDia.values().stream().allMatch(l -> l.stream().flatMap(x -> java.util.stream.Stream.of(x.getIdLocal(), x.getIdVisitante()))
                    .distinct().count() == l.size() * 2L);
            // Fechas a mano: ni antes de la creación ni jugado en el futuro.
            Partido p = ps.get(0);
            Partido antes = new Partido();
            antes.copiarDatosDe(p);
            antes.setFecha(hoy.minusDays(3).toString());
            Partido futuro = new Partido();
            futuro.copiarDatosDe(p);
            futuro.setEstado(alphasport.model.EstadoPartido.JUGADO);
            futuro.setFecha(hoy.plusDays(3).toString());
            boolean fechas = rechaza(() -> servicio.partidos().guardarPartido(p.getId(), antes, true))
                    && rechaza(() -> servicio.partidos().guardarPartido(p.getId(), futuro, true));
            correcto = rechazaPasado && todos && diasBien && cupo && sinRepetir && fechas && mensaje.startsWith("Se programaron " + ps.size());
            if (!correcto) {
                System.out.println("  rechazaPasado=" + rechazaPasado + " todos=" + todos + " dias=" + diasBien + " cupo=" + cupo
                        + " sinRepetir=" + sinRepetir + " fechas=" + fechas + " " + mensaje);
            }
        } catch (Exception e) {
            correcto = false;
            System.out.println("  ERROR: " + e);
        }
        System.out.println("Cronograma automático y fechas válidas: " + (correcto ? "correcto" : "MAL"));
        if (!correcto) {
            fallos++;
        }
    }

    private static void probarWalkoverYCampeones() {
        boolean correcto;
        try {
            java.nio.file.Path carpeta = java.nio.file.Files.createTempDirectory("alphasport-wo");
            CampeonatoService servicio = new CampeonatoService(
                    new alphasport.persistence.ArchivoRepositorioCampeonato(carpeta),
                    new alphasport.persistence.ArchivoRepositorioHistorial(carpeta));
            ConfiguracionTorneo cfg = new ConfiguracionTorneo();
            cfg.setSorteoAleatorio(false);
            cfg.setDivisiones(3);
            servicio.crear("W.O. y campeones", TipoFormato.LIGA, cfg, false);
            for (int i = 1; i <= 9; i++) {
                servicio.equipos().agregarEquipo("W" + i);
            }
            completarPlantillas(servicio.getCampeonato()); // para iniciar, cada equipo necesita su XI
            servicio.avanzar();
            Campeonato c = servicio.getCampeonato();
            Partido p1 = c.getPartidos().get(0), p2 = c.getPartidos().get(1);
            Partido copia1 = new Partido();
            copia1.copiarDatosDe(p1);                               // igual que el editor
            servicio.partidos().darWalkover(p1.getId(), copia1, p1.getIdLocal(), false);
            Partido copia2 = new Partido();
            copia2.copiarDatosDe(p2);
            servicio.partidos().darWalkover(p2.getId(), copia2, p2.getIdVisitante(), false);
            boolean ganaLocal = p1.getGolesLocal() == 3 && p1.getGolesVisitante() == 0 && p1.getGanador() == p1.getIdLocal();
            boolean ganaVisitante = p2.getGolesVisitante() == 3 && p2.getGolesLocal() == 0 && p2.getGanador() == p2.getIdVisitante();
            boolean puntos = servicio.getMotor().tablaDeGrupo(0, p1.getGrupo().charAt(0) - 'A').stream()
                    .anyMatch(f -> f.getIdEquipo() == p1.getIdLocal() && f.getPuntos() == 3);
            for (Partido p : c.getPartidos()) {                    // se juegan los partidos que faltan
                if (!p.isJugado()) {
                    Partido datos = new Partido();
                    datos.copiarDatosDe(p);
                    datos.setEstado(EstadoPartido.JUGADO);
                    datos.setGolesLocal(azar.nextInt(4));
                    datos.setGolesVisitante(azar.nextInt(4));
                    servicio.getMotor().aplicarEdicion(p, datos, false);
                }
            }
            String resultado = servicio.avanzar().contains("Campeones:") ? "OK" : "sin campeones por division";
            List<Integer> campeones = servicio.getMotor().campeonesPorDivision();
            boolean unoPorDivision = campeones.size() == 3 && campeones.get(0) == c.getIdCampeon()
                    && c.equiposDeDivision(1).stream().anyMatch(e -> e.getId() == campeones.get(1))
                    && c.equiposDeDivision(2).stream().anyMatch(e -> e.getId() == campeones.get(2))
                    && servicio.getMotor().describirEstado().contains("Liga C:");
            correcto = ganaLocal && ganaVisitante && puntos && resultado.startsWith("OK") && unoPorDivision;
            if (!correcto) {
                System.out.println("  ganaLocal=" + ganaLocal + " ganaVisitante=" + ganaVisitante + " puntos=" + puntos
                        + " resultado=" + resultado + " campeones=" + campeones + " " + servicio.getMotor().describirEstado());
            }
        } catch (Exception e) {
            correcto = false;
            System.out.println("  ERROR: " + e);
        }
        System.out.println("W.O. (local y visitante) y campeon por division: " + (correcto ? "correcto" : "MAL"));
        if (!correcto) {
            fallos++;
        }
    }

    /**
     * Noticias: el visitante propone (queda pendiente) y no puede aprobar; el administrador aprueba;
     * los comentarios pasan por el filtro (sin censurar palabras inocentes) y no se aceptan archivos
     * que no son imagenes. Todo a traves de ApiControlador, con los permisos reales.
     */
    @SuppressWarnings("unchecked")
    private static void probarNoticias() {
        boolean correcto;
        try {
            CampeonatoService servicio = new CampeonatoService(
                    new alphasport.persistence.ArchivoRepositorioCampeonato(java.nio.file.Files.createTempDirectory("alphasport-noticias")),
                    new alphasport.persistence.ArchivoRepositorioHistorial(java.nio.file.Files.createTempDirectory("alphasport-noticias")));
            NoticiasService noticias = new NoticiasService(new alphasport.persistence.RepositorioNoticiasEnMemoria());
            alphasport.ui.ApiControlador api = new alphasport.ui.ApiControlador(servicio, new AutenticacionService(), noticias);
            String png = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(
                    new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0});
            api.ejecutar("login", java.util.Map.of("usuario", "visitante", "clave", "321")); // cuenta de prueba (aficionado)
            java.util.Map<String, Object> propuesta = api.ejecutar("noticia/proponer", java.util.Map.of(
                    "titulo", "Golazo en la final", "cuerpo", "Un visitante cuenta el gol del partido.", "autor", "Ana", "imagen", png));
            java.util.Map<String, Object> vistaVisitante = (java.util.Map<String, Object>) propuesta.get("noticias");
            boolean pendiente = Boolean.TRUE.equals(propuesta.get("ok")) && ((List<?>) vistaVisitante.get("publicadas")).isEmpty()
                    && ((List<?>) vistaVisitante.get("pendientes")).isEmpty() && noticias.pendientes().size() == 1
                    && noticias.pendientes().get(0).tieneImagen();
            int id = noticias.pendientes().get(0).getId();
            boolean visitanteNoAprueba;
            try {
                api.ejecutar("noticia/aprobar", java.util.Map.of("id", id));
                visitanteNoAprueba = false;
            } catch (ReglaNegocioException e) {
                visitanteNoAprueba = e.getCodigo().equals("PERMISO");
            }
            boolean imagenOculta = api.imagenNoticia(noticias.pendientes().get(0).getImagenRuta()) == null;
            api.ejecutar("login", java.util.Map.of("usuario", "admin", "clave", "123"));
            boolean adminVePendiente = ((List<?>) ((java.util.Map<String, Object>) api.consultar("noticias").get("noticias")).get("pendientes")).size() == 1;
            api.ejecutar("noticia/aprobar", java.util.Map.of("id", id));
            api.ejecutar("noticia/guardar", java.util.Map.of("titulo", "Comunicado oficial", "cuerpo", "La fecha 3 se juega el sábado."));
            boolean publicadas = noticias.publicadas().size() == 2 && noticias.pendientes().isEmpty()
                    && noticias.publicadas().stream().allMatch(alphasport.model.Noticia::isAprobado);
            api.ejecutar("login", java.util.Map.of("usuario", "visitante", "clave", "321")); // cuenta de prueba (aficionado)
            api.ejecutar("noticia/comentar", java.util.Map.of("id", id, "autor", "Pedro",
                    "texto", "Que IDIOTAAA el arbitro, un hp. La pera del juez y el calculo bien."));
            String guardado = noticias.buscar(id).getComentarios().get(0).getTexto();
            boolean filtrado = guardado.equals("Que *** el arbitro, un ***. La pera del juez y el calculo bien.")
                    && FiltroPalabras.sanear("1d10t4 y m@lparidos").equals("*** y ***");
            boolean rechazaFalsa;
            try {
                api.ejecutar("noticia/proponer", java.util.Map.of("titulo", "Otra noticia", "cuerpo", "Texto suficiente para pasar.",
                        "imagen", "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString("no soy foto".getBytes())));
                rechazaFalsa = false;
            } catch (ReglaNegocioException e) {
                rechazaFalsa = true;
            }
            correcto = pendiente && visitanteNoAprueba && imagenOculta && adminVePendiente && publicadas && filtrado && rechazaFalsa;
            if (!correcto) {
                System.out.println("  pendiente=" + pendiente + " visitanteNoAprueba=" + visitanteNoAprueba + " imagenOculta=" + imagenOculta
                        + " adminVePendiente=" + adminVePendiente + " publicadas=" + publicadas + " filtrado=" + filtrado + " («" + guardado + "»)"
                        + " rechazaFalsa=" + rechazaFalsa);
            }
        } catch (Exception e) {
            correcto = false;
            System.out.println("  ERROR: " + e);
        }
        System.out.println("Noticias, moderacion y filtro de comentarios: " + (correcto ? "correcto" : "MAL"));
        if (!correcto) {
            fallos++;
        }
    }
}