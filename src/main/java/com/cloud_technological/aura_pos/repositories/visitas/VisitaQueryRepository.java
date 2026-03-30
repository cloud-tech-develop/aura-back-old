package com.cloud_technological.aura_pos.repositories.visitas;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.visitas.VisitaTableDto;

@Repository
public class VisitaQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public List<VisitaTableDto> page(Integer empresaId, Integer page, Integer rows, Long vendedorId, 
            LocalDate fechaDesde, LocalDate fechaHasta, String estado, String search) {
        
        StringBuilder sql = new StringBuilder();
        sql.append("""
            SELECT 
                v.id,
                l.nombre AS local_nombre,
                l.direccion AS local_direccion,
                CONCAT(e.nombres, ' ', e.apellidos) AS vendedor_nombre,
                r.nombre AS ruta_nombre,
                v.fecha_programada,
                v.hora_programada,
                v.estado,
                COUNT(*) OVER() AS total_rows
            FROM visitas v
            JOIN locales l ON v.local_id = l.id
            JOIN empleados e ON v.vendedor_id = e.id
            LEFT JOIN rutas r ON v.ruta_id = r.id
            WHERE v.empresa_id = :empresaId
        """);

        Map<String, Object> params = new HashMap<>();
        params.put("empresaId", empresaId);
        params.put("limit", rows);
        params.put("offset", page * rows);

        if (vendedorId != null) {
            sql.append(" AND v.vendedor_id = :vendedorId");
            params.put("vendedorId", vendedorId);
        }
        if (fechaDesde != null) {
            sql.append(" AND v.fecha_programada >= :fechaDesde");
            params.put("fechaDesde", fechaDesde.atStartOfDay());
        }
        if (fechaHasta != null) {
            sql.append(" AND v.fecha_programada <= :fechaHasta");
            params.put("fechaHasta", fechaHasta.plusDays(1).atStartOfDay());
        }
        if (estado != null && !estado.isBlank()) {
            sql.append(" AND v.estado = :estado");
            params.put("estado", estado);
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (l.nombre ILIKE :search OR l.direccion ILIKE :search)");
            params.put("search", "%" + search + "%");
        }

        sql.append(" ORDER BY v.fecha_programada ASC, v.hora_programada ASC");

        return jdbcTemplate.query(sql.toString(), params, new BeanPropertyRowMapper<>(VisitaTableDto.class));
    }

    public List<VisitaTableDto> findByVendedorAndFecha(Long empresaId, Long vendedorId, LocalDate fecha) {
        String sql = """
            SELECT 
                v.id,
                l.nombre AS local_nombre,
                l.direccion AS local_direccion,
                CONCAT(e.nombres, ' ', e.apellidos) AS vendedor_nombre,
                r.nombre AS ruta_nombre,
                v.fecha_programada,
                v.hora_programada,
                v.estado,
                0 AS total_rows
            FROM visitas v
            JOIN locales l ON v.local_id = l.id
            JOIN empleados e ON v.vendedor_id = e.id
            LEFT JOIN rutas r ON v.ruta_id = r.id
            WHERE v.empresa_id = :empresaId
            AND v.vendedor_id = :vendedorId
            AND DATE(v.fecha_programada) = :fecha
            ORDER BY v.hora_programada ASC
        """;
        
        Map<String, Object> params = new HashMap<>();
        params.put("empresaId", empresaId);
        params.put("vendedorId", vendedorId);
        params.put("fecha", fecha);
        
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(VisitaTableDto.class));
    }

    public long countByEmpresaId(Long empresaId) {
        String sql = "SELECT COUNT(*) FROM visitas WHERE empresa_id = :empresaId";
        return jdbcTemplate.queryForObject(sql, Map.of("empresaId", empresaId), Long.class);
    }
}