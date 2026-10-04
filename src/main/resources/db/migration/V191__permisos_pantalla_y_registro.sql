-- V191 — Permisos por perfil: pantalla de perfiles y registro de bloqueos
-- (fases P1 y P3 de docs/PLAN_PERMISOS.md).
--
-- 1. Submódulo "Perfiles y Permisos" junto a "Usuarios" (módulo caja), para que
--    la pantalla tenga su propio permiso. El SUPER_ADMIN nunca lo pierde (igual
--    que Usuarios) y el perfil Administrador lo tiene por su acceso total.
--
-- 2. permiso_bloqueo_log: el back revisa cada petición contra el perfil del
--    usuario. En modo OBSERVAR no bloquea: deja aquí lo que habría bloqueado,
--    para ajustar perfiles antes de pasar a BLOQUEAR. En BLOQUEAR registra lo
--    que bloqueó. Cada fila es una ruta (con su patrón, no la URL con ids) por
--    usuario y día, con su contador, para que no crezca sin control.

INSERT INTO submodulos (modulo_id, nombre, codigo, descripcion, activo, orden, created_at, updated_at)
SELECT m.id, 'Perfiles y Permisos', 'perfiles',
       'Perfiles de permisos de la empresa: qué ve y qué puede hacer cada usuario',
       TRUE,
       COALESCE((SELECT MAX(orden) FROM submodulos WHERE modulo_id = m.id), 0) + 1,
       now(), now()
FROM modulos m
WHERE m.codigo = 'caja'
  AND NOT EXISTS (SELECT 1 FROM submodulos s WHERE s.modulo_id = m.id AND s.codigo = 'perfiles');

INSERT INTO empresa_submodulo (empresa_id, submodulo_id, activo, created_at, updated_at)
SELECT em.empresa_id, s.id, TRUE, now(), now()
FROM submodulos s
JOIN modulos m ON m.id = s.modulo_id AND m.codigo = 'caja'
JOIN empresa_modulo em ON em.modulo_id = m.id AND em.activo = TRUE
WHERE s.codigo = 'perfiles'
  AND NOT EXISTS (
      SELECT 1 FROM empresa_submodulo es
      WHERE es.empresa_id = em.empresa_id AND es.submodulo_id = s.id
  );

CREATE TABLE IF NOT EXISTS permiso_bloqueo_log (
    id            BIGSERIAL     PRIMARY KEY,
    empresa_id    INT,
    usuario_id    INT           NOT NULL,
    -- OBSERVAR (no se bloqueó) | BLOQUEAR (se respondió 403)
    modo          VARCHAR(10)   NOT NULL,
    metodo        VARCHAR(10)   NOT NULL,
    -- Patrón de la ruta (/api/compras/{id}), no la URL concreta.
    ruta          VARCHAR(300)  NOT NULL,
    -- modulo.submodulo exigido; NULL = la ruta no tiene submódulo asignado.
    clave         VARCHAR(120),
    accion        VARCHAR(10),
    fecha         DATE          NOT NULL DEFAULT CURRENT_DATE,
    veces         INT           NOT NULL DEFAULT 1,
    primera_vez   TIMESTAMP     NOT NULL DEFAULT now(),
    ultima_vez    TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_permiso_bloqueo_log
    ON permiso_bloqueo_log (usuario_id, metodo, ruta, fecha, modo);

CREATE INDEX IF NOT EXISTS idx_permiso_bloqueo_log_empresa
    ON permiso_bloqueo_log (empresa_id, fecha);
