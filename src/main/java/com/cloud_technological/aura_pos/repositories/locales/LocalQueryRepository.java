package com.cloud_technological.aura_pos.repositories.locales;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.locales.LocalTableDto;

@Repository
public class LocalQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public List<LocalTableDto> page(Integer empresaId, Integer page, Integer rows, Long vendedorActualId, Long vendedorAnteriorId, String search) {
        StringBuilder sql = new StringBuilder();
        sql.append("""
            SELECT 
                l.id, l.nombre, l.direccion, l.ciudad, l.ciudad_id, l.barrio, l.latitud, l.longitud, l.imagen_fachada,
                l.activo,
                CONCAT(ea.nombres, ' ', ea.apellidos) AS vendedor_actual_nombre,
                CONCAT(ep.nombres, ' ', ep.apellidos) AS vendedor_anterior_nombre,
                COUNT(*) OVER() AS total_rows
            FROM locales l
            LEFT JOIN empleados ea ON l.vendedor_actual_id = ea.id
            LEFT JOIN empleados ep ON l.vendedor_anterior_id = ep.id
            WHERE l.empresa_id = :empresaId
            AND l.activo = true
        """);

        Map<String, Object> params = new HashMap<>();
        params.put("empresaId", empresaId);
        params.put("limit", rows);
        params.put("offset", page * rows);

        if (vendedorActualId != null) {
            sql.append(" AND l.vendedor_actual_id = :vendedorActualId");
            params.put("vendedorActualId", vendedorActualId);
        }
        if (vendedorAnteriorId != null) {
            sql.append(" AND l.vendedor_anterior_id = :vendedorAnteriorId");
            params.put("vendedorAnteriorId", vendedorAnteriorId);
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (l.nombre ILIKE :search OR l.direccion ILIKE :search)");
            params.put("search", "%" + search + "%");
        }

        sql.append(" ORDER BY l.nombre ASC");
        sql.append(" LIMIT :limit OFFSET :offset");

        return jdbcTemplate.query(sql.toString(), params, new BeanPropertyRowMapper<>(LocalTableDto.class));
    }

    public long countByEmpresaId(Long empresaId) {
        String sql = "SELECT COUNT(*) FROM locales WHERE empresa_id = :empresaId AND activo = true";
        return jdbcTemplate.queryForObject(sql, Map.of("empresaId", empresaId), Long.class);
    }

    public boolean tieneVisitasAsociadas(Long localId) {
        String sql = "SELECT COUNT(*) FROM visitas WHERE local_id = :localId AND estado != 'CANCELADA'";
        Long count = jdbcTemplate.queryForObject(sql, Map.of("localId", localId), Long.class);
        return count != null && count > 0;
    }
}