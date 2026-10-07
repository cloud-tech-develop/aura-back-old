package com.cloud_technological.aura_pos.repositories.empresas;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.empresas.TableroInicioDtos.ResumenFinancieroDto;
import com.cloud_technological.aura_pos.dto.empresas.TableroInicioDtos.TableroComercialDto;

/** Cifras de los tableros de inicio y de la lista de puesta en marcha. */
@Repository
public class TableroInicioQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private static MapSqlParameterSource p(Integer empresaId) {
        return new MapSqlParameterSource("empresaId", empresaId);
    }

    // ── Financiero ───────────────────────────────────────────────────────────

    public ResumenFinancieroDto financiero(Integer empresaId) {
        ResumenFinancieroDto d = new ResumenFinancieroDto();
        d.setDisponible(decimal("""
            SELECT COALESCE(SUM(ad.debito - ad.credito), 0)
            FROM asiento_detalle ad
            JOIN asiento_contable a ON a.id = ad.asiento_id
            JOIN plan_cuenta pc     ON pc.id = ad.cuenta_id
            WHERE a.empresa_id = :empresaId AND a.estado = 'CONTABILIZADO' AND pc.codigo LIKE '11%'
            """, p(empresaId)));

        jdbc.query("""
            SELECT COALESCE(SUM(GREATEST(COALESCE(saldo_pendiente, 0), 0)), 0) AS saldo,
                   COALESCE(SUM(CASE WHEN fecha_vencimiento::date < CURRENT_DATE
                                     THEN GREATEST(COALESCE(saldo_pendiente, 0), 0) END), 0) AS vencido,
                   COUNT(*) FILTER (WHERE fecha_vencimiento::date < CURRENT_DATE
                                     AND COALESCE(saldo_pendiente, 0) > 0) AS vencidas
            FROM cuentas_cobrar
            WHERE empresa_id = :empresaId AND deleted_at IS NULL AND LOWER(COALESCE(estado, '')) <> 'anulada'
            """, p(empresaId), rs -> {
            d.setCxcSaldo(rs.getBigDecimal("saldo"));
            d.setCxcVencido(rs.getBigDecimal("vencido"));
            d.setCxcVencidas(rs.getLong("vencidas"));
        });
        jdbc.query("""
            SELECT COALESCE(SUM(GREATEST(COALESCE(saldo_pendiente, 0), 0)), 0) AS saldo,
                   COALESCE(SUM(CASE WHEN fecha_vencimiento::date < CURRENT_DATE
                                     THEN GREATEST(COALESCE(saldo_pendiente, 0), 0) END), 0) AS vencido,
                   COUNT(*) FILTER (WHERE fecha_vencimiento::date < CURRENT_DATE
                                     AND COALESCE(saldo_pendiente, 0) > 0) AS vencidas
            FROM cuentas_pagar
            WHERE empresa_id = :empresaId AND deleted_at IS NULL AND LOWER(COALESCE(estado, '')) <> 'anulada'
            """, p(empresaId), rs -> {
            d.setCxpSaldo(rs.getBigDecimal("saldo"));
            d.setCxpVencido(rs.getBigDecimal("vencido"));
            d.setCxpVencidas(rs.getLong("vencidas"));
        });
        return d;
    }

    // ── Comercial ────────────────────────────────────────────────────────────

    public TableroComercialDto comercial(Integer empresaId, LocalDate desde, LocalDate hasta) {
        TableroComercialDto d = new TableroComercialDto();
        MapSqlParameterSource mes = p(empresaId).addValue("desde", desde).addValue("hasta", hasta.plusDays(1));
        jdbc.query("""
            SELECT COUNT(*) AS n, COALESCE(SUM(total), 0) AS total
            FROM factura_venta
            WHERE empresa_id = :empresaId AND estado = 'EMITIDA'
              AND emitida_at >= :desde AND emitida_at < :hasta
            """, mes, rs -> {
            d.setFacturasMes(rs.getLong("n"));
            d.setFacturadoMes(rs.getBigDecimal("total"));
        });
        jdbc.query("""
            SELECT COUNT(*) AS n, COALESCE(SUM(total), 0) AS total
            FROM compra
            WHERE empresa_id = :empresaId AND COALESCE(estado, '') <> 'ANULADA'
              AND fecha >= :desde AND fecha < :hasta
            """, mes, rs -> {
            d.setComprasMes(rs.getLong("n"));
            d.setCompradoMes(rs.getBigDecimal("total"));
        });
        jdbc.query("""
            SELECT COUNT(*) AS n, COALESCE(SUM(total), 0) AS total
            FROM cotizacion
            WHERE empresa_id = :empresaId AND estado = 'PENDIENTE'
            """, p(empresaId), rs -> {
            d.setCotizacionesAbiertas(rs.getLong("n"));
            d.setCotizadoAbierto(rs.getBigDecimal("total"));
        });
        d.setPedidosAbiertos(contar("""
            SELECT COUNT(*) FROM pedido_vendedor
            WHERE empresa_id = :empresaId AND estado IN ('CREADA', 'DESPACHADA')
            """, p(empresaId)));
        d.setProductosStockBajo(contar("""
            SELECT COUNT(DISTINCT i.producto_id)
            FROM inventario i
            JOIN producto p ON p.id = i.producto_id
            JOIN sucursal s ON s.id = i.sucursal_id
            WHERE s.empresa_id = :empresaId AND i.stock_minimo > 0
              AND i.stock_actual <= i.stock_minimo AND p.deleted_at IS NULL
            """, p(empresaId)));
        return d;
    }

    // ── Puesta en marcha ─────────────────────────────────────────────────────

    public long cuentasPuc(Integer empresaId) {
        return contar("SELECT COUNT(*) FROM plan_cuenta WHERE empresa_id = :empresaId", p(empresaId));
    }

    public boolean tieneSaldosIniciales(Integer empresaId) {
        return contar("""
            SELECT COUNT(*) FROM asiento_contable
            WHERE empresa_id = :empresaId AND tipo_origen = 'APERTURA' AND estado <> 'ANULADO'
            """, p(empresaId)) > 0;
    }

    public long cuentasBancarias(Integer empresaId) {
        return contar("SELECT COUNT(*) FROM cuenta_bancaria WHERE empresa_id = :empresaId AND activa = true",
                p(empresaId));
    }

    public long cuentasBancariasSinCuentaContable(Integer empresaId) {
        return contar("""
            SELECT COUNT(*) FROM cuenta_bancaria
            WHERE empresa_id = :empresaId AND activa = true AND cuenta_contable_id IS NULL
            """, p(empresaId));
    }

    public boolean tieneConfigNomina(Integer empresaId) {
        return contar("SELECT COUNT(*) FROM nomina_config WHERE empresa_id = :empresaId", p(empresaId)) > 0;
    }

    public long contratosActivos(Integer empresaId) {
        return contar("""
            SELECT COUNT(*) FROM contrato_laboral
            WHERE empresa_id = :empresaId AND estado = 'ACTIVO' AND deleted_at IS NULL
            """, p(empresaId));
    }

    /** Contratos activos a los que les falta EPS, AFP o caja vigente. */
    public long contratosSinAfiliaciones(Integer empresaId) {
        return contar("""
            SELECT COUNT(*) FROM contrato_laboral c
            WHERE c.empresa_id = :empresaId AND c.estado = 'ACTIVO' AND c.deleted_at IS NULL
              AND (SELECT COUNT(DISTINCT a.tipo) FROM contrato_afiliacion a
                   WHERE a.contrato_id = c.id AND a.fecha_hasta IS NULL AND a.tipo IN ('EPS', 'AFP', 'CCF')) < 3
            """, p(empresaId));
    }

    public boolean tieneAportantePila(Integer empresaId) {
        return contar("SELECT COUNT(*) FROM pila_aportante_config WHERE empresa_id = :empresaId", p(empresaId)) > 0;
    }

    public long productos(Integer empresaId) {
        return contar("SELECT COUNT(*) FROM producto WHERE empresa_id = :empresaId AND deleted_at IS NULL",
                p(empresaId));
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    private long contar(String sql, MapSqlParameterSource params) {
        Long n = jdbc.queryForObject(sql, params, Long.class);
        return n != null ? n : 0;
    }

    private BigDecimal decimal(String sql, MapSqlParameterSource params) {
        BigDecimal n = jdbc.queryForObject(sql, params, BigDecimal.class);
        return n != null ? n : BigDecimal.ZERO;
    }
}
