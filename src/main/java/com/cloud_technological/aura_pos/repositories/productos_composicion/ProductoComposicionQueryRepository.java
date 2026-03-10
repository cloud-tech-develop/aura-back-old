package com.cloud_technological.aura_pos.repositories.productos_composicion;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.producto_composicion.ProductoComposicionTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class ProductoComposicionQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<ProductoComposicionTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                pc.id,
                pc.producto_padre_id,
                pp.nombre AS producto_padre_nombre,
                pc.producto_hijo_id,
                ph.nombre AS producto_hijo_nombre,
                pc.cantidad,
                pc.tipo,
                COUNT(*) OVER() AS total_rows
            FROM producto_composicion pc
            INNER JOIN producto pp ON pc.producto_padre_id = pp.id
            INNER JOIN producto ph ON pc.producto_hijo_id = ph.id
            WHERE pp.empresa_id = :empresaId
            AND pp.deleted_at IS NULL
            AND ph.deleted_at IS NULL
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(pp.nombre) LIKE :search
                OR LOWER(ph.nombre) LIKE :search
                OR LOWER(pc.tipo) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY pc.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<ProductoComposicionTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(ProductoComposicionTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public List<ProductoComposicionTableDto> listarPorPadre(Long productoPadreId) {
        String sql = """
            SELECT
                pc.id,
                pc.producto_padre_id,
                pp.nombre AS producto_padre_nombre,
                pc.producto_hijo_id,
                ph.nombre AS producto_hijo_nombre,
                pc.cantidad,
                pc.tipo
            FROM producto_composicion pc
            INNER JOIN producto pp ON pc.producto_padre_id = pp.id
            INNER JOIN producto ph ON pc.producto_hijo_id = ph.id
            WHERE pc.producto_padre_id = :productoPadreId
            AND pp.deleted_at IS NULL
            AND ph.deleted_at IS NULL
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("productoPadreId", productoPadreId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(ProductoComposicionTableDto.class));
    }
}