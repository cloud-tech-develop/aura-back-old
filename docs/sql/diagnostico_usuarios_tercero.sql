-- ╔════════════════════════════════════════════════════════════════════╗
-- ║  Diagnóstico: usuarios y su tercero (PLAN_PERMISOS, ajustes V193)    ║
-- ║                                                                      ║
-- ║  Desde ahora todo usuario queda ligado a un tercero que ya existe y  ║
-- ║  un tercero solo puede tener un usuario. Antes:                      ║
-- ║   · el alta normal creaba un tercero NUEVO por usuario (duplicaba a  ║
-- ║     la persona si ya era empleado, vendedor o cliente);              ║
-- ║   · "crear usuario desde empleado" dejaba el usuario SIN tercero.    ║
-- ║                                                                      ║
-- ║  Parte 1 = solo lectura. Parte 2 = corrección opcional, comentada:   ║
-- ║  revisar la parte 1 antes de descomentarla. Correr primero en local. ║
-- ╚════════════════════════════════════════════════════════════════════╝

-- ── Parte 1 · Diagnóstico (solo lectura) ─────────────────────────────

-- 1.1 Usuarios sin tercero (no cuenta PLATFORM_ADMIN, que no es de una empresa).
SELECT u.id AS usuario_id, u.empresa_id, u.username, u.rol, u.empleado_id,
       e.tercero_id AS tercero_del_empleado
FROM usuario u
LEFT JOIN empleado e ON e.id = u.empleado_id
WHERE u.tercero_id IS NULL
  AND COALESCE(u.rol, '') <> 'PLATFORM_ADMIN'
ORDER BY u.empresa_id, u.username;

-- 1.2 Usuarios de empleado cuyo tercero no es el del empleado.
SELECT u.id AS usuario_id, u.empresa_id, u.username,
       u.tercero_id AS tercero_del_usuario, e.tercero_id AS tercero_del_empleado
FROM usuario u
JOIN empleado e ON e.id = u.empleado_id
WHERE u.tercero_id IS NOT NULL
  AND e.tercero_id IS NOT NULL
  AND u.tercero_id <> e.tercero_id
ORDER BY u.empresa_id, u.username;

-- 1.3 Personas duplicadas: el tercero del usuario tiene el mismo documento que
--     otro tercero de la empresa. El "otro" suele ser el bueno (el empleado,
--     vendedor o cliente de verdad): reasignarlo desde la página del usuario.
SELECT u.id AS usuario_id, u.empresa_id, u.username,
       tu.id AS tercero_del_usuario, tu.numero_documento,
       COALESCE(NULLIF(TRIM(tu.razon_social), ''), TRIM(CONCAT(tu.nombres, ' ', tu.apellidos))) AS nombre_usuario,
       otro.id AS otro_tercero,
       COALESCE(NULLIF(TRIM(otro.razon_social), ''), TRIM(CONCAT(otro.nombres, ' ', otro.apellidos))) AS nombre_otro,
       otro.es_empleado, otro.es_cliente, otro.es_proveedor,
       (SELECT COUNT(*) FROM usuario x WHERE x.tercero_id = otro.id) AS usuarios_del_otro
FROM usuario u
JOIN tercero tu ON tu.id = u.tercero_id
JOIN tercero otro ON otro.empresa_id = tu.empresa_id
                 AND otro.id <> tu.id
                 AND otro.deleted_at IS NULL
                 AND NULLIF(TRIM(otro.numero_documento), '') IS NOT NULL
                 AND TRIM(otro.numero_documento) = TRIM(tu.numero_documento)
ORDER BY u.empresa_id, u.username;

-- 1.4 Terceros con más de un usuario (la regla nueva lo impide de aquí en adelante).
SELECT u.tercero_id, u.empresa_id, COUNT(*) AS usuarios, STRING_AGG(u.username, ', ') AS usernames
FROM usuario u
WHERE u.tercero_id IS NOT NULL
GROUP BY u.tercero_id, u.empresa_id
HAVING COUNT(*) > 1
ORDER BY usuarios DESC;

-- ── Parte 2 · Corrección opcional (revisar 1.1 antes) ────────────────
-- Liga los usuarios de empleado sin tercero al tercero de su empleado, solo si
-- ese tercero no tiene ya otro usuario. Los demás casos (1.2, 1.3, 1.4) se
-- resuelven a mano desde la página del usuario: cambiar el tercero.
--
-- BEGIN;
-- UPDATE usuario u
-- SET tercero_id = e.tercero_id
-- FROM empleado e
-- WHERE e.id = u.empleado_id
--   AND u.tercero_id IS NULL
--   AND e.tercero_id IS NOT NULL
--   AND NOT EXISTS (SELECT 1 FROM usuario x WHERE x.tercero_id = e.tercero_id);
-- -- Revisar el número de filas y luego:
-- COMMIT;   -- o ROLLBACK;
