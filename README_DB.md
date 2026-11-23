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
7. Clic derecho en **Schemas → public → Tables → Refresh**: aparecen 21 tablas, y 4 en **Views**.

Los dos archivos van en una transacción: si algo falla no cambian nada. `schema.sql` no borra datos (si las tablas ya existen, avisa y no hace nada) y `seed.sql` no hace nada si ya hay un campeonato en curso.

### Si la base ya existía: migraciones

Cuando cambia el esquema, el cambio llega también como un archivo en `database/migraciones/`. En el **Query Tool** de `alphasport_db` ejecute, **en orden**, los que falten. Cada uno va en una transacción y se puede repetir: si ya se aplicó, no cambia nada. Una base nueva creada con `schema.sql` no los necesita.

Para saber en qué versión está su base (si la tabla no existe, es la 0):

```sql
SELECT MAX(version) FROM esquema_version;
```

Si falta alguna, el programa lo avisa al abrir y no toca los datos.

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

La contraseña no tiene valor por defecto en el código a propósito: quedaría publicada en el repositorio.

## 4. Pasar los datos anteriores

Si tenía un campeonato en la carpeta `datos/`, ejecute **Pasar datos/ a PostgreSQL (MigradorArchivos)** en *Run and Debug*, o:

```powershell
java -cp "bin;lib/*" alphasport.persistence.postgres.MigradorArchivos
```

Pasa el campeonato, el historial y las noticias. No borra los archivos y no reemplaza lo que ya esté en la base (por eso, si quiere sus datos anteriores, no ejecute `seed.sql` antes). Las fotos siguen en `datos/imagenes/`; la base guarda su nombre.

## 5. Probar

Ejecute **Prueba de PostgreSQL (PruebaPostgres)**: debe terminar en `TODO BIEN`. Trabaja en un esquema temporal (`prueba_…`) que se borra al final, así que no toca sus datos.

## 6. Tablas

| Tabla | Qué guarda |
|---|---|
| `torneos`, `torneo_criterios`, `divisiones` | El campeonato en curso (`ACTUAL`) y los del historial (`ARCHIVADO`), con sus reglas. Al empezar otro campeonato, el anterior pasa al historial con sus mismos ids |
| `equipos`, `directores_tecnicos`, `jugadores` | Plantillas. Un jugador que sale queda con `activo = FALSE`: sus goles y votos lo siguen usando |
| `personas` | Quién es cada jugador (`codigo` UUID): la misma persona en todas las temporadas y copias del historial. No se borra con el torneo |
| `movimientos_division` | Ascensos y descensos con los que empezó cada temporada: de qué división venía cada equipo, a cuál pasó y en qué puesto terminó |
| `fases`, `fase_participantes`, `fase_grupos`, `fase_cuadro` | Fases, grupos y llaves |
| `partidos` | Fixture, previa, resultado y votación (`horas_votacion`, `finalizado_en`, `votacion_cerrada_en`) |
| `alineaciones` | Titulares y suplentes de cada partido |
| `eventos_partido` | Goles (con asistencia), autogoles, tarjetas y cambios |
| `votos_publico`, `calificaciones_partido` | Votos de los visitantes y la última nota calculada |
| `noticias`, `comentarios` | Noticias (`aprobado` = publicada) y sus comentarios |
| `usuarios` | Lista para el login y el módulo Fantasy; el programa todavía no la usa |

`id` es la llave de la base y `numero` el id que usa el programa dentro de cada torneo.

| Vista | Para qué |
|---|---|
| `v_tabla_posiciones` | Tabla por fase y grupo (puntos, diferencia y goles) |
| `v_ultima_alineacion` | Titulares del último partido jugado de cada equipo |
| `v_votaciones_activas` | Partidos que el público puede votar ahora |
| `v_calificaciones` | Nota final de cada jugador por partido |

## 7. Escritorio y web a la vez

Se pueden usar al mismo tiempo. Antes de cada consulta o acción se revisa `torneos.version` y, si cambió, se vuelve a leer. Si los dos guardan a la vez, el segundo recibe un aviso y los datos nuevos, en vez de pisar el cambio.

Si edita una tabla a mano en pgAdmin mientras el programa está abierto, súbale la versión para que lo note:

```sql
UPDATE torneos SET version = version + 1 WHERE situacion = 'ACTUAL';
```

## 8. Problemas comunes

| Mensaje | Qué hacer |
|---|---|
| No hay conexión con PostgreSQL… | Inicie el servicio `postgresql-x64-…` de Windows y revise `db.host` y `db.port` |
| PostgreSQL rechazó el usuario o la contraseña… | Corrija `db.user` y `db.password` en `db.properties` |
| La base de datos … no existe | Paso 2 |
| … no tiene las tablas de AlphaSport | Ejecute `database/schema.sql` |
| Hay jugadores con el mismo documento (al ejecutar `003_documento_unico.sql`) | El mensaje dice cuáles, con su id. Corrija el documento de uno de ellos (`UPDATE jugadores SET documento = '…' WHERE id = …;`) y vuelva a ejecutar la migración |

Para empezar de cero (**borra todos los datos**): en pgAdmin, clic derecho en `alphasport_db` → **Delete**, y repita el paso 2.
