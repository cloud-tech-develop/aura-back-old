package com.cloud_technological.aura_pos.repositories.kardex;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.kardex.KardexFiltroDto;
import com.cloud_technological.aura_pos.dto.kardex.KardexResumenDto;
import com.cloud_technological.aura_pos.dto.kardex.KardexTableDto;


@Repository
public class KardexQueryRepository {
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<KardexTableDto> listar(KardexFiltroDto filtro, Integer empresaId) {
        int page = filtro.getPage() != null ? filtro.getPage() : 0;
        int size = filtro.getRows() != null ? filtro.getRows() : 20;

        StringBuilder sql = new StringBuilder("""
            SELECT
                m.id,
                m.tipo_movimiento,
                m.cantidad,
                m.saldo_anterior,
                m.saldo_nuevo,
                m.costo_historico,
                m.referencia_origen,
                m.created_at,
                p.nombre AS producto_nombre,
                s.nombre AS sucursal_nombre,
                l.codigo_lote,
                COUNT(*) OVER() AS total_rows
            FROM movimiento_inventario m
            INNER JOIN producto p ON m.producto_id = p.id
            INNER JOIN sucursal s ON m.sucursal_id = s.id
            LEFT JOIN lote l ON m.lote_id = l.id
            WHERE s.empresa_id = :empresaId
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (filtro.getProductoId() != null) {
            sql.append(" AND m.producto_id = :productoId ");
            params.addValue("productoId", filtro.getProductoId());
        }

        if (filtro.getSucursalId() != null) {
            sql.append(" AND m.sucursal_id = :sucursalId ");
            params.addValue("sucursalId", filtro.getSucursalId());
        }

        if (filtro.getLoteId() != null) {
            sql.append(" AND m.lote_id = :loteId ");
            params.addValue("loteId", filtro.getLoteId());
        }

        if (filtro.getTipoMovimiento() != null && !filtro.getTipoMovimiento().isBlank()) {
            sql.append(" AND m.tipo_movimiento = :tipoMovimiento ");
            params.addValue("tipoMovimiento", filtro.getTipoMovimiento());
        }

        if (filtro.getFechaDesde() != null) {
            sql.append(" AND m.created_at >= :fechaDesde ");
            params.addValue("fechaDesde", filtro.getFechaDesde());
        }

        if (filtro.getFechaHasta() != null) {
            sql.append(" AND m.created_at <= :fechaHasta ");
            params.addValue("fechaHasta", filtro.getFechaHasta());
        }

        sql.append(" ORDER BY m.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<KardexTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(KardexTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    // Resumen de stock actual por producto en todas las sucursales
    public List<KardexResumenDto> resumenStockPorProducto(Long productoId, Integer empresaId) {
        String sql = """
            SELECT
                s.id AS sucursal_id,
                s.nombre AS sucursal_nombre,
                p.id AS producto_id,
                p.nombre AS producto_nombre,
                p.sku AS producto_sku,
                i.stock_actual,
                i.stock_minimo,
                CASE WHEN i.stock_actual <= i.stock_minimo THEN true ELSE false END AS stock_critico
            FROM inventario i
            INNER JOIN sucursal s ON i.sucursal_id = s.id
            INNER JOIN producto p ON i.producto_id = p.id
            WHERE s.empresa_id = :empresaId
            AND p.id = :productoId
            ORDER BY s.nombre
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("empresaId", empresaId);
        params.addValue("productoId", productoId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(KardexResumenDto.class));
    }
}
