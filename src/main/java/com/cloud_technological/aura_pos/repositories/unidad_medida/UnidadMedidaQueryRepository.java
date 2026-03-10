package com.cloud_technological.aura_pos.repositories.unidad_medida;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.unidad_medida.UnidadMedida;
import com.cloud_technological.aura_pos.dto.unidad_medida.UnidadMedidaDto;
import com.cloud_technological.aura_pos.dto.unidad_medida.UnidadMedidaTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class UnidadMedidaQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<UnidadMedidaTableDto> listar(PageableDto<Object> pageable) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                u.id,
                u.nombre,
                u.abreviatura,
                u.permite_decimales,
                u.activo,
                COUNT(*) OVER() AS total_rows
            FROM unidad_medida u
            WHERE u.activo = true
        """);

        MapSqlParameterSource params = new MapSqlParameterSource();

        if (!search.isEmpty()) {
            sql.append(" AND (LOWER(u.nombre) LIKE :search OR LOWER(u.abreviatura) LIKE :search) ");
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY u.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<UnidadMedidaTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(UnidadMedidaTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public boolean existeNombre(String nombre) {
        String sql = """
            SELECT COUNT(*) FROM unidad_medida
            WHERE LOWER(nombre) = LOWER(:nombre)
            AND activo = true
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("nombre", nombre);
        Long count = jdbcTemplate.queryForObject(sql, params, Long.class);
        return count != null && count > 0;
    }

    public boolean existeNombreExcluyendo(String nombre, Long id) {
        String sql = """
            SELECT COUNT(*) FROM unidad_medida
            WHERE LOWER(nombre) = LOWER(:nombre)
            AND id != :id
            AND activo = true
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("nombre", nombre);
        params.addValue("id", id);
        Long count = jdbcTemplate.queryForObject(sql, params, Long.class);
        return count != null && count > 0;
    }
    public List<UnidadMedida> list() {
        String sql = """
            SELECT id, nombre, abreviatura
            FROM unidad_medida
            WHERE activo = true
            AND deleted_at IS NULL
            ORDER BY nombre ASC
        """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource(), 
            new BeanPropertyRowMapper<>(UnidadMedida.class));
    }
}