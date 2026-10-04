-- V192 — Permisos, segunda etapa (fases P5–P10 de docs/PLAN_PERMISOS.md).
--
-- Regla del día 1, igual que V190: al subir esto nadie gana ni pierde nada.
--   · Los límites de descuento y precio nacen vacíos (= sin límite).
--   · Todos los perfiles nacen con "todas las sedes".
--   · Las acciones especiales heredan de una acción que el perfil ya tiene
--     (reabrir período ← editar períodos), salvo autorizar descuentos, que
--     solo tienen el Administrador (acceso total) y el Supervisor.

-- ── P5 · Submódulos que faltaban ────────────────────────────────────────────
-- Obligaciones financieras existía sin submódulo: no se podía manejar por perfil.
INSERT INTO submodulos (modulo_id, nombre, codigo, descripcion, activo, orden, created_at, updated_at)
SELECT m.id, 'Obligaciones', 'obligaciones',
       'Obligaciones financieras: préstamos, cuotas e intereses',
       TRUE,
       COALESCE((SELECT MAX(orden) FROM submodulos WHERE modulo_id = m.id), 0) + 1,
       now(), now()
FROM modulos m
WHERE m.codigo = 'tesoreria'
  AND NOT EXISTS (SELECT 1 FROM submodulos s WHERE s.modulo_id = m.id AND s.codigo = 'obligaciones');

-- Bitácora de auditoría (P7), junto a Usuarios y Perfiles.
INSERT INTO submodulos (modulo_id, nombre, codigo, descripcion, activo, orden, created_at, updated_at)
SELECT m.id, 'Bitácora', 'bitacora',
       'Quién anuló, editó, autorizó o cambió qué y cuándo',
       TRUE,
       COALESCE((SELECT MAX(orden) FROM submodulos WHERE modulo_id = m.id), 0) + 1,
       now(), now()
FROM modulos m
WHERE m.codigo = 'caja'
  AND NOT EXISTS (SELECT 1 FROM submodulos s WHERE s.modulo_id = m.id AND s.codigo = 'bitacora');

INSERT INTO empresa_submodulo (empresa_id, submodulo_id, activo, created_at, updated_at)
SELECT em.empresa_id, s.id, TRUE, now(), now()
FROM submodulos s
JOIN modulos m ON m.id = s.modulo_id
JOIN empresa_modulo em ON em.modulo_id = m.id AND em.activo = TRUE
WHERE ((m.codigo = 'tesoreria' AND s.codigo = 'obligaciones')
       OR (m.codigo = 'caja' AND s.codigo = 'bitacora'))
  AND NOT EXISTS (
      SELECT 1 FROM empresa_submodulo es
      WHERE es.empresa_id = em.empresa_id AND es.submodulo_id = s.id
  );

-- Quien veía tesorería sigue viendo Obligaciones (antes la cubría "tesoreria.*").
INSERT INTO perfil_permiso (perfil_id, submodulo_id, ver, crear, editar, anular)
SELECT pp.perfil_id, nuevo.id, bool_or(pp.ver), bool_or(pp.crear), bool_or(pp.editar), bool_or(pp.anular)
FROM perfil_permiso pp
JOIN submodulos s ON s.id = pp.submodulo_id
JOIN modulos m ON m.id = s.modulo_id AND m.codigo = 'tesoreria'
JOIN submodulos nuevo ON nuevo.modulo_id = m.id AND nuevo.codigo = 'obligaciones'
WHERE s.id <> nuevo.id
  AND NOT EXISTS (SELECT 1 FROM perfil_permiso x WHERE x.perfil_id = pp.perfil_id AND x.submodulo_id = nuevo.id)
GROUP BY pp.perfil_id, nuevo.id;

