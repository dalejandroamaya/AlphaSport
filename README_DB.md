# Base de datos PostgreSQL

El escritorio (JavaFX) y la web con Java guardan todo en PostgreSQL. La **web sola** (`web/index.html` sin Java) sigue usando el navegador: no tiene servidor que hable con la base.

## 1. Requisitos

- PostgreSQL 14 o superior con pgAdmin 4 (probado con PostgreSQL 18).
- El driver JDBC ya viene en `lib/postgresql-42.7.14.jar`. VS Code lo toma solo, por `java.project.referencedLibraries`.

Sin VS Code, agregue `lib/*` al classpath:

```powershell
javac -encoding UTF-8 -d bin -cp "lib/*" (Get-ChildItem -Recurse src,test -Filter *.java | ForEach-Object FullName)
```
```powershell
java -cp "bin;lib/*" alphasport.ui.AlphaSportApp
```

## 2. Crear la base en pgAdmin

1. Abra pgAdmin y conéctese a su servidor local (`localhost`, puerto `5432`, usuario `postgres`).
2. Clic derecho en **Databases → Create → Database…**
3. En **Database** escriba `alphasport_db`. En la pestaña **Definition**, **Encoding** `UTF8`. Pulse **Save**.
4. Clic derecho en `alphasport_db` → **Query Tool**.
5. Botón **Open File** (carpeta) → `database/schema.sql` → **Execute** (F5). Debe decir *Query returned successfully*.
6. Opcional, para empezar con datos de prueba: abra `database/seed.sql` y ejecútelo igual.
7. Clic derecho en **Schemas → public → Tables → Refresh**: aparecen 22 tablas, y 4 en **Views**.

Los dos archivos van en una transacción: si algo falla no cambian nada. `schema.sql` no borra datos (si las tablas ya existen, avisa y no hace nada) y `seed.sql` no hace nada si ya hay un campeonato en curso.

### Si la base ya existía: migraciones

Cuando cambia el esquema, el cambio llega también como un archivo en `database/migraciones/`. En el **Query Tool** de `alphasport_db` ejecute, **en orden**, los que falten. Cada uno va en una transacción y se puede repetir: si ya se aplicó, no cambia nada. Una base nueva creada con `schema.sql` no los necesita.

Para saber en qué versión está su base (si la tabla no existe, es la 0):

```sql
SELECT MAX(version) FROM esquema_version;
```

Si falta alguna, el programa lo avisa al abrir y no toca los datos.

| Archivo | Versión | Qué cambia |
|---|---|---|
| `001_walkover.sql` | 1 | Partidos ganados por W.O. |
| `002_personas.sql` | 2 | Tabla `personas`: el mismo jugador en todas las temporadas |
| `003_documento_unico.sql` | 3 | Dos jugadores no pueden tener el mismo documento |
| `004_movimientos_division.sql` | 4 | Ascensos y descensos de cada temporada |
| `005_registro_usuarios.sql` | 5 | Registro de cuentas: roles `ORGANIZADOR` y `AFICIONADO`, usuario y correo en minúsculas |
| `006_varios_campeonatos.sql` | 6 | Varios campeonatos en curso: dueño de cada torneo y noticias por campeonato |
| `007_imagenes.sql` | 7 | Fotos, escudos y fotos de noticias en la tabla `imagenes` (las ven todos los computadores) |
| `008_correo_254.sql` | 8 | La columna `email` admite correos de hasta 254 caracteres (solo la ensancha, no cambia datos) |

**Migración 006.** Permite varios campeonatos en curso a la vez. Solo usa SQL básico (`ALTER TABLE`, `UPDATE`, `INSERT … SELECT`):

1. Agrega `noticias.torneo_id` y pone las noticias que ya había en el campeonato en curso (si hay exactamente uno).
2. Quita el índice `torneos_un_actual`, que permitía un solo campeonato `ACTUAL`.
3. Agrega `torneos.propietario_id`, llave foránea a `usuarios`: el Organizador dueño de cada campeonato.
4. Los torneos sin dueño pasan al Organizador más antiguo (el de menor `id`). Si todavía no hay ninguno, quedan sin dueño y el primer Organizador que los **tome** desde la aplicación se queda con ellos.

Va después de la 005 (sin PL/pgSQL no puede frenar con un mensaje si falta: ejecútelas en orden). No borra datos y se puede repetir.

**Migración 005.** Cambia los roles de la tabla `usuarios` (`ADMIN` pasa a `ORGANIZADOR` y `VISITANTE` a `AFICIONADO`), agranda la columna `rol` (antes `VARCHAR(10)`) y deja el usuario y el correo en minúsculas, así «Ana» y «ana» son la misma cuenta. No borra ninguna fila. Las cuentas de demostración que haya creado un `seed.sql` anterior (`admin` y `visitante`) siguen en la tabla con el rol nuevo; si ya no las quiere, bórrelas a mano:

