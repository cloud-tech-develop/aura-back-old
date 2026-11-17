-- ╔════════════════════════════════════════════════════════════════════╗
-- ║  Menú: Servicio técnico (docs/PLAN_SERVICIO_TECNICO.md)              ║
-- ║                                                                      ║
-- ║  Módulo servicio-tecnico con submódulos ordenes y configuracion      ║
-- ║  (igual que el `codigo` en sidebar.config.ts y PermisoRutas).        ║
-- ║  Requiere V249. NO lo activa a ninguna empresa: se activa desde la   ║
-- ║  plataforma a la empresa que lo contrate (paso 3 es opcional).       ║
-- ║  Idempotente: se puede correr varias veces.                          ║
-- ╚════════════════════════════════════════════════════════════════════╝

-- 1) Módulo (los ids de modulos son fijos en el catálogo: se toma el siguiente).
INSERT INTO modulos (id, nombre, codigo, descripcion, activo, orden, created_at, updated_at)
SELECT (SELECT COALESCE(MAX(id), 0) + 1 FROM modulos), 'Servicio Técnico', 'servicio-tecnico',
       'Órdenes de reparación de celulares, tablets y equipos: diagnóstico, repuestos, estado y fotos',
       true, (SELECT COALESCE(MAX(orden), 0) + 1 FROM modulos), NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM modulos WHERE codigo = 'servicio-tecnico');

SELECT setval(pg_get_serial_sequence('modulos', 'id'), (SELECT MAX(id) FROM modulos))
WHERE pg_get_serial_sequence('modulos', 'id') IS NOT NULL;

-- 2) Submódulos.
INSERT INTO submodulos (modulo_id, nombre, codigo, descripcion, activo, orden, created_at, updated_at)
SELECT m.id, v.nombre, v.codigo, v.descripcion, true, v.orden, NOW(), NOW()
FROM modulos m
JOIN (VALUES
    ('Órdenes de servicio', 'ordenes',       'Recepción del equipo, diagnóstico, repuestos, estados y fotos', 1),
    ('Configuración',       'configuracion', 'Consecutivo, garantía y condiciones de la orden', 2)
) AS v(nombre, codigo, descripcion, orden) ON true
WHERE m.codigo = 'servicio-tecnico'
  AND NOT EXISTS (SELECT 1 FROM submodulos s WHERE s.modulo_id = m.id AND s.codigo = v.codigo);

-- 3) (Opcional) Activarlo a UNA empresa sin pasar por la plataforma: cambie el id.
-- INSERT INTO empresa_modulo (empresa_id, modulo_id, activo, created_at, updated_at)
-- SELECT 1, m.id, true, NOW(), NOW() FROM modulos m
-- WHERE m.codigo = 'servicio-tecnico'
--   AND NOT EXISTS (SELECT 1 FROM empresa_modulo em WHERE em.empresa_id = 1 AND em.modulo_id = m.id);
-- INSERT INTO empresa_submodulo (empresa_id, submodulo_id, activo, created_at, updated_at)
-- SELECT 1, s.id, true, NOW(), NOW() FROM submodulos s JOIN modulos m ON m.id = s.modulo_id
-- WHERE m.codigo = 'servicio-tecnico'
--   AND NOT EXISTS (SELECT 1 FROM empresa_submodulo es WHERE es.empresa_id = 1 AND es.submodulo_id = s.id);
