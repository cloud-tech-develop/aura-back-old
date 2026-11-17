-- V241 — Proyecto sin frentes: frente "General" automático.
--
-- La asistencia, los trabajadores y la nómina por obra cuelgan de un frente
-- (frente_id NOT NULL). Hay empresas que no dividen el proyecto en frentes: para
-- ellas cada proyecto tiene un frente GENERAL que representa "todo el proyecto".
-- El front lo usa solo cuando el proyecto no tiene otros frentes, así que el
-- usuario trabaja por proyecto sin ver frentes. Nada del motor cambia.
--
-- Idempotente: crea el GENERAL solo a los proyectos sin ningún frente vivo.

ALTER TABLE proyecto_frente ADD COLUMN IF NOT EXISTS es_general BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO proyecto_frente (empresa_id, proyecto_id, codigo, nombre, descripcion, estado, es_general,
       created_at, updated_at)
SELECT p.empresa_id, p.id, 'GENERAL', 'General (todo el proyecto)',
       'Frente creado por el sistema para registrar el proyecto completo, sin dividirlo en frentes.',
       'ACTIVO', TRUE, now(), now()
  FROM proyecto p
 WHERE p.deleted_at IS NULL
   AND NOT EXISTS (SELECT 1 FROM proyecto_frente f WHERE f.proyecto_id = p.id AND f.deleted_at IS NULL);

-- Un solo GENERAL vivo por proyecto.
CREATE UNIQUE INDEX IF NOT EXISTS ux_frente_general_proyecto
    ON proyecto_frente (proyecto_id) WHERE es_general AND deleted_at IS NULL;
