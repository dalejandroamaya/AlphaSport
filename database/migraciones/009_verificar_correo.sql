-- =====================================================================
--  Migracion 009 · Mi perfil: verificar el correo
--  Agrega a usuarios si el correo esta verificado y el codigo de
--  verificacion pendiente: su hash (nunca el codigo), cuando vence
--  (15 minutos) y los intentos fallidos (maximo 5). Las cuentas que
--  ya existen quedan con el correo sin verificar.
--  Va despues de la 008. Solo ALTER TABLE ... ADD COLUMN: en una
--  transaccion y se puede repetir (IF NOT EXISTS).
-- =====================================================================
BEGIN;

ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS correo_verificado BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS codigo_verificacion VARCHAR(255);
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS codigo_vence TIMESTAMP;
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS codigo_intentos INTEGER NOT NULL DEFAULT 0;

INSERT INTO esquema_version (version)
SELECT 9 WHERE NOT EXISTS (SELECT 1 FROM esquema_version WHERE version = 9);

COMMIT;
