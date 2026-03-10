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
                p.nombre AS producto_nombre,
                pp.precio,
                pp.utilidad_esperada,
                COUNT(*) OVER() AS total_rows
            FROM producto_precio pp
            INNER JOIN lista_precios lp ON pp.lista_precio_id = lp.id
            INNER JOIN producto_presentacion pres ON pp.producto_presentacion_id = pres.id
            INNER JOIN producto p ON pres.producto_id = p.id
            WHERE lp.empresa_id = :empresaId
            AND lp.activa = true
            AND p.deleted_at IS NULL
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(p.nombre) LIKE :search
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
                p.nombre AS producto_nombre,
                pp.precio,
                pp.utilidad_esperada
            FROM producto_precio pp
            INNER JOIN lista_precios lp ON pp.lista_precio_id = lp.id
            INNER JOIN producto_presentacion pres ON pp.producto_presentacion_id = pres.id
            INNER JOIN producto p ON pres.producto_id = p.id
            WHERE pp.lista_precio_id = :listaPrecioId
            AND p.deleted_at IS NULL
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("listaPrecioId", listaPrecioId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(ProductoPrecioTableDto.class));
    }
}