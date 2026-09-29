-- ── V114: Fase 10 — certificados y desprendible ─────────────────────────────
--
-- Depende enteramente de la Fase 3.b (nomina_detalle con `traza`): sin el
-- desglose no hay nada que imprimir.
--
-- Seguro sin tocar código: solo crea tablas nuevas.

CREATE TABLE IF NOT EXISTS certificado_emitido (
    id           BIGSERIAL     PRIMARY KEY,
    empresa_id   INT           NOT NULL REFERENCES empresa(id),
    contrato_id  BIGINT        REFERENCES contrato_laboral(id),
    tercero_id   BIGINT        NOT NULL REFERENCES tercero(id),

    tipo         VARCHAR(30)   NOT NULL,
    agno         INT,                              -- para certificados anuales
    nomina_id    BIGINT        REFERENCES nomina(id),  -- para desprendibles

    -- SNAPSHOT del contenido tal como se emitió.
    -- Un certificado es un documento con valor probatorio: NO se regenera.
    -- Si el empleado vuelve por el mismo certificado dentro de un año, debe
    -- recibir EXACTAMENTE el mismo documento — aunque los datos hayan cambiado.
    -- Mismo criterio que pila_cotizante.cod_eps y nomina_electronica.xml:
    -- los documentos emitidos guardan literales; los maestros guardan FK.
    contenido_json JSONB,
    pdf_ruta     VARCHAR(500),

    emitido_por  BIGINT,
    emitido_at   TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_cert_tipo CHECK (tipo IN (
        'DESPRENDIBLE',              -- detalle de una nómina
        'INGRESOS_RETENCIONES',      -- formato 220 DIAN, anual
        'LABORAL',                   -- cargo, salario, fechas
        'LABORAL_CON_SALARIO',
        'CESANTIAS'                  -- para retiro parcial
    ))
);

CREATE INDEX IF NOT EXISTS idx_cert_tercero  ON certificado_emitido(tercero_id, tipo);
CREATE INDEX IF NOT EXISTS idx_cert_contrato ON certificado_emitido(contrato_id);
CREATE INDEX IF NOT EXISTS idx_cert_agno     ON certificado_emitido(empresa_id, tipo, agno);


-- ═══════════════════════════════════════════════════════════════════════════
-- NOTAS
--
-- DESPRENDIBLE DE PAGO
--   Fuente: nomina_detalle (concepto, base, porcentaje, valor) + traza.
--   Precedente de PDF en el código: CuentaPdfService.
--
-- CERTIFICADO DE INGRESOS Y RETENCIONES (formato 220)
--   Obligación ANUAL ante la DIAN. Se entrega al empleado antes del 31 de marzo.
--   Agrega por año: ingresos, aportes obligatorios, retenciones practicadas.
--   Fuente: SUM(nomina_detalle) del año agrupado por concepto.
--   Depende de la Fase 4.5 (retefuente) para tener qué reportar.
--
-- CERTIFICADO LABORAL
--   Trivial una vez existe contrato_laboral: cargo, salario, fecha_inicio,
--   fecha_fin, tipo_contrato. Dos variantes: con y sin salario (el empleado
--   elige — a veces no quiere que aparezca).
--
-- SALARIO INTEGRAL — no tiene fase propia
--   Es un MODO DE CÁLCULO del motor, no una tabla:
--     · contrato_laboral.es_salario_integral (V102) ✔
--     · nomina_config.factor_salario_integral = 70 (V107) ✔
--     · El motor: IBC = base * 70%, y NO se provisionan prestaciones
--       (el 30% restante ya es el factor prestacional).
--   Va dentro de la Fase 0 o la 3, según cuándo se toque el motor.
-- ═══════════════════════════════════════════════════════════════════════════
