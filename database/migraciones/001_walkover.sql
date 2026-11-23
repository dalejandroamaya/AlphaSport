-- =====================================================================
--  Migracion 001 · campo walkover en partidos
--  Para una base creada antes de existir la tabla esquema_version.
--  Va en una transaccion y se puede repetir: si ya se aplico no cambia nada.
-- =====================================================================
BEGIN;

CREATE TABLE IF NOT EXISTS esquema_version (
    version      INTEGER PRIMARY KEY,
    aplicada_en  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE partidos ADD COLUMN IF NOT EXISTS walkover BOOLEAN NOT NULL DEFAULT FALSE;

-- Los W.O. anteriores solo se reconocian por el texto que escribia el programa en las observaciones.
UPDATE partidos p SET walkover = TRUE
 WHERE NOT EXISTS (SELECT 1 FROM esquema_version WHERE version >= 1)
   AND p.estado = 'JUGADO'
   AND (p.observaciones LIKE 'W.O. a favor de %' OR p.observaciones LIKE 'W.O. por retiro de %')
   AND GREATEST(p.goles_local, p.goles_visitante) = 3 AND LEAST(p.goles_local, p.goles_visitante) = 0
   AND NOT EXISTS (SELECT 1 FROM eventos_partido e WHERE e.partido_id = p.id);

-- La vista deja de leer el texto.
CREATE OR REPLACE VIEW v_votaciones_activas AS
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

INSERT INTO esquema_version (version) VALUES (1) ON CONFLICT DO NOTHING;

COMMIT;
