# AlphaSport · V4

Gestor de campeonatos de fútbol: **Liga**, **Copa (eliminación directa)**, **Tipo Mundial (grupos y eliminatorias)**, **Apertura y Clausura (con liguilla)**, **Tipo Champions (sistema suizo)** y **Doble oportunidad (doble eliminación)**. Incluye desempates configurables, equipos con DT y plantilla, fixture automático, tarjetas y suspensiones por jugador, historial, premios y palmarés.

El código está organizado en cuatro capas: **ui → service → persistence → model** (ver [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md)).

## Tres formas de usarlo

| Versión | Cómo se abre | Guarda en |
|---|---|---|
| **Escritorio (JavaFX)** | Ventana propia de Java, sin navegador | PostgreSQL |
| **Web con Java** | Java abre la interfaz en el navegador (`127.0.0.1:8080`) | PostgreSQL |
| **Web sola** | Abrir `web/index.html` | el navegador |

El escritorio y la web con Java necesitan la base de datos: siga los pasos de [README_DB.md](README_DB.md).

Las tres tienen las mismas funciones, el mismo diseño («Estadio bajo reflectores»: vidrio sobre fotos de estadio, focos animados y dorado para los logros, en modo oscuro y claro) y los logos de AlphaSport. El sistema visual está documentado en [DESIGN.md](DESIGN.md).

## Novedades del V4

- Sin Swing ni JFrame: la versión de escritorio usa **JavaFX** con hojas de estilo CSS.
- **Modo claro y oscuro** con un botón en el panel lateral (y en el login).
- Menú **Archivo → Cambiar formato del campeonato…** para corregir un formato elegido por error, conservando los equipos y sus plantillas.
- Menú **Archivo → Cancelar campeonato…** para descartar un campeonato sin guardarlo en el historial.
- Nombres y descripciones de los formatos en lenguaje sencillo.
- **Posiciones específicas** al armar la plantilla, agrupadas por línea: Portero; Defensa central, Líbero, Laterales y Carrileros; Mediocampista defensivo, central y ofensivo, Interiores y Volantes; Mediapunta, Extremos, Segundo delantero y Delantero centro.
- **Cambios durante el partido**: en el partido se anota el minuto, quién sale y quién entra (por ejemplo, minuto 66: sale el 11 y entra el 7). El minuto se conserva para anotar seguido varios cambios hechos a la vez. Un jugador sustituido no puede volver a entrar.
- **Roles**: `admin` / `123` es el administrador (crea, edita y elimina). `visitante` / `321`, o el botón «Entrar como visitante», es de solo lectura: no ve Equipos ni Reglas, y al tocar un partido abre su detalle. Los permisos se validan en el servicio (Java y JavaScript), no solo en la pantalla.
- **Formación táctica** por equipo en cada partido (4-4-2, 4-3-3, 4-2-3-1, 4-5-1, 4-1-4-1, 3-5-2, 3-4-3, 5-3-2, 5-4-1). Se puede elegir antes de jugar.
- **XI inicial**: en el partido se marcan los 11 titulares de cada equipo (o toda la plantilla si tiene menos). El botón «Dorsales más bajos» los elige de un clic. En los cambios, «Sale» muestra solo a los que están en la cancha y «Entra» solo a los suplentes que no han entrado. No se pueden anotar cambios sin el XI inicial, y el detalle del partido muestra las alineaciones.
- Estado **«En juego»**: el marcador, las tarjetas y los cambios se guardan en vivo mientras se juega. Ese partido no cuenta en la tabla hasta que pasa a «Jugado».
- Las tarjetas ahora pueden llevar **minuto** (opcional).
- **Detalle del partido** al tocar cualquier partido: **Resumen** (goles, cambios y tarjetas minuto a minuto), **Cara a cara** (incluye campeonatos del historial), **Racha** de los últimos 5 partidos (V/E/D) y **Alineaciones** sobre una cancha horizontal que se ve completa (local a la izquierda, visitante a la derecha), con la foto, el dorsal y el nombre de los 22 titulares; debajo, formación, cuerpo técnico, cambios y suplentes.
- **Goles por jugador** en el editor del partido: minuto, autor, asistencia y autogol.
- **Gestionar partidos** en dos pestañas: «Previa del partido» (fecha, sede, formación y XI) y «Registrar resultados» (marcador, goles, tarjetas y cambios).
- **Noticias con moderación**: el administrador publica noticias con foto y aprueba o rechaza las que proponen los visitantes; todos pueden comentar y los insultos se cambian por `***` automáticamente.
- **Calificación de jugadores**: los visitantes votan del 1 al 10 en la sección **Votaciones**, una vez por jugador desde cada dispositivo, mientras la votación del partido está abierta. El administrador decide cuánto dura (12, 24, 48 horas u otro tiempo) en el editor del partido y puede cerrarla cuando quiera. La pestaña «Calificaciones» del detalle muestra la **nota final** (la del sistema promediada con los votos) y destaca al **MVP**.
- **Llaves solo si el formato las tiene**: en una Liga la sección no aparece (el campeón sale de la tabla).
- **Liga con divisiones (Liga A, B, C…)**: el administrador elige las divisiones al crear la liga, puede agregar o quitar divisiones y mover equipos antes de iniciar, y con «Nueva temporada» se aplican los ascensos y descensos. El campeón es el primero de la Liga A.
- Sección **Estadísticas** solo con datos de todo el campeonato: goleadores, asistencias, valla menos vencida y juego limpio (con tarjetas y sanciones). El administrador corrige desde ahí; el visitante solo consulta.
- **Datos de prueba** (botón en Equipos): crea equipos inventados con DT, estadio y plantilla completa de 20 jugadores (nombre, documento, dorsal y posición) a partir de una **semilla**. La misma semilla genera siempre los mismos datos, en la web y en el escritorio. Con el campeonato ya iniciado solo completa las plantillas vacías.

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
  persistence/    repositorios (interfaces y formato anterior en archivos)
    postgres/     conexión JDBC, DAO y repositorios en PostgreSQL
  service/        casos de uso y reglas del torneo
    formatos/     un formato de campeonato por clase
  ui/             servidor web local, API, presentador y arranque
    escritorio/   ventana JavaFX y su hoja de estilos
test/alphasport/    SimulacionFormatos (reglas) y PruebaPostgres (base de datos)
database/         schema.sql y seed.sql
lib/              JavaFX 21 (base, graphics, controls) y driver JDBC de PostgreSQL
web/
  index.html, css/estilos.css, img/ (logos)
  js/model, js/persistence, js/service, js/ui
  pruebas/        prueba automática del motor web
docs/             arquitectura, formatos y reglas
datos/imagenes/   fotos y escudos (no se sube a Git)
db.properties     su conexión a PostgreSQL (no se sube a Git; copie db.properties.example)
```

Autores: Diego Alejandro Amaya Verdecia y Ednilson Contreras.