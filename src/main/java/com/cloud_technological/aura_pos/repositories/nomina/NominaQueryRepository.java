package com.cloud_technological.aura_pos.repositories.nomina;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.nomina.nomina.NominaTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class NominaQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<NominaTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                n.id,
                n.periodo_id,
                p.fecha_inicio AS periodo_fecha_inicio,
                p.fecha_fin    AS periodo_fecha_fin,
                n.empleado_id,
                CONCAT(e.nombres, ' ', e.apellidos) AS empleado_nombre,
                e.numero_documento AS empleado_documento,
                e.cargo,
                n.dias_trabajados,
                n.total_devengado,
                n.total_deducciones,
                n.neto_pagar,
                n.estado,
                COUNT(*) OVER() AS total_rows
            FROM nomina n
            INNER JOIN empleados e     ON n.empleado_id = e.id
            INNER JOIN periodo_nomina p ON n.periodo_id  = p.id
            WHERE n.empresa_id = :empresaId
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (
                    LOWER(e.nombres)    LIKE :search
                    OR LOWER(e.apellidos)   LIKE :search
                    OR LOWER(e.numero_documento) LIKE :search
                    OR LOWER(n.estado)  LIKE :search
                )
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY n.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<NominaTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(NominaTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }
}
