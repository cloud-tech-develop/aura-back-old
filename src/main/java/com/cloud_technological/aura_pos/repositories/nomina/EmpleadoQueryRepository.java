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

        // El cargo, el salario y el tipo de contrato se muestran desde el CONTRATO
        // activo/principal, no desde el empleado: con el alta nueva esos campos
        // quedan vacíos en `empleados` (el real vive en el contrato). Se cae al
        // dato del empleado solo si no hay contrato (empleados legacy).
        StringBuilder sql = new StringBuilder("""
            SELECT
                e.id,
                e.nombres,
                e.apellidos,
                CONCAT(e.nombres, ' ', e.apellidos) AS nombre_completo,
                e.tipo_documento,
                e.numero_documento,
                COALESCE(ct.cargo, e.cargo) AS cargo,
                e.fecha_ingreso,
                COALESCE(ct.salario_base, e.salario_base) AS salario_base,
                COALESCE(ct.tipo_contrato, e.tipo_contrato) AS tipo_contrato,
                e.activo,
                e.requiere_control_asistencia,
                COUNT(*) OVER() AS total_rows,
                u.id AS usuario_id
            FROM empleados e
            LEFT JOIN usuario u ON u.empleado_id = e.id
            LEFT JOIN LATERAL (
                SELECT c.cargo, c.salario_base, c.tipo_contrato
                FROM contrato_laboral c
                WHERE c.empleado_id = e.id
                  AND c.deleted_at IS NULL
                  AND c.estado = 'ACTIVO'
                ORDER BY c.es_principal DESC, c.fecha_inicio DESC
                LIMIT 1
            ) ct ON true
            WHERE e.empresa_id = :empresaId
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (
                    LOWER(e.nombres) LIKE :search
                    OR LOWER(e.apellidos) LIKE :search
                    OR LOWER(e.numero_documento) LIKE :search
                    OR LOWER(COALESCE(ct.cargo, e.cargo)) LIKE :search
                )
            """);
            params.addValue("search", "%" + search + "%");
        }

        if (cargo != null && !cargo.isEmpty()) {
            sql.append(" AND LOWER(COALESCE(ct.cargo, e.cargo)) LIKE :cargo");
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
