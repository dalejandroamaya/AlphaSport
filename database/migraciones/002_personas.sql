-- =====================================================================
--  Migracion 002 · tabla personas
--  Cada jugador apunta a la persona que es, la misma en todas las
--  temporadas y copias del historial. Necesita la migracion 001.
--  Va en una transaccion y se puede repetir: si ya se aplico no cambia nada.
-- =====================================================================
BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'esquema_version')
       OR NOT EXISTS (SELECT 1 FROM esquema_version WHERE version >= 1) THEN
        RAISE EXCEPTION 'Ejecute antes database/migraciones/001_walkover.sql.';
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS personas (
    id         SERIAL PRIMARY KEY,
    codigo     UUID         NOT NULL UNIQUE,
    nombre     VARCHAR(200) NOT NULL,
    documento  VARCHAR(60)  NOT NULL DEFAULT '',
    creado_en  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE jugadores ADD COLUMN IF NOT EXISTS persona_id INTEGER REFERENCES personas (id);

-- Una persona por jugador de cada campeonato. El campeonato en curso y sus copias del historial
-- comparten el codigo del torneo y el numero del jugador: son la misma persona.
-- El nombre y el documento se toman del campeonato en curso, o de la copia mas reciente.
CREATE TEMP TABLE personas_nuevas ON COMMIT DROP AS
SELECT DISTINCT ON (t.codigo, j.numero) t.codigo AS torneo, j.numero, gen_random_uuid() AS codigo, j.nombre, j.documento
  FROM jugadores j
  JOIN torneos t ON t.id = j.torneo_id
 WHERE j.persona_id IS NULL
 ORDER BY t.codigo, j.numero, t.situacion = 'ACTUAL' DESC, j.id DESC;

INSERT INTO personas (codigo, nombre, documento)
SELECT codigo, nombre, documento FROM personas_nuevas;

UPDATE jugadores j
   SET persona_id = pe.id
  FROM torneos t, personas_nuevas n, personas pe
 WHERE t.id = j.torneo_id
   AND n.torneo = t.codigo AND n.numero = j.numero
   AND pe.codigo = n.codigo
   AND j.persona_id IS NULL;

ALTER TABLE jugadores ALTER COLUMN persona_id SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS jugadores_persona_unica ON jugadores (torneo_id, persona_id);
CREATE INDEX IF NOT EXISTS jugadores_persona ON jugadores (persona_id);

INSERT INTO esquema_version (version) VALUES (2) ON CONFLICT DO NOTHING;

COMMIT;
