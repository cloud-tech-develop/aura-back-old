package com.cloud_technological.aura_pos.repositories.sucursales;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.sucursal.SucursalDto;
import com.cloud_technological.aura_pos.dto.sucursal.SucursalTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class SucursalQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    public PageImpl<SucursalTableDto> paginar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
                SELECT s.id,
                       s.codigo,
                       s.nombre,
                       s.ciudad,
                       s.telefono,
                       s.activa,
                       COUNT(*) OVER() AS total_rows
                FROM sucursal s
                WHERE s.empresa_id = :empresaId
                """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("rows", size)
                .addValue("offset", (long) page * size);

        if (!search.isEmpty()) {
            sql.append("""
                        AND (LOWER(s.codigo) LIKE :search
                        OR LOWER(s.nombre) LIKE :search
                        OR LOWER(s.ciudad) LIKE :search
                        OR LOWER(s.telefono) LIKE :search)

                    """);
            params.addValue("search", "%" + search + "%");
        }
        sql.append(" ORDER BY s.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<SucursalTableDto> content = jdbc.query(sql.toString(), params,
                BeanPropertyRowMapper.newInstance(SucursalTableDto.class));

        long total = content.isEmpty() ? 0 : content.get(0).getTotalRows();
        return new PageImpl<>(content, PageRequest.of(page, size), total);
    }

    public boolean existeNombre(String nombre, Integer empresaId) {
        String sql = "SELECT COUNT(*) FROM sucursal WHERE LOWER(nombre) = LOWER(:nombre) AND empresa_id = :empresaId";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("nombre", nombre)
                .addValue("empresaId", empresaId);
        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count != null && count > 0;
    }

    public boolean existeNombreExcluyendo(String nombre, Integer empresaId, Integer excludeId) {
        String sql = """
                SELECT COUNT(*) FROM sucursal
                WHERE LOWER(nombre) = LOWER(:nombre)
                  AND empresa_id = :empresaId
                  AND id != :excludeId
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("nombre", nombre)
                .addValue("empresaId", empresaId)
                .addValue("excludeId", excludeId);
        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count != null && count > 0;
    }

    public List<SucursalDto> listarActivas(Integer empresaId) {
        String sql = """
                SELECT s.id,
                       s.codigo,
                       s.nombre,
                       s.direccion,
                       s.ciudad,
                       s.telefono,
                       s.activa,
                       s.consecutivo_actual,
                       s.empresa_id
                FROM sucursal s
                WHERE s.empresa_id = :empresaId
                  AND s.activa = true
                ORDER BY s.nombre ASC
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("empresaId", empresaId);
        return jdbc.query(sql, params, BeanPropertyRowMapper.newInstance(SucursalDto.class));
    }
}