-- ── P6 · Acciones especiales ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS permiso_accion_especial (
    id            BIGSERIAL     PRIMARY KEY,
    submodulo_id  BIGINT        NOT NULL REFERENCES submodulos(id) ON DELETE CASCADE,
    -- La clave completa es modulo.submodulo:CODIGO
    codigo        VARCHAR(40)   NOT NULL,
    nombre        VARCHAR(120)  NOT NULL,
    descripcion   VARCHAR(300),
    -- Sin fila explícita en el perfil, la acción vale lo que valga esta acción
    -- base (VER | CREAR | EDITAR | ANULAR). NULL = apagada salvo acceso total.
    hereda_de     VARCHAR(10),
    orden         INT           NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_permiso_accion_especial
    ON permiso_accion_especial (submodulo_id, codigo);

CREATE TABLE IF NOT EXISTS perfil_accion_especial (
    id         BIGSERIAL  PRIMARY KEY,
    perfil_id  BIGINT     NOT NULL REFERENCES perfil(id) ON DELETE CASCADE,
    accion_id  BIGINT     NOT NULL REFERENCES permiso_accion_especial(id) ON DELETE CASCADE,
    permitido  BOOLEAN    NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_perfil_accion_especial
    ON perfil_accion_especial (perfil_id, accion_id);

-- Excepción del usuario sobre lo que diga su perfil.
CREATE TABLE IF NOT EXISTS usuario_accion_especial (
    id          BIGSERIAL  PRIMARY KEY,
    usuario_id  INT        NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    accion_id   BIGINT     NOT NULL REFERENCES permiso_accion_especial(id) ON DELETE CASCADE,
    permitido   BOOLEAN    NOT NULL,
    created_by  INT,
    created_at  TIMESTAMP  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_usuario_accion_especial
    ON usuario_accion_especial (usuario_id, accion_id);

-- Catálogo. Si se agrega una acción aquí, agregarla también donde se exige.
INSERT INTO permiso_accion_especial (submodulo_id, codigo, nombre, descripcion, hereda_de, orden)
SELECT s.id, v.codigo, v.nombre, v.descripcion, v.hereda_de, v.orden
FROM (VALUES
    ('ventas', 'ventas', 'AUTORIZAR_DESCUENTO', 'Autorizar descuentos y precios',
     'Autoriza, con su usuario y PIN, descuentos o rebajas de precio por encima del límite de otro usuario', NULL, 1),
    ('ventas', 'ventas', 'VENDER_A_CREDITO', 'Vender a crédito',
     'Registrar ventas con forma de pago crédito (queda en cartera)', 'CREAR', 2),
    -- El cajero vende por el POS: basta con tenerla en cualquiera de los dos.
    ('principal', 'punto-de-venta', 'VENDER_A_CREDITO', 'Vender a crédito',
     'Cobrar con forma de pago crédito en el punto de venta (queda en cartera)', 'VER', 1),
    ('contabilidad', 'periodos-contables', 'REABRIR', 'Reabrir período',
     'Reabrir un período contable cerrado', 'EDITAR', 1),
    ('inventario', 'reconteos', 'APROBAR', 'Aprobar reconteo',
     'Aprobar un reconteo y ajustar el inventario', 'EDITAR', 1),
    ('cartera', 'cartera', 'APROBAR_CREDITO', 'Aprobar solicitudes de crédito',
     'Aprobar o rechazar ventas a crédito que superan el cupo o están en mora', 'EDITAR', 1),
    ('recursos-humanos', 'liquidacion-nomina', 'APROBAR', 'Aprobar nómina',
     'Aprobar la liquidación de nómina de un período', 'EDITAR', 1),
    ('caja', 'usuarios', 'CERRAR_SESIONES', 'Cerrar sesiones',
     'Cerrar todas las sesiones abiertas de un usuario', 'EDITAR', 1)
) AS v(modulo, submodulo, codigo, nombre, descripcion, hereda_de, orden)
JOIN modulos m ON m.codigo = v.modulo
JOIN submodulos s ON s.modulo_id = m.id AND s.codigo = v.submodulo
ON CONFLICT (submodulo_id, codigo) DO NOTHING;

-- El Supervisor autoriza descuentos (el Administrador ya los tiene por acceso total).
INSERT INTO perfil_accion_especial (perfil_id, accion_id, permitido)
SELECT p.id, a.id, TRUE
FROM perfil p
JOIN permiso_accion_especial a ON a.codigo = 'AUTORIZAR_DESCUENTO'
WHERE p.codigo = 'SUPERVISOR'
ON CONFLICT (perfil_id, accion_id) DO NOTHING;

-- ── P8 · Límites ─────────────────────────────────────────────────────────────
-- NULL = sin límite. Porcentajes de 0 a 100.
ALTER TABLE perfil ADD COLUMN IF NOT EXISTS descuento_max_pct NUMERIC(5,2);
ALTER TABLE perfil ADD COLUMN IF NOT EXISTS rebaja_precio_max_pct NUMERIC(5,2);
-- Del usuario: si tiene valor, reemplaza el del perfil.
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS descuento_max_pct NUMERIC(5,2);
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS rebaja_precio_max_pct NUMERIC(5,2);

-- Autorización de un supervisor: de un solo uso y por pocos minutos.
CREATE TABLE IF NOT EXISTS autorizacion (
    id              BIGSERIAL     PRIMARY KEY,
    empresa_id      INT           NOT NULL,
    solicitante_id  INT           NOT NULL,
    autorizador_id  INT           NOT NULL,
    -- DESCUENTO (cubre descuento y rebaja de precio)
    tipo            VARCHAR(30)   NOT NULL,
    descuento_pct   NUMERIC(7,2),
    rebaja_pct      NUMERIC(7,2),
    motivo          VARCHAR(300),
    -- VIGENTE | USADA
    estado          VARCHAR(12)   NOT NULL DEFAULT 'VIGENTE',
    documento_tipo  VARCHAR(30),
    documento_id    BIGINT,
    expira_en       TIMESTAMP     NOT NULL,
    usada_en        TIMESTAMP,
    created_at      TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_autorizacion_empresa ON autorizacion (empresa_id, created_at);

-- ── P7 · Bitácora de auditoría ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS auditoria_evento (
    id              BIGSERIAL     PRIMARY KEY,
    empresa_id      INT           NOT NULL,
    usuario_id      INT,
    -- Quien autorizó, si la acción necesitó autorización.
    autorizado_por  INT,
    fecha           TIMESTAMP     NOT NULL DEFAULT now(),
    -- modulo.submodulo
    clave           VARCHAR(120),
    -- EDITAR | ANULAR | especial (REABRIR, APROBAR…) | AUTORIZAR | CAMBIO_PRECIO …
    accion          VARCHAR(40)   NOT NULL,
    entidad         VARCHAR(60),
    entidad_id      VARCHAR(60),
    descripcion     TEXT,
    antes           TEXT,
    despues         TEXT,
    metodo          VARCHAR(10),
    ruta            VARCHAR(300),
    ip              VARCHAR(64),
    -- AUTO = la anotó el interceptor; SERVICIO = la anotó el servicio con detalle.
    origen          VARCHAR(10)   NOT NULL DEFAULT 'SERVICIO'
);

CREATE INDEX IF NOT EXISTS idx_auditoria_evento_empresa ON auditoria_evento (empresa_id, fecha);
CREATE INDEX IF NOT EXISTS idx_auditoria_evento_entidad ON auditoria_evento (empresa_id, entidad, entidad_id);

-- ── P9 · Alcance por sede ───────────────────────────────────────────────────
-- Sin "todas las sedes", el usuario solo ve y opera en las suyas (usuario_sucursal).
ALTER TABLE perfil ADD COLUMN IF NOT EXISTS todas_sedes BOOLEAN NOT NULL DEFAULT TRUE;

-- ── P10 · Sesión ─────────────────────────────────────────────────────────────
-- Va en el token: si se sube, las sesiones abiertas caen.
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS token_version INT NOT NULL DEFAULT 0;
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS intentos_fallidos INT NOT NULL DEFAULT 0;
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS bloqueado_hasta TIMESTAMP;
