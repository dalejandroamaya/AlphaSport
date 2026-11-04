# AlphaSport · V4

Gestor de campeonatos de fútbol con seis formatos de competencia (liga, eliminación directa, grupos + eliminatorias, torneo corto con liguilla o cuadrangulares, sistema suizo y doble eliminación), desempates configurables, plantillas, tarjetas y suspensiones, fixture automático, historial, premios y palmarés.

Esta versión reemplaza las ventanas Swing (JFrame) por una **interfaz web** (HTML, CSS y JavaScript) y organiza el código en cuatro capas: **ui → service → persistence → model**.

## Arquitectura

| Capa | Java (`src/alphasport/...`) | Web (`web/js/...`) |
|---|---|---|
| **ui** | `ui`: servidor local (`ServidorWeb`), API JSON (`ApiControlador`), traducción de datos para la pantalla (`Presentador`) y arranque (`AlphaSportApp`) | `ui/app.js` + `index.html` + `css/estilos.css` |
| **service** | `service`: casos de uso (`CampeonatoService`), reglas del torneo (`MotorCampeonato`, `TablaPosiciones`, `Sanciones`, ...) y los 6 formatos (`service.formatos`) | `service/motor.js` (reglas), `service/servicioLocal.js` y `service/servicioRemoto.js` |
| **persistence** | `persistence`: interfaces `RepositorioCampeonato` y `RepositorioHistorial` con implementación en archivos | `persistence/almacen.js` (almacenamiento del navegador) |
| **model** | `model`: `Campeonato`, `Equipo`, `Persona`, `Jugador`, `Partido`, `Tarjeta`, `Fase`, ... | `model/modelo.js` |

Cada capa solo usa a la de abajo. La interfaz es **una sola** para los dos modos:

- **Programa Java:** `AlphaSportApp` inicia un servidor en `http://127.0.0.1:8080` (solo accesible desde el propio equipo) y abre el navegador. La interfaz detecta la API de Java y le envía todas las acciones; Java aplica las reglas y guarda en `datos/`.
- **Web independiente:** si se abre `web/index.html` sin Java, la interfaz usa el servicio en JavaScript y guarda en el navegador.

Las dos respuestas tienen el mismo formato JSON, así que la interfaz no necesita saber cuál está usando. Para pasar a base de datos (Fase 3) solo hay que crear otra implementación de los repositorios de `persistence`.

## Uso en Visual Studio Code

1. Instalar un **JDK 17 o superior** y la extensión **Extension Pack for Java**.
2. **File → Open Folder…** → abrir la carpeta `PrototipoA_P4`.
3. **Run and Debug** (Ctrl+Shift+D) → elegir **AlphaSport** → **F5**. Se abre el navegador; usuario `admin`, contraseña `123`.
4. Para cerrar: menú **Archivo → Cerrar programa** o Ctrl+C en la terminal.

Pruebas:
- **Prueba automatica (SimulacionFormatos)** en *Run and Debug*: debe terminar en `TODO BIEN`.
- `web/pruebas/simulacion.html` en el navegador: también debe decir `TODO BIEN`.

Versión web sola: abrir `web/index.html` con doble clic, o con **Live Server** (clic derecho → *Open with Live Server*).

## Carpetas

```
src/alphasport/
  model/          entidades del dominio
  persistence/    repositorios (archivos .dat)
  service/        casos de uso y reglas del torneo
    formatos/     un formato de campeonato por clase
  ui/             servidor web local y API JSON
test/alphasport/service/SimulacionFormatos.java
web/
  index.html, css/estilos.css, img/
  js/model, js/persistence, js/service, js/ui
  pruebas/        prueba automática del motor web
datos/            (se crea al usar el programa Java; no se sube a Git)
```

Autores: Diego Alejandro Amaya Verdecia y Ednilson Contreras.
