# AlphaSport · V4

Gestor de campeonatos de fútbol: **Liga**, **Copa (eliminación directa)**, **Tipo Mundial (grupos y eliminatorias)**, **Apertura y Clausura (con liguilla)**, **Tipo Champions (sistema suizo)** y **Doble oportunidad (doble eliminación)**. Incluye desempates configurables, equipos con DT y plantilla, fixture automático, tarjetas y suspensiones por jugador, historial, premios y palmarés.

El código está organizado en cuatro capas: **ui → service → persistence → model** (ver [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md)).

## Tres formas de usarlo

| Versión | Cómo se abre | Guarda en |
|---|---|---|
| **Escritorio (JavaFX)** | Ventana propia de Java, sin navegador | `datos/` |
| **Web con Java** | Java abre la interfaz en el navegador (`127.0.0.1:8080`) | `datos/` |
| **Web sola** | Abrir `web/index.html` | el navegador |

Las tres tienen las mismas funciones, el mismo diseño (estadio nocturno con detalles neón y dorados) y los logos de AlphaSport.

## Novedades del V4

- Sin Swing ni JFrame: la versión de escritorio usa **JavaFX** con hojas de estilo CSS.
- **Modo claro y oscuro** con un botón en el panel lateral (y en el login).
- Menú **Archivo → Cambiar formato del campeonato…** para corregir un formato elegido por error, conservando los equipos y sus plantillas.
- Menú **Archivo → Cancelar campeonato…** para descartar un campeonato sin guardarlo en el historial.
- Nombres y descripciones de los formatos en lenguaje sencillo.

## Uso en Visual Studio Code

1. Instalar un **JDK 21 o superior** y la extensión **Extension Pack for Java**.
2. **File → Open Folder…** → abrir la carpeta `PrototipoA_P4`.
3. **Run and Debug** (Ctrl+Shift+D) y elegir:
   - **AlphaSport Escritorio (JavaFX)**: abre la ventana del programa.
   - **AlphaSport Web (navegador)**: abre la interfaz en el navegador. Para cerrar: menú **Archivo → Cerrar programa** o Ctrl+C en la terminal.
   - **Prueba automatica (SimulacionFormatos)**: debe terminar en `TODO BIEN`.
4. Usuario `admin`, contraseña `123`.

JavaFX 21 ya viene en la carpeta `lib/` (Windows) y VS Code lo usa automáticamente.

La versión web sola se abre con doble clic en `web/index.html`, o con **Live Server**. Su prueba automática está en `web/pruebas/simulacion.html` (debe decir `TODO BIEN`).

## Carpetas

```
src/alphasport/
  model/          entidades del dominio
  persistence/    repositorios (interfaces + archivos .dat)
  service/        casos de uso y reglas del torneo
    formatos/     un formato de campeonato por clase
  ui/             servidor web local, API, presentador y arranque
    escritorio/   ventana JavaFX y su hoja de estilos
test/alphasport/service/SimulacionFormatos.java
lib/              JavaFX 21 (base, graphics, controls)
web/
  index.html, css/estilos.css, img/ (logos)
  js/model, js/persistence, js/service, js/ui
  pruebas/        prueba automática del motor web
docs/             arquitectura, formatos y reglas
datos/            se crea al usar el programa (no se sube a Git)
```

Autores: Diego Alejandro Amaya Verdecia y Ednilson Contreras.
