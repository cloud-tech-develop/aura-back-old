package com.cloud_technological.aura_pos.repositories.asistencia_frente;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.asistencia_frente.AsistenciaAlertaDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.AsistenciaDetalleDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.AsistenciaFrenteTableDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.RevisionFilterDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.PreliquidacionFrenteItemDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.SoporteInfoDto;

@Repository
public class AsistenciaFrenteQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    public List<PreliquidacionFrenteItemDto> preliquidacion(java.time.LocalDate desde, java.time.LocalDate hasta,
            Long proyectoId, Long frenteId, Integer empresaId) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                d.empleado_id,
                (e.nombres || ' ' || e.apellidos) AS empleado_nombre,
                e.numero_documento AS documento,
                p.nombre AS proyecto_nombre,
                f.nombre AS frente_nombre,
                d.fecha,
                d.estado_asistencia,
                d.horas_trabajadas,
                (COALESCE(d.horas_extra_diurnas, 0) + COALESCE(d.horas_extra_nocturnas, 0)
                    + COALESCE(d.horas_dominicales, 0) + COALESCE(d.horas_festivas, 0)) AS horas_extra,
                af.estado AS estado_frente,
                (SELECT string_agg(DISTINCT n.tipo_novedad, ', ')
                    FROM asistencia_novedad_nomina n
                    WHERE n.asistencia_frente_detalle_id = d.id
                      AND n.origen = 'PROYECTO_FRENTE') AS novedad_generada
            FROM asistencia_frente_detalle d
            INNER JOIN asistencia_frente af ON d.asistencia_frente_id = af.id
            LEFT JOIN empleados e ON d.empleado_id = e.id
            LEFT JOIN proyecto p ON d.proyecto_id = p.id
            LEFT JOIN proyecto_frente f ON d.frente_id = f.id
            WHERE d.empresa_id = :empresaId
              AND d.deleted_at IS NULL AND af.deleted_at IS NULL
              AND af.estado IN ('APROBADO', 'ENVIADO_NOMINA')
              AND d.fecha BETWEEN :desde AND :hasta
        """);
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId)
                .addValue("desde", desde).addValue("hasta", hasta);
        if (proyectoId != null) {
            sql.append(" AND d.proyecto_id = :proyectoId ");
            params.addValue("proyectoId", proyectoId);
        }
        if (frenteId != null) {
            sql.append(" AND d.frente_id = :frenteId ");
            params.addValue("frenteId", frenteId);
        }
        sql.append(" ORDER BY empleado_nombre ASC, d.fecha ASC ");
        return jdbc.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(PreliquidacionFrenteItemDto.class));
    }

    /** Suma de horas extra (todas las clases) del empleado en el rango de la semana. */
    public java.math.BigDecimal sumExtrasSemana(Integer empresaId, Long empleadoId,
            java.time.LocalDate desde, java.time.LocalDate hasta) {
        String sql = """
            SELECT COALESCE(SUM(
                COALESCE(horas_extra_diurnas, 0) + COALESCE(horas_extra_nocturnas, 0)
                + COALESCE(horas_extra_diurnas_dom_fest, 0) + COALESCE(horas_extra_nocturnas_dom_fest, 0)
            ), 0)
            FROM asistencia_frente_detalle
            WHERE empresa_id = :empresaId AND empleado_id = :empleadoId
              AND fecha BETWEEN :desde AND :hasta
              AND deleted_at IS NULL
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId)
                .addValue("empleadoId", empleadoId).addValue("desde", desde).addValue("hasta", hasta);
        java.math.BigDecimal v = jdbc.queryForObject(sql, params, java.math.BigDecimal.class);
        return v != null ? v : java.math.BigDecimal.ZERO;
    }

    /** ¿El empleado tiene asistencia por frente PENDIENTE en el rango (bloquea liquidación)? */
    public boolean tieneFrentePendiente(Integer empresaId, Long empleadoId,
            java.time.LocalDate desde, java.time.LocalDate hasta) {
        String sql = """
            SELECT COUNT(*) FROM asistencia_frente_detalle d
            INNER JOIN asistencia_frente af ON d.asistencia_frente_id = af.id
            WHERE d.empresa_id = :empresaId AND d.empleado_id = :empleadoId
              AND d.fecha BETWEEN :desde AND :hasta
              AND d.deleted_at IS NULL AND af.deleted_at IS NULL
              AND af.estado IN ('BORRADOR', 'ENVIADO_REVISION', 'EN_CORRECCION')
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId)
                .addValue("empleadoId", empleadoId).addValue("desde", desde).addValue("hasta", hasta);
        Long c = jdbc.queryForObject(sql, params, Long.class);
        return c != null && c > 0;
    }

    /**
     * Cuenta los DÍAS efectivamente trabajados en frentes (asistencia consolidada,
     * excluyendo ausencias, sin registro y días rechazados por el admin) en el rango.
     * Sirve para pagar a jornaleros por lo que realmente trabajaron.
     */
    public int contarDiasTrabajadosFrente(Integer empresaId, Long empleadoId,
            java.time.LocalDate desde, java.time.LocalDate hasta) {
        String sql = """
            SELECT COUNT(DISTINCT d.fecha)
            FROM asistencia_frente_detalle d
            INNER JOIN asistencia_frente af ON d.asistencia_frente_id = af.id
            WHERE d.empresa_id = :empresaId AND d.empleado_id = :empleadoId
              AND d.fecha BETWEEN :desde AND :hasta
              AND d.deleted_at IS NULL AND af.deleted_at IS NULL
              AND af.estado IN ('APROBADO', 'ENVIADO_NOMINA')
              AND d.estado_revision <> 'RECHAZADO'
              AND d.estado_asistencia NOT IN ('NO_ASISTIO', 'SIN_REGISTRO', 'SUSPENDIDO')
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId)
                .addValue("empleadoId", empleadoId).addValue("desde", desde).addValue("hasta", hasta);
        Long c = jdbc.queryForObject(sql, params, Long.class);
        return c != null ? c.intValue() : 0;
    }

    /** ¿El empleado tiene asistencia por frente APROBADA / enviada a nómina en el rango? */
    public boolean tieneFrenteConsolidada(Integer empresaId, Long empleadoId,
            java.time.LocalDate desde, java.time.LocalDate hasta) {
        String sql = """
            SELECT COUNT(*) FROM asistencia_frente_detalle d
            INNER JOIN asistencia_frente af ON d.asistencia_frente_id = af.id
            WHERE d.empresa_id = :empresaId AND d.empleado_id = :empleadoId
              AND d.fecha BETWEEN :desde AND :hasta
              AND d.deleted_at IS NULL AND af.deleted_at IS NULL
              AND af.estado IN ('APROBADO', 'ENVIADO_NOMINA')
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId)
                .addValue("empleadoId", empleadoId).addValue("desde", desde).addValue("hasta", hasta);
        Long c = jdbc.queryForObject(sql, params, Long.class);
        return c != null && c > 0;
    }

    public PageImpl<AsistenciaFrenteTableDto> bandeja(RevisionFilterDto filtro, Integer empresaId) {
        int page = filtro.getPage() != null ? filtro.getPage() : 0;
        int size = filtro.getRows() != null ? filtro.getRows() : 10;

        StringBuilder sql = new StringBuilder("""
            SELECT
                af.id,
                af.proyecto_id,
                p.codigo AS proyecto_codigo,
                p.nombre AS proyecto_nombre,
                af.frente_id,
                f.codigo AS frente_codigo,
                f.nombre AS frente_nombre,
                (lid.nombres || ' ' || lid.apellidos) AS lider_nombre,
                af.fecha,
                af.estado,
                af.enviado_revision_at,
                af.soporte_pdf_id,
                (SELECT COUNT(*) FROM asistencia_frente_detalle d
                    WHERE d.asistencia_frente_id = af.id AND d.deleted_at IS NULL) AS trabajadores_count,
                (SELECT COUNT(*) FROM asistencia_alerta a
                    WHERE a.asistencia_frente_id = af.id AND a.nivel = 'CRITICA'
                      AND a.estado = 'ABIERTA' AND a.deleted_at IS NULL) AS alertas_criticas,
                COUNT(*) OVER() AS total_rows
            FROM asistencia_frente af
            INNER JOIN proyecto p ON af.proyecto_id = p.id
            INNER JOIN proyecto_frente f ON af.frente_id = f.id
            LEFT JOIN empleados lid ON af.lider_id = lid.id
            WHERE af.empresa_id = :empresaId
              AND af.deleted_at IS NULL
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        if (filtro.getEstado() != null && !filtro.getEstado().isBlank()) {
            sql.append(" AND af.estado = :estado ");
            params.addValue("estado", filtro.getEstado());
        }
        if (filtro.getProyectoId() != null) {
            sql.append(" AND af.proyecto_id = :proyectoId ");
            params.addValue("proyectoId", filtro.getProyectoId());
        }
        if (filtro.getFecha() != null) {
            sql.append(" AND af.fecha = :fecha ");
            params.addValue("fecha", filtro.getFecha());
        }

        sql.append(" ORDER BY af.enviado_revision_at DESC NULLS LAST, af.fecha DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        java.util.List<AsistenciaFrenteTableDto> list = jdbc.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(AsistenciaFrenteTableDto.class));
        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public List<AsistenciaDetalleDto> listarDetalles(Long asistenciaFrenteId) {
        String sql = """
            SELECT
                d.id,
                d.empleado_id,
                (e.nombres || ' ' || e.apellidos) AS empleado_nombre,
                e.numero_documento AS documento,
                e.cargo,
                to_char(d.hora_entrada, 'HH24:MI') AS hora_entrada,
                to_char(d.hora_salida, 'HH24:MI') AS hora_salida,
                d.horas_trabajadas,
                d.horas_extra_diurnas,
                d.horas_extra_nocturnas,
                d.horas_dominicales_festivas,
                d.estado_asistencia,
                d.estado_revision,
                d.observacion_lider
            FROM asistencia_frente_detalle d
            INNER JOIN empleados e ON d.empleado_id = e.id
            WHERE d.asistencia_frente_id = :id
              AND d.deleted_at IS NULL
            ORDER BY e.nombres ASC
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("id", asistenciaFrenteId);
        return jdbc.query(sql, params, new BeanPropertyRowMapper<>(AsistenciaDetalleDto.class));
    }

    public SoporteInfoDto soporteInfo(Long soporteId) {
        String sql = """
            SELECT
                s.archivo_url,
                s.nombre_archivo,
                COALESCE(emp.nombres || ' ' || emp.apellidos, u.username) AS subido_por,
                s.created_at AS subido_at
            FROM asistencia_soporte_pdf s
            LEFT JOIN usuario u ON s.created_by = u.id
            LEFT JOIN empleados emp ON u.empleado_id = emp.id
            WHERE s.id = :id AND s.deleted_at IS NULL
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("id", soporteId);
        List<SoporteInfoDto> list = jdbc.query(sql, params, new BeanPropertyRowMapper<>(SoporteInfoDto.class));
        return list.isEmpty() ? null : list.get(0);
    }

    public List<AsistenciaAlertaDto> listarAlertas(Long asistenciaFrenteId) {
        String sql = """
            SELECT
                a.id,
                a.tipo_alerta,
                a.nivel,
                a.descripcion,
                a.estado,
                a.empleado_id,
                (e.nombres || ' ' || e.apellidos) AS empleado_nombre
            FROM asistencia_alerta a
            LEFT JOIN empleados e ON a.empleado_id = e.id
            WHERE a.asistencia_frente_id = :id
              AND a.deleted_at IS NULL
            ORDER BY a.id ASC
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("id", asistenciaFrenteId);
        return jdbc.query(sql, params, new BeanPropertyRowMapper<>(AsistenciaAlertaDto.class));
    }
}
