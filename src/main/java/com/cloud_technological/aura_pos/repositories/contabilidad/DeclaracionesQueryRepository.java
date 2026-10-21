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
    /** ICA causado (créditos a la 2412) dentro del período, sin pagos de declaración. */
    public BigDecimal icaCausado(Integer empresaId, LocalDate desde, LocalDate hasta) {
        java.math.BigDecimal v = jdbc.queryForObject("""
            SELECT COALESCE(SUM(ad.credito), 0)
              FROM asiento_detalle ad
              JOIN asiento_contable a ON a.id = ad.asiento_id
              JOIN plan_cuenta pc ON pc.id = ad.cuenta_id
             WHERE a.empresa_id = :empresaId AND a.estado = 'CONTABILIZADO'
               AND a.fecha BETWEEN :desde AND :hasta AND pc.codigo LIKE '2412%'
               AND COALESCE(a.tipo_origen, '') NOT IN ('DECLARACION_PAGO', 'ANULACION_DECLARACION_PAGO')
            """, rango(empresaId, desde, hasta), java.math.BigDecimal.class);
        return v != null ? v : BigDecimal.ZERO;
    }

    /** La primera de las cuentas (por código, en ese orden) que exista y esté activa. */
    public Long primeraCuenta(Integer empresaId, List<String> codigos) {
        for (String c : codigos) {
            List<Long> r = jdbc.queryForList(
                    "SELECT id FROM plan_cuenta WHERE empresa_id = :e AND codigo = :c AND activa = TRUE LIMIT 1",
                    new MapSqlParameterSource("e", empresaId).addValue("c", c), Long.class);
            if (!r.isEmpty()) return r.get(0);
        }
        return null;
    }

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
              -- El pago de la declaración (V204) cierra estas cuentas: no es
              -- parte de lo que se declara.
              AND COALESCE(a.tipo_origen, '') NOT IN ('DECLARACION_PAGO', 'ANULACION_DECLARACION_PAGO')
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

    /**
     * Lo retenido a un tercero, por documento de origen: el valor del mayor y,
     * si el origen es una compra o un gasto, su base, tarifa y concepto.
     */
    public record RetencionTercero(Long terceroId, String terceroNombre, String tipoDocumento,
            String numeroDocumento, String dv, String direccion, String municipio, String correo,
            String cuentaCodigo, String concepto, BigDecimal tarifa, BigDecimal base, BigDecimal valor) {
    }

    /**
     * Retenciones practicadas por tercero en el período, leídas del mayor
     * (crédito − débito de la cuenta). La anulación de un documento se suma a
     * su original (mismo origen_id), así un documento anulado no aparece.
     * Lo de nómina no entra: al empleado se le certifica con el formulario 220.
     *
     * @param tipo      FUENTE (2365), IVA (2367) o ICA (2368)
     * @param terceroId null = todos los terceros
     */
    public List<RetencionTercero> retencionesPorTercero(Integer empresaId, LocalDate desde,
            LocalDate hasta, String tipo, Long terceroId) {
        String prefijo;
        String baseCompra;
        String tarifaCompra;
        String tarifaIdCompra;
        String baseGasto;
        String tarifaGasto;
        String tarifaIdGasto;
        switch (tipo) {
            case "IVA" -> {
                prefijo = "2367";
                baseCompra = "c.impuestos_total";
                tarifaCompra = "c.reteiva_pct";
                tarifaIdCompra = "c.reteiva_tarifa_id";
                // El gasto no practica reteIVA: si aparece fue por un ajuste.
                baseGasto = "NULL::numeric";
                tarifaGasto = "NULL::numeric";
                tarifaIdGasto = "NULL::bigint";
            }
            case "ICA" -> {
                prefijo = "2368";
                baseCompra = "COALESCE(c.subtotal, 0) - COALESCE(c.descuento_total, 0)";
                tarifaCompra = "c.reteica_pct";
                tarifaIdCompra = "c.reteica_tarifa_id";
                baseGasto = "COALESCE(NULLIF(g.base_reteica, 0), g.monto)";
                tarifaGasto = "g.tarifa_reteica";
                tarifaIdGasto = "g.reteica_tarifa_id";
            }
            default -> {
                prefijo = "2365";
                baseCompra = "COALESCE(c.subtotal, 0) - COALESCE(c.descuento_total, 0)";
                tarifaCompra = "c.retefuente_pct";
                tarifaIdCompra = "c.retefuente_tarifa_id";
                baseGasto = "COALESCE(NULLIF(g.base_retefuente, 0), g.monto)";
                tarifaGasto = "g.tarifa_retefuente";
                tarifaIdGasto = "g.retefuente_tarifa_id";
            }
        }
        MapSqlParameterSource p = rango(empresaId, desde, hasta)
                .addValue("prefijo", prefijo + "%")
                .addValue("terceroId", terceroId);
        String sql = """
            WITH mov AS (
                SELECT ad.tercero_id, pc.codigo, pc.nombre AS cuenta_nombre,
                       regexp_replace(COALESCE(a.tipo_origen, ''), '^ANULACION_', '') AS origen,
                       a.origen_id,
                       SUM(ad.credito - ad.debito) AS valor
                FROM asiento_detalle ad
                JOIN asiento_contable a ON a.id = ad.asiento_id
                JOIN plan_cuenta pc     ON pc.id = ad.cuenta_id
                WHERE a.empresa_id = :empresaId
                  AND a.estado = 'CONTABILIZADO'
                  AND a.fecha BETWEEN :desde AND :hasta
                  AND pc.codigo LIKE :prefijo
                  AND ad.tercero_id IS NOT NULL
                  AND COALESCE(a.tipo_origen, '') NOT IN ('DECLARACION_PAGO', 'ANULACION_DECLARACION_PAGO')
                  AND regexp_replace(COALESCE(a.tipo_origen, ''), '^ANULACION_', '') NOT LIKE 'NOMINA%'
                  AND (CAST(:terceroId AS bigint) IS NULL OR ad.tercero_id = :terceroId)
                GROUP BY ad.tercero_id, pc.codigo, pc.nombre,
                         regexp_replace(COALESCE(a.tipo_origen, ''), '^ANULACION_', ''), a.origen_id
                HAVING SUM(ad.credito - ad.debito) <> 0
            )
            SELECT m.tercero_id,
                   COALESCE(NULLIF(TRIM(t.razon_social), ''), TRIM(CONCAT(t.nombres, ' ', t.apellidos))) AS tercero_nombre,
                   t.tipo_documento, t.numero_documento, t.dv, t.direccion, t.municipio, t.email,
                   m.codigo,
                   COALESCE(tr.concepto, m.cuenta_nombre) AS concepto,
                   CASE WHEN c.id IS NOT NULL THEN {TARIFA_COMPRA}
                        WHEN g.id IS NOT NULL THEN {TARIFA_GASTO}
                   END AS tarifa,
                   SIGN(m.valor) * CASE WHEN c.id IS NOT NULL THEN {BASE_COMPRA}
                                        WHEN g.id IS NOT NULL THEN {BASE_GASTO}
                                   END AS base,
                   m.valor
            FROM mov m
            JOIN tercero t ON t.id = m.tercero_id
            LEFT JOIN compra c ON m.origen = 'COMPRA' AND c.id = m.origen_id
            LEFT JOIN gasto g  ON m.origen = 'GASTO'  AND g.id = m.origen_id
            LEFT JOIN tarifa_retencion tr ON tr.id = CASE
                WHEN c.id IS NOT NULL THEN {TARIFA_ID_COMPRA}
                WHEN g.id IS NOT NULL THEN {TARIFA_ID_GASTO}
                END
            ORDER BY tercero_nombre, m.tercero_id, m.codigo
            """
                // Marcadores en vez de concatenar: el text block se come los espacios
                // de los bordes y pegaba "c.retefuente_pctWHEN".
                .replace("{TARIFA_COMPRA}", tarifaCompra)
                .replace("{TARIFA_GASTO}", tarifaGasto)
                .replace("{BASE_COMPRA}", baseCompra)
                .replace("{BASE_GASTO}", baseGasto)
                .replace("{TARIFA_ID_COMPRA}", tarifaIdCompra)
                .replace("{TARIFA_ID_GASTO}", tarifaIdGasto);
        return jdbc.query(sql, p, (rs, i) -> new RetencionTercero(
                rs.getLong("tercero_id"), rs.getString("tercero_nombre"),
                rs.getString("tipo_documento"), rs.getString("numero_documento"), rs.getString("dv"),
                rs.getString("direccion"), rs.getString("municipio"), rs.getString("email"),
                rs.getString("codigo"), rs.getString("concepto"), rs.getBigDecimal("tarifa"),
                rs.getBigDecimal("base"), rs.getBigDecimal("valor")));
    }

    /** Retenciones del período registradas sin tercero: no se pueden certificar. */
    public BigDecimal retencionesSinTercero(Integer empresaId, LocalDate desde, LocalDate hasta,
            String prefijo) {
        BigDecimal v = jdbc.queryForObject("""
            SELECT COALESCE(SUM(ad.credito - ad.debito), 0)
              FROM asiento_detalle ad
              JOIN asiento_contable a ON a.id = ad.asiento_id
              JOIN plan_cuenta pc ON pc.id = ad.cuenta_id
             WHERE a.empresa_id = :empresaId AND a.estado = 'CONTABILIZADO'
               AND a.fecha BETWEEN :desde AND :hasta AND pc.codigo LIKE :prefijo
               AND ad.tercero_id IS NULL
               AND COALESCE(a.tipo_origen, '') NOT IN ('DECLARACION_PAGO', 'ANULACION_DECLARACION_PAGO')
            """, rango(empresaId, desde, hasta).addValue("prefijo", prefijo + "%"), BigDecimal.class);
        return v != null ? v : BigDecimal.ZERO;
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
