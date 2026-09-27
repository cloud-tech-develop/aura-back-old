package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Lecturas para los borradores de declaraciones (IVA 300, retención 350).
 *
 * <p>La fuente de verdad es el mayor (asientos CONTABILIZADOS): es lo que el
 * contador va a cuadrar. Los documentos (ventas, compras, gastos) solo aportan
 * bases por tarifa, que el mayor no guarda.
 */
@Repository
public class DeclaracionesQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /** Movimiento de una cuenta en el rango, separado por tipo de documento. */
    public record Movimiento(Long cuentaId, String codigo, String nombre, String tipoOrigen,
            BigDecimal debito, BigDecimal credito) {
    }

    /** Base y valor por tarifa, leídos de los documentos. */
    public record PorTarifa(BigDecimal tarifa, BigDecimal base, BigDecimal valor, int documentos) {
    }

    private static MapSqlParameterSource rango(Integer empresaId, LocalDate desde, LocalDate hasta) {
        return new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("desde", Date.valueOf(desde))
                .addValue("hasta", Date.valueOf(hasta));
    }

    /** Movimientos de las cuentas que empiezan por alguno de los prefijos. */
    public List<Movimiento> movimientos(Integer empresaId, LocalDate desde, LocalDate hasta,
            List<String> prefijos) {
        MapSqlParameterSource p = rango(empresaId, desde, hasta);
        StringBuilder filtro = new StringBuilder();
        for (int i = 0; i < prefijos.size(); i++) {
            if (i > 0) filtro.append(" OR ");
            filtro.append("pc.codigo LIKE :pre").append(i);
            p.addValue("pre" + i, prefijos.get(i) + "%");
        }
        String sql = """
            SELECT pc.id AS cuenta_id, pc.codigo, pc.nombre, a.tipo_origen,
                   SUM(ad.debito) AS debito, SUM(ad.credito) AS credito
            FROM asiento_detalle ad
            JOIN asiento_contable a ON a.id = ad.asiento_id
            JOIN plan_cuenta pc     ON pc.id = ad.cuenta_id
            WHERE a.empresa_id = :empresaId
              AND a.estado = 'CONTABILIZADO'
              AND a.fecha BETWEEN :desde AND :hasta
              AND (""" + filtro + """
            )
            GROUP BY pc.id, pc.codigo, pc.nombre, a.tipo_origen
            ORDER BY pc.codigo, a.tipo_origen
            """;
        return jdbc.query(sql, p, (rs, i) -> new Movimiento(rs.getLong("cuenta_id"),
                rs.getString("codigo"), rs.getString("nombre"), rs.getString("tipo_origen"),
                rs.getBigDecimal("debito"), rs.getBigDecimal("credito")));
    }

    /** Asientos del rango que siguen en borrador: no entran al borrador de la declaración. */
    public int borradores(Integer empresaId, LocalDate desde, LocalDate hasta) {
        Integer n = jdbc.queryForObject("""
            SELECT COUNT(*) FROM asiento_contable
            WHERE empresa_id = :empresaId AND estado = 'BORRADOR'
              AND fecha BETWEEN :desde AND :hasta
            """, rango(empresaId, desde, hasta), Integer.class);
        return n != null ? n : 0;
    }

    /**
     * Ventas por tarifa de IVA. subtotal_linea incluye el impuesto, así que la
     * base es subtotal − impuesto y la tarifa se deduce de las dos.
     */
    public List<PorTarifa> ventasPorTarifa(Integer empresaId, LocalDate desde, LocalDate hasta) {
        String sql = """
            SELECT tarifa, SUM(base) AS base, SUM(impuesto) AS valor, COUNT(DISTINCT venta_id) AS docs
            FROM (
                SELECT v.id AS venta_id,
                       (vd.subtotal_linea - COALESCE(vd.impuesto_valor, 0)) AS base,
                       COALESCE(vd.impuesto_valor, 0) AS impuesto,
                       CASE WHEN (vd.subtotal_linea - COALESCE(vd.impuesto_valor, 0)) > 0
                            THEN ROUND(COALESCE(vd.impuesto_valor, 0) * 100
                                       / (vd.subtotal_linea - COALESCE(vd.impuesto_valor, 0)))
                            ELSE 0 END AS tarifa
                FROM venta v
                JOIN venta_detalle vd ON vd.venta_id = v.id
                WHERE v.empresa_id = :empresaId
                  AND v.estado_venta = 'COMPLETADA'
                  AND DATE(v.fecha_emision) BETWEEN :desde AND :hasta
            ) x
            GROUP BY tarifa
            ORDER BY tarifa
            """;
        return jdbc.query(sql, rango(empresaId, desde, hasta), (rs, i) -> new PorTarifa(
                rs.getBigDecimal("tarifa"), rs.getBigDecimal("base"), rs.getBigDecimal("valor"),
                rs.getInt("docs")));
    }

    /** Bases de retención en la fuente practicada en compras, por tarifa. */
    public List<PorTarifa> retencionCompras(Integer empresaId, LocalDate desde, LocalDate hasta) {
        String sql = """
            SELECT c.retefuente_pct AS tarifa,
                   SUM(COALESCE(c.subtotal, 0) - COALESCE(c.descuento_total, 0)) AS base,
                   SUM(c.retefuente_valor) AS valor, COUNT(*) AS docs
            FROM compra c
            WHERE c.empresa_id = :empresaId
              AND c.estado = 'RECIBIDA'
              AND COALESCE(c.tipo_documento, 'FACTURA_COMPRA') <> 'NOTA_CREDITO'
              AND COALESCE(c.retefuente_valor, 0) > 0
              AND DATE(c.fecha) BETWEEN :desde AND :hasta
            GROUP BY c.retefuente_pct
            ORDER BY c.retefuente_pct
            """;
        return jdbc.query(sql, rango(empresaId, desde, hasta), (rs, i) -> new PorTarifa(
                rs.getBigDecimal("tarifa"), rs.getBigDecimal("base"), rs.getBigDecimal("valor"),
                rs.getInt("docs")));
    }

    /** Bases de retención en la fuente practicada en gastos, por tarifa. */
    public List<PorTarifa> retencionGastos(Integer empresaId, LocalDate desde, LocalDate hasta) {
        String sql = """
            SELECT g.tarifa_retefuente AS tarifa,
                   SUM(COALESCE(g.base_retefuente, g.monto)) AS base,
                   SUM(g.valor_retefuente) AS valor, COUNT(*) AS docs
            FROM gasto g
            WHERE g.empresa_id = :empresaId
              AND g.estado = 'ACTIVO'
              AND COALESCE(g.valor_retefuente, 0) > 0
              AND g.fecha BETWEEN :desde AND :hasta
            GROUP BY g.tarifa_retefuente
            ORDER BY g.tarifa_retefuente
            """;
        return jdbc.query(sql, rango(empresaId, desde, hasta), (rs, i) -> new PorTarifa(
                rs.getBigDecimal("tarifa"), rs.getBigDecimal("base"), rs.getBigDecimal("valor"),
                rs.getInt("docs")));
    }

    /** Cuentas de impuestos parametrizadas (E5): para saber cuál es generado y cuál descontable. */
    public record CuentaImpuesto(String tipo, Long cuentaGeneradoId, Long cuentaDescontableId) {
    }

    public List<CuentaImpuesto> cuentasImpuesto(Integer empresaId) {
        return jdbc.query("""
            SELECT tipo, cuenta_generado_id, cuenta_descontable_id
            FROM impuesto WHERE empresa_id = :empresaId AND activo = TRUE
            """, new MapSqlParameterSource("empresaId", empresaId), (rs, i) -> new CuentaImpuesto(
                rs.getString("tipo"),
                (Long) rs.getObject("cuenta_generado_id", Long.class),
                (Long) rs.getObject("cuenta_descontable_id", Long.class)));
    }
}
