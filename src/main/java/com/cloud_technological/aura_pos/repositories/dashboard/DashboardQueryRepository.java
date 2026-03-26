package com.cloud_technological.aura_pos.repositories.dashboard;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.dashboard.LoteVencimientoDto;
import com.cloud_technological.aura_pos.dto.dashboard.MovimientoRecienteDto;
import com.cloud_technological.aura_pos.dto.dashboard.ProductoStockBajoDto;
import com.cloud_technological.aura_pos.dto.dashboard.ResumenVentasDto;
import com.cloud_technological.aura_pos.dto.dashboard.TopProductoDto;
import com.cloud_technological.aura_pos.dto.dashboard.VentaRecienteDto;


@Repository
public class DashboardQueryRepository {
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public ResumenVentasDto resumenVentasHoy(Integer empresaId) {
        String sql = """
            SELECT
                COALESCE(SUM(total_pagar), 0) AS total,
                COUNT(*) AS cantidad,
                COALESCE(AVG(total_pagar), 0) AS promedio
            FROM venta
            WHERE empresa_id = :empresaId
            AND estado_venta = 'COMPLETADA'
            AND DATE(fecha_emision) = CURRENT_DATE
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        return jdbcTemplate.queryForObject(sql, params, new BeanPropertyRowMapper<>(ResumenVentasDto.class));
    }

    public ResumenVentasDto resumenVentasMes(Integer empresaId) {
        String sql = """
            SELECT
                COALESCE(SUM(total_pagar), 0) AS total,
                COUNT(*) AS cantidad,
                COALESCE(AVG(total_pagar), 0) AS promedio
            FROM venta
            WHERE empresa_id = :empresaId
            AND estado_venta = 'COMPLETADA'
            AND DATE_TRUNC('month', fecha_emision) = DATE_TRUNC('month', CURRENT_DATE)
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        return jdbcTemplate.queryForObject(sql, params, new BeanPropertyRowMapper<>(ResumenVentasDto.class));
    }

