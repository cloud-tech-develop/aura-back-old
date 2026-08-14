-- ============================================================================
--  LIMPIEZA TRANSACCIONAL POR EMPRESA
--
--  Borra: ventas, compras, devoluciones, facturas electrónicas, cuentas por
--         pagar (CxP), movimientos de caja, turnos/cierres de caja y los
--         asientos contables generados por todos esos documentos (incluidas
--         sus reversas ANULACION_*).
--
--  CONSERVA la CARTERA (CxC): las cuentas por cobrar, sus abonos y su gestión
--         de cobro NO se borran. Solo se les pone venta_id = NULL para soltar
--         la FK hacia la venta eliminada. Sus asientos (ABONO_COBRAR) también
--         se conservan.
--
--  NO toca: productos, inventario, movimiento_inventario, lotes, seriales,
--           gastos, mermas, nómina, tesorería, terceros, plan de cuentas,
--           órdenes de compra ni ninguna parametrización.
--
--  Motor: PostgreSQL.
--  Uso:   1) cambiar el empresa_id en la sección 0
--         2) ejecutar completo -> queda en ROLLBACK (simulacro)
--         3) revisar los conteos de las secciones 1 y 8
--         4) cambiar el ROLLBACK final por COMMIT y volver a ejecutar
--
--  TOLERA BASES DESACTUALIZADAS: si a la base le faltan migraciones (p.ej.
--  nota_electronica de la V136), esas tablas se saltan en vez de abortar.
--  La sección 0b lista exactamente qué se encontró y qué se va a saltar.
--
--  ⚠  HAGA BACKUP ANTES (pg_dump) — esto es un borrado físico, no lógico.
--  ⚠  Ejecutar con la aplicación detenida (hay caches y consecutivos en uso).
--  ⚠  El stock NO se recalcula: los movimiento_inventario de VENTA y COMPRA
--     se conservan y las existencias quedan como están hoy.
--  ⚠  Si una ejecución falla, la transacción queda abortada: haga ROLLBACK
--     antes de volver a correr el script.
-- ============================================================================

BEGIN;

-- ── 0 · PARÁMETRO ──────────────────────────────────────────────────────────
-- Único punto a editar: el id de la empresa a limpiar.
CREATE TEMP TABLE _p ON COMMIT DROP AS SELECT 6::int AS empresa_id;


-- ── 0b · COMPATIBILIDAD CON EL ESTADO DE LA BASE ───────────────────────────
-- Helpers que ejecutan una sentencia solo si la tabla existe.

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

-- Crea una tabla temporal de alcance; si la tabla origen no existe, crea una
-- temporal vacía para que el resto del script siga funcionando igual.
CREATE OR REPLACE FUNCTION pg_temp.alcance(temporal text, tabla text, consulta text)
RETURNS void AS $fn$
BEGIN
    IF to_regclass(tabla) IS NOT NULL THEN
        EXECUTE 'CREATE TEMP TABLE ' || temporal || ' ON COMMIT DROP AS ' || consulta;
    ELSE
        EXECUTE 'CREATE TEMP TABLE ' || temporal
             || ' ON COMMIT DROP AS SELECT NULL::bigint AS id WHERE false';
        RAISE NOTICE 'SALTADO (no existe la tabla %): alcance %', tabla, temporal;
    END IF;
END;
$fn$ LANGUAGE plpgsql;

-- Inventario de tablas: revise esta salida. 'FALTA' = migración no aplicada,
-- esa parte del script se salta sin borrar nada.
SELECT t AS tabla,
       CASE WHEN to_regclass(t) IS NULL THEN 'FALTA' ELSE 'ok' END AS estado
FROM unnest(ARRAY[
    'venta','venta_detalle','venta_pago','venta_detalle_serial','comision_venta',
    'compra','compra_detalle','compra_pago','orden_compra',
    'factura','factura_log','recibo_pago','nota_contable','nota_electronica',
    'devolucion','devolucion_detalle',
    'cuentas_cobrar','abonos_cobrar','gestion_cobro','solicitud_autorizacion_credito',
    'cuentas_pagar','abonos_pagar','anticipo_cruce',
    'caja','turno_caja','movimiento_caja','comprobante_caja',
    'asiento_contable','asiento_detalle','contabilidad_posting_log','extracto_linea',
    'pedido_vendedor'
]) AS t
ORDER BY 2, 1;


-- ── 1 · ALCANCE (ids afectados) ────────────────────────────────────────────
-- Se materializa el alcance primero para que los DELETE no dependan del
-- orden en que se van vaciando las tablas.

