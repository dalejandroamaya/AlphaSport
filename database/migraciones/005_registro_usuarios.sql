-- =====================================================================
--  Migracion 005 · registro de cuentas (Organizador y Aficionado)
--  Las cuentas se crean desde la pantalla «Registrarse» y se guardan en
--  la tabla usuarios. Cambia los roles ADMIN y VISITANTE por ORGANIZADOR
--  y AFICIONADO sin perder ninguna fila, y deja el usuario y el correo
--  en minusculas (asi UNIQUE no distingue mayusculas).
--  Necesita la migracion 004. Va en una transaccion y se puede repetir.
-- =====================================================================
BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'esquema_version')
       OR NOT EXISTS (SELECT 1 FROM esquema_version WHERE version >= 4) THEN
        RAISE EXCEPTION 'Ejecute antes database/migraciones/004_movimientos_division.sql.';
    END IF;
END $$;

-- 1. El rol: se quita la regla vieja, se agranda la columna ('ORGANIZADOR' tiene 11 letras) y se migran las filas.
ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS usuarios_rol_check;
ALTER TABLE usuarios ALTER COLUMN rol TYPE VARCHAR(12);
UPDATE usuarios SET rol = 'ORGANIZADOR' WHERE rol = 'ADMIN';
UPDATE usuarios SET rol = 'AFICIONADO' WHERE rol = 'VISITANTE';
ALTER TABLE usuarios ALTER COLUMN rol SET DEFAULT 'AFICIONADO';
ALTER TABLE usuarios ADD CONSTRAINT usuarios_rol_check CHECK (rol IN ('ORGANIZADOR', 'AFICIONADO'));

-- 2. Usuario y correo en minusculas: «Ana» y «ana» son la misma cuenta.
UPDATE usuarios SET username = LOWER(TRIM(username)) WHERE username <> LOWER(TRIM(username));
UPDATE usuarios SET email = LOWER(TRIM(email)) WHERE email <> LOWER(TRIM(email));
ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS usuarios_minusculas;
ALTER TABLE usuarios ADD CONSTRAINT usuarios_minusculas
    CHECK (username = LOWER(username) AND (email IS NULL OR email = LOWER(email)));

-- 3. Version 5 (solo si no estaba).
INSERT INTO esquema_version (version)
SELECT 5 WHERE NOT EXISTS (SELECT 1 FROM esquema_version WHERE version = 5);

COMMIT;
