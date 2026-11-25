---
name: AlphaSport
description: Gestor de campeonatos de fútbol con el aspecto de un marcador en vivo, plano y compacto.
colors:
  cesped: "#2ee598"
  cesped-profundo: "#10b36c"
  oro: "#f7c75a"
  oro-profundo: "#d39a22"
  noche: "#050a12"
  superficie: "#0b1522"
  superficie-2: "#12233a"
  linea: "rgba(150, 185, 230, .1)"
  pantalla: "#050c16"
  texto: "#eef4ff"
  texto-sutil: "#a3b2ca"
  rojo-alerta: "#ff6b81"
  ambar: "#ffb950"
  cian-foco: "#74dcff"
  cesped-dia: "#057545"
  oro-texto-dia: "#865c00"
  texto-dia: "#0b1726"
  fondo-dia: "#e7ecf2"
  superficie-dia: "#ffffff"
  superficie-2-dia: "#dfe7f1"
  tinta-cesped: "#03170d"
  sobre-cesped-dia: "#ffffff"
  tinta-oro: "#2a1900"
  rojo-suave: "#ffa3b1"
  gris-coronacion: "#c3cfe2"
typography:
  display:
    fontFamily: "Inter, Segoe UI, Arial, sans-serif"
    fontSize: "clamp(1.5rem, 2.4vw, 2rem)"
    fontWeight: 700
    lineHeight: 1.1
  headline:
    fontFamily: "Inter, Segoe UI, Arial, sans-serif"
    fontSize: "1.35rem"
    fontWeight: 700
  seccion:
    fontFamily: "Inter, Segoe UI, Arial, sans-serif"
    fontSize: "0.78rem"
    fontWeight: 700
    textTransform: "uppercase"
    letterSpacing: "0.04em"
  body:
    fontFamily: "Inter, Segoe UI, Arial, sans-serif"
    fontSize: "0.9rem"
    fontWeight: 400
    lineHeight: 1.4
  marcador:
    fontFamily: "Inter, Segoe UI, Arial, sans-serif"
    fontSize: "0.9rem"
    fontWeight: 700
    fontFeature: "tnum"
rounded:
  barra: "4px"
  campo: "6px"
  panel: "8px"
  login: "12px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
components:
  button-primary:
    backgroundColor: "{colors.cesped}"
    textColor: "{colors.tinta-cesped}"
    rounded: "{rounded.panel}"
    height: "34px"
  button-secondary:
    backgroundColor: "rgba(255, 255, 255, .07)"
    textColor: "{colors.texto}"
    rounded: "{rounded.panel}"
    height: "34px"
  pestana:
    backgroundColor: "rgba(255, 255, 255, .07)"
    textColor: "{colors.texto}"
    typography: "{typography.seccion}"
    rounded: "{rounded.panel}"
    height: "28px"
  panel:
    backgroundColor: "{colors.superficie}"
    rounded: "{rounded.panel}"
  barra-seccion:
    backgroundColor: "{colors.superficie-2}"
    typography: "{typography.seccion}"
    rounded: "{rounded.barra}"
    height: "32px"
  fila-partido:
    borderBottom: "1px solid {colors.linea}"
  chip-estado:
    backgroundColor: "rgba(46, 229, 152, .14)"
    textColor: "{colors.cesped}"
    rounded: "{rounded.barra}"
---

# Design System: AlphaSport

## Overview

**Creative North Star: "Marcador en vivo"**

AlphaSport se ve como una página de resultados en vivo: superficies planas y sólidas, letra pequeña y nítida, mucha información por pantalla y ningún adorno que distraiga del resultado. La interfaz sigue siendo una herramienta de trabajo: barra lateral, cabecera del torneo y una vista activa por vez.

Cada ronda es un panel con una barra de encabezado en mayúsculas, y cada partido es una fila de dos líneas: fecha y estado a la izquierda, los dos equipos uno sobre otro con su escudo pequeño, y el marcador apilado a la derecha. Las tablas tienen el encabezado en barra y filas separadas por líneas de 1 px.

**Key Characteristics:**
- Fondo liso y paneles sólidos con esquinas de 8 px, sin vidrio, sin sombras y sin destellos.
- Inter en toda la interfaz: base de 13–14 px, negrita para ganadores y marcadores.
- Etiquetas, pestañas y encabezados en mayúsculas pequeñas con un poco de espaciado.
- Verde para acción y juego, dorado solo para logros.
- La foto del estadio queda solo en la pantalla de entrada.
- Modo oscuro y claro con el mismo acabado.

## Colors

Una noche azul profunda con dos luces de estadio: verde césped para lo que está en juego y dorado para lo que se gana.

### Primary
- **Verde césped** (cesped): botón principal, pestaña activa, estado «jugado», sección activa de la navegación y bordes de foco. En modo claro baja a **verde cancha de día** (cesped-dia, 5,8:1 con blanco). El texto sobre el verde usa el token **sobre-cesped**: tinta oscura de noche (tinta-cesped) y blanco de día (sobre-cesped-dia).

### Secondary
- **Dorado de trofeo** (oro): el líder de la tabla, el ganador de la final, la cabecera de un torneo terminado y la coronación. En modo claro el texto dorado usa **oro de medalla** (oro-texto-dia).

