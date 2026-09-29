-- ── V124: PILA P2b — catálogo de entidades (EPS/AFP/ARL/CCF) con vigencia ─────
--
-- Catálogo GLOBAL de administradoras de seguridad social, con su código PILA
-- oficial y vigencia. Habilita en el validador:
--   · PILA-ENT-005: código de entidad inválido (no existe en el catálogo).
--   · vigencia de la entidad para el período.
--
-- REGLA DEL VALIDADOR (no inventar): esta tabla se crea VACÍA. Los códigos de
-- EPS/AFP/ARL/CCF son cientos y cambian por período; deben cargarse del catálogo
-- oficial del operador/UGPP. Mientras un tipo de entidad NO tenga filas cargadas,
-- el validador NO valida ese código (no afirma que sea inválido): solo verifica
-- la PRESENCIA (P2a). En cuanto se carga el catálogo de un tipo, se activa la
-- validación de existencia y vigencia para ese tipo, sin tocar código.

CREATE TABLE IF NOT EXISTS pila_entidad (
    id              BIGSERIAL    PRIMARY KEY,
    tipo            VARCHAR(5)   NOT NULL,   -- EPS | AFP | ARL | CCF
    codigo          VARCHAR(10)  NOT NULL,   -- código PILA oficial
    nombre          VARCHAR(200) NOT NULL,
    vigencia_desde  DATE,
    vigencia_hasta  DATE,
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT chk_pila_entidad_tipo CHECK (tipo IN ('EPS', 'AFP', 'ARL', 'CCF')),
    CONSTRAINT uq_pila_entidad UNIQUE (tipo, codigo)
);

CREATE INDEX IF NOT EXISTS idx_pila_entidad_tipo ON pila_entidad(tipo);

-- Sin seed a propósito: cargar del catálogo oficial vigente del período.
