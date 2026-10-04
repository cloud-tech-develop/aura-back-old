-- ============================================================================
--  REALINEAR FECHA DE EMISIÓN DE LAS VENTAS DE UN TURNO DE CAJA
--
--  Cambia `venta.fecha_emision` de todas las ventas de un turno de caja de una
--  empresa a una fecha específica y arrastra esa fecha a TODO lo que se derivó
--  de la venta al contabilizar, para no dejar el documento con fecha nueva y el
--  asiento con la vieja.
--
--  Qué se actualiza (en cascada):
--    1. venta.fecha_emision                    conserva la hora original
--    2. asiento_contable                       tipo_origen VENTA y su reversa
--                                               ANULACION_VENTA, + periodo
--    3. cuentas_cobrar                         fecha_emision y vencimiento
--    4. factura.fecha_hora_emision             (ver la advertencia 2.4)
--    5. (diagnóstico) movimiento_caja          NO se toca, ver 4.3
--    5. devolucion.fecha_devolucion            las fechas de la 4.4
--
--  NO se toca:
--    · movimiento_inventario / inventario / lotes / seriales. El stock no se
--      recalcula ni se re-fecha: movimiento_inventario no tiene columna de
--      fecha, solo `created_at`.
--    · movimiento_caja.fecha / fecha_documento → ver 4.3: el arqueo no lee la
--      fecha de la venta, así que no hay nada que mover.
--    · venta_pago / comprobante_caja → no llevan fecha de documento propia.
--    · cuentas_cobrar.estado y saldos → solo las fechas.
--
--  Motor: PostgreSQL.
--  Uso:   1) ajustar la sección 0 (empresa, turno y fecha destino)
--         2) ejecutar completo -> queda en ROLLBACK (simulacro)
--         3) revisar las secciones 2 (diagnósticos) y 6 (verificación)
--         4) cambiar el ROLLBACK final por COMMIT y volver a ejecutar
--
--  ⚠  HAGA BACKUP ANTES (pg_dump).
--  ⚠  Ejecutar con la aplicación detenida (hay caches y consecutivos en uso).
--  ⚠  Si el periodo contable del mes destino está CERRADO el script ABORTA en
--     2.3: un periodo cerrado es historia firmada.
--  ⚠  Las ventas con factura electrónica NO se deben re-datar: su fecha de
--     emisión es la que validó la DIAN. 2.4 lista cuáles son para que las revise.
--  ⚠  Solo se re-fechan ventas NO ANULADAS (filtro en la sección 1). Si el turno
--     tiene anuladas y quiere incluirlas, quite ese filtro.
--  ⚠  TOLERA BASES DESACTUALIZADAS: `factura` y `devolucion` se saltan si no
--     existen (ver pg_temp.si_existe en la sección 2).
-- ============================================================================

BEGIN;

-- ── 0 · PARÁMETROS ──────────────────────────────────────────────────────────
-- Único punto a editar.
CREATE TEMP TABLE _p ON COMMIT DROP AS
SELECT 6::int            AS empresa_id,
       715::bigint       AS turno_caja_id,
       DATE '2026-01-15' AS nueva_fecha;


-- ── 1 · ALCANCE ─────────────────────────────────────────────────────────────
-- Se materializa el alcance antes de actualizar, para que los UPDATE no dependan
-- del estado en que van dejando las tablas.

CREATE TEMP TABLE _turno ON COMMIT DROP AS
SELECT t.id,
       t.caja_id,
       s.id        AS sucursal_id,
       s.empresa_id,
       t.fecha_apertura,
       t.estado
FROM turno_caja t
JOIN caja     c ON c.id = t.caja_id
JOIN sucursal s ON s.id = c.sucursal_id
WHERE t.id = (SELECT turno_caja_id FROM _p);

CREATE TEMP TABLE _venta ON COMMIT DROP AS
SELECT v.id,
       v.empresa_id,
       v.sucursal_id,
       v.prefijo,
       v.consecutivo,
       v.tipo_documento,
       v.estado_venta,
       v.total_pagar,
       v.fecha_emision AS fecha_anterior,
       ((v.fecha_emision AT TIME ZONE 'America/Bogota')::date) AS dia_anterior,
       -- Cuántos días se mueve esta venta. Se usa para correr vencimientos y
       -- documentos derivados los MISMOS días, no un número fijo.
       ((SELECT nueva_fecha FROM _p)::date
         - ((v.fecha_emision AT TIME ZONE 'America/Bogota')::date)) AS delta_dias
