-- =====================================================================
--  Migracion 007 · imagenes en la base
--  Las fotos de los jugadores, los escudos y las fotos de las noticias
--  pasan de la carpeta datos/imagenes de cada computador a la tabla
--  imagenes: asi las ven todos los que usan la misma base (por ejemplo
--  una base en internet como Neon). El programa sube solo las imagenes
--  que ya estaban en datos/imagenes la primera vez que se abre.
--  Va despues de la 006. En una transaccion y se puede repetir.
-- =====================================================================
BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM esquema_version WHERE version >= 6) THEN
        RAISE EXCEPTION 'Ejecute antes database/migraciones/006_varios_campeonatos.sql.';
    END IF;
END $$;

-- nombre: el mismo que guardan jugadores.foto, equipos.escudo y noticias.imagen_ruta (jugador-…, escudo-…, noticia-…).
CREATE TABLE IF NOT EXISTS imagenes (
    nombre  VARCHAR(80) PRIMARY KEY,
    datos   BYTEA       NOT NULL,
    creada  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO esquema_version (version)
SELECT 7 WHERE NOT EXISTS (SELECT 1 FROM esquema_version WHERE version = 7);

COMMIT;
