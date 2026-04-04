package com.cloud_technological.aura_pos.repositories.nomina;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class EmpleadoQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<EmpleadoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        // Extraer filtro de cargo desde params
        String cargo = null;
        if (pageable.getParams() != null && pageable.getParams() instanceof java.util.Map) {
            java.util.Map<String, Object> paramMap = (java.util.Map<String, Object>) pageable.getParams();
            Object cargoObj = paramMap.get("cargo");
            if (cargoObj != null) {
                cargo = cargoObj.toString().trim().toLowerCase();
            }
        }

        StringBuilder sql = new StringBuilder("""
            SELECT
                e.id,
                e.nombres,
                e.apellidos,
                CONCAT(e.nombres, ' ', e.apellidos) AS nombre_completo,
                e.tipo_documento,
                e.numero_documento,
                e.cargo,
                e.fecha_ingreso,
                e.salario_base,
                e.tipo_contrato,
                e.activo,
                COUNT(*) OVER() AS total_rows
            FROM empleados e
            WHERE e.empresa_id = :empresaId
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (
                    LOWER(e.nombres) LIKE :search
                    OR LOWER(e.apellidos) LIKE :search
                    OR LOWER(e.numero_documento) LIKE :search
                    OR LOWER(e.cargo) LIKE :search
                )
            """);
            params.addValue("search", "%" + search + "%");
        }

        if (cargo != null && !cargo.isEmpty()) {
            sql.append(" AND LOWER(e.cargo) LIKE :cargo");
            params.addValue("cargo", "%" + cargo + "%");
        }

        sql.append(" ORDER BY e.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<EmpleadoTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(EmpleadoTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }
}
