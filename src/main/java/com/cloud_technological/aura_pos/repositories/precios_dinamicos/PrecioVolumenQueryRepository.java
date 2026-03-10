package com.cloud_technological.aura_pos.repositories.precios_dinamicos;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.precios_dinamicos.PrecioVolumenTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class PrecioVolumenQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<PrecioVolumenTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                pv.id,
                pv.producto_presentacion_id,
                pres.nombre AS producto_presentacion_nombre,
                p.nombre AS producto_nombre,
                pv.cantidad_minima,
                pv.cantidad_maxima,
                pv.precio_unitario,
                pv.activo,
                COUNT(*) OVER() AS total_rows
            FROM precio_volumen pv
            INNER JOIN producto_presentacion pres ON pv.producto_presentacion_id = pres.id
            INNER JOIN producto p ON pres.producto_id = p.id
            WHERE pv.empresa_id = :empresaId
            AND pv.deleted_at IS NULL
            AND p.deleted_at IS NULL
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(p.nombre) LIKE :search
                OR LOWER(pres.nombre) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY pv.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<PrecioVolumenTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(PrecioVolumenTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public List<PrecioVolumenTableDto> listarPorProducto(Integer empresaId, Long productoPresentacionId) {
        String sql = """
            SELECT
                pv.id,
                pv.producto_presentacion_id,
                pres.nombre AS producto_presentacion_nombre,
                p.nombre AS producto_nombre,
                pv.cantidad_minima,
                pv.cantidad_maxima,
                pv.precio_unitario,
                pv.activo
            FROM precio_volumen pv
            INNER JOIN producto_presentacion pres ON pv.producto_presentacion_id = pres.id
            INNER JOIN producto p ON pres.producto_id = p.id
            WHERE pv.empresa_id = :empresaId
            AND pv.producto_presentacion_id = :productoPresentacionId
            AND pv.activo = true
            AND pv.deleted_at IS NULL
            AND p.deleted_at IS NULL
            ORDER BY pv.cantidad_minima
        """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("productoPresentacionId", productoPresentacionId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(PrecioVolumenTableDto.class));
    }
}
