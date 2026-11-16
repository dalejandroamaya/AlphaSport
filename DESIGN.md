---
name: AlphaSport
description: Gestor de campeonatos de fútbol en vidrio esmerilado sobre la noche del estadio.
colors:
  cesped: "#2ee598"
  cesped-profundo: "#10b36c"
  oro: "#f7c75a"
  oro-profundo: "#d39a22"
  noche: "#05080f"
  vidrio: "rgba(14, 22, 38, .52)"
  vidrio-denso: "rgba(10, 16, 28, .84)"
  pantalla: "rgba(2, 5, 10, .62)"
  texto: "#eef4ff"
  texto-sutil: "#a3b2ca"
  rojo-alerta: "#ff6b81"
  ambar: "#ffb950"
  cian-foco: "#74dcff"
  cesped-dia: "#078a52"
  oro-texto-dia: "#865c00"
  texto-dia: "#0b1726"
  cielo-dia: "#eaf1f7"
  tinta-cesped: "#03170d"
  tinta-oro: "#2a1900"
  rojo-suave: "#ffa3b1"
  gris-coronacion: "#c3cfe2"
  casilla-goles: "rgba(0, 0, 0, .45)"
typography:
  display:
    fontFamily: "Bricolage Grotesque, Segoe UI, system-ui, sans-serif"
    fontSize: "clamp(2rem, 3.6vw, 3rem)"
    fontWeight: 800
    lineHeight: 1.08
    letterSpacing: "-0.015em"
  headline:
    fontFamily: "Bricolage Grotesque, Segoe UI, system-ui, sans-serif"
    fontSize: "1.9rem"
    fontWeight: 800
    lineHeight: 1.08
  title:
    fontFamily: "Bricolage Grotesque, Segoe UI, system-ui, sans-serif"
    fontSize: "1.15rem"
    fontWeight: 700
    lineHeight: 1.1
  marcador:
    fontFamily: "Bricolage Grotesque, Segoe UI, system-ui, sans-serif"
    fontSize: "1.85rem"
    fontWeight: 800
    fontFeature: "tnum"
  body:
    fontFamily: "Onest, Segoe UI, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "Onest, Segoe UI, system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 500
rounded:
  campo: "12px"
  marcador: "14px"
  goles: "16px"
  grupo: "18px"
  panel: "22px"
  dialogo: "28px"
  dialogo-movil: "24px"
  pildora: "999px"
spacing:
  xs: "8px"
  sm: "12px"
  md: "16px"
  lg: "20px"
  xl: "28px"
components:
  button-primary:
    backgroundColor: "{colors.cesped}"
    textColor: "{colors.tinta-cesped}"
    rounded: "{rounded.pildora}"
    padding: "10px 18px"
    height: "42px"
  button-logro:
    backgroundColor: "{colors.oro}"
    textColor: "{colors.tinta-oro}"
    rounded: "{rounded.pildora}"
    padding: "10px 18px"
  button-secondary:
    backgroundColor: "rgba(255, 255, 255, .07)"
    textColor: "{colors.texto}"
    rounded: "{rounded.pildora}"
    padding: "10px 18px"
  input-field:
    backgroundColor: "rgba(4, 8, 16, .5)"
    textColor: "{colors.texto}"
    rounded: "{rounded.campo}"
    height: "44px"
  panel-vidrio:
    backgroundColor: "{colors.vidrio}"
    textColor: "{colors.texto}"
    rounded: "{rounded.panel}"
  marcador-pantalla:
    backgroundColor: "{colors.pantalla}"
    textColor: "#ffffff"
    typography: "{typography.marcador}"
    rounded: "{rounded.marcador}"
  chip-estado:
    backgroundColor: "rgba(46, 229, 152, .14)"
    textColor: "{colors.cesped}"
    rounded: "{rounded.pildora}"
---

# Design System: AlphaSport

## Overview

**Creative North Star: "Estadio bajo reflectores"**

AlphaSport vive en vidrio esmerilado suspendido sobre una fotografía real de estadio. De noche, la cancha brilla bajo los focos y los paneles dejan ver la grada desenfocada. En la tarde de partido (modo claro) el mismo sistema pasa a cielo claro y vidrio lechoso. La interfaz es una herramienta de trabajo: barra lateral, cabecera del torneo y una vista activa por vez. El mundo aporta la tipografía, el color, la densidad y un movimiento propio, nunca la navegación ni los controles.

