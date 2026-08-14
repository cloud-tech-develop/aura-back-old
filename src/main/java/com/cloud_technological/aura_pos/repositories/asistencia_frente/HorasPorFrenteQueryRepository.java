package com.cloud_technological.aura_pos.repositories.asistencia_frente;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Horas trabajadas por frente en un período (Fase 4.a).
 *
 * <p>Es el <b>driver de la distribución del costo laboral</b>. El ERP de
 * referencia reparte por un porcentaje fijo pactado en la vinculación —un
 * estimado—; aquí se reparte por lo que <b>de verdad pasó</b>.
 *
 * <p>Esa es la ventaja de tener asistencia por frente, que el ERP no tiene. Y
 * hoy se desperdicia: la V92 dimensionó `compra` y `gasto` con proyecto/frente
 * pero no `nomina`, así que la mano de obra —normalmente el costo mayor— no
 * llega al proyecto.
 */
@Repository
public class HorasPorFrenteQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * Horas por frente de un empleado en un rango.
     *
     * <p>Solo cuenta la asistencia <b>aprobada</b>: repartir costo con base en
     * marcajes sin revisar sería imputarle a una obra horas que el líder
     * todavía no confirmó.
     */
    public List<HorasFrente> horasPorFrente(Integer empresaId, Long empleadoId,
                                            LocalDate desde, LocalDate hasta) {
        String sql = """
            SELECT
                d.proyecto_id  AS proyectoId,
                d.frente_id    AS frenteId,
                COALESCE(SUM(d.horas_trabajadas), 0) AS horas
            FROM asistencia_frente_detalle d
            JOIN asistencia_frente a ON a.id = d.asistencia_frente_id
            WHERE d.empresa_id = :empresaId
              AND d.empleado_id = :empleadoId
              AND d.fecha BETWEEN :desde AND :hasta
              AND d.deleted_at IS NULL
              AND a.deleted_at IS NULL
              AND a.estado IN ('APROBADO', 'ENVIADO_NOMINA')
              AND d.estado_revision IN ('APROBADO', 'AJUSTADO', 'ENVIADO_NOMINA')
            GROUP BY d.proyecto_id, d.frente_id
            HAVING COALESCE(SUM(d.horas_trabajadas), 0) > 0
            ORDER BY horas DESC
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("empresaId", empresaId);
        params.addValue("empleadoId", empleadoId);
        params.addValue("desde", desde);
        params.addValue("hasta", hasta);

        return jdbcTemplate.query(sql, params, (rs, i) -> new HorasFrente(
                rs.getObject("proyectoId") != null ? rs.getLong("proyectoId") : null,
                rs.getObject("frenteId") != null ? rs.getLong("frenteId") : null,
                rs.getBigDecimal("horas")));
    }

    /**
     * Marca la asistencia como consumida por la nómina.
     *
     * <p>El estado {@code ENVIADO_NOMINA} ya existía en el CHECK desde la V78
     * y nadie lo ponía. Sirve para saber qué asistencia ya se costeó.
     */
    public int marcarEnviadoNomina(Integer empresaId, LocalDate desde, LocalDate hasta) {
        String sql = """
            UPDATE asistencia_frente
               SET estado = 'ENVIADO_NOMINA'
             WHERE empresa_id = :empresaId
               AND fecha BETWEEN :desde AND :hasta
               AND estado = 'APROBADO'
               AND deleted_at IS NULL
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("empresaId", empresaId);
        params.addValue("desde", desde);
        params.addValue("hasta", hasta);
        return jdbcTemplate.update(sql, params);
    }

    /** Horas de un empleado en un frente. Ordenado de mayor a menor. */
    public record HorasFrente(Long proyectoId, Long frenteId, BigDecimal horas) {}
}
