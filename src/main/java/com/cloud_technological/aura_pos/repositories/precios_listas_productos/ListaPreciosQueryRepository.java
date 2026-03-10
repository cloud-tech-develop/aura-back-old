package com.cloud_technological.aura_pos.repositories.precios_listas_productos;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.lista_precios.ListaPreciosDto;
import com.cloud_technological.aura_pos.dto.lista_precios.ListaPreciosTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class ListaPreciosQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<ListaPreciosTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                lp.id,
                lp.nombre,
                lp.activa,
                COUNT(*) OVER() AS total_rows
            FROM lista_precios lp
            WHERE lp.empresa_id = :empresaId
            AND lp.activa = true
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append(" AND LOWER(lp.nombre) LIKE :search ");
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY lp.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<ListaPreciosTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(ListaPreciosTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public boolean existeNombre(String nombre, Integer empresaId) {
        String sql = """
            SELECT COUNT(*) FROM lista_precios
            WHERE LOWER(nombre) = LOWER(:nombre)
            AND empresa_id = :empresaId
            AND activa = true
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("nombre", nombre);
        params.addValue("empresaId", empresaId);
        Long count = jdbcTemplate.queryForObject(sql, params, Long.class);
        return count != null && count > 0;
    }

    public boolean existeNombreExcluyendo(String nombre, Integer empresaId, Long id) {
        String sql = """
            SELECT COUNT(*) FROM lista_precios
            WHERE LOWER(nombre) = LOWER(:nombre)
            AND empresa_id = :empresaId
            AND id != :id
            AND activa = true
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("nombre", nombre);
        params.addValue("empresaId", empresaId);
        params.addValue("id", id);
        Long count = jdbcTemplate.queryForObject(sql, params, Long.class);
        return count != null && count > 0;
    }
    public List<ListaPreciosDto> listar(Integer empresaId) {
        String sql = """
            SELECT
                id,
                nombre,
                activa
            FROM lista_precios
            WHERE empresa_id = :empresaId
            AND activa = true
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(ListaPreciosDto.class));
    }
}