La tabla de posiciones y los marcadores son la «pantalla gigante» del estadio: cifras grandes y redondas sobre tableros oscuros. Los logros se celebran en dorado bajo un foco; todo lo demás queda en blanco frío y verde césped. La densidad es media: filas generosas, paneles con aire y un único foco de atención por vista.

**Key Characteristics:**
- Fotografía real de estadio (noche y día) detrás de paneles de vidrio con desenfoque de 22 px.
- Haces de reflector que se encienden al entrar y un destello que recorre el borde de cada panel.
- Cifras y títulos en Bricolage Grotesque; interfaz en Onest.
- Verde para acción y juego, dorado solo para logros.
- Modo oscuro y claro con el mismo acabado.

## Colors

Una noche azul profunda con dos luces de estadio: verde césped para lo que está en juego y dorado para lo que se gana.

### Primary
- **Verde césped iluminado** (cesped): botón principal, estado «en juego», ganador de un partido, sección activa de la navegación y bordes de foco. En modo claro baja a **verde cancha de día** (cesped-dia) para mantener el contraste.

### Secondary
- **Dorado de trofeo** (oro): el líder de la tabla, el ganador de la final, la cabecera de un torneo terminado, el botón «Ver campeón» y la coronación. En modo claro el texto dorado usa **oro de medalla** (oro-texto-dia) para pasar AA.

### Neutral
- **Noche de estadio** (noche): fondo base y color de texto sobre botones claros.
- **Vidrio nocturno** (vidrio) y **vidrio denso** (vidrio-denso): paneles y, el denso, menús, avisos y barras fijas.
- **Pantalla gigante** (pantalla): tableros de marcador, llaves y casillas de goles.
- **Blanco de reflector** (texto) y **gris grada** (texto-sutil): texto principal y secundario.
- **Cielo de tarde** (cielo-dia) y **tinta de día** (texto-dia): fondo y texto del modo claro.

### Estados
- **Rojo tarjeta** (rojo-alerta) para errores y acciones destructivas (el texto de los botones destructivos usa **rojo suave**, rojo-suave), **ámbar** para aplazado o repechaje y **cian de foco** para el anillo de foco del teclado y el estado pendiente.

### Named Rules
**The Gold Is Earned Rule.** El dorado aparece solo donde algo se ganó: líder, final, campeón o torneo terminado. Nunca como decoración ni en acciones comunes.

**The Two Lights Rule.** Modo oscuro y claro comparten estructura, roles de color y movimientos; solo cambian la foto, el vidrio y los tonos de texto.

## Typography

**Display Font:** Bricolage Grotesque (con Segoe UI)
**Body Font:** Onest (con Segoe UI)

**Character:** Bricolage es redonda, con carácter y un punto deportivo sin caer en lo futurista; Onest es una sans de trabajo, clara en tablas y formularios.

### Hierarchy
- **Display** (800, clamp(2rem, 3.6vw, 3rem), 1.08): nombre del torneo en la cabecera.
- **Headline** (800, 1.9rem): título de cada vista dentro de su panel.
- **Title** (700, 1.15rem): equipos en las tarjetas de partido, rondas y títulos de diálogo.
- **Marcador** (800, 1.85rem, cifras tabulares): resultados, puntos y casillas de goles (hasta 2.8rem en el modal).
- **Body** (400, 1rem, 1.5): texto general.
- **Label** (500, 0.875rem): etiquetas de campo, chips y encabezados de tabla.

### Named Rules
**The Scoreboard Numerals Rule.** Toda cifra que se compara (goles, puntos, posiciones) va en Bricolage Grotesque 800 con `tabular-nums`.

**The No Eyebrow Rule.** Ningún rótulo pequeño encima de un título: el estado va en un chip junto a los demás datos.

## Layout

Rejilla de dos columnas en escritorio: barra lateral flotante de 248 px y área principal, con 20 px de separación y de margen exterior. El área principal apila la cabecera del torneo y la vista activa; cada vista abre con su propio panel de vidrio (título, ayuda y filtros), seguido de su contenido. Las tarjetas de partido usan `repeat(auto-fill, minmax(340px, 1fr))`.

Por debajo de 1200 px las acciones de la cabecera bajan a su propia fila. Por debajo de 1000 px la barra lateral se convierte en una barra de navegación flotante de vidrio en la parte inferior. Por debajo de 640 px los filtros pasan a una columna, la tabla de posiciones muestra solo #, Equipo, PJ, DG y Pts (con botón «Ver todas las columnas») y la carga rápida avanza un partido por pantalla con scroll-snap.

