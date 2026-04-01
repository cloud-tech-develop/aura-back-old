package com.cloud_technological.aura_pos.repositories.reportes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.reportes.ReporteMargenesProductoDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteResumenAvanzadoDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteRotacionInventarioDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteTopProductoDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteVentasCategoriaDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteVentasVendedorDto;

@Repository
public class ReporteAvanzadoQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    // ── Ventas por categoría ──────────────────────────────────────────────────

    public List<ReporteVentasCategoriaDto> ventasPorCategoria(Integer empresaId,
            LocalDate desde, LocalDate hasta) {
        String sql = """
                SELECT
                    COALESCE(c.id, 0) AS categoria_id,
                    COALESCE(c.nombre, 'Sin categoría') AS categoria_nombre,
                    COUNT(DISTINCT v.id) AS total_ventas,
                    SUM(vd.cantidad) AS total_unidades,
                    SUM(vd.subtotal_linea) AS ingresos,
                    SUM(vd.cantidad * COALESCE(p.costo, 0)) AS costo_estimado,
                    SUM(vd.subtotal_linea) - SUM(vd.cantidad * COALESCE(p.costo, 0)) AS margen_bruto
                FROM venta v
                INNER JOIN venta_detalle vd ON vd.venta_id = v.id
                INNER JOIN producto p ON vd.producto_id = p.id
                LEFT JOIN categoria c ON p.categoria_id = c.id
                WHERE v.empresa_id = :empresaId
                  AND v.estado_venta = 'COMPLETADA'
                  AND v.fecha_emision::date BETWEEN :desde AND :hasta
                GROUP BY c.id, c.nombre
                ORDER BY ingresos DESC
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("desde", desde)
                .addValue("hasta", hasta);
        return jdbc.query(sql, params, new BeanPropertyRowMapper<>(ReporteVentasCategoriaDto.class));
    }

    // ── Top productos ─────────────────────────────────────────────────────────

    public List<ReporteTopProductoDto> topProductos(Integer empresaId,
            LocalDate desde, LocalDate hasta, int limite) {
        String sql = """
                SELECT
                    p.id AS producto_id,
                    p.nombre AS producto_nombre,
                    p.sku,
                    COALESCE(c.nombre, 'Sin categoría') AS categoria_nombre,
                    SUM(vd.cantidad) AS cantidad_vendida,
                    SUM(vd.subtotal_linea) AS ingresos,
                    ROUND(AVG(vd.precio_unitario), 2) AS precio_promedio,
                    COUNT(DISTINCT v.id) AS numero_pedidos
                FROM venta v
                INNER JOIN venta_detalle vd ON vd.venta_id = v.id
                INNER JOIN producto p ON vd.producto_id = p.id
                LEFT JOIN categoria c ON p.categoria_id = c.id
                WHERE v.empresa_id = :empresaId
                  AND v.estado_venta = 'COMPLETADA'
                  AND v.fecha_emision::date BETWEEN :desde AND :hasta
                GROUP BY p.id, p.nombre, p.sku, c.nombre
                ORDER BY ingresos DESC
                LIMIT :limite
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("desde", desde)
                .addValue("hasta", hasta)
                .addValue("limite", limite);
        return jdbc.query(sql, params, new BeanPropertyRowMapper<>(ReporteTopProductoDto.class));
    }

    // ── Ventas por vendedor ───────────────────────────────────────────────────

    public List<ReporteVentasVendedorDto> ventasPorVendedor(Integer empresaId,
            LocalDate desde, LocalDate hasta) {
        String sql = """
                SELECT
                    u.id AS usuario_id,
                    COALESCE(t.razon_social,
                        TRIM(COALESCE(t.nombres,'') || ' ' || COALESCE(t.apellidos,'')),
                        u.username) AS vendedor_nombre,
                    COUNT(v.id) AS total_ventas,
                    SUM(v.total_pagar) AS ingresos,
                    ROUND(AVG(v.total_pagar), 2) AS ticket_promedio,
                    SUM(v.descuento_total) AS descuento_total
                FROM venta v
                INNER JOIN usuario u ON v.usuario_id = u.id
                LEFT JOIN tercero t ON u.tercero_id = t.id
                WHERE v.empresa_id = :empresaId
                  AND v.estado_venta = 'COMPLETADA'
                  AND v.fecha_emision::date BETWEEN :desde AND :hasta
                GROUP BY u.id, u.username, t.razon_social, t.nombres, t.apellidos
                ORDER BY ingresos DESC
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("desde", desde)
                .addValue("hasta", hasta);
        return jdbc.query(sql, params, new BeanPropertyRowMapper<>(ReporteVentasVendedorDto.class));
    }

    // ── Márgenes por producto ─────────────────────────────────────────────────

    public List<ReporteMargenesProductoDto> margenesPorProducto(Integer empresaId,
            LocalDate desde, LocalDate hasta) {
        String sql = """
                SELECT
                    p.id AS producto_id,
                    p.nombre AS producto_nombre,
                    p.sku,
                    COALESCE(c.nombre, 'Sin categoría') AS categoria_nombre,
                    ROUND(AVG(vd.precio_unitario), 2) AS precio_venta,
                    COALESCE(p.costo, 0) AS costo,
                    ROUND(AVG(vd.precio_unitario) - COALESCE(p.costo, 0), 2) AS margen_bruto,
                    CASE WHEN AVG(vd.precio_unitario) > 0
                        THEN ROUND(((AVG(vd.precio_unitario) - COALESCE(p.costo, 0))
                            / AVG(vd.precio_unitario)) * 100, 2)
                        ELSE 0
                    END AS margen_porcentaje,
                    SUM(vd.cantidad) AS cantidad_vendida,
                    SUM(vd.subtotal_linea) AS ingreso_total
                FROM venta v
                INNER JOIN venta_detalle vd ON vd.venta_id = v.id
                INNER JOIN producto p ON vd.producto_id = p.id
                LEFT JOIN categoria c ON p.categoria_id = c.id
                WHERE v.empresa_id = :empresaId
                  AND v.estado_venta = 'COMPLETADA'
                  AND v.fecha_emision::date BETWEEN :desde AND :hasta
                GROUP BY p.id, p.nombre, p.sku, c.nombre, p.costo
                ORDER BY ingreso_total DESC
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("desde", desde)
                .addValue("hasta", hasta);
        return jdbc.query(sql, params, new BeanPropertyRowMapper<>(ReporteMargenesProductoDto.class));
    }

    // ── Rotación de inventario ────────────────────────────────────────────────

    public List<ReporteRotacionInventarioDto> rotacionInventario(Integer empresaId) {
        String sql = """
                SELECT
                    p.id AS producto_id,
                    p.nombre AS producto_nombre,
                    p.sku,
                    COALESCE(c.nombre, 'Sin categoría') AS categoria_nombre,
                    COALESCE(SUM(inv.stock_actual), 0) AS stock_actual,
                    COALESCE(MAX(inv.stock_minimo), 0) AS stock_minimo,
                    COALESCE(ventas_30.unidades_vendidas, 0) AS unidades_vendidas,
                    COALESCE(
                        EXTRACT(DAY FROM NOW() - MAX(k.created_at))::bigint, 999
                    ) AS dias_sin_movimiento,
                    CASE WHEN COALESCE(SUM(inv.stock_actual), 0) > 0
                        THEN ROUND(
                            COALESCE(ventas_30.unidades_vendidas, 0)
                            / COALESCE(SUM(inv.stock_actual), 1), 2)
                        ELSE 0
                    END AS rotacion,
                    CASE
                        WHEN COALESCE(SUM(inv.stock_actual), 0) = 0 THEN 'CRITICO'
                        WHEN COALESCE(SUM(inv.stock_actual), 0) <= COALESCE(MAX(inv.stock_minimo), 0) THEN 'BAJO'
                        WHEN COALESCE(ventas_30.unidades_vendidas, 0) = 0 THEN 'ALTO'
                        ELSE 'NORMAL'
                    END AS estado_stock
                FROM producto p
                LEFT JOIN categoria c ON p.categoria_id = c.id
                LEFT JOIN inventario inv ON inv.producto_id = p.id
                    AND inv.sucursal_id IN (SELECT id FROM sucursal WHERE empresa_id = :empresaId)
                LEFT JOIN (
                    SELECT vd.producto_id, SUM(vd.cantidad) AS unidades_vendidas
                    FROM venta v
                    INNER JOIN venta_detalle vd ON vd.venta_id = v.id
                    WHERE v.empresa_id = :empresaId
                      AND v.estado_venta = 'COMPLETADA'
                      AND v.fecha_emision >= NOW() - INTERVAL '30 days'
                    GROUP BY vd.producto_id
                ) ventas_30 ON ventas_30.producto_id = p.id
                LEFT JOIN movimiento_inventario k ON k.producto_id = p.id
                WHERE p.empresa_id = :empresaId
                  AND p.activo = true
                GROUP BY p.id, p.nombre, p.sku, c.nombre, ventas_30.unidades_vendidas
                ORDER BY rotacion DESC
                """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        return jdbc.query(sql, params, new BeanPropertyRowMapper<>(ReporteRotacionInventarioDto.class));
    }

    // ── Resumen avanzado ──────────────────────────────────────────────────────

    public ReporteResumenAvanzadoDto resumenAvanzado(Integer empresaId,
            LocalDate desde, LocalDate hasta) {
        ReporteResumenAvanzadoDto dto = new ReporteResumenAvanzadoDto();

        // Calcular días del período para comparar con período anterior equivalente
        long dias = java.time.temporal.ChronoUnit.DAYS.between(desde, hasta) + 1;
        LocalDate desdeAnterior = desde.minusDays(dias);
        LocalDate hastaAnterior = desde.minusDays(1);

        // KPIs del período actual
        String sqlPeriodo = """
                SELECT
                    COALESCE(SUM(v.total_pagar), 0) AS total,
                    COUNT(v.id) AS cantidad,
                    COALESCE(AVG(v.total_pagar), 0) AS ticket,
                    COALESCE(SUM(v.total_pagar) - SUM(
                        SELECT COALESCE(SUM(vd2.cantidad * COALESCE(p2.costo, 0)), 0)
                        FROM venta_detalle vd2
                        INNER JOIN producto p2 ON vd2.producto_id = p2.id
                        WHERE vd2.venta_id = v.id
                    ), 0) AS margen
                FROM venta v
                WHERE v.empresa_id = :empresaId
                  AND v.estado_venta = 'COMPLETADA'
                  AND v.fecha_emision::date BETWEEN :desde AND :hasta
                """;

        // Query simplificado para KPIs
        String sqlKpis = """
                SELECT
                    COALESCE(SUM(v.total_pagar), 0) AS total_ventas,
                    COUNT(v.id) AS cantidad_ventas,
                    COALESCE(ROUND(AVG(v.total_pagar), 2), 0) AS ticket_promedio
                FROM venta v
                WHERE v.empresa_id = :empresaId
                  AND v.estado_venta = 'COMPLETADA'
                  AND v.fecha_emision::date BETWEEN :desde AND :hasta
                """;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("desde", desde)
                .addValue("hasta", hasta);

        jdbc.query(sqlKpis, p, rs -> {
            dto.setTotalVentasPeriodo(rs.getBigDecimal("total_ventas"));
            dto.setCantidadTransacciones(rs.getLong("cantidad_ventas"));
            dto.setTicketPromedio(rs.getBigDecimal("ticket_promedio"));
        });

        // Período anterior
        String sqlAnterior = """
                SELECT COALESCE(SUM(v.total_pagar), 0) AS total_ventas
                FROM venta v
                WHERE v.empresa_id = :empresaId
                  AND v.estado_venta = 'COMPLETADA'
                  AND v.fecha_emision::date BETWEEN :desde AND :hasta
                """;
        MapSqlParameterSource pAnterior = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("desde", desdeAnterior)
                .addValue("hasta", hastaAnterior);
        BigDecimal totalAnterior = jdbc.queryForObject(sqlAnterior, pAnterior, BigDecimal.class);
        dto.setTotalVentasPeriodoAnterior(totalAnterior != null ? totalAnterior : BigDecimal.ZERO);

        // Variación
        if (dto.getTotalVentasPeriodoAnterior() != null
                && dto.getTotalVentasPeriodoAnterior().compareTo(BigDecimal.ZERO) > 0
                && dto.getTotalVentasPeriodo() != null) {
            BigDecimal var = dto.getTotalVentasPeriodo()
                    .subtract(dto.getTotalVentasPeriodoAnterior())
                    .multiply(new BigDecimal("100"))
                    .divide(dto.getTotalVentasPeriodoAnterior(), 2, java.math.RoundingMode.HALF_UP);
            dto.setVariacionVentas(var);
        } else {
            dto.setVariacionVentas(BigDecimal.ZERO);
        }

        // Total compras
        String sqlCompras = """
                SELECT COALESCE(SUM(c.total), 0)
                FROM compra c
                WHERE c.empresa_id = :empresaId
                  AND c.estado = 'RECIBIDA'
                  AND c.fecha::date BETWEEN :desde AND :hasta
                """;
        BigDecimal totalCompras = jdbc.queryForObject(sqlCompras, p, BigDecimal.class);
        dto.setTotalComprasPeriodo(totalCompras != null ? totalCompras : BigDecimal.ZERO);

        // Margen bruto
        String sqlMargen = """
                SELECT COALESCE(SUM(vd.subtotal_linea - (vd.cantidad * COALESCE(pr.costo, 0))), 0)
                FROM venta v
                INNER JOIN venta_detalle vd ON vd.venta_id = v.id
                INNER JOIN producto pr ON vd.producto_id = pr.id
                WHERE v.empresa_id = :empresaId
                  AND v.estado_venta = 'COMPLETADA'
                  AND v.fecha_emision::date BETWEEN :desde AND :hasta
                """;
        BigDecimal margen = jdbc.queryForObject(sqlMargen, p, BigDecimal.class);
        dto.setMargenBrutoPeriodo(margen != null ? margen : BigDecimal.ZERO);

        // Clientes nuevos vs recurrentes
        String sqlClientes = """
                SELECT
                    COUNT(DISTINCT CASE WHEN primera_compra >= :desde THEN cliente_id END) AS nuevos,
                    COUNT(DISTINCT CASE WHEN primera_compra < :desde THEN cliente_id END) AS recurrentes
                FROM (
                    SELECT v.cliente_id, MIN(v.fecha_emision::date) AS primera_compra
                    FROM venta v
                    WHERE v.empresa_id = :empresaId
                      AND v.cliente_id IS NOT NULL
                      AND v.estado_venta = 'COMPLETADA'
                    GROUP BY v.cliente_id
                ) sub
                WHERE sub.cliente_id IN (
                    SELECT DISTINCT v2.cliente_id
                    FROM venta v2
                    WHERE v2.empresa_id = :empresaId
                      AND v2.estado_venta = 'COMPLETADA'
                      AND v2.fecha_emision::date BETWEEN :desde AND :hasta
                      AND v2.cliente_id IS NOT NULL
                )
                """;
        jdbc.query(sqlClientes, p, rs -> {
            dto.setClientesNuevos(rs.getLong("nuevos"));
            dto.setClientesRecurrentes(rs.getLong("recurrentes"));
        });

        // Top categoría
        List<ReporteVentasCategoriaDto> cats = ventasPorCategoria(empresaId, desde, hasta);
        dto.setTopCategoria(cats.isEmpty() ? "-" : cats.get(0).getCategoriaNombre());

        // Top producto
        List<ReporteTopProductoDto> prods = topProductos(empresaId, desde, hasta, 1);
        dto.setTopProducto(prods.isEmpty() ? "-" : prods.get(0).getProductoNombre());

        // Top vendedor
        List<ReporteVentasVendedorDto> vends = ventasPorVendedor(empresaId, desde, hasta);
        dto.setTopVendedor(vends.isEmpty() ? "-" : vends.get(0).getVendedorNombre());

        // Ventas por día
        String sqlPorDia = """
                SELECT
                    TO_CHAR(v.fecha_emision::date, 'YYYY-MM-DD') AS dia,
                    SUM(v.total_pagar) AS total
                FROM venta v
                WHERE v.empresa_id = :empresaId
                  AND v.estado_venta = 'COMPLETADA'
                  AND v.fecha_emision::date BETWEEN :desde AND :hasta
                GROUP BY v.fecha_emision::date
                ORDER BY v.fecha_emision::date
                """;
        List<Map<String, Object>> porDia = new ArrayList<>();
        jdbc.query(sqlPorDia, p, rs -> {
            Map<String, Object> row = new HashMap<>();
            row.put("dia", rs.getString("dia"));
            row.put("total", rs.getBigDecimal("total"));
            porDia.add(row);
        });
        dto.setVentasPorDia(porDia);

        // Ventas por método de pago
        String sqlMetodo = """
                SELECT vp.metodo_pago AS metodo_pago, SUM(vp.monto) AS total
                FROM venta v
                INNER JOIN venta_pago vp ON vp.venta_id = v.id
                WHERE v.empresa_id = :empresaId
                  AND v.estado_venta = 'COMPLETADA'
                  AND v.fecha_emision::date BETWEEN :desde AND :hasta
                GROUP BY vp.metodo_pago
                ORDER BY total DESC
                """;
        List<Map<String, Object>> porMetodo = new ArrayList<>();
        jdbc.query(sqlMetodo, p, rs -> {
            Map<String, Object> row = new HashMap<>();
            row.put("metodoPago", rs.getString("metodo_pago"));
            row.put("total", rs.getBigDecimal("total"));
            porMetodo.add(row);
        });
        dto.setVentasPorMetodoPago(porMetodo);

        return dto;
    }
}
