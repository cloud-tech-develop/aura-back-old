-- ╔════════════════════════════════════════════════════════════════════╗
-- ║  URGENTE: el módulo Caja desapareció del menú tras subir permisos     ║
-- ║                                                                      ║
-- ║  Causa: con permisos por perfil (V190–V192) el menú compara la clave  ║
-- ║  COMPLETA "modulo.submodulo" (ej. "caja.turnos"). Antes solo miraba   ║
-- ║  el submódulo y además lo normalizaba, así que daba igual cómo        ║
-- ║  estuviera escrito el código del MÓDULO. En producción el código del  ║
-- ║  módulo Caja no es exactamente 'caja' (mayúscula, tilde o espacios;   ║
-- ║  los SQL viejos ya lo buscaban con LOWER(TRANSLATE(...))). Resultado: ║
-- ║    - el back arma "Caja.turnos" y el front busca "caja.turnos";      ║
-- ║    - V190/V191/V192 filtraban con m.codigo = 'caja' y se saltaron     ║
-- ║      en silencio: Cajero sin turnos/comprobantes, sin "Perfiles y     ║
-- ║      Permisos", sin "Bitácora" y sin la acción CERRAR_SESIONES.       ║
-- ║                                                                      ║
-- ║  Solo datos: no hay que redesplegar. Idempotente.                     ║
-- ║  Después de correrlo los usuarios recargan (F5) o vuelven a entrar.  ║
-- ╚════════════════════════════════════════════════════════════════════╝

-- ── 0. DIAGNÓSTICO (correr primero y mirar) ──────────────────────────────────
-- Módulos y submódulos cuyo código no está normalizado (minúsculas, sin tildes,
-- espacios → guiones). Si aparece "Caja" aquí, esta es la causa.
SELECT 'modulo' AS tipo, m.id, m.codigo AS codigo_actual,
       regexp_replace(lower(translate(trim(m.codigo), 'áéíóúÁÉÍÓÚñÑ', 'aeiouAEIOUnN')), '\s+', '-', 'g') AS codigo_esperado
FROM modulos m
WHERE m.codigo IS DISTINCT FROM regexp_replace(lower(translate(trim(m.codigo), 'áéíóúÁÉÍÓÚñÑ', 'aeiouAEIOUnN')), '\s+', '-', 'g')
UNION ALL
SELECT 'submodulo', s.id, m.codigo || '.' || s.codigo,
       regexp_replace(lower(translate(trim(s.codigo), 'áéíóúÁÉÍÓÚñÑ', 'aeiouAEIOUnN')), '\s+', '-', 'g')
FROM submodulos s JOIN modulos m ON m.id = s.modulo_id
WHERE s.codigo IS DISTINCT FROM regexp_replace(lower(translate(trim(s.codigo), 'áéíóúÁÉÍÓÚñÑ', 'aeiouAEIOUnN')), '\s+', '-', 'g');

-- Cómo quedó Caja: código real del módulo y sus submódulos.
SELECT m.id AS modulo_id, m.codigo AS modulo_codigo, m.nombre, m.activo, s.id AS submodulo_id, s.codigo, s.activo AS sub_activo
FROM modulos m LEFT JOIN submodulos s ON s.modulo_id = m.id
WHERE lower(translate(m.codigo, 'áéíóúÁÉÍÓÚ', 'aeiouAEIOU')) LIKE '%caja%'
   OR lower(m.nombre) LIKE '%caja%'
ORDER BY m.id, s.orden;


-- ── 1. ARREGLO ───────────────────────────────────────────────────────────────
BEGIN;

-- 1a. Normaliza el código de los módulos (solo si no choca con otro módulo).
UPDATE modulos m
SET codigo = n.esperado, updated_at = now()
FROM (
    SELECT id, regexp_replace(lower(translate(trim(codigo), 'áéíóúÁÉÍÓÚñÑ', 'aeiouAEIOUnN')), '\s+', '-', 'g') AS esperado
    FROM modulos
) n
WHERE n.id = m.id
  AND m.codigo IS DISTINCT FROM n.esperado
  AND NOT EXISTS (SELECT 1 FROM modulos o WHERE o.id <> m.id AND o.codigo = n.esperado);

