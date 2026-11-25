-- =====================================================================
--  AlphaSport · esquema de la base de datos (PostgreSQL 14 o superior)
--  Ejecutar UNA vez sobre la base alphasport_db (ver README_DB.md).
--  Va en una transaccion: si algo falla (por ejemplo, las tablas ya
--  existen) no se cambia nada. Nunca borra datos.
-- =====================================================================
BEGIN;

-- Version del esquema: la sube cada archivo de database/migraciones.
CREATE TABLE esquema_version (
    version      INTEGER PRIMARY KEY,
    aplicada_en  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO esquema_version (version) VALUES (7);

-- ---------------------------------------------------------------- usuarios
-- Cuentas creadas con «Registrarse»: usuario y correo en minusculas (unicos sin importar mayusculas).
CREATE TABLE usuarios (
    id              SERIAL PRIMARY KEY,
    nombre          VARCHAR(80)  NOT NULL,
    username        VARCHAR(40)  NOT NULL UNIQUE,
    email           VARCHAR(120) UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    rol             VARCHAR(12)  NOT NULL DEFAULT 'AFICIONADO' CONSTRAINT usuarios_rol_check CHECK (rol IN ('ORGANIZADOR', 'AFICIONADO')),
    fecha_registro  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT usuarios_minusculas CHECK (username = LOWER(username) AND (email IS NULL OR email = LOWER(email)))
);

-- ---------------------------------------------------------------- torneos
-- ACTUAL = campeonato en curso (puede haber varios, cada uno con su dueno); ARCHIVADO = copia completa guardada en el historial.
-- «version» sube con cada guardado: asi el escritorio y la web notan los cambios del otro.
CREATE TABLE torneos (
    id                         SERIAL PRIMARY KEY,
    codigo                     VARCHAR(40)  NOT NULL,
    situacion                  VARCHAR(10)  NOT NULL DEFAULT 'ACTUAL' CHECK (situacion IN ('ACTUAL', 'ARCHIVADO')),
    propietario_id             INTEGER      REFERENCES usuarios (id) ON DELETE SET NULL,  -- el Organizador que lo creo (NULL = sin dueno)
    nombre                     VARCHAR(200) NOT NULL,
    formato                    VARCHAR(30)  NOT NULL CHECK (formato IN ('LIGA', 'ELIMINACION_DIRECTA',
                                   'GRUPOS_Y_ELIMINATORIA', 'TORNEO_CORTO', 'SUIZO', 'DOBLE_ELIMINACION')),
    version                    INTEGER      NOT NULL DEFAULT 1,
    paso_actual                INTEGER      NOT NULL DEFAULT 0,
    campeon_id                 INTEGER,     -- FK a equipos (se agrega mas abajo)
    semilla                    BIGINT       NOT NULL DEFAULT 0,
    fecha_archivo              DATE,
    -- numeros internos que siguen (no se reutilizan aunque se borre un equipo o un jugador)
    siguiente_equipo           INTEGER      NOT NULL DEFAULT 1,
    siguiente_jugador          INTEGER      NOT NULL DEFAULT 1,
    siguiente_partido          INTEGER      NOT NULL DEFAULT 1,
    -- reglas del torneo
    puntos_victoria            INTEGER      NOT NULL DEFAULT 3,
    puntos_empate              INTEGER      NOT NULL DEFAULT 1,
    ida_y_vuelta               BOOLEAN      NOT NULL DEFAULT FALSE,
    eliminatoria_ida_y_vuelta  BOOLEAN      NOT NULL DEFAULT FALSE,
    final_unica                BOOLEAN      NOT NULL DEFAULT TRUE,
    sorteo_aleatorio           BOOLEAN      NOT NULL DEFAULT TRUE,
    tamano_grupo               INTEGER      NOT NULL DEFAULT 4,
    clasificados_por_grupo     INTEGER      NOT NULL DEFAULT 2,
    clasificados_playoff       INTEGER      NOT NULL DEFAULT 8,
    tipo_playoff               VARCHAR(20)  NOT NULL DEFAULT 'LLAVE',
    ventaja_deportiva          BOOLEAN      NOT NULL DEFAULT TRUE,
    rondas_suizo               INTEGER      NOT NULL DEFAULT 8,
    directos_suizo             INTEGER      NOT NULL DEFAULT 8,
    repechaje_suizo            INTEGER      NOT NULL DEFAULT 16,
    gran_final_con_reinicio    BOOLEAN      NOT NULL DEFAULT TRUE,
    cantidad_divisiones        INTEGER      NOT NULL DEFAULT 1 CHECK (cantidad_divisiones BETWEEN 1 AND 6),
    ascensos                   INTEGER      NOT NULL DEFAULT 2 CHECK (ascensos >= 0),
    creado_en                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (codigo, situacion)
);
CREATE INDEX torneos_propietario ON torneos (propietario_id);

-- Criterios de desempate del torneo, en orden.
CREATE TABLE torneo_criterios (
    torneo_id  INTEGER     NOT NULL REFERENCES torneos (id) ON DELETE CASCADE,
    orden      INTEGER     NOT NULL,
    criterio   VARCHAR(30) NOT NULL,
    PRIMARY KEY (torneo_id, orden)
);

-- Liga A (indice 0), Liga B (1)... Todo torneo tiene al menos una.
CREATE TABLE divisiones (
    id         SERIAL PRIMARY KEY,
    torneo_id  INTEGER     NOT NULL REFERENCES torneos (id) ON DELETE CASCADE,
    indice     INTEGER     NOT NULL CHECK (indice >= 0),
    nombre     VARCHAR(40) NOT NULL,
    UNIQUE (torneo_id, indice)
);

-- ---------------------------------------------------------------- equipos
-- «numero» es el id que usa el programa dentro de cada torneo.
CREATE TABLE equipos (
    id           SERIAL PRIMARY KEY,
    torneo_id    INTEGER      NOT NULL REFERENCES torneos (id) ON DELETE CASCADE,
    numero       INTEGER      NOT NULL,
    nombre       VARCHAR(200) NOT NULL,
    estadio      VARCHAR(200) NOT NULL DEFAULT '',
    escudo_ruta  VARCHAR(255),
    division_id  INTEGER      NOT NULL REFERENCES divisiones (id),
    retirado     BOOLEAN      NOT NULL DEFAULT FALSE,
    orden        INTEGER      NOT NULL DEFAULT 0,
    UNIQUE (torneo_id, numero)
);
CREATE INDEX equipos_division ON equipos (division_id);

ALTER TABLE torneos ADD CONSTRAINT torneos_campeon_fk
    FOREIGN KEY (campeon_id) REFERENCES equipos (id) ON DELETE SET NULL;

CREATE TABLE directores_tecnicos (
    equipo_id     INTEGER PRIMARY KEY REFERENCES equipos (id) ON DELETE CASCADE,
    nombre        VARCHAR(200) NOT NULL,
    documento     VARCHAR(60)  NOT NULL DEFAULT '',
    nacionalidad  VARCHAR(80)  NOT NULL DEFAULT ''
);

-- La persona detras de cada jugador: la misma en todas las temporadas y copias del historial.
-- codigo es un UUID que crea el programa, asi un respaldo de otra base nunca se mezcla con esta.
-- No se borra con el torneo: es la llave que usaran el historial del jugador y el modulo Fantasy.
CREATE TABLE personas (
    id         SERIAL PRIMARY KEY,
    codigo     UUID         NOT NULL UNIQUE,
    nombre     VARCHAR(200) NOT NULL,
    documento  VARCHAR(60)  NOT NULL DEFAULT '',
    creado_en  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Un jugador que sale de la plantilla no se borra (activo = FALSE): sus goles, tarjetas y votos lo siguen usando.
-- nombre y documento son los de esa temporada; persona_id dice quien es.
CREATE TABLE jugadores (
    id          SERIAL PRIMARY KEY,
    torneo_id   INTEGER      NOT NULL REFERENCES torneos (id) ON DELETE CASCADE,
    equipo_id   INTEGER      NOT NULL REFERENCES equipos (id) ON DELETE CASCADE,
    persona_id  INTEGER      NOT NULL REFERENCES personas (id),
    numero      INTEGER      NOT NULL,
    nombre      VARCHAR(200) NOT NULL,
    documento   VARCHAR(60)  NOT NULL DEFAULT '',
    dorsal      INTEGER      NOT NULL CHECK (dorsal BETWEEN 0 AND 99),
    posicion    VARCHAR(60)  NOT NULL DEFAULT '',
    es_capitan  BOOLEAN      NOT NULL DEFAULT FALSE,
    foto_ruta   VARCHAR(255),
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    orden       INTEGER      NOT NULL DEFAULT 0,
    UNIQUE (torneo_id, numero),
    -- Dorsal unico en la plantilla. Se revisa al final de la transaccion: asi dos jugadores pueden intercambiarlo.
    CONSTRAINT jugadores_dorsal_unico EXCLUDE USING btree (equipo_id WITH =, dorsal WITH =)
        WHERE (activo) DEFERRABLE INITIALLY DEFERRED,
    -- Un documento es de un solo jugador del torneo (sin contar espacios ni mayusculas; sin documento se permite).
    CONSTRAINT jugadores_documento_unico
        EXCLUDE USING btree (torneo_id WITH =, (upper(regexp_replace(documento, '\s', '', 'g'))) WITH =)
        WHERE (activo AND regexp_replace(documento, '\s', '', 'g') <> '') DEFERRABLE INITIALLY DEFERRED
);
CREATE UNIQUE INDEX jugadores_un_capitan ON jugadores (equipo_id) WHERE es_capitan AND activo;
-- Una persona juega una sola vez en cada torneo.
CREATE UNIQUE INDEX jugadores_persona_unica ON jugadores (torneo_id, persona_id);
CREATE INDEX jugadores_persona ON jugadores (persona_id);

-- Ascensos y descensos con los que empezo cada temporada: de que division venia el equipo, a cual paso y en
-- que puesto termino (constancia del paso de temporada).
CREATE TABLE movimientos_division (
    id                  SERIAL PRIMARY KEY,
    torneo_id           INTEGER      NOT NULL REFERENCES torneos (id) ON DELETE CASCADE,  -- la temporada nueva
    equipo_id           INTEGER      NOT NULL REFERENCES equipos (id) ON DELETE CASCADE,
    division_anterior   INTEGER      NOT NULL CHECK (division_anterior >= 0),
    division_nueva      INTEGER      NOT NULL CHECK (division_nueva >= 0),
    puesto              INTEGER      NOT NULL CHECK (puesto >= 1),                         -- en su division, al cerrar la temporada
    temporada_anterior  VARCHAR(200) NOT NULL,
    UNIQUE (torneo_id, equipo_id),
    CHECK (division_anterior <> division_nueva)
);

-- ---------------------------------------------------------------- fases
CREATE TABLE fases (
    id                 SERIAL PRIMARY KEY,
    torneo_id          INTEGER      NOT NULL REFERENCES torneos (id) ON DELETE CASCADE,
    indice             INTEGER      NOT NULL,
    nombre             VARCHAR(120) NOT NULL,
    tipo               VARCHAR(20)  NOT NULL CHECK (tipo IN ('LIGA', 'GRUPOS', 'SUIZO', 'ELIMINATORIA', 'DOBLE_ELIMINACION')),
    paso_inicio        INTEGER      NOT NULL DEFAULT 0,
    ida_y_vuelta       BOOLEAN      NOT NULL DEFAULT FALSE,
    final_unica        BOOLEAN      NOT NULL DEFAULT TRUE,
    ventaja_deportiva  BOOLEAN      NOT NULL DEFAULT FALSE,
    max_rondas         INTEGER      NOT NULL DEFAULT 0,
    rondas_suizo       INTEGER      NOT NULL DEFAULT 0,
    con_reinicio       BOOLEAN      NOT NULL DEFAULT FALSE,
    es_divisiones      BOOLEAN      NOT NULL DEFAULT FALSE,
    cantidad_grupos    INTEGER      NOT NULL DEFAULT 0,
    UNIQUE (torneo_id, indice)
);

-- Equipos de la fase por siembra (el primero es el mejor).
CREATE TABLE fase_participantes (
    fase_id    INTEGER NOT NULL REFERENCES fases (id) ON DELETE CASCADE,
    orden      INTEGER NOT NULL,
    equipo_id  INTEGER NOT NULL REFERENCES equipos (id) ON DELETE CASCADE,
    PRIMARY KEY (fase_id, orden)
);

-- Equipos de cada grupo (0 = A, 1 = B...).
CREATE TABLE fase_grupos (
    fase_id    INTEGER NOT NULL REFERENCES fases (id) ON DELETE CASCADE,
    grupo      INTEGER NOT NULL,
    orden      INTEGER NOT NULL,
    equipo_id  INTEGER NOT NULL REFERENCES equipos (id) ON DELETE CASCADE,
    PRIMARY KEY (fase_id, grupo, orden)
);

-- Orden de la llave en la primera ronda; sin equipo = pasa directo.
CREATE TABLE fase_cuadro (
    fase_id    INTEGER NOT NULL REFERENCES fases (id) ON DELETE CASCADE,
    orden      INTEGER NOT NULL,
    equipo_id  INTEGER REFERENCES equipos (id) ON DELETE CASCADE,
    PRIMARY KEY (fase_id, orden)
);

-- ---------------------------------------------------------------- partidos
CREATE TABLE partidos (
    id                    SERIAL PRIMARY KEY,
    torneo_id             INTEGER      NOT NULL REFERENCES torneos (id) ON DELETE CASCADE,
    numero                INTEGER      NOT NULL,
    fase_id               INTEGER      NOT NULL REFERENCES fases (id) ON DELETE CASCADE,
    local_id              INTEGER      NOT NULL REFERENCES equipos (id),
    visitante_id          INTEGER      NOT NULL REFERENCES equipos (id),
    -- ubicacion en el fixture
    paso                  INTEGER      NOT NULL DEFAULT 0,
    grupo                 VARCHAR(5)   NOT NULL DEFAULT '',
    ronda                 INTEGER      NOT NULL DEFAULT 0,
    nombre_ronda          VARCHAR(80)  NOT NULL DEFAULT '',
    serie                 VARCHAR(60)  NOT NULL DEFAULT '',
    pierna                INTEGER      NOT NULL DEFAULT 0,
    llave                 VARCHAR(40)  NOT NULL DEFAULT '',
    eliminatorio          BOOLEAN      NOT NULL DEFAULT FALSE,
    -- previa
    fecha                 DATE,
    hora                  TIME,
    sede                  VARCHAR(200) NOT NULL DEFAULT '',
    observaciones         TEXT         NOT NULL DEFAULT '',
    formacion_local       VARCHAR(15),
    formacion_visitante   VARCHAR(15),
    -- resultado
    estado                VARCHAR(12)  NOT NULL DEFAULT 'PENDIENTE'
                              CHECK (estado IN ('PENDIENTE', 'EN_JUEGO', 'JUGADO', 'APLAZADO', 'SUSPENDIDO')),
    walkover              BOOLEAN      NOT NULL DEFAULT FALSE,   -- se gano por W.O.: no se jugo
    goles_local           INTEGER      NOT NULL DEFAULT 0 CHECK (goles_local >= 0),
    goles_visitante       INTEGER      NOT NULL DEFAULT 0 CHECK (goles_visitante >= 0),
    penales_local         INTEGER      CHECK (penales_local >= 0),
    penales_visitante     INTEGER      CHECK (penales_visitante >= 0),
    amarillas_local       INTEGER      NOT NULL DEFAULT 0,
    rojas_local           INTEGER      NOT NULL DEFAULT 0,
    amarillas_visitante   INTEGER      NOT NULL DEFAULT 0,
    rojas_visitante       INTEGER      NOT NULL DEFAULT 0,
    -- votacion del publico
    finalizado_en         TIMESTAMP WITH TIME ZONE,
    horas_votacion        INTEGER      NOT NULL DEFAULT 6 CHECK (horas_votacion BETWEEN 1 AND 720),
    votacion_cerrada_en   TIMESTAMP WITH TIME ZONE,
    UNIQUE (torneo_id, numero),
    CHECK (local_id <> visitante_id)
);
CREATE INDEX partidos_fase ON partidos (fase_id);
CREATE INDEX partidos_local ON partidos (local_id, fecha DESC);
CREATE INDEX partidos_visitante ON partidos (visitante_id, fecha DESC);
CREATE INDEX partidos_votacion ON partidos (finalizado_en) WHERE estado = 'JUGADO' AND votacion_cerrada_en IS NULL;

-- XI inicial (titular) y suplentes de cada equipo en el partido.
CREATE TABLE alineaciones (
    partido_id  INTEGER NOT NULL REFERENCES partidos (id) ON DELETE CASCADE,
    jugador_id  INTEGER NOT NULL REFERENCES jugadores (id) ON DELETE CASCADE,
    equipo_id   INTEGER NOT NULL REFERENCES equipos (id) ON DELETE CASCADE,
    titular     BOOLEAN NOT NULL,
    orden       INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (partido_id, jugador_id)
);
CREATE INDEX alineaciones_equipo ON alineaciones (equipo_id, partido_id) WHERE titular;

-- Lo que pasa en el partido. El segundo jugador es quien asiste (GOL) o quien entra (CAMBIO).
-- El nombre y el dorsal son los de ese dia: no cambian si despues se edita el jugador.
CREATE TABLE eventos_partido (
    id                         SERIAL PRIMARY KEY,
    partido_id                 INTEGER      NOT NULL REFERENCES partidos (id) ON DELETE CASCADE,
    tipo                       VARCHAR(10)  NOT NULL CHECK (tipo IN ('GOL', 'AUTOGOL', 'AMARILLA', 'ROJA', 'CAMBIO')),
    orden                      INTEGER      NOT NULL,
    minuto                     INTEGER      CHECK (minuto BETWEEN 1 AND 130),
    equipo_id                  INTEGER      NOT NULL REFERENCES equipos (id),   -- al que suma el gol, en goles
    jugador_id                 INTEGER      NOT NULL REFERENCES jugadores (id), -- autor, sancionado o quien sale
    jugador_nombre             VARCHAR(200) NOT NULL DEFAULT '',
    jugador_dorsal             INTEGER      NOT NULL DEFAULT 0,
    segundo_jugador_id         INTEGER      REFERENCES jugadores (id),
    segundo_jugador_nombre     VARCHAR(200) NOT NULL DEFAULT '',
    segundo_jugador_dorsal     INTEGER      NOT NULL DEFAULT 0,
    fechas_suspension          INTEGER      NOT NULL DEFAULT 0 CHECK (fechas_suspension >= 0),
    CHECK (tipo <> 'CAMBIO' OR segundo_jugador_id IS NOT NULL),
    CHECK (tipo IN ('GOL', 'CAMBIO') OR segundo_jugador_id IS NULL)
);
CREATE INDEX eventos_partido_partido ON eventos_partido (partido_id);
CREATE INDEX eventos_partido_jugador ON eventos_partido (jugador_id, tipo);

-- ---------------------------------------------------------------- votacion y calificaciones
-- Un voto por jugador desde cada dispositivo. usuario_id queda listo para cuando haya login.
CREATE TABLE votos_publico (
    id           SERIAL PRIMARY KEY,
    partido_id   INTEGER     NOT NULL REFERENCES partidos (id) ON DELETE CASCADE,
    jugador_id   INTEGER     NOT NULL REFERENCES jugadores (id) ON DELETE CASCADE,
    usuario_id   INTEGER     REFERENCES usuarios (id) ON DELETE SET NULL,
    dispositivo  VARCHAR(64) NOT NULL,
    valor        INTEGER     NOT NULL CHECK (valor BETWEEN 1 AND 10),
    fecha        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (partido_id, jugador_id, dispositivo)
);

-- Ultima nota calculada por el programa (sistema + publico) de cada jugador.
CREATE TABLE calificaciones_partido (
    partido_id        INTEGER      NOT NULL REFERENCES partidos (id) ON DELETE CASCADE,
    jugador_id        INTEGER      NOT NULL REFERENCES jugadores (id) ON DELETE CASCADE,
    nota_sistema      NUMERIC(3,1) NOT NULL CHECK (nota_sistema BETWEEN 1 AND 10),
    votos             INTEGER      NOT NULL DEFAULT 0,
    promedio_publico  NUMERIC(3,1),
    nota_final        NUMERIC(3,1) NOT NULL CHECK (nota_final BETWEEN 1 AND 10),
    es_mvp            BOOLEAN      NOT NULL DEFAULT FALSE,
    actualizado_en    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (partido_id, jugador_id)
);
CREATE INDEX calificaciones_jugador ON calificaciones_partido (jugador_id);
CREATE UNIQUE INDEX calificaciones_un_mvp ON calificaciones_partido (partido_id) WHERE es_mvp;

-- ---------------------------------------------------------------- imagenes
-- Fotos de jugadores, escudos y fotos de noticias: el nombre es el que guardan jugadores.foto, equipos.escudo
-- y noticias.imagen_ruta. En la base (y no en datos/imagenes) para que las vean todos los computadores.
CREATE TABLE imagenes (
    nombre  VARCHAR(80) PRIMARY KEY,
    datos   BYTEA       NOT NULL,
    creada  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------- noticias
-- Cada noticia es de un campeonato (torneo_id); las de antes de los varios campeonatos pueden no tenerlo.
CREATE TABLE noticias (
    id           SERIAL PRIMARY KEY,
    torneo_id    INTEGER      REFERENCES torneos (id) ON DELETE CASCADE,
    titulo       VARCHAR(120) NOT NULL,
    cuerpo       VARCHAR(4000) NOT NULL,
    autor        VARCHAR(80)  NOT NULL,
    rol_autor    VARCHAR(10)  NOT NULL CHECK (rol_autor IN ('ADMIN', 'VISITANTE')),  -- ADMIN = organizador; VISITANTE = aficionado
    usuario_id   INTEGER      REFERENCES usuarios (id) ON DELETE SET NULL,
    fecha        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    imagen_ruta  VARCHAR(255),
    aprobado     BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX noticias_aprobado_fecha ON noticias (aprobado, fecha DESC, id DESC);
CREATE INDEX noticias_torneo ON noticias (torneo_id);
CREATE INDEX noticias_imagen ON noticias (imagen_ruta) WHERE imagen_ruta IS NOT NULL;

CREATE TABLE comentarios (
    id          SERIAL PRIMARY KEY,
    noticia_id  INTEGER      NOT NULL REFERENCES noticias (id) ON DELETE CASCADE,
    usuario_id  INTEGER      REFERENCES usuarios (id) ON DELETE SET NULL,
    autor       VARCHAR(120) NOT NULL,
    texto       TEXT         NOT NULL,
    fecha       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX comentarios_noticia ON comentarios (noticia_id, id);

-- ---------------------------------------------------------------- vistas para consultar
-- Tabla de posiciones por fase y grupo (orden: puntos, diferencia y goles a favor;
-- los demas criterios de desempate los aplica el programa).
CREATE VIEW v_tabla_posiciones AS
WITH resultados AS (
    SELECT p.torneo_id, p.fase_id, p.grupo, p.local_id AS equipo_id, p.goles_local AS gf, p.goles_visitante AS gc
      FROM partidos p WHERE p.estado = 'JUGADO'
    UNION ALL
    SELECT p.torneo_id, p.fase_id, p.grupo, p.visitante_id, p.goles_visitante, p.goles_local
      FROM partidos p WHERE p.estado = 'JUGADO'
)
SELECT t.id AS torneo_id, f.indice AS fase, f.nombre AS nombre_fase, r.grupo, e.id AS equipo_id, e.numero AS equipo_numero, e.nombre AS equipo,
       COUNT(*)                                        AS pj,
       COUNT(*) FILTER (WHERE r.gf > r.gc)             AS pg,
       COUNT(*) FILTER (WHERE r.gf = r.gc)             AS pe,
       COUNT(*) FILTER (WHERE r.gf < r.gc)             AS pp,
       SUM(r.gf)                                       AS gf,
       SUM(r.gc)                                       AS gc,
       SUM(r.gf - r.gc)                                AS dg,
       COUNT(*) FILTER (WHERE r.gf > r.gc) * t.puntos_victoria
         + COUNT(*) FILTER (WHERE r.gf = r.gc) * t.puntos_empate AS pts
  FROM resultados r
  JOIN torneos t ON t.id = r.torneo_id
  JOIN fases f   ON f.id = r.fase_id
  JOIN equipos e ON e.id = r.equipo_id
 GROUP BY t.id, f.indice, f.nombre, r.grupo, e.id;

-- Titulares del ultimo partido jugado (o en juego) con alineacion registrada de cada equipo.
CREATE VIEW v_ultima_alineacion AS
WITH ultimo AS (
    SELECT DISTINCT ON (a.equipo_id) a.equipo_id, p.id AS partido_id, p.numero AS partido_numero, p.fecha,
           CASE WHEN p.local_id = a.equipo_id THEN p.formacion_local ELSE p.formacion_visitante END AS formacion
      FROM alineaciones a
      JOIN partidos p ON p.id = a.partido_id
     WHERE a.titular AND p.estado IN ('JUGADO', 'EN_JUEGO')
     ORDER BY a.equipo_id, p.fecha DESC NULLS LAST, p.numero DESC
)
SELECT u.equipo_id, u.partido_id, u.partido_numero, u.fecha, u.formacion, j.id AS jugador_id, j.numero AS jugador_numero,
       j.dorsal, j.nombre, j.posicion, j.activo, a.orden
  FROM ultimo u
  JOIN alineaciones a ON a.partido_id = u.partido_id AND a.equipo_id = u.equipo_id AND a.titular
  JOIN jugadores j    ON j.id = a.jugador_id;

-- Partidos del campeonato en curso que el publico puede votar ahora.
CREATE VIEW v_votaciones_activas AS
SELECT p.id AS partido_id, p.torneo_id, p.numero AS partido_numero, l.nombre AS local, v.nombre AS visitante,
       p.goles_local, p.goles_visitante, p.horas_votacion,
       p.finalizado_en + make_interval(hours => p.horas_votacion) AS cierra_en,
       (SELECT COUNT(*) FROM votos_publico x WHERE x.partido_id = p.id) AS votos
  FROM partidos p
  JOIN torneos t ON t.id = p.torneo_id AND t.situacion = 'ACTUAL'
  JOIN equipos l ON l.id = p.local_id
  JOIN equipos v ON v.id = p.visitante_id
 WHERE p.estado = 'JUGADO'
   AND p.votacion_cerrada_en IS NULL
   AND p.finalizado_en <= CURRENT_TIMESTAMP
   AND p.finalizado_en + make_interval(hours => p.horas_votacion) > CURRENT_TIMESTAMP
   AND NOT p.walkover
   AND (EXISTS (SELECT 1 FROM alineaciones a WHERE a.partido_id = p.id AND a.titular)
        OR EXISTS (SELECT 1 FROM eventos_partido e WHERE e.partido_id = p.id));

-- Nota final de cada jugador por partido, lista para leer.
CREATE VIEW v_calificaciones AS
SELECT p.torneo_id, p.id AS partido_id, p.numero AS partido_numero, e.nombre AS equipo, j.numero AS jugador_numero,
       j.dorsal, j.nombre AS jugador, c.nota_sistema, c.promedio_publico, c.votos, c.nota_final, c.es_mvp
  FROM calificaciones_partido c
  JOIN partidos p  ON p.id = c.partido_id
  JOIN jugadores j ON j.id = c.jugador_id
  JOIN equipos e   ON e.id = j.equipo_id;

COMMIT;
