-- ── V134: prestaciones — estado PROGRAMADA en el ciclo de pago (B-07) ────────
--
-- Antes el pago saltaba APROBADA → PAGADA en un solo paso, descontando el banco
-- y contabilizando de una, sin distinguir entre "orden de pago generada" y
-- "el banco confirmó el pago". Una transferencia rechazada dejaba la prestación
-- marcada como pagada y el asiento ya hecho.
--
-- El nuevo estado PROGRAMADA separa la dispersión de la confirmación: la
-- transferencia queda PROGRAMADA y solo pasa a PAGADA cuando se confirma
-- (ahí se descuenta el banco y se contabiliza). El efectivo/cheque siguen
-- yendo directo a PAGADA.
--
-- Seguro: solo amplía el CHECK con un valor nuevo; ninguna fila existente lo usa.

ALTER TABLE liquidacion_prestacion
    DROP CONSTRAINT IF EXISTS chk_prestacion_estado;

ALTER TABLE liquidacion_prestacion
    ADD CONSTRAINT chk_prestacion_estado
    CHECK (estado IN ('BORRADOR', 'APROBADA', 'PROGRAMADA', 'PAGADA', 'ANULADA'));