    public BigDecimal totalComprasMes(Integer empresaId) {
        String sql = """
            SELECT COALESCE(SUM(total), 0)
            FROM compra
            WHERE empresa_id = :empresaId
            AND estado = 'RECIBIDA'
            AND DATE_TRUNC('month', fecha) = DATE_TRUNC('month', CURRENT_DATE)
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        BigDecimal result = jdbcTemplate.queryForObject(sql, params, BigDecimal.class);
        return result != null ? result : BigDecimal.ZERO;
    }

    public List<ProductoStockBajoDto> stockBajo(Integer empresaId) {
        String sql;
        sql = """
                  SELECT
                      p.id AS producto_id,
                      p.nombre AS producto_nombre,
                      p.sku AS producto_sku,
                      s.id AS sucursal_id,
                      s.nombre AS sucursal_nombre,
                      i.stock_actual,
                      i.stock_minimo
                  FROM inventario i
                  INNER JOIN producto p ON i.producto_id = p.id
                  INNER JOIN sucursal s ON i.sucursal_id = s.id
                  WHERE s.empresa_id = :empresaId
                  AND i.stock_actual <= i.stock_minimo
                  AND i.stock_minimo > 0
                  AND p.deleted_at IS NULL
                  ORDER BY (i.stock_actual - i.stock_minimo) ASC
                  LIMIT 10
              """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(ProductoStockBajoDto.class));
    }

    public List<LoteVencimientoDto> lotesProximosVencer(Integer empresaId) {
        String sql = """
            SELECT
                l.id AS lote_id,
                l.codigo_lote,
                p.nombre AS producto_nombre,
                s.nombre AS sucursal_nombre,
                l.fecha_vencimiento,
                l.stock_actual,
                (l.fecha_vencimiento - CURRENT_DATE) AS dias_restantes
            FROM lote l
            INNER JOIN producto p ON l.producto_id = p.id
            INNER JOIN sucursal s ON l.sucursal_id = s.id
            WHERE s.empresa_id = :empresaId
            AND l.activo = true
            AND l.stock_actual > 0
            AND l.fecha_vencimiento BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '30 days'
            ORDER BY l.fecha_vencimiento ASC
            LIMIT 10
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(LoteVencimientoDto.class));
    }

    public List<VentaRecienteDto> ultimasVentas(Integer empresaId) {
        String sql = """
            SELECT
                v.id,
                v.prefijo,
                v.consecutivo,
                COALESCE(NULLIF(t.razon_social, ''), CONCAT(t.nombres, ' ', t.apellidos), 'Consumidor Final') AS cliente_nombre,
                v.total_pagar,
                v.fecha_emision,
                v.estado_venta
            FROM venta v
            LEFT JOIN tercero t ON v.cliente_id = t.id
            WHERE v.empresa_id = :empresaId
            ORDER BY v.id DESC
            LIMIT 10
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(VentaRecienteDto.class));
    }

    public List<TopProductoDto> topProductosMes(Integer empresaId) {
        String sql = """
            SELECT
                p.id AS producto_id,
                p.nombre AS producto_nombre,
                p.sku AS producto_sku,
                SUM(vd.cantidad) AS total_vendido,
                SUM(vd.subtotal_linea) AS total_ingresos
            FROM venta_detalle vd
            INNER JOIN venta v ON vd.venta_id = v.id
            INNER JOIN producto p ON vd.producto_id = p.id
            WHERE v.empresa_id = :empresaId
            AND v.estado_venta = 'COMPLETADA'
            AND DATE_TRUNC('month', v.fecha_emision) = DATE_TRUNC('month', CURRENT_DATE)
            GROUP BY p.id, p.nombre, p.sku
            ORDER BY total_ingresos DESC
            LIMIT 5
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(TopProductoDto.class));
    }

    public List<MovimientoRecienteDto> ultimosMovimientos(Integer empresaId) {
        String sql = """
            SELECT
                m.id,
                m.tipo_movimiento,
                p.nombre AS producto_nombre,
                s.nombre AS sucursal_nombre,
                m.cantidad,
                m.saldo_nuevo,
                m.created_at
            FROM movimiento_inventario m
            INNER JOIN producto p ON m.producto_id = p.id
            INNER JOIN sucursal s ON m.sucursal_id = s.id
            WHERE s.empresa_id = :empresaId
            ORDER BY m.id DESC
            LIMIT 10
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(MovimientoRecienteDto.class));
    }

    public BigDecimal totalInventarioCosto(Integer empresaId) {
        String sql = """
            SELECT COALESCE(SUM(i.stock_actual * p.costo), 0)
            FROM inventario i
            INNER JOIN producto p ON i.producto_id = p.id
            INNER JOIN sucursal s ON i.sucursal_id = s.id
            WHERE s.empresa_id = :empresaId
            AND p.deleted_at IS NULL
            AND p.activo = true
            AND p.costo IS NOT NULL
            AND p.costo > 0
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        BigDecimal result = jdbcTemplate.queryForObject(sql, params, BigDecimal.class);
        return result != null ? result : BigDecimal.ZERO;
    }

    // Ventas por día de la semana actual (para gráfica)
    public List<Map<String, Object>> ventasPorDiaSemana(Integer empresaId) {
        String sql = """
            SELECT
                TO_CHAR(fecha_emision, 'YYYY-MM-DD') AS fecha,
                TO_CHAR(fecha_emision, 'Day') AS dia_nombre,
                COALESCE(SUM(total_pagar), 0) AS total,
                COUNT(*) AS cantidad
            FROM venta
            WHERE empresa_id = :empresaId
            AND estado_venta = 'COMPLETADA'
            AND fecha_emision >= DATE_TRUNC('week', CURRENT_DATE)
            GROUP BY TO_CHAR(fecha_emision, 'YYYY-MM-DD'), TO_CHAR(fecha_emision, 'Day')
            ORDER BY fecha ASC
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        return jdbcTemplate.queryForList(sql, params);
    }

    // Ventas por método de pago del mes (para gráfica de torta)
    public List<Map<String, Object>> ventasPorMetodoPago(Integer empresaId) {
        String sql = """
            SELECT
                vp.metodo_pago,
                COALESCE(SUM(vp.monto), 0) AS total,
                COUNT(DISTINCT v.id) AS cantidad_ventas
            FROM venta_pago vp
            INNER JOIN venta v ON vp.venta_id = v.id
            WHERE v.empresa_id = :empresaId
            AND v.estado_venta = 'COMPLETADA'
            AND DATE_TRUNC('month', v.fecha_emision) = DATE_TRUNC('month', CURRENT_DATE)
            GROUP BY vp.metodo_pago
            ORDER BY total DESC
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        return jdbcTemplate.queryForList(sql, params);
    }
}
