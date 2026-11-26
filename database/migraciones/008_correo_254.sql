-- =====================================================================
--  Migracion 008 · correos de hasta 254 caracteres
--  El registro acepta correos de hasta 254 caracteres (el maximo de un
--  correo); la columna email tenia 120. Solo la ensancha: no cambia ni
--  borra ningun dato, y el programa sigue funcionando sin ella (solo
--  fallaria al guardar un correo de mas de 120 caracteres).
--  Va despues de la 007. En una transaccion y se puede repetir.
-- =====================================================================
BEGIN;

ALTER TABLE usuarios ALTER COLUMN email TYPE VARCHAR(254);

INSERT INTO esquema_version (version)
SELECT 8 WHERE NOT EXISTS (SELECT 1 FROM esquema_version WHERE version = 8);

COMMIT;
