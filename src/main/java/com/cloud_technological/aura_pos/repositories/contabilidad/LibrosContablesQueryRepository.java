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
 * Lecturas de los libros oficiales (auxiliar por tercero y diario).
 *
 * <p>Solo asientos CONTABILIZADOS, como el resto de reportes: un borrador
 * todavía no es contabilidad. Devuelve filas planas; el servicio las agrupa.
 */
@Repository
public class LibrosContablesQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /** Una partida del mayor con los datos de su cuenta, su asiento y su tercero. */
    public record Partida(
            Long cuentaId, String cuentaCodigo, String cuentaNombre, String naturaleza,
            Long terceroId, String terceroDocumento, String terceroNombre,
            Long asientoId, LocalDate fecha, String numeroComprobante, String tipoOrigen,
            String descripcionAsiento, String descripcion,
            BigDecimal debito, BigDecimal credito) {
    }

    /** Saldo (débito − crédito) de una cuenta con un tercero hasta antes del rango. */
    public record SaldoAnterior(
            Long cuentaId, String cuentaCodigo, String cuentaNombre, String naturaleza,
            Long terceroId, String terceroDocumento, String terceroNombre,
            BigDecimal saldo) {
    }

    private static final String TERCERO_DOCUMENTO =
            "CASE WHEN t.id IS NULL THEN NULL "
            + "WHEN t.dv IS NOT NULL AND t.dv <> '' THEN t.numero_documento || '-' || t.dv "
            + "ELSE t.numero_documento END";

    private static final String TERCERO_NOMBRE =
            "COALESCE(NULLIF(TRIM(COALESCE(t.razon_social, '')), ''), "
            + "NULLIF(TRIM(COALESCE(t.nombres, '') || ' ' || COALESCE(t.apellidos, '')), ''))";

    /**
     * Filtro de cuentas por rango de códigos PUC. Compara el prefijo del largo
     * del límite, así que "13" a "13" trae todas las 13xx y "1305" a "1380"
     * trae el rango completo con sus auxiliares.
     */
    private static void filtros(StringBuilder sql, MapSqlParameterSource p,
            String cuentaDesde, String cuentaHasta, Long terceroId) {
        if (cuentaDesde != null && !cuentaDesde.isBlank()) {
            sql.append(" AND LEFT(pc.codigo, LENGTH(:cuentaDesde)) >= :cuentaDesde");
            p.addValue("cuentaDesde", cuentaDesde.trim());
        }
        if (cuentaHasta != null && !cuentaHasta.isBlank()) {
            sql.append(" AND LEFT(pc.codigo, LENGTH(:cuentaHasta)) <= :cuentaHasta");
            p.addValue("cuentaHasta", cuentaHasta.trim());
        }
        if (terceroId != null) {
            sql.append(" AND ad.tercero_id = :terceroId");
            p.addValue("terceroId", terceroId);
        }
    }

    public List<SaldoAnterior> saldosAnteriores(Integer empresaId, LocalDate desde,
            String cuentaDesde, String cuentaHasta, Long terceroId) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("desde", Date.valueOf(desde));
        StringBuilder sql = new StringBuilder("""
            SELECT pc.id AS cuenta_id, pc.codigo, pc.nombre, pc.naturaleza,
                   ad.tercero_id,
            """).append(TERCERO_DOCUMENTO).append(" AS tercero_documento, ")
                .append(TERCERO_NOMBRE).append(" AS tercero_nombre, ").append("""
                   SUM(ad.debito - ad.credito) AS saldo
            FROM asiento_detalle ad
            JOIN asiento_contable a ON a.id = ad.asiento_id
            JOIN plan_cuenta pc     ON pc.id = ad.cuenta_id
            LEFT JOIN tercero t     ON t.id = ad.tercero_id
            WHERE a.empresa_id = :empresaId
              AND a.estado = 'CONTABILIZADO'
              AND a.fecha < :desde
            """);
        filtros(sql, p, cuentaDesde, cuentaHasta, terceroId);
        sql.append("""
             GROUP BY pc.id, pc.codigo, pc.nombre, pc.naturaleza, ad.tercero_id,
                      t.id, t.dv, t.numero_documento, t.razon_social, t.nombres, t.apellidos
             HAVING SUM(ad.debito - ad.credito) <> 0
            """);
        return jdbc.query(sql.toString(), p, (rs, i) -> new SaldoAnterior(
                rs.getLong("cuenta_id"), rs.getString("codigo"), rs.getString("nombre"),
                rs.getString("naturaleza"),
                (Long) rs.getObject("tercero_id", Long.class),
                rs.getString("tercero_documento"), rs.getString("tercero_nombre"),
                rs.getBigDecimal("saldo")));
    }

    public List<Partida> partidas(Integer empresaId, LocalDate desde, LocalDate hasta,
            String cuentaDesde, String cuentaHasta, Long terceroId) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("desde", Date.valueOf(desde))
                .addValue("hasta", Date.valueOf(hasta));
        StringBuilder sql = new StringBuilder("""
            SELECT pc.id AS cuenta_id, pc.codigo, pc.nombre, pc.naturaleza,
                   ad.tercero_id,
            """).append(TERCERO_DOCUMENTO).append(" AS tercero_documento, ")
                .append(TERCERO_NOMBRE).append(" AS tercero_nombre, ").append("""
                   a.id AS asiento_id, a.fecha, a.numero_comprobante, a.tipo_origen,
                   a.descripcion AS descripcion_asiento,
                   COALESCE(NULLIF(ad.descripcion, ''), a.descripcion) AS descripcion,
                   ad.debito, ad.credito
            FROM asiento_detalle ad
            JOIN asiento_contable a ON a.id = ad.asiento_id
            JOIN plan_cuenta pc     ON pc.id = ad.cuenta_id
            LEFT JOIN tercero t     ON t.id = ad.tercero_id
            WHERE a.empresa_id = :empresaId
              AND a.estado = 'CONTABILIZADO'
              AND a.fecha BETWEEN :desde AND :hasta
            """);
        filtros(sql, p, cuentaDesde, cuentaHasta, terceroId);
        // Orden cronológico estable: el diario lo usa tal cual y el auxiliar
        // lo reagrupa sin perder el orden de las líneas de cada tercero.
        sql.append(" ORDER BY a.fecha, a.id, ad.id");
        return jdbc.query(sql.toString(), p, (rs, i) -> new Partida(
                rs.getLong("cuenta_id"), rs.getString("codigo"), rs.getString("nombre"),
                rs.getString("naturaleza"),
                (Long) rs.getObject("tercero_id", Long.class),
                rs.getString("tercero_documento"), rs.getString("tercero_nombre"),
                rs.getLong("asiento_id"),
                rs.getDate("fecha").toLocalDate(),
                rs.getString("numero_comprobante"), rs.getString("tipo_origen"),
                rs.getString("descripcion_asiento"), rs.getString("descripcion"),
                rs.getBigDecimal("debito"), rs.getBigDecimal("credito")));
    }
}
