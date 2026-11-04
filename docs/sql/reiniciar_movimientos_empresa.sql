-- =====================================================================================
-- Reiniciar una empresa desde cero: borra TODOS sus movimientos y deja su configuración.
-- =====================================================================================
--
-- SE BORRA: ventas, facturas, notas, cotizaciones, pedidos, compras, órdenes de compra,
--   devoluciones, gastos, causaciones, caja (turnos y movimientos), recibos y comprobantes,
--   cartera (CxC, CxP, abonos, acuerdos, castigos, intereses), anticipos, tesorería,
--   traslados de fondos, extractos, obligaciones financieras, inventario (kardex, lotes,
--   seriales, mermas, obsequios, consumo interno, traslados, reconteos, historial de costo),
--   contabilidad (asientos, períodos, cierres, conciliación fiscal, exógena, declaraciones,
--   certificados, diferidos, activos fijos y su depreciación), nómina (prenóminas, nóminas,
--   novedades, prestaciones, vacaciones, retiros, incrementos, nómina electrónica, PILA),
--   asistencia, comisiones, carritos abandonados, visitas, correos enviados y bitácoras.
--
-- SE CONSERVA: la empresa, su suscripción y módulos, usuarios, perfiles y permisos, sedes,
--   bodegas, cajas, plan de cuentas y su configuración, impuestos, retenciones, formas de
--   pago, cuentas bancarias, centros de costo, terceros, productos (con precios,
--   presentaciones y recetas), categorías, marcas, listas de precios, empleados, contratos,
--   cargos, conceptos de nómina, embargos, proyectos, rutas y plantillas.
--
-- SE PONE EN CERO: el stock de inventario, el saldo de las cuentas bancarias y el saldo de
--   los embargos (vuelve al valor total). El costo de los productos NO se toca.
--
-- NO SE TOCA: los consecutivos de facturación electrónica y de nómina electrónica. Lo que
--   se envió a la DIAN no se puede reutilizar: la numeración sigue donde iba.
--
-- CÓMO CORRERLO
--   0. Saque un respaldo de la base (pg_dump) antes de empezar.
--   1. Cambie v_empresa_id y v_nit abajo. Si no coinciden con la empresa, el script aborta.
--   2. Ensayo: corra todo tal cual (termina en ROLLBACK). Revise los avisos (NOTICE) con lo
--      que se borraría de cada tabla.
--   3. Si está bien, cambie el ROLLBACK final por COMMIT y vuelva a correrlo.
--   Si algo falla a mitad, la transacción entera se deshace: no queda a medias.
-- =====================================================================================

BEGIN;

