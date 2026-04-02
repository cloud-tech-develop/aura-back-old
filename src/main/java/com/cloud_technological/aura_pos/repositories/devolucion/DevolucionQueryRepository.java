package com.cloud_technological.aura_pos.repositories.devolucion;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.devolucion.DevolucionTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class DevolucionQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<DevolucionTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
                SELECT
                    d.id,
                    d.consecutivo,
                    d.venta_id,
                    v.consecutivo AS venta_consecutivo,
                    COALESCE(t.razon_social, t.nombres || ' ' || t.apellidos, 'Consumidor Final') AS cliente_nombre,
                    d.tipo,
                    d.estado,
                    d.total_devolucion,
                    d.motivo,
                    TO_CHAR(d.created_at, 'YYYY-MM-DD HH24:MI') AS created_at,
                    COUNT(*) OVER() AS total_rows
                FROM devolucion d
                INNER JOIN venta v ON d.venta_id = v.id
                LEFT JOIN tercero t ON d.cliente_id = t.id
                WHERE d.empresa_id = :empresaId
                """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                    AND (LOWER(d.tipo) LIKE :search
                    OR LOWER(d.estado) LIKE :search
                    OR LOWER(d.motivo) LIKE :search
                    OR CAST(d.consecutivo AS TEXT) LIKE :search
                    OR CAST(v.consecutivo AS TEXT) LIKE :search)
                    """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY d.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<DevolucionTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(DevolucionTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }
}
