-- ╔════════════════════════════════════════════════════════════════════╗
-- ║  Menú: catálogos de nómina (rediseño R1, V209)                      ║
-- ║                                                                      ║
-- ║  Cargos, tipos de vinculación y los catálogos simples (grupos de     ║
-- ║  nómina, niveles y tipos de cargo, tipos de embargo, motivos de      ║
-- ║  retiro y de ausentismo, riesgos ARL), cada uno como submódulo       ║
-- ║  aparte de Recursos Humanos para darle su propio permiso.            ║
-- ║                                                                      ║
-- ║  Clave del permiso = modulo.codigo || '.' || submodulo.codigo        ║
-- ║  (recursos-humanos.cargos, recursos-humanos.tipos-embargo, …), igual ║
-- ║  que el `codigo` del ítem en sidebar.config.ts y PermisoRutas.       ║
-- ║                                                                      ║
-- ║  El perfil Administrador las ve solo (acceso total); a otros perfiles║
-- ║  se las da el administrador desde Perfiles.                          ║
-- ║                                                                      ║
-- ║  Idempotente: se puede correr varias veces sin duplicar.             ║
-- ╚════════════════════════════════════════════════════════════════════╝

-- 1) Catálogo: crea los submódulos bajo Recursos Humanos (si no existen).
INSERT INTO submodulos (modulo_id, nombre, codigo, descripcion, activo, orden, created_at, updated_at)
SELECT m.id, v.nombre, v.codigo, v.descripcion, true,
       COALESCE((SELECT MAX(orden) FROM submodulos WHERE modulo_id = m.id), 0) + v.orden,
       NOW(), NOW()
FROM modulos m
JOIN (VALUES
    ('Cargos',                'cargos',             'Cargos con nivel, tipo, plazas y rango salarial', 1),
    ('Tipos de vinculación',  'tipos-vinculacion',  'Planta, fijo, aprendiz, jornal: contrato y frecuencia por defecto', 2),
    ('Grupos de nómina',      'grupos-nomina',      'Empleados que se liquidan juntos', 3),
    ('Niveles de cargo',      'niveles-cargo',      'Jerarquía de los cargos', 4),
    ('Tipos de cargo',        'tipos-cargo',        'Planta, temporal, por proyecto', 5),
    ('Tipos de embargo',      'tipos-embargo',      'Clases de embargo con su prioridad y límite legal', 6),
    ('Motivos de retiro',     'motivos-retiro',     'Causas de terminación y si generan indemnización', 7),
    ('Motivos de ausentismo', 'motivos-ausentismo', 'Incapacidades, licencias y permisos', 8),
    ('Riesgos ARL',           'riesgos-arl',        'Clases de riesgo y su tarifa', 9)
) AS v(nombre, codigo, descripcion, orden) ON TRUE
WHERE m.codigo = 'recursos-humanos'
  AND NOT EXISTS (
      SELECT 1 FROM submodulos s
      WHERE s.modulo_id = m.id AND s.codigo = v.codigo
  );

-- 2) Activación por empresa: los enciende para toda empresa que tenga
--    activo el módulo Recursos Humanos.
INSERT INTO empresa_submodulo (empresa_id, submodulo_id, activo, created_at, updated_at)
SELECT em.empresa_id, s.id, true, NOW(), NOW()
FROM modulos m
JOIN submodulos s ON s.modulo_id = m.id
JOIN empresa_modulo em ON em.modulo_id = m.id AND em.activo = true
WHERE m.codigo = 'recursos-humanos'
  AND s.codigo IN ('cargos', 'tipos-vinculacion', 'grupos-nomina', 'niveles-cargo', 'tipos-cargo',
                   'tipos-embargo', 'motivos-retiro', 'motivos-ausentismo', 'riesgos-arl')
  AND NOT EXISTS (
      SELECT 1 FROM empresa_submodulo es
      WHERE es.empresa_id = em.empresa_id AND es.submodulo_id = s.id
  );

-- Verificación:
-- SELECT m.codigo || '.' || s.codigo AS clave, COUNT(es.*) AS empresas
-- FROM modulos m
-- JOIN submodulos s ON s.modulo_id = m.id
-- LEFT JOIN empresa_submodulo es ON es.submodulo_id = s.id AND es.activo
-- WHERE m.codigo = 'recursos-humanos'
-- GROUP BY 1 ORDER BY 1;