CREATE TEMP TABLE _venta ON COMMIT DROP AS
SELECT id FROM venta WHERE empresa_id = (SELECT empresa_id FROM _p);

CREATE TEMP TABLE _venta_detalle ON COMMIT DROP AS
SELECT id FROM venta_detalle WHERE venta_id IN (SELECT id FROM _venta);

CREATE TEMP TABLE _compra ON COMMIT DROP AS
SELECT id FROM compra WHERE empresa_id = (SELECT empresa_id FROM _p);

CREATE TEMP TABLE _factura ON COMMIT DROP AS
SELECT id FROM factura
WHERE empresa_id = (SELECT empresa_id FROM _p)
   OR venta_id IN (SELECT id FROM _venta);

SELECT pg_temp.alcance('_devolucion', 'devolucion', $q$
    SELECT id FROM devolucion
    WHERE empresa_id = (SELECT empresa_id FROM _p)
       OR venta_id IN (SELECT id FROM _venta)
$q$);

SELECT pg_temp.alcance('_nota_electronica', 'nota_electronica', $q$
    SELECT id FROM nota_electronica WHERE empresa_id = (SELECT empresa_id FROM _p)
$q$);

-- CARTERA (CxC): NO se borra. Estas son las cuentas por cobrar atadas a las
-- ventas que van a desaparecer; la FK cuentas_cobrar.venta_id -> venta obliga
-- a soltar el vínculo, así que a estas se les pondrá venta_id = NULL.
-- La cuenta, su saldo, sus abonos y su gestión de cobro quedan intactos.
CREATE TEMP TABLE _cxc_desligar ON COMMIT DROP AS
SELECT id FROM cuentas_cobrar WHERE venta_id IN (SELECT id FROM _venta);

-- CUENTAS POR PAGAR (CxP): todas las de la empresa.
CREATE TEMP TABLE _cxp ON COMMIT DROP AS
SELECT id FROM cuentas_pagar WHERE empresa_id = (SELECT empresa_id FROM _p);

CREATE TEMP TABLE _abono_pagar ON COMMIT DROP AS
SELECT id FROM abonos_pagar WHERE cuenta_pagar_id IN (SELECT id FROM _cxp);

-- turno_caja no tiene empresa_id: se llega por caja -> sucursal -> empresa
CREATE TEMP TABLE _turno ON COMMIT DROP AS
SELECT t.id
FROM turno_caja t
JOIN caja     c ON c.id = t.caja_id
JOIN sucursal s ON s.id = c.sucursal_id
WHERE s.empresa_id = (SELECT empresa_id FROM _p);

CREATE TEMP TABLE _mov_caja ON COMMIT DROP AS
SELECT id FROM movimiento_caja WHERE turno_caja_id IN (SELECT id FROM _turno);

-- Solo los cruces de anticipo contra CxP (que sí se borran). Los cruces
-- contra CxC se conservan porque la cartera se conserva.
SELECT pg_temp.alcance('_anticipo_cruce', 'anticipo_cruce', $q$
    SELECT id FROM anticipo_cruce
    WHERE empresa_id = (SELECT empresa_id FROM _p)
      AND cuenta_pagar_id IN (SELECT id FROM _cxp)
$q$);

-- ASIENTOS: asiento_contable es genérico (tipo_origen + origen_id). Se cruza
-- cada tipo contra los ids que se van a borrar. El regexp quita el prefijo
-- ANULACION_ para que las reversas caigan junto con su asiento original.
-- Lo que NO aparezca en esta lista (ABONO_COBRAR —la cartera se conserva—,
-- GASTO, MERMA, NOMINA, TESORERIA, MANUAL, DEPRECIACION, CIERRE, CIERRE_ANUAL,
-- OBLIGACION, ANTICIPO, CAUSACION…) queda intacto.
SELECT pg_temp.alcance('_asiento', 'asiento_contable', $q$
    SELECT a.id
    FROM asiento_contable a
    CROSS JOIN LATERAL (SELECT regexp_replace(a.tipo_origen, '^ANULACION_', '') AS t) x
    WHERE a.empresa_id = (SELECT empresa_id FROM _p)
      AND (
            (x.t = 'VENTA'           AND a.origen_id IN (SELECT id FROM _venta))
         OR (x.t = 'COMPRA'          AND a.origen_id IN (SELECT id FROM _compra))
         OR (x.t = 'DEVOLUCION'      AND a.origen_id IN (SELECT id FROM _devolucion))
         OR (x.t = 'ABONO_PAGAR'     AND a.origen_id IN (SELECT id FROM _abono_pagar))
         OR (x.t = 'MOVIMIENTO_CAJA' AND a.origen_id IN (SELECT id FROM _mov_caja))
         OR (x.t = 'DIFERENCIA_CAJA' AND a.origen_id IN (SELECT id FROM _turno))
         OR (x.t = 'ANTICIPO_CRUCE'  AND a.origen_id IN (SELECT id FROM _anticipo_cruce))
         OR (x.t IN ('NOTA_CREDITO', 'NOTA_DEBITO')
             AND a.origen_id IN (SELECT id FROM _nota_electronica))
      )
$q$);

