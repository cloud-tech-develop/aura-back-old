package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.contabilidad.DashboardContableDto.GrupoGastoDto;

/** Consultas de solo lectura del Centro de Contabilidad. */
@Repository
public class DashboardContableQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /** Fila de la serie mensual: un total por (año, mes, tipo de cuenta). */
    public record TotalMesTipo(int anio, int mes, String tipo, BigDecimal saldo) {}

    /**
     * Ingresos, costos y gastos por mes entre dos fechas. Misma regla que el
     * estado de resultados: solo CONTABILIZADO y sin los asientos de CIERRE
     * (si no, el cierre mensual deja las clases 4–6 en cero).
     */
    public List<TotalMesTipo> totalesPorMes(Integer empresaId, LocalDate desde, LocalDate hasta) {
        String sql = """
            SELECT EXTRACT(YEAR  FROM a.fecha)::int AS anio,
                   EXTRACT(MONTH FROM a.fecha)::int AS mes,
                   pc.tipo,
                   SUM(CASE WHEN pc.naturaleza = 'CREDITO' THEN ad.credito - ad.debito
                            ELSE                                ad.debito  - ad.credito END) AS saldo
            FROM asiento_detalle ad
            JOIN asiento_contable a ON a.id = ad.asiento_id
            JOIN plan_cuenta pc     ON pc.id = ad.cuenta_id
            WHERE a.empresa_id = :empresaId
              AND a.fecha BETWEEN :desde AND :hasta
              AND a.estado = 'CONTABILIZADO'
              AND a.tipo_origen <> 'CIERRE'
              AND pc.tipo IN ('INGRESO', 'COSTO', 'GASTO')
            GROUP BY 1, 2, 3
            """;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("desde", desde)
                .addValue("hasta", hasta);
        return jdbc.query(sql, p, (rs, i) -> new TotalMesTipo(
                rs.getInt("anio"), rs.getInt("mes"), rs.getString("tipo"), rs.getBigDecimal("saldo")));
    }

    /**
     * Gastos del rango agrupados por la cuenta de dos dígitos. El nombre sale
     * del plan de cuentas de la empresa; si esa cuenta no existe se usa el código.
     */
    public List<GrupoGastoDto> gastosPorGrupo(Integer empresaId, LocalDate desde, LocalDate hasta) {
        String sql = """
            SELECT g.codigo,
                   COALESCE(pg.nombre, 'Cuenta ' || g.codigo) AS nombre,
                   g.valor
            FROM (
                SELECT LEFT(pc.codigo, 2) AS codigo,
                       SUM(CASE WHEN pc.naturaleza = 'CREDITO' THEN ad.credito - ad.debito
                                ELSE                                ad.debito  - ad.credito END) AS valor
                FROM asiento_detalle ad
                JOIN asiento_contable a ON a.id = ad.asiento_id
                JOIN plan_cuenta pc     ON pc.id = ad.cuenta_id
                WHERE a.empresa_id = :empresaId
                  AND a.fecha BETWEEN :desde AND :hasta
                  AND a.estado = 'CONTABILIZADO'
                  AND a.tipo_origen <> 'CIERRE'
                  AND pc.tipo = 'GASTO'
                GROUP BY 1
            ) g
            LEFT JOIN plan_cuenta pg ON pg.empresa_id = :empresaId AND pg.codigo = g.codigo
            WHERE g.valor <> 0
            ORDER BY g.valor DESC
            """;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("desde", desde)
                .addValue("hasta", hasta);
        return jdbc.query(sql, p, (rs, i) -> {
            GrupoGastoDto dto = new GrupoGastoDto();
            dto.setCodigo(rs.getString("codigo"));
            dto.setNombre(rs.getString("nombre"));
            dto.setValor(rs.getBigDecimal("valor"));
            return dto;
        });
    }

    /** Estado del período del mes, o null si todavía no se ha creado. */
    public String estadoPeriodo(Integer empresaId, int anio, int mes) {
        String sql = """
            SELECT estado FROM periodo_contable
            WHERE empresa_id = :empresaId AND anio = :anio AND mes = :mes
            ORDER BY id DESC LIMIT 1
            """;
        List<String> r = jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("empresaId", empresaId).addValue("anio", anio).addValue("mes", mes), String.class);
        return r.isEmpty() ? null : r.get(0);
    }

    public long comprobantes(Integer empresaId, LocalDate desde, LocalDate hasta) {
        String sql = """
            SELECT COUNT(*) FROM asiento_contable
            WHERE empresa_id = :empresaId AND estado = 'CONTABILIZADO'
              AND fecha BETWEEN :desde AND :hasta
            """;
        Long n = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("empresaId", empresaId).addValue("desde", desde).addValue("hasta", hasta), Long.class);
        return n == null ? 0 : n;
    }

    public long borradores(Integer empresaId) {
        String sql = "SELECT COUNT(*) FROM asiento_contable WHERE empresa_id = :empresaId AND estado = 'BORRADOR'";
        Long n = jdbc.queryForObject(sql, new MapSqlParameterSource("empresaId", empresaId), Long.class);
        return n == null ? 0 : n;
    }

    public long extractosAbiertos(Integer empresaId) {
        String sql = "SELECT COUNT(*) FROM extracto_bancario WHERE empresa_id = :empresaId AND estado = 'ABIERTO'";
        Long n = jdbc.queryForObject(sql, new MapSqlParameterSource("empresaId", empresaId), Long.class);
        return n == null ? 0 : n;
    }

    /** Operaciones de cierre anual registradas para el año (PROVISION_RENTA, TRASLADO). */
    public List<String> operacionesCierreAnual(Integer empresaId, int anio) {
        String sql = "SELECT tipo FROM cierre_anual WHERE empresa_id = :empresaId AND anio = :anio";
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("empresaId", empresaId).addValue("anio", anio), String.class);
    }
}
