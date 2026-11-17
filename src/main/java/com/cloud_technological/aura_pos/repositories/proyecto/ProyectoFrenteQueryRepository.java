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
                f.es_general,
                (SELECT COUNT(*) FROM proyecto_frente_trabajador t
                    WHERE t.frente_id = f.id AND t.estado = 'ACTIVO' AND t.deleted_at IS NULL
                      AND EXISTS (SELECT 1 FROM contrato_laboral c
                                   WHERE c.empleado_id = t.empleado_id
                                     AND c.estado = 'ACTIVO' AND c.deleted_at IS NULL)) AS trabajadores_count
            FROM proyecto_frente f
            LEFT JOIN empleados lid ON f.lider_id = lid.id
            WHERE f.proyecto_id = :proyectoId
              AND f.empresa_id = :empresaId
              AND f.deleted_at IS NULL
            ORDER BY f.es_general DESC, f.codigo ASC
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
                t.estado,
                e.activo AS empleado_activo
            FROM proyecto_frente_trabajador t
            INNER JOIN empleados e ON t.empleado_id = e.id
            WHERE t.frente_id = :frenteId
              AND t.empresa_id = :empresaId
              AND t.deleted_at IS NULL
              AND EXISTS (
                  SELECT 1 FROM contrato_laboral c
                   WHERE c.empleado_id = e.id
                     AND c.estado = 'ACTIVO'
                     AND c.deleted_at IS NULL
              )
            ORDER BY e.nombres ASC
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("frenteId", frenteId)
                .addValue("empresaId", empresaId);
        return jdbc.query(sql, params, new BeanPropertyRowMapper<>(FrenteTrabajadorDto.class));
    }

    // ── Empresa sin frentes (V243/V244) ─────────────────────────────────────

    /**
     * Deja la empresa lista para trabajar por proyecto sin frentes: cada proyecto
     * con su frente General, y en él los trabajadores activos y el turno de los
     * frentes del proyecto. Idempotente; no quita nada de los frentes.
     *
     * @return cuántos trabajadores se pasaron al General
     */
    public int consolidarEnGeneral(Integer empresaId, Long usuarioId) {
        MapSqlParameterSource p = new MapSqlParameterSource("empresaId", empresaId).addValue("usuarioId", usuarioId);
        jdbc.update("""
INSERT INTO proyecto_frente (empresa_id, proyecto_id, codigo, nombre, descripcion, estado, es_general,
       created_by, created_at, updated_at)
SELECT p.empresa_id, p.id, 'GENERAL', 'General (todo el proyecto)',
       'Frente creado por el sistema para registrar el proyecto completo, sin dividirlo en frentes.',
       'ACTIVO', TRUE, :usuarioId, now(), now()
  FROM proyecto p
 WHERE p.empresa_id = :empresaId AND p.deleted_at IS NULL
   AND NOT EXISTS (SELECT 1 FROM proyecto_frente f
                    WHERE f.proyecto_id = p.id AND f.es_general AND f.deleted_at IS NULL)
                """, p);
        int trabajadores = jdbc.update("""
INSERT INTO proyecto_frente_trabajador (empresa_id, proyecto_id, frente_id, empleado_id, fecha_inicio,
       estado, observacion, created_by, created_at, updated_at)
SELECT DISTINCT ON (g.id, t.empleado_id) t.empresa_id, t.proyecto_id, g.id, t.empleado_id, t.fecha_inicio,
       'ACTIVO', 'Pasado al proyecto completo al dejar de usar frentes.', :usuarioId, now(), now()
  FROM proyecto_frente_trabajador t
  JOIN proyecto_frente f ON f.id = t.frente_id AND f.deleted_at IS NULL AND NOT f.es_general
  JOIN proyecto_frente g ON g.proyecto_id = t.proyecto_id AND g.es_general AND g.deleted_at IS NULL
 WHERE t.empresa_id = :empresaId AND t.estado = 'ACTIVO' AND t.deleted_at IS NULL
   AND NOT EXISTS (SELECT 1 FROM proyecto_frente_trabajador x
                    WHERE x.frente_id = g.id AND x.empleado_id = t.empleado_id
                      AND x.estado = 'ACTIVO' AND x.deleted_at IS NULL)
 ORDER BY g.id, t.empleado_id, t.fecha_inicio NULLS LAST
                """, p);
        jdbc.update("""
INSERT INTO frente_turno (empresa_id, proyecto_id, frente_id, turno_id, fecha_inicio, fecha_fin,
       created_by, created_at, updated_at)
SELECT DISTINCT ON (g.id) ft.empresa_id, ft.proyecto_id, g.id, ft.turno_id, ft.fecha_inicio, ft.fecha_fin,
       :usuarioId, now(), now()
  FROM frente_turno ft
  JOIN proyecto_frente f ON f.id = ft.frente_id AND f.deleted_at IS NULL AND NOT f.es_general
  JOIN proyecto_frente g ON g.proyecto_id = ft.proyecto_id AND g.es_general AND g.deleted_at IS NULL
 WHERE ft.empresa_id = :empresaId AND ft.deleted_at IS NULL
   AND NOT EXISTS (SELECT 1 FROM frente_turno x WHERE x.frente_id = g.id AND x.deleted_at IS NULL)
 ORDER BY g.id, ft.fecha_inicio DESC NULLS LAST, ft.id DESC
                """, p);
        return trabajadores;
    }
}
