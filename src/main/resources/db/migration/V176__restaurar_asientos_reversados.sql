-- V176 — Devuelve a CONTABILIZADO los asientos que una anulación dejó ANULADOS.
--
-- Desde el 2026-08-15 ContabilidadAutoServiceImpl.reversar() hacía dos cosas:
-- creaba el contraasiento (RV, tipo ANULACION_X) y además marcaba ANULADO el
-- asiento original. Los reportes solo leen CONTABILIZADO, así que del par solo
-- quedaba la reversa: anular un gasto de 100 lo mostraba en -100 en el mayor,
-- el balance y el estado de resultados.
--
-- El código ya no toca el original (el contraasiento es quien lo neutraliza).
-- Esta migración repara lo que quedó: un original ANULADO cuya reversa viva
-- lo cita por número en la descripción "(reversa de XX-0001)". Los ANULADOS por
-- otras vías (borradores de documentos anulados, comprobantes manuales, cierres
-- reabiertos) no tienen esa reversa y no se tocan.
--
-- Idempotente: una segunda corrida no encuentra originales ANULADOS con reversa.

UPDATE asiento_contable o
   SET estado = 'CONTABILIZADO'
 WHERE o.estado = 'ANULADO'
   AND o.numero_comprobante IS NOT NULL
   AND o.origen_id IS NOT NULL
   AND EXISTS (SELECT 1
                 FROM asiento_contable r
                WHERE r.empresa_id  = o.empresa_id
                  AND r.tipo_origen = 'ANULACION_' || o.tipo_origen
                  AND r.origen_id   = o.origen_id
                  AND r.estado     <> 'ANULADO'
                  AND r.descripcion LIKE '%(reversa de ' || o.numero_comprobante || ')%');
