package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.contabilidad.CuentaTerceroMovimientoDto;
import com.cloud_technological.aura_pos.dto.contabilidad.TerceroExogenaDto;

/**
 * Consultas jdbc de la exógena (E11): movimientos por cuenta × tercero para
 * la generación de lotes y los insumos del validador previo (borradores,
 * períodos abiertos, datos fiscales de terceros).
 */
@Repository
public class ExogenaQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /**
     * Movimientos del año agrupados por cuenta × tercero, solo contabilizados
     * y sin asientos de CIERRE (el cierre cancela las cuentas de resultado y
     * distorsionaría los pagos/ingresos brutos del año).
     */
    public List<CuentaTerceroMovimientoDto> movimientosDelAnio(Integer empresaId, int anio) {
        return consultarMovimientos(empresaId,
                "AND a.fecha BETWEEN CAST(:desde AS DATE) AND CAST(:hasta AS DATE)",
                new MapSqlParameterSource()
                        .addValue("empresaId", empresaId)
                        .addValue("desde", anio + "-01-01")
                        .addValue("hasta", anio + "-12-31"));
    }

    /** Saldos acumulados a diciembre 31 por cuenta × tercero (formatos 1008/1009). */
    public List<CuentaTerceroMovimientoDto> saldosAlCorte(Integer empresaId, int anio) {
        return consultarMovimientos(empresaId,
                "AND a.fecha <= CAST(:hasta AS DATE)",
                new MapSqlParameterSource()
                        .addValue("empresaId", empresaId)
                        .addValue("hasta", anio + "-12-31"));
    }

    private List<CuentaTerceroMovimientoDto> consultarMovimientos(Integer empresaId,
            String filtroFecha, MapSqlParameterSource params) {
        String sql = """
            SELECT pc.codigo,
                   ad.tercero_id,
                   COALESCE(SUM(ad.debito), 0)  AS debito,
                   COALESCE(SUM(ad.credito), 0) AS credito
            FROM asiento_detalle ad
            JOIN asiento_contable a ON a.id = ad.asiento_id
            JOIN plan_cuenta pc     ON pc.id = ad.cuenta_id
            WHERE a.empresa_id = :empresaId
              AND a.estado = 'CONTABILIZADO'
              AND a.tipo_origen <> 'CIERRE'
              %s
            GROUP BY pc.codigo, ad.tercero_id
            """.formatted(filtroFecha);
        return jdbc.query(sql, params,
                (rs, i) -> new CuentaTerceroMovimientoDto(
                        rs.getString("codigo"),
                        rs.getObject("tercero_id") != null ? rs.getLong("tercero_id") : null,
                        rs.getBigDecimal("debito"),
                        rs.getBigDecimal("credito")));
    }

    /** Comprobantes en borrador del año (bloquean la exógena). */
    public List<String> comprobantesBorrador(Integer empresaId, int anio) {
        String sql = """
            SELECT a.numero_comprobante || ' (' || a.fecha || ')'
            FROM asiento_contable a
            WHERE a.empresa_id = :empresaId
              AND a.estado = 'BORRADOR'
              AND a.fecha BETWEEN CAST(:desde AS DATE) AND CAST(:hasta AS DATE)
            ORDER BY a.fecha
            LIMIT 50
            """;
        return jdbc.queryForList(sql, Map.of("empresaId", empresaId,
                "desde", anio + "-01-01", "hasta", anio + "-12-31"), String.class);
    }

    /** Meses del año con período contable aún ABIERTO. */
    public List<Integer> mesesAbiertos(Integer empresaId, int anio) {
        String sql = """
            SELECT mes FROM periodo_contable
            WHERE empresa_id = :empresaId AND anio = :anio AND estado = 'ABIERTO'
            ORDER BY mes
            """;
        return jdbc.queryForList(sql,
                Map.of("empresaId", empresaId, "anio", anio), Integer.class);
    }

    /** Datos fiscales de los terceros con movimiento (validador y export). */
    public List<TerceroExogenaDto> terceros(Integer empresaId, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String sql = """
            SELECT id, tipo_documento, numero_documento, dv, razon_social,
                   nombres, apellidos, direccion, municipio
            FROM tercero
            WHERE empresa_id = :empresaId AND id IN (:ids)
            """;
        return jdbc.query(sql,
                new MapSqlParameterSource()
                        .addValue("empresaId", empresaId)
                        .addValue("ids", ids),
                (rs, i) -> new TerceroExogenaDto(
                        rs.getLong("id"),
                        rs.getString("tipo_documento"),
                        rs.getString("numero_documento"),
                        rs.getString("dv"),
                        rs.getString("razon_social"),
                        rs.getString("nombres"),
                        rs.getString("apellidos"),
                        rs.getString("direccion"),
                        rs.getString("municipio")));
    }
}
