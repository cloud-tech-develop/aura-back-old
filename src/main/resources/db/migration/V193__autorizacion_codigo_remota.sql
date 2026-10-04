-- V193 — Autorización del supervisor sin escribir su clave en el equipo del cajero
-- (docs/PLAN_PERMISOS.md, P8). V192 la pedía con usuario + PIN/clave en el POS, y
-- esa petición se podía leer con las herramientas del navegador. Ahora hay dos
-- caminos y la clave nunca sale de la sesión del supervisor:
--   · CODIGO: el supervisor genera en SU sesión un código de 6 dígitos (2 min) y
--     se lo dicta al cajero; aquí solo queda su huella (codigo_hash).
--   · SOLICITADA: el cajero pide aprobación y el supervisor la aprueba en su sesión.
-- Usado el código o aprobada la solicitud, pasa a VIGENTE (10 min) y la venta la
-- consume (USADA). RECHAZADA si el supervisor la niega.
--
-- Estados: CODIGO | SOLICITADA | VIGENTE | USADA | RECHAZADA (estado VARCHAR(12) alcanza).

-- Un código aún no tiene solicitante; una solicitud aún no tiene autorizador.
ALTER TABLE autorizacion ALTER COLUMN solicitante_id DROP NOT NULL;
ALTER TABLE autorizacion ALTER COLUMN autorizador_id DROP NOT NULL;

-- Qué pasa el límite, para que el supervisor sepa qué aprueba.
ALTER TABLE autorizacion ADD COLUMN IF NOT EXISTS detalle TEXT;

-- SHA-256 de empresa + código; nunca el código.
ALTER TABLE autorizacion ADD COLUMN IF NOT EXISTS codigo_hash VARCHAR(64);

-- Buscar códigos y solicitudes vigentes de la empresa.
CREATE INDEX IF NOT EXISTS idx_autorizacion_estado ON autorizacion (empresa_id, estado, expira_en);
