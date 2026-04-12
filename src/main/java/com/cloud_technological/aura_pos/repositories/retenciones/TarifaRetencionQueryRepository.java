package com.cloud_technological.aura_pos.repositories.retenciones;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.retenciones.TarifaRetencionDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class TarifaRetencionQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    public PageImpl<TarifaRetencionDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 25;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                id, empresa_id, tipo, concepto, codigo_concepto,
                tarifa_natural, tarifa_juridica, base_minima, activo,
                COUNT(*) OVER() AS total_rows
            FROM tarifa_retencion
            WHERE empresa_id = :empresaId
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append(" AND (LOWER(concepto) LIKE :search OR LOWER(tipo) LIKE :search) ");
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY tipo ASC, concepto ASC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<TarifaRetencionDto> list = jdbc.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(TarifaRetencionDto.class));

        long total = list.isEmpty() ? 0 : 0; // totalRows not in this DTO — use count query
        // Simple approach: count separately
        String countSql = "SELECT COUNT(*) FROM tarifa_retencion WHERE empresa_id = :empresaId"
                + (search.isEmpty() ? "" : " AND (LOWER(concepto) LIKE :search OR LOWER(tipo) LIKE :search)");
        Long count = jdbc.queryForObject(countSql, params, Long.class);
        total = count != null ? count : list.size();

        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }
}
