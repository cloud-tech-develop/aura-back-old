package com.cloud_technological.aura_pos.repositories.cierre_contable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.cierre_contable.CierreContableDto;
import com.cloud_technological.aura_pos.dto.cierre_contable.MovimientoCierreDto;
import com.cloud_technological.aura_pos.dto.cierre_contable.ReporteIvaDto;

@Repository
public class CierreContableQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    public CierreContableDto construir(Integer empresaId, String fechaDesde, String fechaHasta) {

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("fechaDesde", fechaDesde)
                .addValue("fechaHasta", fechaHasta);

        // ── Ventas ────────────────────────────────────────────
        Map<String, Object> ventas = jdbc.queryForMap("""
            SELECT
                COUNT(v.id)                              AS cantidad_ventas,
                COALESCE(SUM(v.subtotal),        0)      AS total_ventas_bruto,
                COALESCE(SUM(v.descuento_total), 0)      AS total_descuentos,
                COALESCE(SUM(v.impuestos_total), 0)      AS total_impuestos,
                COALESCE(SUM(v.total_pagar),     0)      AS total_ventas_neto
            FROM venta v
            WHERE v.empresa_id   = :empresaId
              AND v.estado_venta = 'COMPLETADA'
              AND DATE(v.fecha_emision) BETWEEN CAST(:fechaDesde AS DATE) AND CAST(:fechaHasta AS DATE)
            """, params);

        // ── Compras (solo RECIBIDA) ───────────────────────────
        Map<String, Object> compras = jdbc.queryForMap("""
            SELECT
                COUNT(c.id)                    AS cantidad_compras,
                COALESCE(SUM(c.total), 0)      AS total_compras_neto
            FROM compra c
            WHERE c.empresa_id = :empresaId
              AND c.estado      = 'RECIBIDA'
              AND DATE(c.fecha) BETWEEN CAST(:fechaDesde AS DATE) AND CAST(:fechaHasta AS DATE)
            """, params);

        // ── Comisiones ────────────────────────────────────────
        Map<String, Object> comisiones = jdbc.queryForMap("""
            SELECT
                COUNT(cv.id)                              AS cantidad_comisiones,
                COALESCE(SUM(cv.valor_tecnico), 0)        AS total_comisiones_tecnicos
            FROM comision_venta cv
            JOIN venta v ON cv.venta_id = v.id
            WHERE v.empresa_id   = :empresaId
              AND v.estado_venta = 'COMPLETADA'
              AND DATE(v.fecha_emision) BETWEEN CAST(:fechaDesde AS DATE) AND CAST(:fechaHasta AS DATE)
            """, params);

        // ── Mermas (solo APROBADA) ────────────────────────────
        Map<String, Object> mermas = jdbc.queryForMap("""
            SELECT
                COUNT(m.id)                        AS cantidad_mermas,
                COALESCE(SUM(m.costo_total), 0)    AS total_mermas
            FROM merma m
            WHERE m.empresa_id = :empresaId
              AND m.estado      = 'APROBADA'
              AND DATE(m.fecha) BETWEEN CAST(:fechaDesde AS DATE) AND CAST(:fechaHasta AS DATE)
            """, params);

        // ── Movimientos de caja (ingresos / egresos manuales) ─
        Map<String, Object> movimientos = jdbc.queryForMap("""
            SELECT
                COALESCE(SUM(CASE WHEN mc.tipo = 'INGRESO' THEN mc.monto ELSE 0 END), 0) AS total_ingresos,
                COALESCE(SUM(CASE WHEN mc.tipo = 'EGRESO'  THEN mc.monto ELSE 0 END), 0) AS total_egresos,
                COUNT(CASE WHEN mc.tipo = 'INGRESO' THEN 1 END)                           AS cantidad_ingresos,
                COUNT(CASE WHEN mc.tipo = 'EGRESO'  THEN 1 END)                           AS cantidad_egresos
            FROM movimiento_caja mc
            JOIN turno_caja tc ON mc.turno_caja_id = tc.id
            JOIN caja       ca ON tc.caja_id       = ca.id
            JOIN sucursal    s ON ca.sucursal_id    = s.id
            WHERE s.empresa_id = :empresaId
              AND DATE(mc.created_at) BETWEEN CAST(:fechaDesde AS DATE) AND CAST(:fechaHasta AS DATE)
            """, params);

