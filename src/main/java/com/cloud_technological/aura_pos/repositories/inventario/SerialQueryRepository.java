package com.cloud_technological.aura_pos.repositories.inventario;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.inventario.SerialProductoTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class SerialQueryRepository {
    
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<SerialProductoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                sp.id,
                sp.producto_id,
                p.nombre AS producto_nombre,
                sp.sucursal_id,
                s.nombre AS sucursal_nombre,
                sp.serial,
                sp.estado,
                COUNT(*) OVER() AS total_rows
            FROM serial_producto sp
            INNER JOIN producto p ON sp.producto_id = p.id
            INNER JOIN sucursal s ON sp.sucursal_id = s.id
            WHERE s.empresa_id = :empresaId
            AND p.deleted_at IS NULL
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(p.nombre) LIKE :search
                OR LOWER(sp.serial) LIKE :search
                OR LOWER(sp.estado) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY sp.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<SerialProductoTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(SerialProductoTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    // Seriales disponibles por producto y sucursal (usado en ventas)
    public List<SerialProductoTableDto> listarDisponiblesPorProducto(Long productoId, Long sucursalId) {
        String sql = """
            SELECT
                sp.id,
                sp.producto_id,
                p.nombre AS producto_nombre,
                sp.sucursal_id,
                s.nombre AS sucursal_nombre,
                sp.serial,
                sp.estado
            FROM serial_producto sp
            INNER JOIN producto p ON sp.producto_id = p.id
            INNER JOIN sucursal s ON sp.sucursal_id = s.id
            WHERE sp.producto_id = :productoId
            AND sp.sucursal_id = :sucursalId
            AND sp.estado = 'DISPONIBLE'
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("productoId", productoId);
        params.addValue("sucursalId", sucursalId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(SerialProductoTableDto.class));
    }
}
