-- ── V73: Nómina/Asistencia Fase 6 — auditoría transversal ────────────────────

CREATE TABLE auditoria_nomina_asistencia (
    id              BIGSERIAL   PRIMARY KEY,
    empresa_id      INT         NOT NULL REFERENCES empresa(id),
    entidad         VARCHAR(50) NOT NULL,   -- NOMINA | ASISTENCIA_DIA | INCIDENCIA | MARCAJE | PERIODO_ASISTENCIA | AUTORIZACION
    entidad_id      BIGINT,
    accion          VARCHAR(50) NOT NULL,   -- LIQUIDAR | APROBAR | PAGAR | ANULAR | REVISAR | ...
    usuario_id      INT,
    fecha_hora      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valor_anterior  TEXT,
    valor_nuevo     TEXT,
    motivo          VARCHAR(255),
    ip              VARCHAR(60),
    origen          VARCHAR(30)
);

CREATE INDEX idx_auditoria_na_empresa  ON auditoria_nomina_asistencia(empresa_id);
CREATE INDEX idx_auditoria_na_entidad  ON auditoria_nomina_asistencia(entidad, entidad_id);