        // ── Detalle individual de movimientos ─────────────
        List<MovimientoCierreDto> detalleMovimientos = jdbc.query("""
            SELECT
                mc.tipo,
                mc.concepto,
                mc.monto,
                mc.created_at::TEXT  AS fecha,
                ca.nombre            AS caja_nombre,
                u.username           AS usuario_nombre
            FROM movimiento_caja mc
            JOIN turno_caja tc ON mc.turno_caja_id = tc.id
            JOIN caja       ca ON tc.caja_id       = ca.id
            JOIN sucursal    s ON ca.sucursal_id    = s.id
            JOIN usuario     u ON tc.usuario_id     = u.id
            WHERE s.empresa_id = :empresaId
              AND DATE(mc.created_at) BETWEEN CAST(:fechaDesde AS DATE) AND CAST(:fechaHasta AS DATE)
            ORDER BY mc.created_at
            """, params, new BeanPropertyRowMapper<>(MovimientoCierreDto.class));

        // ── Gastos del período ────────────────────────────────
        Map<String, Object> gastos = jdbc.queryForMap("""
            SELECT
                COUNT(g.id)                                                     AS cantidad_gastos,
                COALESCE(SUM(CASE WHEN g.deducible THEN g.monto ELSE 0 END), 0) AS total_gastos_deducibles,
                COALESCE(SUM(CASE WHEN NOT g.deducible THEN g.monto ELSE 0 END), 0) AS total_gastos_no_deducibles,
                COALESCE(SUM(g.monto), 0)                                       AS total_gastos
            FROM gasto g
            WHERE g.empresa_id = :empresaId
              AND g.estado = 'ACTIVO'
              AND g.fecha BETWEEN CAST(:fechaDesde AS DATE) AND CAST(:fechaHasta AS DATE)
            """, params);

        // ── CxC snapshot (deudas de clientes pendientes) ──────
        Map<String, Object> cxc = jdbc.queryForMap("""
            SELECT
                COUNT(*)                                              AS cxc_cantidad_activas,
                COUNT(CASE WHEN cc.fecha_vencimiento < CURRENT_DATE
                           AND cc.fecha_vencimiento IS NOT NULL
                           THEN 1 END)                               AS cxc_cantidad_vencidas,
                COALESCE(SUM(cc.total_deuda),      0)                AS cxc_total_deuda,
                COALESCE(SUM(cc.saldo_pendiente),  0)                AS cxc_saldo_pendiente
            FROM cuentas_cobrar cc
            WHERE cc.empresa_id    = :empresaId
              AND cc.deleted_at    IS NULL
              AND cc.saldo_pendiente > 0
            """, params);

        // ── CxP snapshot (deudas con proveedores pendientes) ──
        Map<String, Object> cxp = jdbc.queryForMap("""
            SELECT
                COUNT(*)                                              AS cxp_cantidad_activas,
                COUNT(CASE WHEN cp.fecha_vencimiento < CURRENT_DATE
                           AND cp.fecha_vencimiento IS NOT NULL
                           THEN 1 END)                               AS cxp_cantidad_vencidas,
                COALESCE(SUM(cp.total_deuda),      0)                AS cxp_total_deuda,
                COALESCE(SUM(cp.saldo_pendiente),  0)                AS cxp_saldo_pendiente
            FROM cuentas_pagar cp
            WHERE cp.empresa_id    = :empresaId
              AND cp.deleted_at    IS NULL
              AND cp.saldo_pendiente > 0
            """, params);

        // ── Armar DTO ─────────────────────────────────────────
        CierreContableDto dto = new CierreContableDto();
        dto.setFechaDesde(fechaDesde);
        dto.setFechaHasta(fechaHasta);

        // Ventas
        dto.setCantidadVentas(toInt(ventas.get("cantidad_ventas")));
        dto.setTotalVentasBruto(toBD(ventas.get("total_ventas_bruto")));
        dto.setTotalDescuentos(toBD(ventas.get("total_descuentos")));
        dto.setTotalImpuestos(toBD(ventas.get("total_impuestos")));
        dto.setTotalVentasNeto(toBD(ventas.get("total_ventas_neto")));

        // Compras
        dto.setCantidadCompras(toInt(compras.get("cantidad_compras")));
        dto.setTotalComprasNeto(toBD(compras.get("total_compras_neto")));

        // Comisiones
        dto.setCantidadComisiones(toInt(comisiones.get("cantidad_comisiones")));
        dto.setTotalComisionesTecnicos(toBD(comisiones.get("total_comisiones_tecnicos")));

        // Resultados
        BigDecimal ventasNeto     = dto.getTotalVentasNeto();
        BigDecimal comprasNeto    = dto.getTotalComprasNeto();
        BigDecimal comisionesTec  = dto.getTotalComisionesTecnicos();
        BigDecimal mermasTotal    = toBD(mermas.get("total_mermas"));
        BigDecimal utilBruta      = ventasNeto.subtract(comprasNeto);
        BigDecimal utilNeta       = utilBruta.subtract(comisionesTec).subtract(mermasTotal);

