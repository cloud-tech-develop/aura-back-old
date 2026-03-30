package com.cloud_technological.aura_pos.repositories.rutas;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.rutas.RutaTableDto;

@Repository
public class RutaQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public List<RutaTableDto> page(Integer empresaId, Integer page, Integer rows, Long vendedorId, String search) {
        StringBuilder sql = new StringBuilder();
        sql.append("""
            SELECT 
                r.id, r.nombre,
                CONCAT(e.nombres, ' ', e.apellidos) AS vendedor_nombre,
                (SELECT COUNT(*) FROM rutas_locales WHERE ruta_id = r.id) AS cantidad_locales,
                r.activo,
                COUNT(*) OVER() AS total_rows
            FROM rutas r
            JOIN empleados e ON r.vendedor_id = e.id
            WHERE r.empresa_id = :empresaId
            AND r.activo = true
        """);

        Map<String, Object> params = new HashMap<>();
        params.put("empresaId", empresaId);
        params.put("limit", rows);
        params.put("offset", page * rows);

        if (vendedorId != null) {
            sql.append(" AND r.vendedor_id = :vendedorId");
            params.put("vendedorId", vendedorId);
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND r.nombre ILIKE :search");
            params.put("search", "%" + search + "%");
        }

        sql.append(" ORDER BY r.nombre ASC");

        return jdbcTemplate.query(sql.toString(), params, new BeanPropertyRowMapper<>(RutaTableDto.class));
    }

    public long countByEmpresaId(Long empresaId) {
        String sql = "SELECT COUNT(*) FROM rutas WHERE empresa_id = :empresaId AND activo = true";
        return jdbcTemplate.queryForObject(sql, Map.of("empresaId", empresaId), Long.class);
    }

    public boolean tieneVisitasAsociadas(Long rutaId) {
        String sql = "SELECT COUNT(*) FROM visitas WHERE ruta_id = :rutaId AND estado != 'CANCELADA'";
        Long count = jdbcTemplate.queryForObject(sql, Map.of("rutaId", rutaId), Long.class);
        return count != null && count > 0;
    }
}