package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Lecturas del pago de declaraciones (V204). */
@Repository
public class DeclaracionPagoQueryRepository {

    /** Orígenes del propio pago: no entran a los saldos que el pago va a cerrar. */
    public static final String TIPO_ORIGEN = "DECLARACION_PAGO";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /** Saldo del período de una cuenta: crédito − débito (positivo = por pagar). */
    public record SaldoCuenta(Long cuentaId, String codigo, String nombre, BigDecimal saldo) {
    }

    /**
     * Saldo del período de las cuentas que empiezan por los prefijos, sin el
     * pago de la declaración ni su anulación (si no, una vez pagada el saldo
     * quedaría en cero y el borrador ya no mostraría qué se declaró).
     */
    public List<SaldoCuenta> saldos(Integer empresaId, LocalDate desde, LocalDate hasta, List<String> prefijos) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("desde", Date.valueOf(desde))
                .addValue("hasta", Date.valueOf(hasta))
                .addValue("origenes", List.of(TIPO_ORIGEN, "ANULACION_" + TIPO_ORIGEN));
        StringBuilder filtro = new StringBuilder();
        for (int i = 0; i < prefijos.size(); i++) {
            if (i > 0) filtro.append(" OR ");
            filtro.append("pc.codigo LIKE :pre").append(i);
            p.addValue("pre" + i, prefijos.get(i) + "%");
        }
        String sql = """
                SELECT pc.id AS cuenta_id, pc.codigo, pc.nombre,
                       SUM(ad.credito) - SUM(ad.debito) AS saldo
                FROM asiento_detalle ad
                JOIN asiento_contable a ON a.id = ad.asiento_id
                JOIN plan_cuenta pc     ON pc.id = ad.cuenta_id
                WHERE a.empresa_id = :empresaId
                  AND a.estado = 'CONTABILIZADO'
                  AND a.fecha BETWEEN :desde AND :hasta
                  AND COALESCE(a.tipo_origen, '') NOT IN (:origenes)
                  AND (""" + filtro + """
                )
                GROUP BY pc.id, pc.codigo, pc.nombre
                HAVING SUM(ad.credito) - SUM(ad.debito) <> 0
                ORDER BY pc.codigo
                """;
        return jdbc.query(sql, p, (rs, i) -> new SaldoCuenta(rs.getLong("cuenta_id"), rs.getString("codigo"),
                rs.getString("nombre"), rs.getBigDecimal("saldo")));
    }

    /** Pago vigente de una declaración, con su asiento si ya se generó. */
    public record PagoVigente(Long id, String tipo, Long terceroAcreedorId, Integer consecutivo,
            LocalDate fecha, BigDecimal valor, String numeroFormulario, String numeroAsiento) {
    }

    public List<PagoVigente> pagosVigentes(Integer empresaId, LocalDate desde, LocalDate hasta) {
        return jdbc.query("""
                SELECT p.id, p.tipo, p.tercero_acreedor_id, p.consecutivo, p.fecha,
                       p.valor + p.ajuste_redondeo + p.intereses + p.sancion AS valor,
                       p.numero_formulario,
                       (SELECT a.numero_comprobante FROM asiento_contable a
                         WHERE a.empresa_id = p.empresa_id AND a.tipo_origen = :origen
                           AND a.origen_id = p.id AND a.estado = 'CONTABILIZADO'
                         ORDER BY a.id DESC LIMIT 1) AS numero_asiento
                FROM declaracion_pago p
                WHERE p.empresa_id = :empresaId AND p.desde = :desde AND p.hasta = :hasta
                  AND p.estado = 'PAGADA'
                """, new MapSqlParameterSource()
                        .addValue("empresaId", empresaId)
                        .addValue("desde", Date.valueOf(desde))
                        .addValue("hasta", Date.valueOf(hasta))
                        .addValue("origen", TIPO_ORIGEN),
                (rs, i) -> new PagoVigente(rs.getLong("id"), rs.getString("tipo"),
                        rs.getLong("tercero_acreedor_id"), rs.getInt("consecutivo"),
                        rs.getDate("fecha").toLocalDate(), rs.getBigDecimal("valor"),
                        rs.getString("numero_formulario"), rs.getString("numero_asiento")));
    }

    public int siguienteConsecutivo(Integer empresaId) {
        Integer n = jdbc.queryForObject(
                "SELECT COALESCE(MAX(consecutivo), 0) + 1 FROM declaracion_pago WHERE empresa_id = :empresaId",
                new MapSqlParameterSource("empresaId", empresaId), Integer.class);
        return n != null ? n : 1;
    }

    /** Pago con lo necesario para contabilizarlo. */
    public record PagoContable(String numero, String tipo, LocalDate desde, LocalDate hasta, LocalDate fecha,
            Long terceroAcreedorId, String terceroNombre, String numeroFormulario, BigDecimal valor,
            String metodoPago, Long cuentaBancariaId, Long cuentaContableId,
            BigDecimal ajusteRedondeo, BigDecimal intereses, BigDecimal sancion) {
    }

    public Optional<PagoContable> pagoContable(Long pagoId, Integer empresaId) {
        return jdbc.query("""
                SELECT 'PD-' || p.consecutivo AS numero, p.tipo, p.desde, p.hasta, p.fecha,
                       p.tercero_acreedor_id,
                       COALESCE(NULLIF(t.razon_social, ''), TRIM(CONCAT(t.nombres, ' ', t.apellidos))) AS tercero_nombre,
                       p.numero_formulario, p.valor, p.metodo_pago, p.cuenta_bancaria_id, p.cuenta_contable_id,
                       p.ajuste_redondeo, p.intereses, p.sancion
                FROM declaracion_pago p
                JOIN tercero t ON t.id = p.tercero_acreedor_id
                WHERE p.id = :id AND p.empresa_id = :empresaId
                """, new MapSqlParameterSource("id", pagoId).addValue("empresaId", empresaId),
                (rs, i) -> new PagoContable(rs.getString("numero"), rs.getString("tipo"),
                        rs.getDate("desde").toLocalDate(), rs.getDate("hasta").toLocalDate(),
                        rs.getDate("fecha").toLocalDate(), rs.getLong("tercero_acreedor_id"),
                        rs.getString("tercero_nombre"), rs.getString("numero_formulario"),
                        rs.getBigDecimal("valor"), rs.getString("metodo_pago"),
                        rs.getObject("cuenta_bancaria_id", Long.class),
                        rs.getObject("cuenta_contable_id", Long.class),
                        rs.getBigDecimal("ajuste_redondeo"), rs.getBigDecimal("intereses"),
                        rs.getBigDecimal("sancion")))
                .stream().findFirst();
    }

    /** Lo que cierra el pago de cada cuenta (valor &gt; 0 débito, &lt; 0 crédito). */
    public record CuentaPago(Long cuentaId, BigDecimal valor) {
    }

    public List<CuentaPago> cuentasPago(Long pagoId) {
        return jdbc.query("""
                SELECT cuenta_id, valor FROM declaracion_pago_cuenta WHERE pago_id = :id ORDER BY id
                """, new MapSqlParameterSource("id", pagoId),
                (rs, i) -> new CuentaPago(rs.getLong("cuenta_id"), rs.getBigDecimal("valor")));
    }
}