        dto.setUtilidadBruta(utilBruta);
        dto.setUtilidadNeta(utilNeta);
        dto.setMargenBruto(ventasNeto.compareTo(BigDecimal.ZERO) > 0
                ? utilBruta.multiply(BigDecimal.valueOf(100)).divide(ventasNeto, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        dto.setMargenNeto(ventasNeto.compareTo(BigDecimal.ZERO) > 0
                ? utilNeta.multiply(BigDecimal.valueOf(100)).divide(ventasNeto, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        // CxC
        dto.setCxcTotalDeuda(toBD(cxc.get("cxc_total_deuda")));
        dto.setCxcSaldoPendiente(toBD(cxc.get("cxc_saldo_pendiente")));
        dto.setCxcCantidadActivas(toInt(cxc.get("cxc_cantidad_activas")));
        dto.setCxcCantidadVencidas(toInt(cxc.get("cxc_cantidad_vencidas")));

        // CxP
        dto.setCxpTotalDeuda(toBD(cxp.get("cxp_total_deuda")));
        dto.setCxpSaldoPendiente(toBD(cxp.get("cxp_saldo_pendiente")));
        dto.setCxpCantidadActivas(toInt(cxp.get("cxp_cantidad_activas")));
        dto.setCxpCantidadVencidas(toInt(cxp.get("cxp_cantidad_vencidas")));

        // Posición neta
        dto.setPosicionNeta(dto.getCxcSaldoPendiente().subtract(dto.getCxpSaldoPendiente()));

        // Mermas
        dto.setCantidadMermas(toInt(mermas.get("cantidad_mermas")));
        dto.setTotalMermas(toBD(mermas.get("total_mermas")));

        // Movimientos de caja
        dto.setTotalIngresos(toBD(movimientos.get("total_ingresos")));
        dto.setTotalEgresos(toBD(movimientos.get("total_egresos")));
        dto.setCantidadIngresos(toInt(movimientos.get("cantidad_ingresos")));
        dto.setCantidadEgresos(toInt(movimientos.get("cantidad_egresos")));
        dto.setDetalleMovimientos(detalleMovimientos);

        // Gastos
        dto.setCantidadGastos(toInt(gastos.get("cantidad_gastos")));
        dto.setTotalGastosDeducibles(toBD(gastos.get("total_gastos_deducibles")));
        dto.setTotalGastosNoDeducibles(toBD(gastos.get("total_gastos_no_deducibles")));
        dto.setTotalGastos(toBD(gastos.get("total_gastos")));

        return dto;
    }

    public ReporteIvaDto reporteIva(Integer empresaId, String fechaDesde, String fechaHasta) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("fechaDesde", fechaDesde)
                .addValue("fechaHasta", fechaHasta);

        Map<String, Object> ventas = jdbc.queryForMap("""
            SELECT
                COALESCE(SUM(v.impuestos_total), 0) AS iva_ventas,
                CAST(COUNT(v.id) AS INT)             AS cantidad_ventas
            FROM venta v
            WHERE v.empresa_id = :empresaId
              AND v.estado_venta = 'COMPLETADA'
              AND DATE(v.fecha_emision) BETWEEN CAST(:fechaDesde AS DATE) AND CAST(:fechaHasta AS DATE)
            """, params);

        Map<String, Object> compras = jdbc.queryForMap("""
            SELECT
                COALESCE(SUM(c.impuestos_total), 0) AS iva_compras,
                CAST(COUNT(c.id) AS INT)             AS cantidad_compras
            FROM compra c
            WHERE c.empresa_id = :empresaId
              AND c.estado = 'RECIBIDA'
              AND DATE(c.fecha) BETWEEN CAST(:fechaDesde AS DATE) AND CAST(:fechaHasta AS DATE)
            """, params);

        BigDecimal ivaVentas  = toBD(ventas.get("iva_ventas"));
        BigDecimal ivaCompras = toBD(compras.get("iva_compras"));

        ReporteIvaDto dto = new ReporteIvaDto();
        dto.setFechaDesde(fechaDesde);
        dto.setFechaHasta(fechaHasta);
        dto.setIvaVentas(ivaVentas);
        dto.setCantidadVentas(toInt(ventas.get("cantidad_ventas")));
        dto.setIvaCompras(ivaCompras);
        dto.setCantidadCompras(toInt(compras.get("cantidad_compras")));
        dto.setIvaADeclararOPagarAlEstado(ivaVentas.subtract(ivaCompras));
        return dto;
    }

    private BigDecimal toBD(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        return new BigDecimal(v.toString());
    }

    private Integer toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Long l) return l.intValue();
        if (v instanceof Integer i) return i;
        return Integer.parseInt(v.toString());
    }
}