FROM venta v
WHERE v.empresa_id = (SELECT empresa_id FROM _p)
  AND v.turno_caja_id = (SELECT turno_caja_id FROM _p)
  AND COALESCE(v.estado_venta, 'COMPLETADA') <> 'ANULADA';

-- Asientos de esas ventas, incluyendo la reversa de la anulación.
CREATE TEMP TABLE _asiento ON COMMIT DROP AS
SELECT a.id,
       a.origen_id,
       a.tipo_origen,
       a.estado,
       a.fecha               AS fecha_anterior,
       a.periodo_contable_id AS periodo_anterior,
       a.total_debito,
       a.total_credito
FROM asiento_contable a
WHERE a.empresa_id = (SELECT empresa_id FROM _p)
  AND a.origen_id IN (SELECT id FROM _venta)
  AND regexp_replace(a.tipo_origen, '^ANULACION_', '') = 'VENTA';

CREATE TEMP TABLE _cxc ON COMMIT DROP AS
SELECT c.id,
       c.venta_id,
       c.estado,
       c.saldo_pendiente,
       c.fecha_emision     AS emision_anterior,
       c.fecha_vencimiento AS vencimiento_anterior,
       -- Días de plazo pactados, para correr el vencimiento los mismos días.
       CASE WHEN c.fecha_emision IS NOT NULL
             AND c.fecha_vencimiento IS NOT NULL
            THEN (c.fecha_vencimiento::date - c.fecha_emision::date)
       END AS dias_plazo
FROM cuentas_cobrar c
WHERE c.venta_id IN (SELECT id FROM _venta)
  AND c.deleted_at IS NULL;


-- ── 2 · DIAGNÓSTICOS (revisar ANTES de seguir) ───────────────────────────────

-- Helper: ejecuta la sentencia solo si la tabla existe.
CREATE OR REPLACE FUNCTION pg_temp.si_existe(tabla text, sentencia text)
RETURNS void AS $fn$
BEGIN
    IF to_regclass(tabla) IS NOT NULL THEN
        EXECUTE sentencia;
    ELSE
        RAISE NOTICE 'SALTADO (no existe la tabla %): %', tabla, left(sentencia, 60);
    END IF;
END;
$fn$ LANGUAGE plpgsql;

-- 2.0 El turno existe y es de la empresa indicada. Si de_la_empresa = 0, el
--     turno no existe o es de otra empresa, y _venta saldrá vacía.
SELECT 'alcance' AS que,
       (SELECT count(*) FROM _turno)                      AS turnos,
       (SELECT count(*) FROM _turno
         WHERE empresa_id = (SELECT empresa_id FROM _p))  AS de_la_empresa,
       (SELECT count(*) FROM _venta)                     AS ventas,
       (SELECT count(*) FROM _asiento)                   AS asientos,
       (SELECT count(*) FROM _cxc)                       AS cuentas_cobrar;

-- 2.1 Distribución por fecha actual. Si sale más de un día, el turno mezcló
--     fechas: el script las lleva TODAS a la misma fecha destino.
SELECT dia_anterior,
       count(*)         AS ventas,
       sum(total_pagar) AS total_pagar,
       min(fecha_anterior) AS hora_mas_antigua,
       max(fecha_anterior) AS hora_mas_reciente
FROM _venta
GROUP BY dia_anterior
ORDER BY 1;

-- 2.2 Estado de los asientos que se van a mover. 'ANULADO' + 'ANULACION_VENTA'
--     es un par que también se mueve, para que no quede en meses distintos.
SELECT tipo_origen, estado, count(*) AS asientos, sum(total_debito) AS debito
FROM _asiento
GROUP BY 1, 2
ORDER BY 1, 2;

