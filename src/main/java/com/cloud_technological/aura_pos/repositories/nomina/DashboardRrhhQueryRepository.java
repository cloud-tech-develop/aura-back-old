package com.cloud_technological.aura_pos.repositories.nomina;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Consultas de solo lectura del Centro de Recursos Humanos. */
@Repository
public class DashboardRrhhQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /** Totales de nómina de un mes (por la fecha fin del período). */
    public record TotalesMes(int anio, int mes, BigDecimal salarios, BigDecimal auxilio, BigDecimal otrosDevengos,
            BigDecimal devengado, BigDecimal seguridadSocial, BigDecimal parafiscales, BigDecimal provisiones,
            BigDecimal neto, long empleados) {}

    public List<TotalesMes> totalesPorMes(Integer empresaId, LocalDate desde, LocalDate hasta) {
        String sql = """
            SELECT EXTRACT(YEAR  FROM p.fecha_fin)::int AS anio,
                   EXTRACT(MONTH FROM p.fecha_fin)::int AS mes,
                   SUM(n.salario_proporcional)  AS salarios,
                   SUM(n.auxilio_transporte)    AS auxilio,
                   SUM(n.total_novedades_dev)   AS otros_devengos,
                   SUM(n.total_devengado)       AS devengado,
                   SUM(n.aporte_salud + n.aporte_pension + n.aporte_arl) AS seguridad_social,
                   SUM(n.aporte_caja + n.aporte_icbf + n.aporte_sena)    AS parafiscales,
                   SUM(n.provision_prima + n.provision_cesantias
                       + n.provision_int_cesantias + n.provision_vacaciones) AS provisiones,
                   SUM(n.neto_pagar)            AS neto,
                   COUNT(DISTINCT n.empleado_id) AS empleados
            FROM nomina n
            JOIN periodo_nomina p ON p.id = n.periodo_id
            WHERE n.empresa_id = :empresaId
              AND p.fecha_fin BETWEEN :desde AND :hasta
              AND n.estado <> 'ANULADO'
              AND p.estado <> 'ANULADO'
            GROUP BY 1, 2
            """;
        return jdbc.query(sql, rango(empresaId, desde, hasta), (rs, i) -> new TotalesMes(
                rs.getInt("anio"), rs.getInt("mes"),
                cero(rs.getBigDecimal("salarios")), cero(rs.getBigDecimal("auxilio")),
                cero(rs.getBigDecimal("otros_devengos")), cero(rs.getBigDecimal("devengado")),
                cero(rs.getBigDecimal("seguridad_social")), cero(rs.getBigDecimal("parafiscales")),
                cero(rs.getBigDecimal("provisiones")), cero(rs.getBigDecimal("neto")),
                rs.getLong("empleados")));
    }

    /**
     * Empleados vinculados al cierre de {@code fecha}: ya ingresaron y no se han
     * retirado. Un inactivo sin fecha de retiro no cuenta (se desactivó a mano).
     */
    public long activosAl(Integer empresaId, LocalDate fecha) {
        Long n = jdbc.queryForObject("""
            SELECT COUNT(*) FROM empleados e
            WHERE e.empresa_id = :empresaId
              AND e.fecha_ingreso <= :fecha
              AND (e.fecha_retiro IS NULL OR e.fecha_retiro > :fecha)
              AND (e.activo = TRUE OR e.fecha_retiro IS NOT NULL)
            """, new MapSqlParameterSource().addValue("empresaId", empresaId).addValue("fecha", fecha), Long.class);
        return n != null ? n : 0;
    }

    public long ingresos(Integer empresaId, LocalDate desde, LocalDate hasta) {
        return contar("""
            SELECT COUNT(*) FROM empleados
            WHERE empresa_id = :empresaId AND fecha_ingreso BETWEEN :desde AND :hasta
            """, rango(empresaId, desde, hasta));
    }

    public long retiros(Integer empresaId, LocalDate desde, LocalDate hasta) {
        return contar("""
            SELECT COUNT(*) FROM empleados
            WHERE empresa_id = :empresaId AND fecha_retiro BETWEEN :desde AND :hasta
            """, rango(empresaId, desde, hasta));
    }

    /** Estado y fechas del último período no anulado que termina en el rango; null si no hay. */
    public PeriodoMes ultimoPeriodo(Integer empresaId, LocalDate desde, LocalDate hasta) {
        List<PeriodoMes> r = jdbc.query("""
            SELECT estado, fecha_inicio, fecha_fin FROM periodo_nomina
            WHERE empresa_id = :empresaId
              AND fecha_fin BETWEEN :desde AND :hasta
              AND estado <> 'ANULADO'
            ORDER BY fecha_fin DESC, id DESC
            LIMIT 1
            """, rango(empresaId, desde, hasta), (rs, i) -> new PeriodoMes(
                rs.getString("estado"), rs.getDate("fecha_inicio").toLocalDate(),
                rs.getDate("fecha_fin").toLocalDate()));
        return r.isEmpty() ? null : r.get(0);
    }

    public record PeriodoMes(String estado, LocalDate inicio, LocalDate fin) {}

    /** Nóminas del mes en un estado (BORRADOR, APROBADO…). */
    public long nominasEnEstado(Integer empresaId, LocalDate desde, LocalDate hasta, String estado) {
        return contar("""
            SELECT COUNT(*) FROM nomina n
            JOIN periodo_nomina p ON p.id = n.periodo_id
            WHERE n.empresa_id = :empresaId
              AND p.fecha_fin BETWEEN :desde AND :hasta
              AND p.estado <> 'ANULADO'
              AND n.estado = :estado
            """, rango(empresaId, desde, hasta).addValue("estado", estado));
    }

    public long novedadesPendientes(Integer empresaId) {
        return contar("""
            SELECT COUNT(*) FROM asistencia_novedad_nomina
            WHERE empresa_id = :empresaId AND estado = 'PENDIENTE'
            """, new MapSqlParameterSource("empresaId", empresaId));
    }

    public long contratosPorVencer(Integer empresaId, LocalDate desde, LocalDate hasta) {
        return contar("""
            SELECT COUNT(*) FROM empleados
            WHERE empresa_id = :empresaId
              AND activo = TRUE
              AND fecha_retiro IS NULL
              AND fecha_fin_contrato BETWEEN :desde AND :hasta
            """, rango(empresaId, desde, hasta));
    }

    private long contar(String sql, MapSqlParameterSource p) {
        Long n = jdbc.queryForObject(sql, p, Long.class);
        return n != null ? n : 0;
    }

    private static MapSqlParameterSource rango(Integer empresaId, LocalDate desde, LocalDate hasta) {
        return new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("desde", desde)
                .addValue("hasta", hasta);
    }

    private static BigDecimal cero(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
