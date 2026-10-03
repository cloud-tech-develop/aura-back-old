package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Movimiento del mayor por cuenta auxiliar para el balance de prueba. */
@Repository
public class BalancePruebaQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /** anterior = débito − crédito antes de {@code desde}; débitos y créditos dentro del rango. */
    public record Movimiento(Long cuentaId, BigDecimal anterior, BigDecimal debitos, BigDecimal creditos) {
    }

    public record CuentaPlan(Long id, String codigo, String nombre, String naturaleza, Integer nivel, boolean auxiliar) {
    }

    public List<Movimiento> movimientos(Integer empresaId, LocalDate desde, LocalDate hasta, Long terceroId,
            Long centroCostoId) {
        StringBuilder sql = new StringBuilder("""
            SELECT ad.cuenta_id,
                   SUM(CASE WHEN a.fecha < :desde THEN ad.debito - ad.credito ELSE 0 END) AS anterior,
                   SUM(CASE WHEN a.fecha >= :desde THEN ad.debito  ELSE 0 END)            AS debitos,
                   SUM(CASE WHEN a.fecha >= :desde THEN ad.credito ELSE 0 END)            AS creditos
              FROM asiento_detalle ad
              JOIN asiento_contable a ON a.id = ad.asiento_id
             WHERE a.empresa_id = :e
               AND a.estado = 'CONTABILIZADO'
               AND a.fecha <= :hasta
            """);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("e", empresaId).addValue("desde", desde).addValue("hasta", hasta);
        if (terceroId != null) {
            sql.append(" AND ad.tercero_id = :t ");
            p.addValue("t", terceroId);
        }
        if (centroCostoId != null) {
            sql.append(" AND ad.centro_costo_id = :cc ");
            p.addValue("cc", centroCostoId);
        }
        sql.append(" GROUP BY ad.cuenta_id ");
        return jdbc.query(sql.toString(), p, (rs, i) -> new Movimiento(rs.getLong("cuenta_id"),
                rs.getBigDecimal("anterior"), rs.getBigDecimal("debitos"), rs.getBigDecimal("creditos")));
    }

    public List<CuentaPlan> plan(Integer empresaId) {
        return jdbc.query("""
            SELECT id, codigo, nombre, naturaleza, nivel, COALESCE(auxiliar, FALSE) AS auxiliar
              FROM plan_cuenta WHERE empresa_id = :e ORDER BY codigo
            """, new MapSqlParameterSource("e", empresaId), (rs, i) -> new CuentaPlan(rs.getLong("id"),
                rs.getString("codigo"), rs.getString("nombre"), rs.getString("naturaleza"),
                rs.getInt("nivel"), rs.getBoolean("auxiliar")));
    }
}