-- 2.3 GUARD: el periodo destino debe existir y estar ABIERTO. Si no existe lo
--     crea la sección 3.0. Si está CERRADO, este DO aborta toda la transacción.
SELECT 'periodo destino' AS que,
       EXTRACT(YEAR  FROM nueva_fecha)::smallint AS anio,
       EXTRACT(MONTH FROM nueva_fecha)::smallint AS mes,
       (SELECT count(*) FROM periodo_contable pc
         WHERE pc.empresa_id = (SELECT empresa_id FROM _p)
           AND pc.anio = EXTRACT(YEAR  FROM (SELECT nueva_fecha FROM _p))::smallint
           AND pc.mes  = EXTRACT(MONTH FROM (SELECT nueva_fecha FROM _p))::smallint)
                                                      AS periodos_que_existen,
       COALESCE((SELECT string_agg(pc.estado, ',')
                   FROM periodo_contable pc
                  WHERE pc.empresa_id = (SELECT empresa_id FROM _p)
                    AND pc.anio = EXTRACT(YEAR  FROM (SELECT nueva_fecha FROM _p))::smallint
                    AND pc.mes  = EXTRACT(MONTH FROM (SELECT nueva_fecha FROM _p))::smallint),
                'NO EXISTE')                        AS estado_periodo
FROM _p;

DO $$
DECLARE v_cerrado int;
BEGIN
    SELECT count(*) INTO v_cerrado
      FROM periodo_contable pc
      JOIN _p p ON pc.empresa_id = p.empresa_id
                AND pc.anio = EXTRACT(YEAR  FROM p.nueva_fecha)::smallint
                AND pc.mes  = EXTRACT(MONTH FROM p.nueva_fecha)::smallint
     WHERE pc.estado = 'CERRADO';

    IF v_cerrado > 0 THEN
        RAISE EXCEPTION
            'ABORTADO: el periodo contable destino esta CERRADO. Re-datar hacia un mes cerrado no se permite.';
    END IF;
END $$;

-- 2.4 GUARD: ventas con factura electrónica. La fecha de emisión es la que
--     validó la DIAN; re-datarla rompe la trazabilidad. Si estas facturas están
--     ENVIADAS o ACEPTADAS, comente el UPDATE de la sección 4.2.
SELECT f.id     AS factura_id,
       v.id     AS venta_id,
       v.prefijo || '-' || v.consecutivo AS numero,
       v.fecha_emision,
       f.fecha_hora_emision,
       f.estado_dian,
       f.cufe
FROM _venta v
JOIN factura f ON f.venta_id = v.id
ORDER BY v.id;

-- 2.5 Aviso: los movimientos de inventario de esas ventas NO se van a mover.
SELECT 'inventario NO se toca' AS aviso,
       count(*) AS movimientos_en_los_dias_origenales
FROM movimiento_inventario
WHERE tipo_movimiento = 'VENTA'
  AND DATE_TRUNC('day', created_at)
      = ANY (SELECT DISTINCT dia_anterior::timestamp FROM _venta);


-- ── 3 · VENTA + ASIENTOS ────────────────────────────────────────────────────

-- 3.0 El mes destino debe existir como periodo. La V171 lo auto-crea al
--     contabilizar un asiento, pero esto es un UPDATE manual y no pasa por ahí.
INSERT INTO periodo_contable (empresa_id, anio, mes, estado, fecha_apertura,
                              creado_automatico, observaciones)
SELECT p.empresa_id,
       EXTRACT(YEAR  FROM p.nueva_fecha)::smallint,
       EXTRACT(MONTH FROM p.nueva_fecha)::smallint,
       'ABIERTO',
       date_trunc('month', p.nueva_fecha)::date,
       TRUE,
       'Creado por realinear_fecha_emision_venta_turno.sql'
FROM _p p
WHERE NOT EXISTS (
        SELECT 1 FROM periodo_contable pc
         WHERE pc.empresa_id = p.empresa_id
           AND pc.anio = EXTRACT(YEAR  FROM p.nueva_fecha)::smallint
           AND pc.mes  = EXTRACT(MONTH FROM p.nueva_fecha)::smallint);

-- 3.1 venta.fecha_emision.
--     Es la ÚNICA columna con zona horaria de la base, así que no se puede hacer
--     `nueva_fecha + hora`: hay que pasar a la hora de pared de America/Bogota,
--     cambiar el día y volver a la zona. Así se conserva la hora original de
--     cada venta y no se corre el instante.
--     Para poner una hora FIJA en vez de conservar la original, reemplace la
--     expresión por:  (DATE '2026-01-15 08:30:00') AT TIME ZONE 'America/Bogota'
UPDATE venta v
   SET fecha_emision = ((p.nueva_fecha::timestamp
                        + ((v.fecha_emision AT TIME ZONE 'America/Bogota')::time))
                        AT TIME ZONE 'America/Bogota')
  FROM _p p
 WHERE v.id IN (SELECT id FROM _venta)
   AND v.empresa_id = p.empresa_id;

