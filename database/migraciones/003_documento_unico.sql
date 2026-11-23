-- =====================================================================
--  Migracion 003 · un documento por jugador
--  En cada torneo, un numero de documento es de un solo jugador activo
--  (sin contar espacios ni mayusculas; sin documento se permite).
--  Necesita la migracion 002. Va en una transaccion y se puede repetir.
--  Si ya hay documentos repetidos no cambia nada y dice cuales son.
-- =====================================================================
BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'esquema_version')
       OR NOT EXISTS (SELECT 1 FROM esquema_version WHERE version >= 2) THEN
        RAISE EXCEPTION 'Ejecute antes database/migraciones/002_personas.sql.';
    END IF;
END $$;

DO $$
DECLARE
    repetidos TEXT;
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'jugadores_documento_unico'
               AND conrelid = (current_schema() || '.jugadores')::regclass) THEN
        RETURN; -- ya aplicada
    END IF;
    SELECT string_agg(t.nombre || ': documento ' || x.documento || ' (' || x.quienes || ')', '; ')
      INTO repetidos
      FROM (SELECT j.torneo_id, min(j.documento) AS documento,
                   string_agg('#' || j.dorsal || ' ' || j.nombre || ' [id ' || j.id || ']', ', ' ORDER BY j.dorsal) AS quienes
              FROM jugadores j
             WHERE j.activo AND regexp_replace(j.documento, '\s', '', 'g') <> ''
             GROUP BY j.torneo_id, upper(regexp_replace(j.documento, '\s', '', 'g'))
            HAVING COUNT(*) > 1) x
      JOIN torneos t ON t.id = x.torneo_id;
    IF repetidos IS NOT NULL THEN
        RAISE EXCEPTION 'Hay jugadores con el mismo documento: %. Corríjalos en pgAdmin (UPDATE jugadores SET documento = ''nuevo'' WHERE id = ...;) y vuelva a ejecutar esta migración.', repetidos;
    END IF;
    ALTER TABLE jugadores ADD CONSTRAINT jugadores_documento_unico
        EXCLUDE USING btree (torneo_id WITH =, (upper(regexp_replace(documento, '\s', '', 'g'))) WITH =)
        WHERE (activo AND regexp_replace(documento, '\s', '', 'g') <> '') DEFERRABLE INITIALLY DEFERRED;
END $$;

INSERT INTO esquema_version (version) VALUES (3) ON CONFLICT DO NOTHING;

COMMIT;
