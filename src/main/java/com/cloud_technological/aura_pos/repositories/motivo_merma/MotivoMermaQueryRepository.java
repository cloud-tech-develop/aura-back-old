package com.cloud_technological.aura_pos.repositories.motivo_merma;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.merma.MotivoMermaTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class MotivoMermaQueryRepository {
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<MotivoMermaTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                m.id,
                m.nombre,
                m.afecta_contabilidad,
                COUNT(*) OVER() AS total_rows
            FROM motivo_merma m
            WHERE m.empresa_id = :empresaId
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append(" AND LOWER(m.nombre) LIKE :search ");
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY m.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<MotivoMermaTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(MotivoMermaTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public boolean existeNombre(String nombre, Integer empresaId) {
        String sql = """
            SELECT COUNT(*) FROM motivo_merma
            WHERE LOWER(nombre) = LOWER(:nombre)
            AND empresa_id = :empresaId
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("nombre", nombre);
        params.addValue("empresaId", empresaId);
        Long count = jdbcTemplate.queryForObject(sql, params, Long.class);
        return count != null && count > 0;
    }

    public boolean existeNombreExcluyendo(String nombre, Integer empresaId, Long id) {
        String sql = """
            SELECT COUNT(*) FROM motivo_merma
            WHERE LOWER(nombre) = LOWER(:nombre)
            AND empresa_id = :empresaId
            AND id != :id
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("nombre", nombre);
        params.addValue("empresaId", empresaId);
        params.addValue("id", id);
        Long count = jdbcTemplate.queryForObject(sql, params, Long.class);
        return count != null && count > 0;
    }
}