-- 3.2 Asientos que originaron esas ventas. `fecha` es DATE, sin lío de zona.
--     fecha_vencimiento se corre los mismos delta_dias de su venta: es el
--     vencimiento del comprobante, no un dato de la venta.
--     periodo_contable_id se reapunta porque el periodo lo define la FECHA
--     (V171): sin esto el asiento de enero quedaría reportado en el mes viejo.
UPDATE asiento_contable a
   SET fecha             = (SELECT nueva_fecha FROM _p),
       fecha_vencimiento = CASE WHEN a.fecha_vencimiento IS NULL THEN NULL
                                ELSE (a.fecha_vencimiento + v.delta_dias)::date
                           END,
       periodo_contable_id = (
            SELECT pc.id
              FROM periodo_contable pc
              JOIN _p p ON pc.empresa_id = p.empresa_id
                        AND pc.anio = EXTRACT(YEAR  FROM p.nueva_fecha)::smallint
                        AND pc.mes  = EXTRACT(MONTH FROM p.nueva_fecha)::smallint
             LIMIT 1),
       updated_at = now()
  FROM _venta v
 WHERE a.id IN (SELECT id FROM _asiento)
   AND v.id = a.origen_id;


-- ── 4 · DOCUMENTOS DERIVADOS ────────────────────────────────────────────────

-- 4.1 Cartera. La cuenta por cobrar copia la fecha de la venta; el vencimiento
--     se corre los días de plazo pactados, no un número fijo.
UPDATE cuentas_cobrar c
   SET fecha_emision     = ((p.nueva_fecha::timestamp
                             + ((c.fecha_emision AT TIME ZONE 'America/Bogota')::time))
                             AT TIME ZONE 'America/Bogota'),
       fecha_vencimiento = CASE WHEN x.dias_plazo IS NULL THEN c.fecha_vencimiento
                                ELSE (((p.nueva_fecha + x.dias_plazo)::timestamp)
                                      + ((c.fecha_vencimiento AT TIME ZONE 'America/Bogota')::time))
                                      AT TIME ZONE 'America/Bogota'
                           END,
       updated_at = now()
  FROM _cxc x
  CROSS JOIN _p p
 WHERE c.id = x.id;