```sql
DELETE FROM usuarios WHERE username IN ('admin', 'visitante');
```

## Cuentas

Ya no hay cuentas de demostración ni entrada como visitante: cada persona pulsa **Registrarse** en la pantalla de entrada y elige su tipo de cuenta. **Organizador** crea y gestiona torneos; **Aficionado** ve resultados y noticias, comenta y califica jugadores. La cuenta queda en la tabla `usuarios` (la clave solo como hash PBKDF2-SHA256, nunca la clave) y la próxima vez basta con **Iniciar sesión**. El primer Organizador es simplemente la primera persona que se registra con ese tipo.

En la **web sola** (`web/index.html` sin Java) no hay base de datos: las cuentas se guardan en el navegador, con la clave en hash, y solo sirven en ese navegador.

## Varios campeonatos

Desde la migración 006 puede haber varios campeonatos en curso a la vez. Cada uno es independiente (sus equipos, partidos, votos y noticias no se mezclan) y tiene un dueño:

- **Organizador:** ve y gestiona solo los campeonatos que creó (o que tomó sin dueño).
- **Aficionado:** ve todos los campeonatos en curso y elige cuál seguir.

Para saber qué campeonatos hay en curso y de quién son:

```sql
SELECT t.codigo, t.nombre, u.nombre AS organizador
  FROM torneos t LEFT JOIN usuarios u ON u.id = t.propietario_id
 WHERE t.situacion = 'ACTUAL';
```

## 3. Credenciales

Cada quien tiene su propio archivo; Git lo ignora y **nunca se sube**:

```powershell
Copy-Item db.properties.example db.properties
```

Abra `db.properties` y escriba su contraseña:

| Dato | Si falta se usa |
|---|---|
| `db.host` | `localhost` |
| `db.port` | `5432` |
| `db.name` | `alphasport_db` |
| `db.user` | `postgres` |
| `db.password` | la variable de entorno `ALPHASPORT_DB_PASSWORD` |
| `db.ssl` | vacío (base local). Con una base en internet, `require` |

La contraseña no tiene valor por defecto en el código a propósito: quedaría publicada en el repositorio.

### Una base compartida en internet (Neon)

