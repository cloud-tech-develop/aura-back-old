package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Saldos de los auxiliares para proponer la apertura contable (Fase 4): lo
 * que dicen el inventario, las fichas de activos, los diferidos y la cartera
 * tiene que ser lo mismo que abre la contabilidad.
 */
@Repository
public class SaldosInicialesQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private static final String NOMBRE_TERCERO = """
            COALESCE(NULLIF(TRIM(COALESCE(t.razon_social, '')), ''), TRIM(CONCAT_WS(' ', t.nombres, t.apellidos)))""";

    /** Inventario valorizado al costo promedio, por cuenta de inventario (null = la de la empresa). */
    public List<Map<String, Object>> inventario(Integer empresaId) {
        return jdbc.queryForList("""
            SELECT COALESCE(p.cuenta_inventario_id, ccp.cuenta_inventario_id) AS cuenta_id,
                   SUM(i.stock_actual * COALESCE(p.costo, 0))                AS valor
              FROM inventario i
              JOIN sucursal s ON s.id = i.sucursal_id
              JOIN producto p ON p.id = i.producto_id
              LEFT JOIN categoria_contable_producto ccp
                     ON ccp.id = p.categoria_contable_id AND ccp.tipo IN ('BIEN', 'INSUMO')
             WHERE s.empresa_id = :e
               AND p.deleted_at IS NULL
               AND p.clasificacion = 'PRODUCTO'
               AND i.stock_actual > 0
             GROUP BY 1
            HAVING SUM(i.stock_actual * COALESCE(p.costo, 0)) <> 0
            """, new MapSqlParameterSource("e", empresaId));
    }

    /** Fichas vigentes: costo y depreciación acumulada por cuenta. */
    public List<Map<String, Object>> activos(Integer empresaId) {
        return jdbc.queryForList("""
            SELECT cuenta_activo_id, cuenta_depreciacion_id, categoria,
                   SUM(valor_compra + valor_adiciones) AS costo,
                   SUM(depreciacion_acumulada)         AS depreciacion
              FROM activo_fijo
             WHERE empresa_id = :e AND deleted_at IS NULL AND estado IN ('ACTIVO', 'DEPRECIADO')
             GROUP BY cuenta_activo_id, cuenta_depreciacion_id, categoria
            """, new MapSqlParameterSource("e", empresaId));
    }

    /** Lo que falta por amortizar de los diferidos vigentes, por cuenta del diferido. */
    public List<Map<String, Object>> diferidos(Integer empresaId) {
        return jdbc.queryForList("""
            SELECT d.cuenta_diferido_id AS cuenta_id,
                   SUM(d.monto - COALESCE((SELECT SUM(a.monto) FROM diferido_amortizacion a
                                            WHERE a.diferido_id = d.id), 0)) AS valor
              FROM diferido d
             WHERE d.empresa_id = :e AND d.estado = 'VIGENTE'
             GROUP BY 1
            HAVING SUM(d.monto) <> 0
            """, new MapSqlParameterSource("e", empresaId));
    }

    /** Saldo pendiente por tercero de las cuentas por cobrar o por pagar abiertas. */
    public List<Map<String, Object>> cartera(Integer empresaId, boolean porPagar) {
        String tabla = porPagar ? "cuentas_pagar" : "cuentas_cobrar";
        return jdbc.queryForList("""
            SELECT c.tercero_id, %s AS tercero_nombre, SUM(c.saldo_pendiente) AS valor
              FROM %s c
              LEFT JOIN tercero t ON t.id = c.tercero_id
             WHERE c.empresa_id = :e AND c.deleted_at IS NULL
               AND LOWER(COALESCE(c.estado, 'activa')) NOT IN ('anulada', 'pagada', 'cancelada')
               AND c.saldo_pendiente > 0
             GROUP BY c.tercero_id, 2
             ORDER BY 2
            """.formatted(NOMBRE_TERCERO, tabla), new MapSqlParameterSource("e", empresaId));
    }

    public BigDecimal saldoCuenta(Integer empresaId, Long cuentaId) {
        return jdbc.queryForObject("""
            SELECT COALESCE(SUM(d.debito - d.credito), 0)
              FROM asiento_detalle d JOIN asiento_contable a ON a.id = d.asiento_id
             WHERE a.empresa_id = :e AND d.cuenta_id = :c AND a.estado = 'CONTABILIZADO'
            """, new MapSqlParameterSource().addValue("e", empresaId).addValue("c", cuentaId), BigDecimal.class);
    }
}
