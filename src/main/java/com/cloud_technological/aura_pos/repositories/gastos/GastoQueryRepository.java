package com.cloud_technological.aura_pos.repositories.gastos;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.compras.GastoTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class GastoQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<GastoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                g.id,
                g.categoria,
                g.descripcion,
                g.monto,
                g.fecha,
                g.deducible,
                g.estado,
                s.nombre AS sucursal_nombre,
                u.username AS usuario_nombre,
                COUNT(*) OVER() AS total_rows
            FROM gasto g
            LEFT JOIN sucursal s ON g.sucursal_id = s.id
            LEFT JOIN usuario u ON g.usuario_id = u.id
            WHERE g.empresa_id = :empresaId
              AND g.estado = 'ACTIVO'
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append(" AND (LOWER(g.categoria) LIKE :search OR LOWER(g.descripcion) LIKE :search) ");
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY g.fecha DESC, g.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<GastoTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(GastoTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }
}
