-- ── V72: Nómina Fase 5 — gating de asistencia y autorización excepcional ─────

-- Ampliar los tipos de novedad permitidos para incluir los generados por asistencia.
ALTER TABLE nomina_novedad DROP CONSTRAINT IF EXISTS chk_novedad_tipo;
ALTER TABLE nomina_novedad
    ADD CONSTRAINT chk_novedad_tipo CHECK (
        tipo IN (
            'HORA_EXTRA_DIURNA', 'HORA_EXTRA_NOCTURNA', 'HORA_EXTRA_DOMINICAL',
            'HORA_EXTRA_FESTIVO', 'HORA_EXTRA_DOMINICAL_FESTIVA',
            'RECARGO_NOCTURNO', 'RECARGO_DOMINICAL_FESTIVO',
            'AUSENCIA_NO_JUSTIFICADA', 'LLEGADA_TARDE_DESCONTADA', 'SALIDA_TEMPRANA_DESCONTADA',
            'PERMISO_REMUNERADO', 'PERMISO_NO_REMUNERADO',
            'INCAPACIDAD', 'LICENCIA_REMUNERADA', 'LICENCIA_NO_REMUNERADA', 'VACACIONES',
            'BONO', 'COMISION', 'PRESTAMO', 'EMBARGO', 'OTRO_DEVENGO', 'OTRO_DESCUENTO'
        )
    );

-- ─────────────────────────────────────────────────────────────────────────────

-- Autorización para liquidar sin asistencia aprobada (deja trazabilidad).
CREATE TABLE autorizacion_liquidacion_excepcional (
    id                 BIGSERIAL   PRIMARY KEY,
    empresa_id         INT         NOT NULL REFERENCES empresa(id),
    empleado_id        BIGINT      NOT NULL REFERENCES empleados(id) ON DELETE CASCADE,
    periodo_nomina_id  BIGINT      NOT NULL REFERENCES periodo_nomina(id),
    usuario_autoriza   INT,
    fecha_autorizacion TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    motivo             VARCHAR(40) NOT NULL,
                       -- FALLA_SISTEMA_ASISTENCIA | MARCACION_NO_DISPONIBLE | ORDEN_ADMINISTRATIVA
                       -- CIERRE_URGENTE_NOMINA | CORRECCION_POSTERIOR
    observacion        VARCHAR(255),
    estado             VARCHAR(20) NOT NULL DEFAULT 'ACTIVA'
                       -- ACTIVA | ANULADA
);

CREATE INDEX idx_autoriz_liq_empresa   ON autorizacion_liquidacion_excepcional(empresa_id);
CREATE INDEX idx_autoriz_liq_empleado  ON autorizacion_liquidacion_excepcional(empleado_id, periodo_nomina_id);