-- Conteo ANTES de borrar (revisar esta salida antes de hacer COMMIT)
SELECT 'venta'                AS tabla, count(*) FROM _venta
UNION ALL SELECT 'venta_detalle',        count(*) FROM _venta_detalle
UNION ALL SELECT 'compra',               count(*) FROM _compra
UNION ALL SELECT 'factura',              count(*) FROM _factura
UNION ALL SELECT 'devolucion',           count(*) FROM _devolucion
UNION ALL SELECT 'nota_electronica',     count(*) FROM _nota_electronica
UNION ALL SELECT 'cuentas_cobrar (solo venta_id=NULL, NO se borran)',
                                         count(*) FROM _cxc_desligar
UNION ALL SELECT 'cuentas_pagar',        count(*) FROM _cxp
UNION ALL SELECT 'abonos_pagar',         count(*) FROM _abono_pagar
UNION ALL SELECT 'turno_caja',           count(*) FROM _turno
UNION ALL SELECT 'movimiento_caja',      count(*) FROM _mov_caja
UNION ALL SELECT 'anticipo_cruce',       count(*) FROM _anticipo_cruce
UNION ALL SELECT 'asiento_contable',     count(*) FROM _asiento;

-- Diagnóstico 1: la columna cuentas_cobrar.venta_id debe admitir NULL para
-- poder desligar la cartera. Si sale 'NO' hay que revisar antes de continuar
-- (el UPDATE fallaría y abortaría toda la transacción).
SELECT 'cuentas_cobrar.venta_id admite NULL' AS aviso, is_nullable
FROM information_schema.columns
WHERE table_name = 'cuentas_cobrar' AND column_name = 'venta_id';

-- Diagnóstico 2: movimientos de caja sin turno. No se pueden atribuir a una
-- empresa (movimiento_caja solo tiene turno_caja_id), así que NO se borran.
SELECT 'movimiento_caja SIN turno (no se borran)' AS aviso, count(*)
FROM movimiento_caja WHERE turno_caja_id IS NULL;

-- Diagnóstico 3: desglose de los asientos a borrar, por tipo.
SELECT a.tipo_origen, count(*) AS asientos, sum(a.total_debito) AS debito
FROM asiento_contable a
WHERE a.id IN (SELECT id FROM _asiento)
GROUP BY a.tipo_origen
ORDER BY 1;


-- ── 2 · HIJOS DE VENTA ─────────────────────────────────────────────────────

-- Comisiones generadas por la venta (FK NOT NULL a venta)
SELECT pg_temp.si_existe('comision_venta',
    'DELETE FROM comision_venta WHERE venta_id IN (SELECT id FROM _venta)');

