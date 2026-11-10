# Product


## Platform

web

## Users

- Organizadores de torneos de fútbol (barriales, universitarios, de empresa) que arman y llevan un campeonato desde el computador: registran equipos y plantillas, cargan resultados y tarjetas, y consultan tablas y llaves. También lo usan desde el teléfono al borde de la cancha para cargar marcadores.
- Evaluadores de un proyecto académico (profesor y compañeros) que revisan las funciones en una demostración. Hoy se entrega como proyecto de clase; la meta del dueño es seguir mejorándolo hasta que sea vendible, como proyecto final de grado o negocio propio.

## Product Purpose

AlphaSport organiza un campeonato completo de principio a fin: genera el fixture, registra resultados y sanciones, calcula posiciones con desempates configurables, arma las llaves y guarda el historial con premios y palmarés. El éxito es que un organizador lleve todo el torneo sin hojas de cálculo ni errores de tabla, y que la herramienta se vea y se sienta como un producto profesional.

## Positioning

Seis formatos reales explicados en lenguaje de cancha (Liga, Copa, Tipo Mundial, Apertura y Clausura con liguilla, Tipo Champions suizo y Doble oportunidad), con reglas de verdad: desempates en orden configurable, W.O., doble amarilla, suspensiones por fechas, final única o ida y vuelta.

## Operating Context

- Tres formas de uso con las mismas funciones: escritorio (JavaFX), web servida por Java (127.0.0.1:8080) y web sola en el navegador (guarda en el navegador).
- Flujo típico: crear campeonato y elegir formato → registrar equipos (DT y plantilla) → iniciar (fixture automático) → cargar resultados y tarjetas por jornada → cerrar ronda y avanzar → campeón, historial y palmarés.
- Respaldo y restauración de archivos, exportación del historial en texto.

## Capabilities and Constraints

- Arquitectura en capas ui → service → persistence → model; el motor web (`web/js/service/motor.js`) replica las reglas del servicio Java.
- La web es HTML, CSS y JavaScript sin framework ni paso de compilación; debe abrirse con doble clic en `web/index.html`.
- Pruebas automáticas: `web/pruebas/simulacion.html` y `SimulacionFormatos` (Java) deben terminar en «TODO BIEN».
- Usuario de prueba `admin` / `123` (documentado en el README, no en la interfaz).
- La versión de escritorio JavaFX adoptará después el mismo diseño que la web.

## Brand Commitments

- Nombre AlphaSport y su logo (`web/img/logo.png`, `web/img/logo-texto.png`) se conservan tal cual.
- La esencia es el ambiente de estadio de fútbol.
- Todo debe ser funcional y utilizable en modo oscuro y en modo claro, con el mismo nivel de acabado en ambos.
- Pedido explícito del dueño para el rediseño: ventanas suaves y modernas, mejores fuentes, mejores imágenes de fondo, transparencias y animaciones, sin que se note un frontend genérico.

## Evidence on Hand

- Logos propios en `web/img/`. No hay fotografías propias, clientes, testimonios ni métricas de uso: no se deben inventar.
- Documentación de formatos y reglas en `docs/FORMATOS_Y_REGLAS.md`.

## Product Principles

1. La tabla nunca miente: ningún flujo debe registrar un resultado que no ocurrió.
2. Hablar como en la cancha: términos del fútbol real, explicados sin jerga técnica.
3. Lo frecuente es rápido: cargar una jornada debe tomar segundos, también en el teléfono.
4. Profesional en las dos luces: oscuro y claro igual de cuidados.
5. Celebrar lo que importa: el campeón y los logros tienen su momento.

## Accessibility & Inclusion

- Uso completo con teclado y lector de pantalla en menús, diálogos y formularios.
- Contraste WCAG AA en ambos temas y respeto de «reducir movimiento».