DO $$
DECLARE
    v_empresa_id INTEGER := 0;              -- <<< id de la empresa
    v_nit        TEXT    := '000000000';    -- <<< NIT de la empresa (sin dígito de verificación)
    v_nombre     TEXT;
    v_nit_real   TEXT;
    v_tabla      TEXT;
    v_donde      TEXT;
    v_filas      BIGINT;
    v_total      BIGINT := 0;
    v_pasos      TEXT[][] := ARRAY[
        ['abonos_cobrar', 'cuenta_cobrar_id IN (SELECT id FROM cuentas_cobrar WHERE empresa_id = $1)'],
        ['abonos_pagar', 'cuenta_pagar_id IN (SELECT id FROM cuentas_pagar WHERE empresa_id = $1)'],
        ['activo_fijo_adicion', 'empresa_id = $1'],
        ['activo_fijo_mantenimiento', 'empresa_id = $1'],
        ['acuerdo_pago_cuenta', 'acuerdo_pago_id IN (SELECT id FROM acuerdo_pago WHERE empresa_id = $1)'],
        ['acuerdo_pago_cuota', 'acuerdo_pago_id IN (SELECT id FROM acuerdo_pago WHERE empresa_id = $1)'],
        ['anticipo_cruce', 'empresa_id = $1'],
        ['asiento_detalle', 'asiento_id IN (SELECT id FROM asiento_contable WHERE empresa_id = $1)'],
        ['asistencia_alerta', 'empresa_id = $1'],
        ['asistencia_frente', 'empresa_id = $1'],
        ['asistencia_frente_aprobacion', 'empresa_id = $1'],
        ['asistencia_frente_detalle', 'empresa_id = $1'],
        ['asistencia_marcaje', 'empresa_id = $1'],
        ['asistencia_novedad_nomina', 'empresa_id = $1'],
        ['asistencia_soporte_pdf', 'empresa_id = $1'],
        ['auditoria_evento', 'empresa_id = $1'],
        ['auditoria_nomina_asistencia', 'empresa_id = $1'],
        ['autorizacion', 'empresa_id = $1'],
        ['autorizacion_liquidacion_excepcional', 'empresa_id = $1'],
        ['carrito_abandonado_item', 'carrito_id IN (SELECT id FROM carrito_abandonado WHERE empresa_id = $1)'],
        ['cartera_castigo', 'empresa_id = $1'],
        ['causacion_ejecucion', 'empresa_id = $1'],
        ['causacion_gasto_linea', 'causacion_id IN (SELECT id FROM causacion_gasto WHERE empresa_id = $1)'],
        ['certificado_emitido', 'empresa_id = $1'],
        ['cierre_anual', 'empresa_id = $1'],
        ['comision_venta', 'empresa_id = $1'],
        ['compra_detalle_lote', 'lote_id IN (SELECT id FROM lote WHERE empresa_id = $1)'],
        ['compra_pago', 'compra_id IN (SELECT id FROM compra WHERE empresa_id = $1)'],
        ['comprobante_caja', 'empresa_id = $1'],
        ['conciliacion_fiscal_ajuste', 'conciliacion_id IN (SELECT id FROM conciliacion_fiscal WHERE empresa_id = $1)'],
        ['conciliacion_fiscal_cuenta', 'conciliacion_id IN (SELECT id FROM conciliacion_fiscal WHERE empresa_id = $1)'],
        ['consumo_interno_detalle', 'consumo_interno_id IN (SELECT id FROM consumo_interno WHERE empresa_id = $1)'],
        ['contabilidad_posting_log', 'empresa_id = $1'],
        ['correo_envio', 'empresa_id = $1'],
        ['cotizacion_detalle', 'cotizacion_id IN (SELECT id FROM cotizacion WHERE empresa_id = $1)'],
        ['cuota_amortizacion', 'obligacion_id IN (SELECT id FROM obligacion_financiera WHERE empresa_id = $1)'],
        ['declaracion_pago_cuenta', 'pago_id IN (SELECT id FROM declaracion_pago WHERE empresa_id = $1)'],
        ['depreciacion_periodo', 'empresa_id = $1'],
        ['deterioro_calculo', 'empresa_id = $1'],
        ['devolucion_detalle', 'devolucion_id IN (SELECT id FROM devolucion WHERE empresa_id = $1)'],
        ['diferido', 'empresa_id = $1'],
        ['diferido_amortizacion', 'empresa_id = $1'],
        ['dividendo_pago', 'empresa_id = $1'],
        ['documento_lote', 'lote_id IN (SELECT id FROM lote WHERE empresa_id = $1)'],
        ['documento_relacion', 'empresa_id = $1'],
        ['documento_serial', 'serial_id IN (SELECT id FROM serial_producto WHERE empresa_id = $1)'],
        ['documento_soporte', 'empresa_id = $1'],
        ['embargo_descuento', 'embargo_id IN (SELECT id FROM embargo WHERE empresa_id = $1) OR nomina_id IN (SELECT id FROM nomina WHERE empresa_id = $1)'],
        ['error_log', 'empresa_id = $1'],
        ['exogena_error', 'empresa_id = $1'],
        ['exogena_linea', 'lote_id IN (SELECT id FROM exogena_lote WHERE empresa_id = $1)'],
        ['extracto_linea', 'extracto_id IN (SELECT id FROM extracto_bancario WHERE empresa_id = $1)'],
        ['factura_log', 'factura_id IN (SELECT id FROM factura WHERE empresa_id = $1)'],
        ['factura_venta_detalle', 'factura_venta_id IN (SELECT id FROM factura_venta WHERE empresa_id = $1)'],
        ['gasto', 'empresa_id = $1'],
        ['gestion_cobro', 'empresa_id = $1'],
        ['historial_credito', 'empresa_id = $1'],
        ['interes_mora_detalle', 'interes_mora_id IN (SELECT id FROM interes_mora WHERE empresa_id = $1)'],
        ['inventario_consumo_componente', 'empresa_id = $1'],
        ['liquidacion_prestacion_detalle', 'liquidacion_id IN (SELECT id FROM liquidacion_prestacion WHERE empresa_id = $1)'],
        ['lote_ajuste', 'empresa_id = $1'],
        ['merma_detalle', 'merma_id IN (SELECT id FROM merma WHERE empresa_id = $1) OR producto_id IN (SELECT id FROM producto WHERE empresa_id = $1)'],
        ['movimiento_inventario', 'bodega_id IN (SELECT id FROM bodega WHERE empresa_id = $1)'],
        ['nomina_detalle', 'nomina_id IN (SELECT id FROM nomina WHERE empresa_id = $1)'],
        ['nomina_electronica_log', 'nomina_electronica_id IN (SELECT id FROM nomina_electronica WHERE empresa_id = $1)'],
        ['nomina_incremento_detalle', 'incremento_id IN (SELECT id FROM nomina_incremento WHERE empresa_id = $1)'],
        ['nomina_retiro', 'empresa_id = $1'],
        ['nomina_vacacion', 'empresa_id = $1'],
        ['nota_contable', 'usuario_id IN (SELECT id FROM usuario WHERE empresa_id = $1) OR compra_id IN (SELECT id FROM compra WHERE empresa_id = $1) OR factura_id IN (SELECT id FROM factura WHERE empresa_id = $1)'],
        ['nota_diario_soporte', 'empresa_id = $1'],
        ['nota_electronica', 'empresa_id = $1'],
        ['obsequio_detalle', 'obsequio_id IN (SELECT id FROM obsequio WHERE empresa_id = $1)'],
        ['orden_compra_detalle', 'orden_compra_id IN (SELECT id FROM orden_compra WHERE empresa_id = $1)'],
        ['pedido_vendedor_detalle', 'pedido_vendedor_id IN (SELECT id FROM pedido_vendedor WHERE empresa_id = $1)'],
        ['periodo_asistencia', 'empresa_id = $1'],
        ['periodo_contable_evento', 'empresa_id = $1'],
        ['pila_archivo', 'pila_encabezado_id IN (SELECT id FROM pila_encabezado WHERE empresa_id = $1)'],
        ['pila_cotizante', 'pila_planilla_id IN (SELECT id FROM pila_planilla WHERE pila_encabezado_id IN (SELECT id FROM pila_encabezado WHERE empresa_id = $1))'],
        ['proceso_nomina', 'empresa_id = $1'],
        ['producto_cambio_unidad', 'empresa_id = $1'],
        ['producto_costo_historial', 'empresa_id = $1'],
        ['recibo_caja_aplicacion', 'recibo_caja_id IN (SELECT id FROM recibo_caja WHERE empresa_id = $1) OR cuenta_cobrar_id IN (SELECT id FROM cuentas_cobrar WHERE empresa_id = $1)'],
        ['recibo_pago', 'factura_id IN (SELECT id FROM factura WHERE empresa_id = $1)'],
        ['reconteo_detalles', 'reconteo_id IN (SELECT id FROM reconteos WHERE empresa_id = $1)'],
        ['score_crediticio_log', 'empresa_id = $1'],
        ['solicitud_autorizacion_credito', 'empresa_id = $1'],
        ['sync_outbox', 'empresa_id = $1'],
        ['traslado_cuenta_log', 'empresa_id = $1'],
        ['traslado_detalle', 'traslado_id IN (SELECT id FROM traslado WHERE empresa_id = $1) OR producto_id IN (SELECT id FROM producto WHERE empresa_id = $1)'],
        ['traslado_fondos', 'empresa_id = $1'],
        ['venta_detalle_serial', 'venta_detalle_id IN (SELECT id FROM venta_detalle WHERE venta_id IN (SELECT id FROM venta WHERE empresa_id = $1)) OR serial_producto_id IN (SELECT id FROM serial_producto WHERE empresa_id = $1)'],
        ['venta_pago', 'venta_id IN (SELECT id FROM venta WHERE empresa_id = $1)'],
        ['visitas', 'empresa_id = $1'],
        ['activo_fijo', 'empresa_id = $1'],
        ['acuerdo_pago', 'empresa_id = $1'],
        ['asiento_contable', 'empresa_id = $1'],
        ['asistencia_incidencia', 'empresa_id = $1'],
        ['carrito_abandonado', 'empresa_id = $1'],
        ['causacion_gasto', 'empresa_id = $1'],
        ['comision_liquidacion', 'empresa_id = $1'],
        ['compra_detalle', 'compra_id IN (SELECT id FROM compra WHERE empresa_id = $1) OR producto_id IN (SELECT id FROM producto WHERE empresa_id = $1)'],
        ['conciliacion_fiscal', 'empresa_id = $1'],
        ['consumo_interno', 'empresa_id = $1'],
        ['cotizacion', 'empresa_id = $1'],
        ['declaracion_pago', 'empresa_id = $1'],
        ['devolucion', 'empresa_id = $1'],
        ['distribucion_utilidades', 'empresa_id = $1'],
        ['exogena_lote', 'empresa_id = $1'],
        ['extracto_bancario', 'empresa_id = $1'],
        ['factura', 'empresa_id = $1'],
        ['factura_venta', 'empresa_id = $1'],
        ['interes_mora', 'empresa_id = $1'],
        ['liquidacion_prestacion', 'empresa_id = $1'],
        ['merma', 'empresa_id = $1'],
        ['nomina_electronica', 'empresa_id = $1'],
        ['nomina_incremento', 'empresa_id = $1'],
        ['nomina_novedad', 'nomina_id IN (SELECT id FROM nomina WHERE empresa_id = $1)'],
        ['obligacion_financiera', 'empresa_id = $1'],
        ['obsequio', 'empresa_id = $1'],
        ['orden_compra', 'empresa_id = $1'],
        ['pedido_vendedor', 'empresa_id = $1'],
        ['pila_planilla', 'pila_encabezado_id IN (SELECT id FROM pila_encabezado WHERE empresa_id = $1)'],
        ['recibo_caja', 'empresa_id = $1'],
        ['reconteos', 'empresa_id = $1'],
        ['serial_producto', 'empresa_id = $1'],
        ['traslado', 'empresa_id = $1'],
        ['venta_detalle', 'venta_id IN (SELECT id FROM venta WHERE empresa_id = $1) OR producto_id IN (SELECT id FROM producto WHERE empresa_id = $1)'],
        ['anticipo', 'empresa_id = $1'],
        ['asistencia_dia', 'empresa_id = $1'],
        ['lote', 'empresa_id = $1'],
        ['movimiento_caja', 'turno_caja_id IN (SELECT id FROM turno_caja WHERE caja_id IN (SELECT id FROM caja WHERE sucursal_id IN (SELECT id FROM sucursal WHERE empresa_id = $1)) OR usuario_id IN (SELECT id FROM usuario WHERE empresa_id = $1))'],
        ['nomina', 'empresa_id = $1'],
        ['periodo_contable', 'empresa_id = $1'],
        ['pila_encabezado', 'empresa_id = $1'],
        ['tesoreria_movimiento', 'empresa_id = $1'],
        ['cuentas_cobrar', 'empresa_id = $1'],
        ['cuentas_pagar', 'empresa_id = $1'],
        ['nomina_documento', 'empresa_id = $1'],
        ['compra', 'empresa_id = $1'],
        ['periodo_nomina', 'empresa_id = $1'],
        ['venta', 'empresa_id = $1'],
        ['turno_caja', 'caja_id IN (SELECT id FROM caja WHERE sucursal_id IN (SELECT id FROM sucursal WHERE empresa_id = $1)) OR usuario_id IN (SELECT id FROM usuario WHERE empresa_id = $1)']
    ];
