-- ── V109: Fase 5 — nómina electrónica (DIAN vía Factus) ─────────────────────
--
-- Obligatoria ante la DIAN. Sin esto el módulo no es vendible como nómina formal.
--
-- Factus confirmado: emite nómina electrónica. Verificar el contrato específico
-- del endpoint al implementar (la integración actual, FactusService.generarFactura,
-- es solo factura de venta — nómina es otro documento, con CUNE en vez de CUFE).
--
-- Seguro sin tocar código: solo crea tablas nuevas.

CREATE TABLE IF NOT EXISTS nomina_electronica (
    id                 BIGSERIAL    PRIMARY KEY,
    empresa_id         INT          NOT NULL REFERENCES empresa(id),
    nomina_id          BIGINT       NOT NULL REFERENCES nomina(id),

    agno               INT          NOT NULL,
    mes                INT          NOT NULL,

    -- Consecutivo propio del documento de nómina electrónica, independiente
    -- del de facturación. Se RESERVA antes de enviar (ver nota de idempotencia).
    consecutivo        BIGINT       NOT NULL,
    prefijo            VARCHAR(10),

    -- Notas de ajuste
    es_ajuste          BOOLEAN      NOT NULL DEFAULT FALSE,
    nomina_ajustada_id BIGINT       REFERENCES nomina_electronica(id),

    estado             VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    intentos           INT          NOT NULL DEFAULT 0,

    -- Identificador único que devuelve la DIAN
    cune               VARCHAR(120),
    fecha_envio        TIMESTAMP,
    fecha_respuesta    TIMESTAMP,

    -- Snapshot de lo que se envió. NO se regenera: es el documento tal cual
    -- quedó ante la DIAN. Mismo criterio que pila_cotizante.cod_eps.
    payload_json       JSONB,
    xml                TEXT,

    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_ne_estado CHECK (estado IN
        ('PENDIENTE', 'ENVIADO', 'ACEPTADO', 'RECHAZADO', 'ANULADO')),
    CONSTRAINT chk_ne_mes CHECK (mes BETWEEN 1 AND 12),

    -- ⚠️ EL GUARDARRAÍL MÁS IMPORTANTE DE ESTA FASE.
    -- Impide que un reintento genere un documento nuevo ante la DIAN.
    CONSTRAINT uq_ne_consecutivo UNIQUE (empresa_id, agno, consecutivo),

    -- Una nómina no puede tener dos documentos originales (sí ajustes).
    CONSTRAINT chk_ne_ajuste CHECK (es_ajuste = TRUE OR nomina_ajustada_id IS NULL)
);

CREATE INDEX IF NOT EXISTS idx_ne_nomina  ON nomina_electronica(nomina_id);
CREATE INDEX IF NOT EXISTS idx_ne_estado  ON nomina_electronica(estado) WHERE estado IN ('PENDIENTE','RECHAZADO');
CREATE INDEX IF NOT EXISTS idx_ne_periodo ON nomina_electronica(empresa_id, agno, mes);

-- Un solo documento ORIGINAL por nómina. Los ajustes son filas aparte.
CREATE UNIQUE INDEX IF NOT EXISTS ux_ne_original
    ON nomina_electronica(nomina_id) WHERE es_ajuste = FALSE;


-- ── Log de cada intento contra la DIAN ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS nomina_electronica_log (
    id                    BIGSERIAL   PRIMARY KEY,
    nomina_electronica_id BIGINT      NOT NULL REFERENCES nomina_electronica(id) ON DELETE CASCADE,
    intento               INT         NOT NULL DEFAULT 1,
    codigo_respuesta      VARCHAR(20),
    mensaje_respuesta     TEXT,
    request_body          TEXT,
    response_body         TEXT,
    duracion_ms           INT,
    created_at            TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ne_log ON nomina_electronica_log(nomina_electronica_id);


-- ── Consecutivo por empresa/año ─────────────────────────────────────────────
-- Secuencia propia, reservada ANTES de enviar. No usar el consecutivo de
-- facturación: son numeraciones distintas ante la DIAN.
CREATE TABLE IF NOT EXISTS nomina_electronica_consecutivo (
    empresa_id INT    NOT NULL,
    agno       INT    NOT NULL,
    ultimo     BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (empresa_id, agno)
);


-- ═══════════════════════════════════════════════════════════════════════════
-- NOTAS DE IMPLEMENTACIÓN
--
-- 1. IDEMPOTENCIA — es donde más duele equivocarse.
--    El consecutivo se RESERVA antes de enviar:
--
--      UPDATE nomina_electronica_consecutivo
--         SET ultimo = ultimo + 1
--       WHERE empresa_id = ? AND agno = ?
--      RETURNING ultimo;
--
--    (con INSERT ... ON CONFLICT DO UPDATE para el primer uso del año)
--
--    Luego se inserta la fila en estado PENDIENTE con ese consecutivo, y
--    RECIÉN AHÍ se envía. Si el envío falla, se reintenta con LA MISMA fila:
--    uq_ne_consecutivo impide duplicar. Un reintento NUNCA debe generar un
--    documento nuevo ante la DIAN.
--
-- 2. ASÍNCRONO, nunca en el request.
--    nomina APROBADO → encolar → estado PENDIENTE → job → ENVIADO →
--    respuesta → ACEPTADO / RECHAZADO. Cada intento deja fila en el log.
--
-- 3. Seguir el patrón de FactusService (ya existe y es correcto):
--      · constructor injection
--      · @CircuitBreaker(name = "factus-nomina")
--      · @Retry(name = "factus-nomina")
--      · método fallback
--      · reutilizar FactusTokenService — está separado a propósito para que
--        el circuit breaker del token no se mezcle con el del documento.
--        MANTENER esa separación.
--
--    Agregar instancias `factus-nomina` en application.properties con sus
--    propios umbrales.
--
-- 4. DEPENDE DE:
--      · Fase 1 → datos fiscales y nombres desagregados del trabajador
--      · Fase 3 → nomina_detalle con conceptos tipificados. La DIAN exige
--        cada devengado y deducción en SU etiqueta (Basico, Transporte,
--        HEDs, Vacaciones, Prima, Cesantias...), no un total. De ahí sale
--        concepto_nomina.codigo_dian.
--      · Fase 4.5 → la retención va como deducción en el XML
--
-- 5. El disparo va tras el commit de la aprobación, igual que hoy se genera
--    el asiento contable.
-- ═══════════════════════════════════════════════════════════════════════════
