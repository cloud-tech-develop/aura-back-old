package com.cloud_technological.aura_pos.repositories.categorias;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import com.cloud_technological.aura_pos.dto.categorias.CategoriaDto;
import com.cloud_technological.aura_pos.dto.categorias.CategoriaTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class CategoriaQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<CategoriaTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        // Hacemos LEFT JOIN con la misma tabla (c2) para sacar el nombre del padre
        StringBuilder sql = new StringBuilder("""
            SELECT 
                c.id,
                c.nombre,
                p.nombre as nombre_padre,
                c.impuesto_defecto,
                c.activo,
                COUNT(*) OVER() AS total_rows
            FROM categoria c
            LEFT JOIN categoria p ON c.padre_id = p.id
            WHERE c.empresa_id = :empresaId
            AND c.deleted_at IS NULL
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append(" AND (LOWER(c.nombre) LIKE :search) ");
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY c.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<CategoriaTableDto> list = jdbcTemplate.query(sql.toString(), params, 
            new BeanPropertyRowMapper<>(CategoriaTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }
    public boolean existeNombre(String nombre, Integer empresaId) {
        String sql = """
            SELECT COUNT(*) FROM categoria 
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
            SELECT COUNT(*) FROM categoria 
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
    public List<CategoriaDto> list(Integer empresaId) {
    String sql = """
        SELECT id, nombre
        FROM categoria
        WHERE empresa_id = :empresaId
        AND activo = true
        AND deleted_at IS NULL
        ORDER BY nombre ASC
    """;
    MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
    return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(CategoriaDto.class));
}
}