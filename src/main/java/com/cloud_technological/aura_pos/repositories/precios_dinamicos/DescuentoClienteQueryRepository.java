package com.cloud_technological.aura_pos.repositories.precios_dinamicos;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.precios_dinamicos.DescuentoClienteDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class DescuentoClienteQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<DescuentoClienteDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                dc.id,
                dc.empresa_id,
                dc.tercero_id,
                t.nombre AS tercero_nombre,
                t.documento AS tercero_documento,
                dc.categoria_id,
                c.nombre AS categoria_nombre,
                dc.porcentaje_descuento,
                dc.tipo_descuento,
                dc.fecha_inicio,
                dc.fecha_fin,
                dc.activo,
                dc.observaciones,
                dc.created_at,
                COUNT(*) OVER() AS total_rows
            FROM descuento_cliente dc
            INNER JOIN tercero t ON dc.tercero_id = t.id
            LEFT JOIN categoria c ON dc.categoria_id = c.id
            WHERE dc.empresa_id = :empresaId
            AND dc.deleted_at IS NULL
            AND t.deleted_at IS NULL
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(t.nombre) LIKE :search
                OR LOWER(t.documento) LIKE :search
                OR LOWER(c.nombre) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY dc.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<DescuentoClienteDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(DescuentoClienteDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotal_rows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public List<DescuentoClienteDto> listarPorCliente(Integer empresaId, Long terceroId) {
        String sql = """
            SELECT
                dc.id,
                dc.empresa_id,
                dc.tercero_id,
                t.nombre AS tercero_nombre,
                t.documento AS tercero_documento,
                dc.categoria_id,
                c.nombre AS categoria_nombre,
                dc.porcentaje_descuento,
                dc.tipo_descuento,
                dc.fecha_inicio,
                dc.fecha_fin,
                dc.activo,
                dc.observaciones,
                dc.created_at
            FROM descuento_cliente dc
            INNER JOIN tercero t ON dc.tercero_id = t.id
            LEFT JOIN categoria c ON dc.categoria_id = c.id
            WHERE dc.empresa_id = :empresaId
            AND dc.tercero_id = :terceroId
            AND dc.activo = true
            AND dc.deleted_at IS NULL
            AND t.deleted_at IS NULL
            ORDER BY c.nombre
        """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("terceroId", terceroId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(DescuentoClienteDto.class));
    }
}