BEGIN
    SELECT razon_social, nit INTO v_nombre, v_nit_real FROM empresa WHERE id = v_empresa_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'No existe la empresa %', v_empresa_id;
    END IF;
    IF regexp_replace(coalesce(v_nit_real, ''), '[^0-9]', '', 'g')
       NOT LIKE regexp_replace(v_nit, '[^0-9]', '', 'g') || '%' THEN
        RAISE EXCEPTION 'El NIT % no corresponde a la empresa % (%, NIT %)', v_nit, v_empresa_id, v_nombre, v_nit_real;
    END IF;
    RAISE NOTICE 'Reiniciando movimientos de % (id %, NIT %)', v_nombre, v_empresa_id, v_nit_real;

    FOR i IN 1 .. array_length(v_pasos, 1) LOOP
        v_tabla := v_pasos[i][1];
        v_donde := v_pasos[i][2];
        -- Una base atrasada en migraciones puede no tener todavía alguna tabla.
        IF to_regclass('public.' || v_tabla) IS NULL THEN
            RAISE NOTICE '   (no existe la tabla %, se omite)', v_tabla;
            CONTINUE;
        END IF;
        EXECUTE format('DELETE FROM %I WHERE %s', v_tabla, v_donde) USING v_empresa_id;
        GET DIAGNOSTICS v_filas = ROW_COUNT;
        v_total := v_total + v_filas;
        IF v_filas > 0 THEN
            RAISE NOTICE '% filas borradas de %', lpad(v_filas::TEXT, 8), v_tabla;
        END IF;
    END LOOP;

    -- Saldos que viven en tablas que se conservan.
    UPDATE inventario SET stock_actual = 0
     WHERE bodega_id IN (SELECT id FROM bodega WHERE empresa_id = v_empresa_id);
    GET DIAGNOSTICS v_filas = ROW_COUNT;
    RAISE NOTICE '% filas de inventario con stock en cero', lpad(v_filas::TEXT, 8);

    UPDATE cuenta_bancaria SET saldo_actual = 0, saldo_inicial = 0 WHERE empresa_id = v_empresa_id;
    GET DIAGNOSTICS v_filas = ROW_COUNT;
    RAISE NOTICE '% cuentas bancarias con saldo en cero', lpad(v_filas::TEXT, 8);

    UPDATE embargo SET saldo = valor_total WHERE empresa_id = v_empresa_id AND valor_total IS NOT NULL;
    GET DIAGNOSTICS v_filas = ROW_COUNT;
    RAISE NOTICE '% embargos con el saldo devuelto al valor total', lpad(v_filas::TEXT, 8);

    RAISE NOTICE 'Total: % filas borradas', v_total;
END $$;

ROLLBACK;   -- <<< ensayo. Cuando los conteos estén bien, cámbielo por COMMIT.
