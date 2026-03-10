package com.cloud_technological.aura_pos.repositories.inventario;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.inventario.InventarioTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class InventarioQueryRepository {
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<InventarioTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                i.id,
                i.sucursal_id,
                s.nombre AS sucursal_nombre,
                i.producto_id,
                p.nombre AS producto_nombre,
                p.sku AS producto_sku,
                i.stock_actual,
                i.stock_minimo,
                i.ubicacion,
                COUNT(*) OVER() AS total_rows
            FROM inventario i
            INNER JOIN sucursal s ON i.sucursal_id = s.id
            INNER JOIN producto p ON i.producto_id = p.id
            WHERE s.empresa_id = :empresaId
            AND p.deleted_at IS NULL
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(p.nombre) LIKE :search
                OR LOWER(p.sku) LIKE :search
                OR LOWER(s.nombre) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY i.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<InventarioTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(InventarioTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    // Usado en ventas y compras para verificar stock disponible
    public BigDecimal obtenerStock(Long productoId, Long sucursalId) {
        String sql = """
            SELECT COALESCE(stock_actual, 0)
            FROM inventario
            WHERE producto_id = :productoId
            AND sucursal_id = :sucursalId
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("productoId", productoId);
        params.addValue("sucursalId", sucursalId);
        BigDecimal stock = jdbcTemplate.queryForObject(sql, params, BigDecimal.class);
        return stock != null ? stock : BigDecimal.ZERO;
    }

    // Productos con stock bajo el mínimo
    public List<InventarioTableDto> listarStockBajo(Integer empresaId) {
        String sql = """
            SELECT
                i.id,
                i.sucursal_id,
                s.nombre AS sucursal_nombre,
                i.producto_id,
                p.nombre AS producto_nombre,
                p.sku AS producto_sku,
                i.stock_actual,
                i.stock_minimo,
                i.ubicacion
            FROM inventario i
            INNER JOIN sucursal s ON i.sucursal_id = s.id
            INNER JOIN producto p ON i.producto_id = p.id
            WHERE s.empresa_id = :empresaId
            AND i.stock_actual <= i.stock_minimo
            AND p.deleted_at IS NULL
            ORDER BY i.stock_actual ASC
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(InventarioTableDto.class));
    }
}