Con PostgreSQL en cada computador, cada uno tiene su propia base: una cuenta creada en un PC no existe en otro. Para que todos vean las mismas cuentas, campeonatos, resultados y fotos, usen una sola base en internet. Neon (https://neon.tech) es PostgreSQL en la nube con plan gratuito:

1. Cree la cuenta y un proyecto (la región más cercana: desde Colombia, AWS US East).
2. En **Connect**, desactive *Connection pooling* (el host no debe decir `-pooler`) y copie los datos de la conexión.
3. En `db.properties` (el mismo para todo el equipo, repartido por un medio privado):
   ```
   db.host=ep-xxxx.region.aws.neon.tech
   db.port=5432
   db.name=alphasport_db
   db.user=neondb_owner
   db.password=...
   db.ssl=require
   ```
4. Cree las tablas en esa base (pasos 2 y 4 de esta guía, con el **SQL Editor** de Neon o con pgAdmin conectado a Neon).

**Para los demás del equipo:** quien ya tiene la base pasa la cadena de conexión (`postgresql://…`) por un medio privado. El otro ejecuta `configurar_base.bat` (en la carpeta del proyecto), la pega y queda listo: el archivo crea `db.properties` con `db.url=…`. Nunca suban esa cadena ni `db.properties` al repositorio (es público).

### La web publicada en internet (Render)

El `Dockerfile` publica la versión web (sin el escritorio) en Render, con la misma base de Neon:

1. En Render: **New → Web Service**, elija este repositorio, rama `main`, **Language: Docker**, región **Virginia (US East)** (junto a Neon) y plan **Free**.
2. En **Environment Variables** agregue `DATABASE_URL` con la cadena de Neon. Render da solo `PORT` y la dirección pública.
3. Cada cambio que llegue a `main` se publica solo. Si el cambio trae una migración nueva, aplíquela en Neon antes.

En internet no aparece «Cerrar programa» y la sesión viaja solo por HTTPS. El plan gratis «duerme» tras unos minutos sin visitas: la primera visita tarda unos segundos más.

Notas: cada acción viaja por internet (un poco más lenta que la base local) y Neon «duerme» la base tras unos minutos sin uso, así que la primera acción puede tardar uno o dos segundos. La web sola (`index.html` sin Java) no usa la base: sigue guardando en el navegador.

## 4. Pasar los datos anteriores

Si tenía un campeonato en la carpeta `datos/`, ejecute **Pasar datos/ a PostgreSQL (MigradorArchivos)** en *Run and Debug*, o:

```powershell
java -cp "bin;lib/*" alphasport.persistence.postgres.MigradorArchivos
```

Pasa el campeonato (como uno más en curso, sin dueño: un Organizador lo puede tomar), el historial y las noticias. No borra los archivos y no reemplaza lo que ya esté en la base (por eso, si quiere sus datos anteriores, no ejecute `seed.sql` antes). Las fotos de `datos/imagenes/` se suben solas a la tabla `imagenes` la primera vez que se abre el programa (los archivos no se borran).

## 5. Probar

Ejecute **Prueba de PostgreSQL (PruebaPostgres)**: debe terminar en `TODO BIEN`. Trabaja en un esquema temporal (`prueba_…`) que se borra al final, así que no toca sus datos.

## 6. Tablas

| Tabla | Qué guarda |
|---|---|
| `torneos`, `torneo_criterios`, `divisiones` | Los campeonatos en curso (`ACTUAL`, puede haber varios) y los del historial (`ARCHIVADO`), con sus reglas y su dueño (`propietario_id`). Terminar un campeonato lo pasa al historial con sus mismos ids |
| `equipos`, `directores_tecnicos`, `jugadores` | Plantillas. Un jugador que sale queda con `activo = FALSE`: sus goles y votos lo siguen usando |
| `personas` | Quién es cada jugador (`codigo` UUID): la misma persona en todas las temporadas y copias del historial. No se borra con el torneo |
| `imagenes` | Fotos de jugadores, escudos y fotos de noticias (los bytes). Así las ven todos los computadores de la misma base |
| `movimientos_division` | Ascensos y descensos con los que empezó cada temporada: de qué división venía cada equipo, a cuál pasó y en qué puesto terminó |
| `fases`, `fase_participantes`, `fase_grupos`, `fase_cuadro` | Fases, grupos y llaves |
| `partidos` | Fixture, previa, resultado y votación (`horas_votacion`, `finalizado_en`, `votacion_cerrada_en`) |
| `alineaciones` | Titulares y suplentes de cada partido |
| `eventos_partido` | Goles (con asistencia), autogoles, tarjetas y cambios |
| `votos_publico`, `calificaciones_partido` | Votos de los aficionados y la última nota calculada |
| `noticias`, `comentarios` | Noticias de cada campeonato (`torneo_id`; vacío = general) y sus comentarios. `aprobado` = publicada |
| `usuarios` | Cuentas creadas con **Registrarse**: usuario y correo únicos (en minúsculas), rol `ORGANIZADOR` o `AFICIONADO` y la clave en hash |

`id` es la llave de la base y `numero` el id que usa el programa dentro de cada torneo.

| Vista | Para qué |
|---|---|
| `v_tabla_posiciones` | Tabla por fase y grupo (puntos, diferencia y goles) |
| `v_ultima_alineacion` | Titulares del último partido jugado de cada equipo |
| `v_votaciones_activas` | Partidos que el público puede votar ahora |
| `v_calificaciones` | Nota final de cada jugador por partido |

## 7. Escritorio y web a la vez

Se pueden usar al mismo tiempo, también desde varios computadores con la misma base. Antes de cada consulta o acción se revisa `torneos.version` y, si cambió, se vuelve a leer. Si los dos guardan a la vez, el segundo recibe un aviso y los datos nuevos, en vez de pisar el cambio.

**En vivo:** el escritorio y la web con Java revisan esa versión cada 5 segundos y se redibujan solos cuando otro equipo cambió algo («Actualizado con los cambios de otro equipo»). Esperan mientras haya un diálogo abierto o se esté escribiendo, y se pausan con la ventana minimizada o la pestaña oculta (así una base en internet puede «dormir»).

Si edita una tabla a mano en pgAdmin mientras el programa está abierto, súbale la versión para que lo note:

```sql
UPDATE torneos SET version = version + 1 WHERE situacion = 'ACTUAL';
```

## 8. Problemas comunes

| Mensaje | Qué hacer |
|---|---|
| No hay conexión con PostgreSQL… | Inicie el servicio `postgresql-x64-…` de Windows y revise `db.host` y `db.port` |
| No hay conexión con la base de datos en internet… | Revise la conexión a internet y los datos de `db.properties` (con Neon, `db.ssl=require`) |
| PostgreSQL rechazó el usuario o la contraseña… | Corrija `db.user` y `db.password` en `db.properties` |
| La base de datos … no existe | Paso 2 |
| … no tiene las tablas de AlphaSport | Ejecute `database/schema.sql` |
| Hay jugadores con el mismo documento (al ejecutar `003_documento_unico.sql`) | El mensaje dice cuáles, con su id. Corrija el documento de uno de ellos (`UPDATE jugadores SET documento = '…' WHERE id = …;`) y vuelva a ejecutar la migración |

Para empezar de cero (**borra todos los datos**): en pgAdmin, clic derecho en `alphasport_db` → **Delete**, y repita el paso 2.
