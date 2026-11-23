-- =====================================================================
--  AlphaSport · datos iniciales de prueba
--  Ejecutar despues de schema.sql. Crea un torneo configurado (liga sin
--  iniciar) con 6 equipos, sus tecnicos y 16 jugadores por equipo, dos
--  noticias y los usuarios de demostracion.
--  Si ya hay un campeonato en curso no hace nada y avisa.
-- =====================================================================
BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM torneos WHERE situacion = 'ACTUAL') THEN
        RAISE EXCEPTION 'Ya hay un campeonato en curso: seed.sql no toca datos existentes.';
    END IF;
END $$;

-- ---------------------------------------------------------------- torneo
INSERT INTO torneos (codigo, situacion, nombre, formato, siguiente_equipo, siguiente_jugador, siguiente_partido,
                     puntos_victoria, puntos_empate, ida_y_vuelta, cantidad_divisiones, semilla)
VALUES ('seed0001', 'ACTUAL', 'Liga AlphaSport de prueba', 'LIGA', 7, 97, 1, 3, 1, FALSE, 1, 4242);

INSERT INTO torneo_criterios (torneo_id, orden, criterio)
SELECT t.id, c.orden, c.criterio
  FROM torneos t,
       (VALUES (0, 'DIFERENCIA_GOLES'), (1, 'GOLES_A_FAVOR'), (2, 'ENFRENTAMIENTO_DIRECTO'), (3, 'FAIR_PLAY'), (4, 'SORTEO')) AS c (orden, criterio)
 WHERE t.codigo = 'seed0001' AND t.situacion = 'ACTUAL';

INSERT INTO divisiones (torneo_id, indice, nombre)
SELECT id, 0, 'Liga A' FROM torneos WHERE codigo = 'seed0001' AND situacion = 'ACTUAL';

-- ---------------------------------------------------------------- equipos y directores tecnicos
INSERT INTO equipos (torneo_id, numero, nombre, estadio, division_id, orden)
SELECT t.id, e.numero, e.nombre, e.estadio, d.id, e.numero - 1
  FROM torneos t
  JOIN divisiones d ON d.torneo_id = t.id AND d.indice = 0,
       (VALUES (1, 'Pumas del Sur', 'Estadio Los Andes'),
               (2, 'Atlético Frontera', 'Estadio La Frontera'),
               (3, 'Deportivo Laguna', 'Estadio El Mirador'),
               (4, 'Real Cordillera', 'Estadio La Cumbre'),
               (5, 'Unión Capital', 'Estadio Central'),
               (6, 'Tiburones FC', 'Estadio del Puerto')) AS e (numero, nombre, estadio)
 WHERE t.codigo = 'seed0001' AND t.situacion = 'ACTUAL';

INSERT INTO directores_tecnicos (equipo_id, nombre, documento, nacionalidad)
SELECT e.id, d.nombre, d.documento, 'Colombia'
  FROM equipos e
  JOIN torneos t ON t.id = e.torneo_id AND t.codigo = 'seed0001' AND t.situacion = 'ACTUAL'
  JOIN (VALUES (1, 'Hernán Rojas', 'DT-001'), (2, 'Óscar Medina', 'DT-002'), (3, 'Julián Prada', 'DT-003'),
               (4, 'Mauricio Cano', 'DT-004'), (5, 'Andrés Villa', 'DT-005'), (6, 'Fabio Suárez', 'DT-006'))
       AS d (numero, nombre, documento) ON d.numero = e.numero;