## Elevation & Depth

La profundidad viene del vidrio: desenfoque de fondo, una línea de luz superior interna y una sombra ambiental desplazada hacia abajo. No hay halos de color.

### Shadow Vocabulary
- **Panel** (`inset 0 1px 0 rgba(255,255,255,.16), 0 18px 40px -12px rgba(0,0,0,.5)`): todo panel de vidrio.
- **Diálogo** (`inset 0 1px 0 rgba(255,255,255,.16), 0 30px 60px -20px rgba(0,0,0,.7)`): ventanas modales.
- **Botón lleno** (`inset 0 1px 0 rgba(255,255,255,.5), 0 10px 22px -10px rgba(0,0,0,.6)`): botón principal y de logro.

### Named Rules
**The Light From Above Rule.** Las sombras siempre caen hacia abajo con desplazamiento; nunca un halo centrado de color.

## Shapes

Esquinas suaves y generosas: 12 px en campos, 14 px en marcadores, 16 px en las casillas de goles, 18 px en grupos de formulario, 22 px en paneles y 28 px en diálogos (24 px en el teléfono). Botones, chips, etiquetas, pestañas y avisos son píldoras completas. Los escudos de equipo son el único corte poligonal, un escudo de seis lados.

## Components

### Buttons
- **Shape:** píldora (999px), mínimo 42 px de alto.
- **Primary:** degradado vertical de verde césped con luz superior; un haz de luz cruza el botón al pasar el puntero.
- **Logro:** el mismo tratamiento en dorado, solo para acciones de un torneo terminado.
- **Secondary:** vidrio translúcido con borde de luz.
- **Destructive:** texto rojo sin fondo hasta que se señala.
- **Focus:** contorno cian de 2 px con 3 px de separación.

### Chips
- **Style:** píldoras de vidrio con borde fino; el chip de estado lleva verde o dorado según la fase del torneo.

### Cards / Containers
- **Corner Style:** 22 px.
- **Background:** vidrio nocturno, o lechoso de día, con desenfoque de 22 px.
- **Border:** 1 px de luz blanca al 12 %.
- **Internal Padding:** de 1 a 1.5 rem.

### Inputs / Fields
- **Style:** fondo translúcido oscuro, borde fino y radio de 12 px.
- **Focus:** borde verde y contorno de foco, sin halo.
- **Error:** borde y contorno rojos.

### Navigation
- **Style:** lista de vidrio con iconos de trazo; la sección activa lleva fondo verde translúcido y borde verde. En móvil es una barra inferior flotante con etiquetas de 0.75rem.

### Marcador de pantalla gigante
Tablero oscuro con cifras en Bricolage 800. El modal del partido lo agranda a casillas de 82×70 px con fondo **casilla de goles** (casilla-goles) y radio de 16 px. Un partido sin jugar muestra «–» en lugar de 0.

### Coronación
Diálogo con la foto del estadio, tres haces dorados que barren hacia el trofeo, nombre del campeón en dorado a 3.6rem, seis cifras en tableros de vidrio con etiquetas en **gris de coronación** (gris-coronacion) y confeti. Mientras está abierto, los focos del fondo de la app se vuelven dorados.

## Do's and Don'ts

### Do:
- **Do** poner cada vista dentro de su panel de vidrio, con el título y los filtros adentro.
- **Do** reservar el dorado para el líder, la final, el campeón y el torneo terminado.
- **Do** usar Bricolage Grotesque 800 con cifras tabulares para todo marcador o puntaje.
- **Do** respetar «reducir movimiento»: los focos quedan encendidos sin animar y se oculta el confeti.
- **Do** acreditar las fotos de Unsplash (Josemar Duarte, noche; Miguel Ramírez, día) en el login; las fotos y las fuentes viven en `web/img` y `web/fuentes` para funcionar sin internet, también en la versión de escritorio (JavaFX).

### Don't:
- **Don't** usar halos de color centrados ni brillos neón alrededor de botones o textos.
- **Don't** poner un rótulo pequeño encima de un título (eyebrow o kicker).
- **Don't** dejar texto directamente sobre la foto sin panel de vidrio.
- **Don't** usar emojis como iconos: tarjetas, trofeo, balón y tema son SVG dibujados.
- **Don't** tocar el logo de AlphaSport.