-- Seriales entregados en la venta: se borra SOLO el vínculo venta<->serial.
-- El serial_producto (inventario) se conserva intacto.
SELECT pg_temp.si_existe('venta_detalle_serial',
    'DELETE FROM venta_detalle_serial
      WHERE venta_detalle_id IN (SELECT id FROM _venta_detalle)');

-- Devoluciones sobre esas ventas
SELECT pg_temp.si_existe('devolucion_detalle',
    'DELETE FROM devolucion_detalle WHERE devolucion_id IN (SELECT id FROM _devolucion)');
SELECT pg_temp.si_existe('devolucion',
    'DELETE FROM devolucion WHERE id IN (SELECT id FROM _devolucion)');

-- Facturación electrónica (factura -> venta) y sus documentos asociados
SELECT pg_temp.si_existe('factura_log',
    'DELETE FROM factura_log WHERE factura_id IN (SELECT id FROM _factura)');
SELECT pg_temp.si_existe('recibo_pago',
    'DELETE FROM recibo_pago WHERE factura_id IN (SELECT id FROM _factura)');
SELECT pg_temp.si_existe('nota_contable',
    'DELETE FROM nota_contable WHERE factura_id IN (SELECT id FROM _factura)
                                  OR compra_id  IN (SELECT id FROM _compra)');

DELETE FROM factura WHERE id IN (SELECT id FROM _factura);

-- Notas electrónicas DIAN (NC/ND emitidas contra esas facturas).
-- Comentar si se quieren conservar.
SELECT pg_temp.si_existe('nota_electronica',
    'DELETE FROM nota_electronica WHERE id IN (SELECT id FROM _nota_electronica)');

-- Documentos que apuntan a la venta pero NO deben borrarse: se desligan.
SELECT pg_temp.si_existe('pedido_vendedor',
    'UPDATE pedido_vendedor SET venta_id = NULL WHERE venta_id IN (SELECT id FROM _venta)');
SELECT pg_temp.si_existe('solicitud_autorizacion_credito',
    'UPDATE solicitud_autorizacion_credito SET venta_id = NULL
      WHERE venta_id IN (SELECT id FROM _venta)');


-- ── 3 · CARTERA (CxC): SE CONSERVA, SOLO SE DESLIGA ────────────────────────
-- La cuenta por cobrar, su saldo, sus abonos, su gestión de cobro y sus
-- asientos ABONO_COBRAR quedan como están. Únicamente se suelta la FK a la
-- venta que va a desaparecer.
--
-- Opcional: dejar rastro del documento eliminado en las observaciones.
-- Descomentar si se quiere conservar la trazabilidad del número de venta.
-- UPDATE cuentas_cobrar c
--    SET observaciones = concat_ws(' | ', c.observaciones,
--          'Venta origen eliminada: ' || coalesce(v.prefijo, '') || v.consecutivo)
--   FROM venta v
--  WHERE v.id = c.venta_id
--    AND c.id IN (SELECT id FROM _cxc_desligar);

UPDATE cuentas_cobrar
   SET venta_id = NULL
 WHERE id IN (SELECT id FROM _cxc_desligar);


-- ── 3b · CUENTAS POR PAGAR (CxP): SE BORRAN ────────────────────────────────

SELECT pg_temp.si_existe('anticipo_cruce',
    'DELETE FROM anticipo_cruce WHERE id IN (SELECT id FROM _anticipo_cruce)');

DELETE FROM abonos_pagar  WHERE id IN (SELECT id FROM _abono_pagar);
DELETE FROM cuentas_pagar WHERE id IN (SELECT id FROM _cxp);


-- ── 4 · VENTA ──────────────────────────────────────────────────────────────

DELETE FROM venta_pago    WHERE venta_id IN (SELECT id FROM _venta);
DELETE FROM venta_detalle WHERE id       IN (SELECT id FROM _venta_detalle);
DELETE FROM venta         WHERE id       IN (SELECT id FROM _venta);


-- ── 5 · COMPRA ─────────────────────────────────────────────────────────────
-- Las CxP de estas compras ya se borraron en la sección 3b.
-- La orden de compra se conserva: solo se desliga de la compra recibida.

SELECT pg_temp.si_existe('orden_compra',
    'UPDATE orden_compra SET compra_id = NULL WHERE compra_id IN (SELECT id FROM _compra)');
SELECT pg_temp.si_existe('compra_pago',
    'DELETE FROM compra_pago WHERE compra_id IN (SELECT id FROM _compra)');

DELETE FROM compra_detalle WHERE compra_id IN (SELECT id FROM _compra);
DELETE FROM compra         WHERE id        IN (SELECT id FROM _compra);


-- ── 6 · CAJA Y CIERRES ─────────────────────────────────────────────────────

-- Comprobantes de caja (ingreso/egreso impresos) de la empresa
SELECT pg_temp.si_existe('comprobante_caja',
    'DELETE FROM comprobante_caja WHERE empresa_id = (SELECT empresa_id FROM _p)');

-- Los abonos de cartera SOBREVIVEN pero tienen FK a turno_caja: hay que
-- desligarlos antes de borrar los turnos, o el DELETE de turno_caja falla.
UPDATE abonos_cobrar
   SET turno_caja_id = NULL
 WHERE turno_caja_id IN (SELECT id FROM _turno);

-- Movimientos y turnos (apertura + cierre) de caja
DELETE FROM movimiento_caja WHERE id IN (SELECT id FROM _mov_caja);
DELETE FROM turno_caja      WHERE id IN (SELECT id FROM _turno);


-- ── 7 · CONTABILIDAD ───────────────────────────────────────────────────────

-- Conciliación bancaria: soltar los matches contra detalles que van a morir
SELECT pg_temp.si_existe('extracto_linea', $q$
    UPDATE extracto_linea
       SET asiento_detalle_id = NULL,
           estado             = 'PENDIENTE'
     WHERE asiento_detalle_id IN (
            SELECT d.id FROM asiento_detalle d WHERE d.asiento_id IN (SELECT id FROM _asiento)
           )
$q$);

-- Bitácora de auto-posting de esos documentos
SELECT pg_temp.si_existe('contabilidad_posting_log', $q$
    DELETE FROM contabilidad_posting_log
     WHERE empresa_id = (SELECT empresa_id FROM _p)
       AND (asiento_id IN (SELECT id FROM _asiento)
            OR (regexp_replace(tipo_origen, '^ANULACION_', '') = 'VENTA'
                AND origen_id IN (SELECT id FROM _venta))
            OR (regexp_replace(tipo_origen, '^ANULACION_', '') = 'COMPRA'
                AND origen_id IN (SELECT id FROM _compra))
            OR (regexp_replace(tipo_origen, '^ANULACION_', '') = 'DEVOLUCION'
                AND origen_id IN (SELECT id FROM _devolucion))
            OR (regexp_replace(tipo_origen, '^ANULACION_', '') = 'ABONO_PAGAR'
                AND origen_id IN (SELECT id FROM _abono_pagar))
            OR (regexp_replace(tipo_origen, '^ANULACION_', '') = 'MOVIMIENTO_CAJA'
                AND origen_id IN (SELECT id FROM _mov_caja))
            OR (regexp_replace(tipo_origen, '^ANULACION_', '') = 'DIFERENCIA_CAJA'
                AND origen_id IN (SELECT id FROM _turno)))
$q$);

-- Asiento y sus líneas (asiento_detalle tiene ON DELETE CASCADE, se borra
-- explícito de todas formas por si el FK fue recreado por Hibernate)
SELECT pg_temp.si_existe('asiento_detalle',
    'DELETE FROM asiento_detalle WHERE asiento_id IN (SELECT id FROM _asiento)');
SELECT pg_temp.si_existe('asiento_contable',
    'DELETE FROM asiento_contable WHERE id IN (SELECT id FROM _asiento)');


-- ── 8 · VERIFICACIÓN (debe dar todo en 0) ──────────────────────────────────
SELECT 'venta'            AS tabla, count(*) FROM venta            WHERE empresa_id = (SELECT empresa_id FROM _p)
UNION ALL SELECT 'compra',          count(*) FROM compra           WHERE empresa_id = (SELECT empresa_id FROM _p)
UNION ALL SELECT 'cuentas_pagar',   count(*) FROM cuentas_pagar    WHERE empresa_id = (SELECT empresa_id FROM _p)
UNION ALL SELECT 'turno_caja',      count(*) FROM turno_caja       WHERE id IN (SELECT id FROM _turno)
UNION ALL SELECT 'movimiento_caja', count(*) FROM movimiento_caja  WHERE turno_caja_id IN (SELECT id FROM _turno)
UNION ALL SELECT 'asiento_contable',count(*) FROM asiento_contable WHERE id IN (SELECT id FROM _asiento)
-- esta debe seguir en 0: ninguna CxC puede quedar apuntando a una venta borrada
UNION ALL SELECT 'cuentas_cobrar aún ligadas a venta borrada',
                                    count(*) FROM cuentas_cobrar   WHERE id IN (SELECT id FROM _cxc_desligar)
                                                                     AND venta_id IS NOT NULL;

-- CARTERA CONSERVADA: debe seguir igual que antes de correr el script.
SELECT 'cuentas_cobrar vivas'          AS tabla, count(*), sum(saldo_pendiente) AS saldo
FROM cuentas_cobrar WHERE empresa_id = (SELECT empresa_id FROM _p)
UNION ALL
SELECT 'de ellas, desligadas de venta', count(*), sum(saldo_pendiente)
FROM cuentas_cobrar WHERE id IN (SELECT id FROM _cxc_desligar);

-- Lo que QUEDA vivo en contabilidad de esta empresa (gastos, nómina, tesorería,
-- manuales, depreciación, cierre anual…). Sirve para confirmar que no se
-- borró de más.
SELECT tipo_origen, count(*) AS asientos
FROM asiento_contable
WHERE empresa_id = (SELECT empresa_id FROM _p)
GROUP BY tipo_origen
ORDER BY 1;


-- ============================================================================
--  Cambiar por COMMIT cuando los conteos estén revisados.
-- ============================================================================
ROLLBACK;
-- COMMIT;
