-- ╔════════════════════════════════════════════════════════════════════╗
-- ║  Menú: Nube de documentos (docs/PLAN_NUBE_DOCUMENTOS.md)            ║
-- ║                                                                      ║
-- ║  Submódulo principal.documentos (igual que el `codigo` en            ║
-- ║  sidebar.config.ts, PermisoRutas y NubeServiceImpl.CLAVE).           ║
-- ║  Requiere V236. Idempotente: se puede correr varias veces.           ║
-- ╚════════════════════════════════════════════════════════════════════╝

-- 1) Catálogo: el submódulo bajo Principal.
INSERT INTO submodulos (modulo_id, nombre, codigo, descripcion, activo, orden, created_at, updated_at)
SELECT m.id, 'Documentos', 'documentos',
       'Nube de documentos de la empresa: carpetas y archivos de máximo 1 MB, compartidos con su firma contable',
       true, COALESCE((SELECT MAX(orden) FROM submodulos WHERE modulo_id = m.id), 0) + 1, NOW(), NOW()
FROM modulos m
WHERE m.codigo = 'principal'
  AND NOT EXISTS (SELECT 1 FROM submodulos s WHERE s.modulo_id = m.id AND s.codigo = 'documentos');

-- 2) Activación: para toda empresa con el módulo Principal activo.
INSERT INTO empresa_submodulo (empresa_id, submodulo_id, activo, created_at, updated_at)
SELECT em.empresa_id, s.id, true, NOW(), NOW()
FROM modulos m
JOIN submodulos s ON s.modulo_id = m.id AND s.codigo = 'documentos'
JOIN empresa_modulo em ON em.modulo_id = m.id AND em.activo = true
WHERE m.codigo = 'principal'
  AND NOT EXISTS (SELECT 1 FROM empresa_submodulo es WHERE es.empresa_id = em.empresa_id AND es.submodulo_id = s.id);

-- 3) Perfil "Contador externo" (firma contable en sus clientes): ver, subir, editar y
--    papelera. Los perfiles con acceso total (Administrador) ya lo tienen. Los demás
--    perfiles NO reciben nada: el administrador de cada empresa decide quién entra.
INSERT INTO perfil_permiso (perfil_id, submodulo_id, ver, crear, editar, anular)
SELECT p.id, s.id, true, true, true, true
FROM perfil p
JOIN modulos m ON m.codigo = 'principal'
JOIN submodulos s ON s.modulo_id = m.id AND s.codigo = 'documentos'
WHERE p.codigo = 'CONTADOR_EXTERNO'
  AND COALESCE(p.acceso_total, false) = false
ON CONFLICT (perfil_id, submodulo_id) DO NOTHING;