-- 1b. Igual con los submódulos (el front viejo ya los normalizaba al comparar).
UPDATE submodulos s
SET codigo = n.esperado, updated_at = now()
FROM (
    SELECT id, regexp_replace(lower(translate(trim(codigo), 'áéíóúÁÉÍÓÚñÑ', 'aeiouAEIOUnN')), '\s+', '-', 'g') AS esperado
    FROM submodulos
) n
WHERE n.id = s.id
  AND s.codigo IS DISTINCT FROM n.esperado
  AND NOT EXISTS (SELECT 1 FROM submodulos o WHERE o.id <> s.id AND o.modulo_id = s.modulo_id AND o.codigo = n.esperado);

-- 2. Re-aplica lo de V190–V192 que se saltó por el código del módulo.

-- 2a. V191: submódulo "Perfiles y Permisos".
INSERT INTO submodulos (modulo_id, nombre, codigo, descripcion, activo, orden, created_at, updated_at)
SELECT m.id, 'Perfiles y Permisos', 'perfiles',
       'Perfiles de permisos de la empresa: qué ve y qué puede hacer cada usuario',
       TRUE, COALESCE((SELECT MAX(orden) FROM submodulos WHERE modulo_id = m.id), 0) + 1, now(), now()
FROM modulos m
WHERE m.codigo = 'caja'
  AND NOT EXISTS (SELECT 1 FROM submodulos s WHERE s.modulo_id = m.id AND s.codigo = 'perfiles');

-- 2b. V192: submódulo "Bitácora".
INSERT INTO submodulos (modulo_id, nombre, codigo, descripcion, activo, orden, created_at, updated_at)
SELECT m.id, 'Bitácora', 'bitacora', 'Quién anuló, editó, autorizó o cambió qué y cuándo',
       TRUE, COALESCE((SELECT MAX(orden) FROM submodulos WHERE modulo_id = m.id), 0) + 1, now(), now()
FROM modulos m
WHERE m.codigo = 'caja'
  AND NOT EXISTS (SELECT 1 FROM submodulos s WHERE s.modulo_id = m.id AND s.codigo = 'bitacora');

-- 2c. Los activa en las empresas que tienen Caja.
INSERT INTO empresa_submodulo (empresa_id, submodulo_id, activo, created_at, updated_at)
SELECT em.empresa_id, s.id, TRUE, now(), now()
FROM submodulos s
JOIN modulos m ON m.id = s.modulo_id AND m.codigo = 'caja'
JOIN empresa_modulo em ON em.modulo_id = m.id AND em.activo = TRUE
WHERE s.codigo IN ('perfiles', 'bitacora')
  AND NOT EXISTS (SELECT 1 FROM empresa_submodulo es
                  WHERE es.empresa_id = em.empresa_id AND es.submodulo_id = s.id);

-- 2d. V190: lo que Cajero / Vendedor / Supervisor / Básico veían de Caja.
INSERT INTO perfil_permiso (perfil_id, submodulo_id, ver, crear, editar, anular)
SELECT p.id, s.id, TRUE, TRUE, TRUE, TRUE
FROM perfil p
JOIN (VALUES
    ('CAJERO', 'comprobantes'),
    ('CAJERO', 'turnos'),
    ('VENDEDOR', 'turnos'),
    ('SUPERVISOR', 'turnos'),
    ('BASICO', 'turnos')
) AS v(perfil, submodulo) ON v.perfil = p.codigo
JOIN modulos m ON m.codigo = 'caja'
JOIN submodulos s ON s.modulo_id = m.id AND s.codigo = v.submodulo
WHERE p.es_sistema = TRUE
ON CONFLICT (perfil_id, submodulo_id) DO NOTHING;

-- 2e. V192: acción especial "Cerrar sesiones" en Usuarios.
INSERT INTO permiso_accion_especial (submodulo_id, codigo, nombre, descripcion, hereda_de, orden)
SELECT s.id, 'CERRAR_SESIONES', 'Cerrar sesiones', 'Cerrar todas las sesiones abiertas de un usuario', 'EDITAR', 1
FROM modulos m
JOIN submodulos s ON s.modulo_id = m.id AND s.codigo = 'usuarios'
WHERE m.codigo = 'caja'
ON CONFLICT (submodulo_id, codigo) DO NOTHING;

-- Revisar antes de confirmar: debe salir 'caja' con sus submódulos.
SELECT m.codigo || '.' || s.codigo AS clave, s.activo
FROM modulos m JOIN submodulos s ON s.modulo_id = m.id
WHERE m.codigo = 'caja' ORDER BY s.orden;

COMMIT;   -- si algo se ve raro: ROLLBACK;
