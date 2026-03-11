package com.cloud_technological.aura_pos.repositories.precios_listas_productos;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.lista_precios.ProductoPrecioTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class ProductoPrecioQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<ProductoPrecioTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                pp.id,
                pp.lista_precio_id,
                lp.nombre AS lista_precio_nombre,
                pp.producto_presentacion_id,
                pres.nombre AS producto_presentacion_nombre,
                COALESCE(p_pres.id, p_dir.id) AS producto_id,
                COALESCE(p_pres.nombre, p_dir.nombre) AS producto_nombre,
                pp.precio,
                pp.utilidad_esperada,
                COUNT(*) OVER() AS total_rows
            FROM producto_precio pp
            INNER JOIN lista_precios lp ON pp.lista_precio_id = lp.id
            LEFT JOIN producto_presentacion pres ON pp.producto_presentacion_id = pres.id
            LEFT JOIN producto p_pres ON pres.producto_id = p_pres.id AND p_pres.deleted_at IS NULL
            LEFT JOIN producto p_dir ON pp.producto_id = p_dir.id AND p_dir.deleted_at IS NULL
            WHERE lp.empresa_id = :empresaId
            AND lp.activa = true
            AND (p_pres.id IS NOT NULL OR p_dir.id IS NOT NULL)
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(COALESCE(p_pres.nombre, p_dir.nombre)) LIKE :search
                OR LOWER(lp.nombre) LIKE :search
                OR LOWER(pres.nombre) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY pp.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<ProductoPrecioTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(ProductoPrecioTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public List<ProductoPrecioTableDto> listarPorLista(Long listaPrecioId) {
        String sql = """
            SELECT
                pp.id,
                pp.lista_precio_id,
                lp.nombre AS lista_precio_nombre,
                pp.producto_presentacion_id,
                pres.nombre AS producto_presentacion_nombre,
                COALESCE(p_pres.id, p_dir.id) AS producto_id,
                COALESCE(p_pres.nombre, p_dir.nombre) AS producto_nombre,
                pp.precio,
                pp.utilidad_esperada
            FROM producto_precio pp
            INNER JOIN lista_precios lp ON pp.lista_precio_id = lp.id
            LEFT JOIN producto_presentacion pres ON pp.producto_presentacion_id = pres.id
            LEFT JOIN producto p_pres ON pres.producto_id = p_pres.id AND p_pres.deleted_at IS NULL
            LEFT JOIN producto p_dir ON pp.producto_id = p_dir.id AND p_dir.deleted_at IS NULL
            WHERE pp.lista_precio_id = :listaPrecioId
            AND (p_pres.id IS NOT NULL OR p_dir.id IS NOT NULL)
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("listaPrecioId", listaPrecioId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(ProductoPrecioTableDto.class));
    }
}