-- =====================================================================
--  Migracion 004 · ascensos y descensos de cada temporada
--  Constancia de por que cada equipo empieza la temporada en su
--  division: de cual venia, a cual paso y en que puesto termino.
--  Necesita la migracion 003. Va en una transaccion y se puede repetir.
-- =====================================================================
BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'esquema_version')
       OR NOT EXISTS (SELECT 1 FROM esquema_version WHERE version >= 3) THEN
        RAISE EXCEPTION 'Ejecute antes database/migraciones/003_documento_unico.sql.';
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS movimientos_division (
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

INSERT INTO esquema_version (version) VALUES (4) ON CONFLICT DO NOTHING;

COMMIT;
