package com.cloud_technological.aura_pos.repositories.proyecto;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.proyecto.FrenteTableDto;
import com.cloud_technological.aura_pos.dto.proyecto.FrenteTrabajadorDto;

@Repository
public class ProyectoFrenteQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    public List<FrenteTableDto> listarPorProyecto(Long proyectoId, Integer empresaId) {
        String sql = """
            SELECT
                f.id,
                f.proyecto_id,
                f.codigo,
                f.nombre,
                f.descripcion,
                f.ubicacion,
                f.lider_id,
                (lid.nombres || ' ' || lid.apellidos) AS lider_nombre,
                f.fecha_inicio,
                f.fecha_fin,
                f.estado,
                f.observacion,
                (SELECT COUNT(*) FROM proyecto_frente_trabajador t
                    WHERE t.frente_id = f.id AND t.estado = 'ACTIVO' AND t.deleted_at IS NULL) AS trabajadores_count
            FROM proyecto_frente f
            LEFT JOIN empleados lid ON f.lider_id = lid.id
            WHERE f.proyecto_id = :proyectoId
              AND f.empresa_id = :empresaId
              AND f.deleted_at IS NULL
            ORDER BY f.codigo ASC
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("proyectoId", proyectoId)
                .addValue("empresaId", empresaId);
        return jdbc.query(sql, params, new BeanPropertyRowMapper<>(FrenteTableDto.class));
    }

    public List<FrenteTrabajadorDto> listarTrabajadores(Long frenteId, Integer empresaId) {
        String sql = """
            SELECT
                t.id,
                t.empleado_id,
                (e.nombres || ' ' || e.apellidos) AS empleado_nombre,
                e.numero_documento AS documento,
                e.cargo,
                t.fecha_inicio,
                t.fecha_fin,
                t.estado
            FROM proyecto_frente_trabajador t
            INNER JOIN empleados e ON t.empleado_id = e.id
            WHERE t.frente_id = :frenteId
              AND t.empresa_id = :empresaId
              AND t.deleted_at IS NULL
            ORDER BY e.nombres ASC
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("frenteId", frenteId)
                .addValue("empresaId", empresaId);
        return jdbc.query(sql, params, new BeanPropertyRowMapper<>(FrenteTrabajadorDto.class));
    }
}
