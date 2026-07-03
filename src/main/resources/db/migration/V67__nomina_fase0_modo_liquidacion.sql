-- ── V67: Nómina Fase 0 — modo de liquidación y control de asistencia ─────────

-- Modo de liquidación a nivel de empresa: define si la nómina exige asistencia.
ALTER TABLE nomina_config
    ADD COLUMN modo_liquidacion VARCHAR(30) NOT NULL DEFAULT 'SIN_ASISTENCIA';
    -- SIN_ASISTENCIA | CON_ASISTENCIA_OBLIGATORIA | MIXTA

ALTER TABLE nomina_config
    ADD CONSTRAINT chk_nomina_config_modo_liq
        CHECK (modo_liquidacion IN ('SIN_ASISTENCIA', 'CON_ASISTENCIA_OBLIGATORIA', 'MIXTA'));

-- Marca por empleado: en modo MIXTA indica quién debe pasar por control de asistencia.
ALTER TABLE empleados
    ADD COLUMN requiere_control_asistencia BOOLEAN NOT NULL DEFAULT FALSE;
