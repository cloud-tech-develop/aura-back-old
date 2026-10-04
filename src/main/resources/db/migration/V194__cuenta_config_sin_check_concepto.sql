-- V194 — Quita la restricción CHECK de cuenta_config.concepto.
--
-- Hibernate la generó hace tiempo con la lista de conceptos de ese momento y
-- ninguna migración la mantiene: cada concepto nuevo del enum ConceptoContable
-- (p. ej. RETEIVA_ASUMIDA, de V181) revienta al sembrar la configuración.
-- El valor ya lo valida el enum en Java.

ALTER TABLE cuenta_config DROP CONSTRAINT IF EXISTS cuenta_config_concepto_check;
