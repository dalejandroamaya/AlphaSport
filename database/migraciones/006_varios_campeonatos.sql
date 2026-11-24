-- =====================================================================
--  Migracion 006 · varios campeonatos en curso
--  Cada Organizador puede tener varios campeonatos activos a la vez:
--  - se quita el indice que permitia un solo campeonato ACTUAL;
--  - cada torneo guarda su dueno (propietario_id, llave foranea a usuarios);
--  - cada noticia pertenece a un campeonato (torneo_id).
--  Va despues de la 005. SQL basico (ALTER, UPDATE e INSERT con SELECT),
--  en una transaccion y se puede repetir sin cambiar nada.
-- =====================================================================
BEGIN;

-- 1. Noticias por campeonato. Se rellenan ANTES de quitar el indice: hasta aqui hay como mucho un ACTUAL.
--    Si no hay exactamente uno (base vacia o ya migrada) no se toca ninguna noticia.
ALTER TABLE noticias ADD COLUMN IF NOT EXISTS torneo_id INTEGER REFERENCES torneos (id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS noticias_torneo ON noticias (torneo_id);
UPDATE noticias
   SET torneo_id = (SELECT MIN(id) FROM torneos WHERE situacion = 'ACTUAL')
 WHERE torneo_id IS NULL
   AND (SELECT COUNT(*) FROM torneos WHERE situacion = 'ACTUAL') = 1;

-- 2. Varios campeonatos en curso.
DROP INDEX IF EXISTS torneos_un_actual;

-- 3. Dueno de cada torneo. Si al borrar la cuenta se borra el dueno, el torneo queda sin organizador.
ALTER TABLE torneos ADD COLUMN IF NOT EXISTS propietario_id INTEGER REFERENCES usuarios (id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS torneos_propietario ON torneos (propietario_id);

-- 4. Los torneos sin dueno pasan al Organizador mas antiguo. Si todavia no hay ninguno, quedan sin dueno:
--    el primer Organizador que los «tome» desde la aplicacion se queda con ellos.
UPDATE torneos
   SET propietario_id = (SELECT MIN(id) FROM usuarios WHERE rol = 'ORGANIZADOR')
 WHERE propietario_id IS NULL;

-- 5. Version 6 (solo si no estaba).
INSERT INTO esquema_version (version)
SELECT 6 WHERE NOT EXISTS (SELECT 1 FROM esquema_version WHERE version = 6);

COMMIT;