### Neutral
- **Noche** (noche): fondo de la aplicación.
- **Superficie** (superficie): paneles, diálogos y menús. **Superficie 2** (superficie-2): barras de encabezado de sección y de tabla.
- **Línea** (linea): divisores de 1 px entre filas.
- **Pantalla** (pantalla): marcador grande del detalle del partido y llaves.
- **Texto** (texto) y **texto sutil** (texto-sutil).
- De día: **fondo** (fondo-dia), **superficie** blanca (superficie-dia), **superficie 2** (superficie-2-dia) y **tinta** (texto-dia).

### Estados
- **Rojo tarjeta** (rojo-alerta) para errores, acciones destructivas y el marcador de un partido en juego; **ámbar** para aplazado o repechaje; **cian** para el anillo de foco del teclado y el estado pendiente.

### Named Rules
**The Gold Is Earned Rule.** El dorado aparece solo donde algo se ganó: líder, final, campeón o torneo terminado.

**The Two Lights Rule.** Modo oscuro y claro comparten estructura y roles de color; solo cambian las superficies y los tonos de texto.

## Typography

**Font:** Inter (regular, semibold y bold), con Segoe UI y Arial de respaldo. Se guarda en `web/fuentes`: la web usa `Inter-latin.woff2` (variable, 48 KB, todo el español) y el escritorio los TTF de cada peso (JavaFX no lee WOFF2).

### Hierarchy
- **Display** (700, clamp(1.5rem, 2.4vw, 2rem)): nombre del torneo en la cabecera.
- **Headline** (700, 1.35rem): título de cada vista.
- **Sección** (700, 0.78rem, mayúsculas, +0.04em): barras de ronda, encabezados de tabla, pestañas, etiquetas de campo y chips.
- **Body** (400, 0.9rem, 1.4): texto general y nombres de equipo.
- **Marcador** (700, 0.9rem, cifras tabulares): goles en las filas y puntos en la tabla.

### Named Rules
**The Winner Is Bold Rule.** En una fila de partido el ganador va en negrita; el perdedor queda en regular. No se usan colores para marcar al ganador en las filas.

## Layout

Dos columnas en pantallas anchas: barra lateral de 248 px y área principal, con 12 px de separación y de margen. El área principal apila la cabecera del torneo y la vista activa; cada vista abre con su panel (título, ayuda y filtros) y debajo su contenido. Los partidos van en una sola columna de filas dentro del panel de su ronda.

Por debajo de 1000 px la barra lateral pasa a una barra de navegación inferior. Por debajo de 640 px esa barra muestra las 4 secciones que más usa cada rol (Organizador: Partidos, Posiciones, Equipos, Torneos; Aficionado: Partidos, Votaciones, Posiciones, Noticias) y «Más», que abre el resto en filas encima de la barra; las etiquetas nunca bajan de 11 px. La columna de fecha de cada fila se angosta y la tabla de posiciones muestra solo las columnas principales.

## Elevation & Depth

Plano. Los paneles se separan del fondo por el color de su superficie, no por sombras. Solo los menús emergentes y los diálogos llevan una sombra suave para despegarse de lo que tapan.

## Shapes

4 px en barras de encabezado y chips, 6 px en campos, 8 px en paneles, botones, pestañas y diálogos, 12 px en la tarjeta de entrada. Los escudos de equipo son el único corte poligonal, un escudo de seis lados; en las filas se ven a 18×20 px.

## Components

### Buttons
- **Shape:** rectángulo con esquinas de 8 px, 34 px de alto (28 px en los mini).
- **Primary:** verde césped sólido con texto oscuro (blanco de día).
- **Logro:** dorado sólido, solo para acciones de un torneo terminado.
- **Secondary:** relleno blanco al 7 % (gris claro de día).
- **Destructive:** texto rojo sin fondo hasta que se señala.

### Pestañas
Pastillas de 28 px, mayúsculas pequeñas en negrita; la activa en verde césped sólido.

### Chips y estados
Esquinas de 4 px, mayúsculas pequeñas en negrita, fondo tenue del color del estado.

### Paneles de ronda y filas de partido
Panel de superficie con barra de encabezado (superficie 2, 32 px). Filas de dos líneas separadas por una línea de 1 px; al pasar el puntero la fila se aclara apenas.

### Tablas
Encabezado en barra de superficie 2 con mayúsculas pequeñas; filas separadas por líneas de 1 px, sin rayado alternado. El líder lleva un fondo dorado muy tenue.

### Fotos y escudos
Fotos de jugador tipo cédula (3:4) o la persona «NN»; el escudo subido reemplaza al escudo de iniciales en todos los tamaños.

### Coronación
Diálogo con la foto del estadio, haces dorados hacia el trofeo, nombre del campeón en dorado, seis cifras y confeti.

## Do's and Don'ts

### Do:
- **Do** poner cada ronda en su panel con la barra de encabezado y los partidos como filas.
- **Do** reservar el dorado para el líder, la final, el campeón y el torneo terminado.
- **Do** usar Inter en negrita con cifras tabulares para goles y puntos.
- **Do** respetar «reducir movimiento».
- **Do** acreditar las fotos de Unsplash (Josemar Duarte, noche; Miguel Ramírez, día) en la pantalla de entrada; fotos, fuentes y logos viven en `web/img` y `web/fuentes` para funcionar sin internet, también en el escritorio (JavaFX).

### Don't:
- **Don't** volver al vidrio, los degradados o los brillos en paneles y botones.
- **Don't** usar esquinas de píldora en botones o paneles.
- **Don't** usar emojis como iconos: tarjetas, trofeo, balón y tema son SVG dibujados.
- **Don't** tocar el logo de AlphaSport (`web/img/logo.png` sin texto y `web/img/logo-texto.png` con texto).
