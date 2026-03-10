package com.cloud_technological.aura_pos.repositories.inventario;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.inventario.LoteTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class LoteQueryRepository {
    
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<LoteTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                l.id,
                l.producto_id,
                p.nombre AS producto_nombre,
                l.sucursal_id,
                s.nombre AS sucursal_nombre,
                l.codigo_lote,
                l.fecha_vencimiento,
                l.stock_actual,
                l.costo_unitario,
                l.activo,
                COUNT(*) OVER() AS total_rows
            FROM lote l
            INNER JOIN producto p ON l.producto_id = p.id
            INNER JOIN sucursal s ON l.sucursal_id = s.id
            WHERE s.empresa_id = :empresaId
            AND l.activo = true
            AND p.deleted_at IS NULL
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(p.nombre) LIKE :search
                OR LOWER(l.codigo_lote) LIKE :search
                OR LOWER(s.nombre) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY l.fecha_vencimiento ASC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<LoteTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(LoteTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    // Lotes próximos a vencer (próximos 30 días)
    public List<LoteTableDto> listarPorVencer(Integer empresaId) {
        String sql = """
            SELECT
                l.id,
                l.producto_id,
                p.nombre AS producto_nombre,
                l.sucursal_id,
                s.nombre AS sucursal_nombre,
                l.codigo_lote,
                l.fecha_vencimiento,
                l.stock_actual,
                l.costo_unitario,
                l.activo
            FROM lote l
            INNER JOIN producto p ON l.producto_id = p.id
            INNER JOIN sucursal s ON l.sucursal_id = s.id
            WHERE s.empresa_id = :empresaId
            AND l.activo = true
            AND l.stock_actual > 0
            AND l.fecha_vencimiento BETWEEN NOW() AND NOW() + INTERVAL '30 days'
            ORDER BY l.fecha_vencimiento ASC
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(LoteTableDto.class));
    }

    // Lotes disponibles para un producto en una sucursal (usado en ventas)
    public List<LoteTableDto> listarDisponiblesPorProducto(Long productoId, Long sucursalId) {
        String sql = """
            SELECT
                l.id,
                l.producto_id,
                p.nombre AS producto_nombre,
                l.sucursal_id,
                s.nombre AS sucursal_nombre,
                l.codigo_lote,
                l.fecha_vencimiento,
                l.stock_actual,
                l.costo_unitario,
                l.activo
            FROM lote l
            INNER JOIN producto p ON l.producto_id = p.id
            INNER JOIN sucursal s ON l.sucursal_id = s.id
            WHERE l.producto_id = :productoId
            AND l.sucursal_id = :sucursalId
            AND l.activo = true
            AND l.stock_actual > 0
            ORDER BY l.fecha_vencimiento ASC
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("productoId", productoId);
        params.addValue("sucursalId", sucursalId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(LoteTableDto.class));
    }
}
