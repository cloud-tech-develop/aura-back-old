-- ── V98: Fase 1.c — roles de tercero, de booleanos a tabla ──────────────────
--
-- Hoy: 4 booleanos (es_cliente, es_proveedor, es_empleado, es_banco).
-- La Fase 5.5 necesita 4 más (EPS, AFP, CCF, ARL) → 8 booleanos.
--
-- Ese es el punto donde el patrón deja de escalar: cada rol nuevo es una
-- migración, una columna en 4 DTOs (Create/Update/Tercero/TerceroTable) y un
-- método de query casi idéntico. Con tabla, un rol nuevo es una fila.
--
-- Ver Fase 1.c del PLAN_MIGRACION_NOMINA.md.

CREATE TABLE IF NOT EXISTS tercero_rol (
    tercero_id BIGINT      NOT NULL REFERENCES tercero(id) ON DELETE CASCADE,
    rol        VARCHAR(20) NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tercero_id, rol),
    CONSTRAINT chk_tercero_rol CHECK (rol IN (
        'CLIENTE', 'PROVEEDOR', 'EMPLEADO', 'BANCO',
        -- Seguridad social (Fase 5.5): son terceros a los que se les paga.
        'EPS', 'AFP', 'CCF', 'ARL'
    ))
);

CREATE INDEX IF NOT EXISTS idx_tercero_rol_rol ON tercero_rol(rol);

-- ── Backfill desde los booleanos existentes ─────────────────────────────────
INSERT INTO tercero_rol (tercero_id, rol)
SELECT id, 'CLIENTE'   FROM tercero WHERE es_cliente   = TRUE
ON CONFLICT DO NOTHING;

INSERT INTO tercero_rol (tercero_id, rol)
SELECT id, 'PROVEEDOR' FROM tercero WHERE es_proveedor = TRUE
ON CONFLICT DO NOTHING;

INSERT INTO tercero_rol (tercero_id, rol)
SELECT id, 'EMPLEADO'  FROM tercero WHERE es_empleado  = TRUE
ON CONFLICT DO NOTHING;

INSERT INTO tercero_rol (tercero_id, rol)
SELECT id, 'BANCO'     FROM tercero WHERE es_banco     = TRUE
ON CONFLICT DO NOTHING;

-- ── Los booleanos NO se borran todavía ──────────────────────────────────────
-- Siguen siendo la fuente de verdad hasta que el código lea de tercero_rol.
-- Orden de retiro:
--   1. Esta migración (tabla + backfill)              ← estás aquí
--   2. Código: escribir en AMBOS lados (doble escritura)
--   3. Código: leer de tercero_rol; colapsar listarClientes/Proveedores/Bancos
--      en un listarPorRol(rol, search, empresaId)
--   4. Quitar los booleanos de los 4 DTOs y de TerceroEntity
--   5. Migración futura: DROP COLUMN es_cliente, es_proveedor, es_empleado, es_banco
--
-- No saltarse el paso 2: mientras el código viejo escriba solo booleanos, la
-- tabla se desincroniza en silencio.
