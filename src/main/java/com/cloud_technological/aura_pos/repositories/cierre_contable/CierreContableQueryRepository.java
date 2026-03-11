package com.cloud_technological.aura_pos.repositories.cierre_contable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.cierre_contable.CierreContableDto;

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

        // ── Compras ───────────────────────────────────────────
        Map<String, Object> compras = jdbc.queryForMap("""
            SELECT
                COUNT(c.id)                    AS cantidad_compras,
                COALESCE(SUM(c.total), 0)      AS total_compras_neto
            FROM compra c
            WHERE c.empresa_id = :empresaId
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
        BigDecimal utilBruta      = ventasNeto.subtract(comprasNeto);
        BigDecimal utilNeta       = utilBruta.subtract(comisionesTec);

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
