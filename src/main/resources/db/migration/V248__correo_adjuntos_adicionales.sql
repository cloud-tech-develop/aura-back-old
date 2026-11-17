-- V248 — Documentos adicionales en el correo de una factura, cuenta de cobro o
-- cotización (contrato, RUT, orden de compra, fotos…), además del PDF del documento.
--
-- Los archivos no se guardan: se envían en el momento. Aquí queda solo la lista de
-- nombres, para que el historial diga qué se mandó. Idempotente.

ALTER TABLE correo_envio ADD COLUMN IF NOT EXISTS adjuntos_adicionales VARCHAR(1000);