-- 4.2 Facturación electrónica. OJO: la fecha de emisión es la que validó la
--     DIAN. Si el diagnóstico 2.4 salió con filas y están ENVIADAS o ACEPTADAS,
--     comente este UPDATE.
SELECT pg_temp.si_existe('factura',
    'UPDATE factura f
        SET fecha_hora_emision = ((p.nueva_fecha::timestamp
                                   + ((f.fecha_hora_emision AT TIME ZONE ''America/Bogota'')::time))
                                   AT TIME ZONE ''America/Bogota'')
       FROM _p p
      WHERE f.venta_id IN (SELECT id FROM _venta)');

-- 4.3 Caja: NO hay nada que re-fechar, y es deliberado.
--     El arqueo NO lee la fecha de la venta: lee venta_pago unida al TURNO
--     (`venta_pago JOIN venta ON venta_pago.venta_id = venta.id WHERE
--     venta.turno_caja_id = ?`), y venta_pago no tiene columna de fecha. Como el
--     turno no se mueve, el arqueo sigue cuadrando igual.
--     `movimiento_caja.fecha` es el día en que la plata se movió físicamente:
--     tocarlo haría que el turno respondiera por plata de otro día. No se toca.
--     Este diagnóstico es por si alguien dejó filas a mano con origen_tipo
--     'VENTA' (el flujo normal NUNCA las crea): si sale > 0 y usted SÍ quiere
--     mover la fecha del documento, use el UPDATE opcional de abajo. Si lo que
--     quiere es que el turno cuadre con otro día, ese es otro problema (cierre
--     de caja / traslado de fondos), no esta query.
SELECT count(*)                              AS movimientos_con_origen_venta,
       count(*) FILTER (WHERE fecha_documento IS NULL) AS sin_fecha_documento,
       min(fecha) AS fecha_movimiento_min,
       max(fecha) AS fecha_movimiento_max
FROM movimiento_caja
WHERE origen_tipo = 'VENTA'
  AND origen_id IN (SELECT id FROM _venta);

-- UPDATE OPCIONAL (normalmente no aplica, ver arriba):
-- SELECT pg_temp.si_existe('movimiento_caja',
--     'UPDATE movimiento_caja m
--         SET fecha_documento = (m.fecha_documento + v.delta_dias)::date
--        FROM _venta v
--       WHERE m.origen_tipo = ''VENTA''
--         AND m.origen_id IN (SELECT id FROM _venta)');

-- 4.4 Devoluciones de estas ventas: se recorren los mismos delta_dias.
SELECT pg_temp.si_existe('devolucion',
    'UPDATE devolucion d
        SET fecha_devolucion = ((d.fecha_devolucion::date + v.delta_dias)::timestamp)
       FROM _venta v
      WHERE d.venta_id = v.id');


-- ── 5 · CONTEO DE LO TOCADO ─────────────────────────────────────────────────

SELECT 'venta'            AS tabla, count(*) AS filas FROM _venta
UNION ALL SELECT 'asiento_contable',   count(*) FROM _asiento
UNION ALL SELECT 'cuentas_cobrar',     count(*) FROM _cxc;


-- ── 6 · VERIFICACIÓN ────────────────────────────────────────────────────────

-- 6.1 Ventas: todas en la fecha nueva y con la hora original conservada.
SELECT count(*)                                           AS ventas,
       count(*) FILTER (WHERE v.fecha_emision::date
                          = (SELECT nueva_fecha FROM _p)) AS en_fecha_nueva,
       count(*) FILTER (WHERE (v.fecha_emision AT TIME ZONE 'America/Bogota')::time
                          = (o.fecha_anterior  AT TIME ZONE 'America/Bogota')::time)
                                                             AS horas_conservadas,
       min(v.fecha_emision) AS hora_min,
       max(v.fecha_emision) AS hora_max
FROM venta v
JOIN _venta o ON o.id = v.id;

-- 6.2 Asientos: deben quedar en la fecha nueva y en el periodo del mes destino.
SELECT a.tipo_origen,
       a.estado,
       count(*)                                           AS asientos,
       count(*) FILTER (WHERE a.fecha = (SELECT nueva_fecha FROM _p))
                                                             AS fecha_ok,
       count(*) FILTER (WHERE pc.anio
                          = EXTRACT(YEAR  FROM (SELECT nueva_fecha FROM _p))::smallint
                        AND pc.mes
                          = EXTRACT(MONTH FROM (SELECT nueva_fecha FROM _p))::smallint)
                                                             AS periodo_ok,
       count(*) FILTER (WHERE a.periodo_contable_id IS NULL) AS sin_periodo
FROM asiento_contable a
JOIN _venta v ON v.id = a.origen_id AND a.empresa_id = v.empresa_id
LEFT JOIN periodo_contable pc ON pc.id = a.periodo_contable_id
WHERE regexp_replace(a.tipo_origen, '^ANULACION_', '') = 'VENTA'
GROUP BY 1, 2
ORDER BY 1, 2;

-- 6.3 Debe dar 0: ningún asiento quedó con fecha distinta a la de su venta.
SELECT count(*) AS descuadradas
FROM _venta v
JOIN asiento_contable a
       ON a.empresa_id = v.empresa_id
      AND a.origen_id = v.id
      AND regexp_replace(a.tipo_origen, '^ANULACION_', '') = 'VENTA'
WHERE a.fecha IS DISTINCT FROM (v.fecha_emision AT TIME ZONE 'America/Bogota')::date;

-- 6.4 La partida doble sigue cuadrando. Este script solo mueve fechas, pero
--     verifíquelo: si diferencia <> 0 ya venía descuadrado desde antes.
SELECT count(*)                               AS asientos,
       sum(total_debito)                      AS debito,
       sum(total_credito)                     AS credito,
       sum(total_debito) - sum(total_credito) AS diferencia
FROM _asiento;

-- 6.5 Lo que NO se tocó, para que conste en el acta.
SELECT count(*)                AS movimientos_caja,
       min(fecha)              AS fecha_movimiento_min,
       max(fecha)              AS fecha_movimiento_max,
       count(*) FILTER (WHERE fecha = fecha_documento) AS con_fechas_iguales
FROM movimiento_caja
WHERE origen_tipo = 'VENTA'
  AND origen_id IN (SELECT id FROM _venta);


ROLLBACK;   -- simulación. Cambie por COMMIT cuando revisó las secciones 2 y 6.