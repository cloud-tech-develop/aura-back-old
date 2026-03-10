package com.cloud_technological.aura_pos.repositories.marcas;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import com.cloud_technological.aura_pos.dto.marcas.MarcaDto;
import com.cloud_technological.aura_pos.dto.marcas.MarcaTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class MarcaQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<MarcaTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT 
                m.id,
                m.nombre,
                m.activo,
                COUNT(*) OVER() AS total_rows
            FROM marca m
            WHERE m.empresa_id = :empresaId
            AND m.deleted_at IS NULL
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append(" AND LOWER(m.nombre) LIKE :search ");
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY m.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<MarcaTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(MarcaTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public boolean existeNombre(String nombre, Integer empresaId) {
        String sql = """
            SELECT COUNT(*) FROM marca
            WHERE LOWER(nombre) = LOWER(:nombre)
            AND empresa_id = :empresaId
            AND deleted_at IS NULL
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("nombre", nombre);
        params.addValue("empresaId", empresaId);
        Long count = jdbcTemplate.queryForObject(sql, params, Long.class);
        return count != null && count > 0;
    }

    public boolean existeNombreExcluyendo(String nombre, Integer empresaId, Long id) {
        String sql = """
            SELECT COUNT(*) FROM marca
            WHERE LOWER(nombre) = LOWER(:nombre)
            AND empresa_id = :empresaId
            AND id != :id
            AND deleted_at IS NULL
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("nombre", nombre);
        params.addValue("empresaId", empresaId);
        params.addValue("id", id);
        Long count = jdbcTemplate.queryForObject(sql, params, Long.class);
        return count != null && count > 0;
    }
    public List<MarcaDto> list(Integer empresaId) {
        String sql = """
            SELECT id, nombre
            FROM marca
            WHERE empresa_id = :empresaId
            AND activo = true
            AND deleted_at IS NULL
            ORDER BY nombre ASC
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(MarcaDto.class));
    }
}