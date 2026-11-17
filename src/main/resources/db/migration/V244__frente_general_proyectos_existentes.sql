-- V244 — Frente General también en los proyectos que ya tenían frentes.
--
-- V241 creó el frente General solo en los proyectos SIN frentes. Una empresa que
-- luego elige "no usa frentes" (V243) quedaba con proyectos sin General: la
-- digitación seguía pidiendo frente. Aquí:
--   1) todo proyecto queda con su General;
--   2) en las empresas que ya trabajan sin frentes, el General recibe los
--      trabajadores activos y el turno de los frentes del proyecto.
-- Lo mismo hace el sistema al guardar "no usa frentes" en la configuración.
-- Idempotente; no quita nada de los frentes.

INSERT INTO proyecto_frente (empresa_id, proyecto_id, codigo, nombre, descripcion, estado, es_general,
       created_at, updated_at)
SELECT p.empresa_id, p.id, 'GENERAL', 'General (todo el proyecto)',
       'Frente creado por el sistema para registrar el proyecto completo, sin dividirlo en frentes.',
       'ACTIVO', TRUE, now(), now()
  FROM proyecto p
 WHERE p.deleted_at IS NULL
   AND NOT EXISTS (SELECT 1 FROM proyecto_frente f
                    WHERE f.proyecto_id = p.id AND f.es_general AND f.deleted_at IS NULL);

INSERT INTO proyecto_frente_trabajador (empresa_id, proyecto_id, frente_id, empleado_id, fecha_inicio,
       estado, observacion, created_at, updated_at)
SELECT DISTINCT ON (g.id, t.empleado_id) t.empresa_id, t.proyecto_id, g.id, t.empleado_id, t.fecha_inicio,
       'ACTIVO', 'Pasado al proyecto completo al dejar de usar frentes.', now(), now()
  FROM proyecto_frente_trabajador t
  JOIN proyecto_frente f ON f.id = t.frente_id AND f.deleted_at IS NULL AND NOT f.es_general
  JOIN proyecto_frente g ON g.proyecto_id = t.proyecto_id AND g.es_general AND g.deleted_at IS NULL
  JOIN nomina_config c ON c.empresa_id = t.empresa_id AND NOT c.asistencia_usa_frentes
 WHERE t.estado = 'ACTIVO' AND t.deleted_at IS NULL
   AND NOT EXISTS (SELECT 1 FROM proyecto_frente_trabajador x
                    WHERE x.frente_id = g.id AND x.empleado_id = t.empleado_id
                      AND x.estado = 'ACTIVO' AND x.deleted_at IS NULL)
 ORDER BY g.id, t.empleado_id, t.fecha_inicio NULLS LAST;

INSERT INTO frente_turno (empresa_id, proyecto_id, frente_id, turno_id, fecha_inicio, fecha_fin,
       created_at, updated_at)
SELECT DISTINCT ON (g.id) ft.empresa_id, ft.proyecto_id, g.id, ft.turno_id, ft.fecha_inicio, ft.fecha_fin,
       now(), now()
  FROM frente_turno ft
  JOIN proyecto_frente f ON f.id = ft.frente_id AND f.deleted_at IS NULL AND NOT f.es_general
  JOIN proyecto_frente g ON g.proyecto_id = ft.proyecto_id AND g.es_general AND g.deleted_at IS NULL
  JOIN nomina_config c ON c.empresa_id = ft.empresa_id AND NOT c.asistencia_usa_frentes
 WHERE ft.deleted_at IS NULL
   AND NOT EXISTS (SELECT 1 FROM frente_turno x WHERE x.frente_id = g.id AND x.deleted_at IS NULL)
 ORDER BY g.id, ft.fecha_inicio DESC NULLS LAST, ft.id DESC;