-- ---------------------------------------------------------------- jugadores
-- 16 por equipo: el dorsal 10 es el capitan. Cada uno es una persona nueva (el documento J0001... la identifica aqui).
CREATE TEMP TABLE semilla_jugadores ON COMMIT DROP AS
SELECT e.torneo_id, e.id AS equipo_id, (e.numero - 1) * 16 + p.dorsal AS numero,
       (ARRAY['Mateo','Samuel','Nicolás','Tomás','Daniel','Felipe','Andrés','Santiago','Esteban','Camilo','Julián','Sergio','Iván','Óscar','Kevin','David'])[1 + (p.dorsal * 5 + e.numero * 3) % 16]
         || ' ' ||
       (ARRAY['Rojas','Gómez','Castro','Medina','Vargas','Ortiz','Salazar','Peña','Cardona','Ríos','Mejía','Torres','Parra','Lozano','Cruz','Molina','Arias'])[1 + (p.dorsal * 7 + e.numero * 4) % 17] AS nombre,
       'J' || lpad(((e.numero - 1) * 16 + p.dorsal)::text, 4, '0') AS documento,
       p.dorsal, p.posicion, p.dorsal = 10 AS es_capitan, p.dorsal - 1 AS orden
  FROM equipos e
  JOIN torneos t ON t.id = e.torneo_id AND t.codigo = 'seed0001' AND t.situacion = 'ACTUAL',
       (VALUES (1, 'Portero'), (2, 'Lateral derecho'), (3, 'Defensa central'), (4, 'Defensa central'), (5, 'Lateral izquierdo'),
               (6, 'Mediocampista defensivo'), (7, 'Extremo derecho'), (8, 'Mediocampista central'), (9, 'Delantero centro'),
               (10, 'Mediocampista ofensivo'), (11, 'Extremo izquierdo'), (12, 'Portero'), (13, 'Defensa central'),
               (14, 'Interior derecho'), (15, 'Volante izquierdo'), (16, 'Segundo delantero')) AS p (dorsal, posicion);

WITH nuevas AS (
    INSERT INTO personas (codigo, nombre, documento)
    SELECT gen_random_uuid(), nombre, documento FROM semilla_jugadores
    RETURNING id, documento
)
INSERT INTO jugadores (torneo_id, equipo_id, persona_id, numero, nombre, documento, dorsal, posicion, es_capitan, orden)
SELECT s.torneo_id, s.equipo_id, n.id, s.numero, s.nombre, s.documento, s.dorsal, s.posicion, s.es_capitan, s.orden
  FROM semilla_jugadores s JOIN nuevas n ON n.documento = s.documento;

-- ---------------------------------------------------------------- noticias
INSERT INTO noticias (titulo, cuerpo, autor, rol_autor, fecha, aprobado)
VALUES ('¡Arranca la Liga AlphaSport!', 'Seis equipos buscan el título. Consulte el calendario y las plantillas en la aplicación.',
        'Administración', 'ADMIN', CURRENT_TIMESTAMP, TRUE),
       ('Los Pumas ya entrenan', 'El equipo del sur hizo su primera práctica abierta al público este fin de semana.',
        'Camila', 'VISITANTE', CURRENT_TIMESTAMP, FALSE);

INSERT INTO comentarios (noticia_id, autor, texto)
SELECT id, 'Andrés', '¡Qué buena noticia!' FROM noticias WHERE titulo = '¡Arranca la Liga AlphaSport!';

-- ---------------------------------------------------------------- usuarios de demostracion
-- Claves de demostracion del prototipo (las del README), guardadas como hash PBKDF2-SHA256: el mismo formato
-- que valida AutenticacionService (ClavesPbkdf2). Para otra clave: java -cp bin alphasport.service.ClavesPbkdf2 <clave>
INSERT INTO usuarios (nombre, username, email, password_hash, rol)
VALUES ('Administrador', 'admin', 'admin@alphasport.local', 'pbkdf2_sha256$600000$ORYWJGZO2MLaUsJJD1StqA$eJmZnrPDP4/FNdM4k5l/HEFmhFxS4qPKtrYueWmiJB8', 'ADMIN'),
       ('Visitante', 'visitante', 'visitante@alphasport.local', 'pbkdf2_sha256$600000$sleF8gem07Djtx8KO31+QQ$YrnucN8AoK6aG/heRS6AhI4oRY2WAGF7eHy89aGrAc4', 'VISITANTE')
ON CONFLICT (username) DO NOTHING;

COMMIT;
