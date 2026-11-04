package com.cloud_technological.aura_pos.repositories.reportes;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.reportes.ReporteUtilidadCreditoDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteUtilidadDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteUtilidadFilaDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteUtilidadFiltroDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteUtilidadFiltroDto.Agrupacion;
import com.cloud_technological.aura_pos.dto.reportes.ReporteUtilidadFiltroDto.Forma;
import com.cloud_technological.aura_pos.services.permisos.AlcanceSede;

/**
 * Consultas del reporte de utilidad en ventas.
 *
 * <p>Usa la misma regla que los reportes avanzados
 * ({@link ReporteAvanzadoQueryRepository#UTILIDAD_LINEA}): venta neta sin IVA con el
 * descuento general prorrateado, menos el costo congelado al vender. Entra toda
 * venta no anulada; "crédito" es la venta que quedó con saldo (venta.pago_parcial),
 * aunque después se haya pagado.
 */
@Repository
public class ReporteUtilidadQueryRepository {

    /** Nombre visible de un tercero (alias {@code t}): razón social o nombres. */
    private static final String NOMBRE_TERCERO = """
            COALESCE(NULLIF(TRIM(%1$s.razon_social), ''),
                     NULLIF(TRIM(COALESCE(%1$s.nombres, '') || ' ' || COALESCE(%1$s.apellidos, '')), ''))""";

    private static final String FROM = """
                FROM venta v
                INNER JOIN venta_detalle vd ON vd.venta_id = v.id
                INNER JOIN producto p ON vd.producto_id = p.id
                LEFT JOIN categoria c ON p.categoria_id = c.id
                LEFT JOIN usuario us ON us.id = v.usuario_id
                LEFT JOIN tercero tv ON tv.id = us.tercero_id
                LEFT JOIN tercero tc ON tc.id = v.cliente_id
            """ + ReporteAvanzadoQueryRepository.UTILIDAD_LINEA + """
                WHERE v.empresa_id = :empresaId /*SEDE:v.sucursal_id*/
                  AND v.estado_venta <> 'ANULADA'
                  AND v.fecha_emision::date BETWEEN :desde AND :hasta
                  AND vd.cantidad > 0
            """;

    /** Conteos de alerta por línea, comunes a filas y totales. */
    private static final String ALERTAS = """
                    COUNT(*) FILTER (WHERE u.costo <= 0
                        AND COALESCE(p.maneja_inventario, TRUE)) AS lineas_sin_costo,
                    COUNT(*) FILTER (WHERE u.venta_neta < u.costo) AS lineas_bajo_costo
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final AlcanceSede alcanceSede;

    public ReporteUtilidadQueryRepository(NamedParameterJdbcTemplate jdbc, AlcanceSede alcanceSede) {
        this.jdbc = jdbc;
        this.alcanceSede = alcanceSede;
    }

    /**
     * Filas agrupadas, de mayor a menor utilidad.
     *
     * @param filtro    período, agrupación y forma de pago
     * @param empresaId empresa de la sesión
     * @return filas sin márgenes calculados (los pone el servicio)
     */
    public List<ReporteUtilidadFilaDto> filas(ReporteUtilidadFiltroDto filtro, Integer empresaId) {
        String[] g = grupo(filtro.getAgrupar());
        String sql = "SELECT " + g[0] + " AS clave, " + g[1] + " AS nombre, " + g[2] + " AS detalle,"
                + """
                    COUNT(DISTINCT v.id) AS ventas,
                    SUM(vd.cantidad) AS cantidad,
                    ROUND(SUM(u.venta_neta), 2) AS venta_neta,
                    ROUND(SUM(u.costo), 2) AS costo,
                    ROUND(SUM(u.venta_neta - u.costo), 2) AS utilidad,
                """
                + ALERTAS + FROM + forma(filtro.getForma())
                + " GROUP BY 1, 2, 3 ORDER BY utilidad DESC, nombre";
        MapSqlParameterSource p = params(filtro, empresaId);
        return jdbc.query(alcanceSede.aplicar(sql, p), p, new BeanPropertyRowMapper<>(ReporteUtilidadFilaDto.class));
    }

    /**
     * Totales del período con la parte a crédito separada.
     *
     * @param filtro    período y forma de pago (la agrupación no aplica)
     * @param empresaId empresa de la sesión
     * @return totales sin márgenes ni saldo por cobrar
     */
    public ReporteUtilidadDto totales(ReporteUtilidadFiltroDto filtro, Integer empresaId) {
        String sql = """
                SELECT
                    COUNT(DISTINCT v.id) AS ventas,
                    COALESCE(ROUND(SUM(u.venta_neta), 2), 0) AS venta_neta,
                    COALESCE(ROUND(SUM(u.costo), 2), 0) AS costo,
                    COALESCE(ROUND(SUM(u.venta_neta - u.costo), 2), 0) AS utilidad,
                    COALESCE(ROUND(SUM(u.venta_neta) FILTER (WHERE COALESCE(v.pago_parcial, FALSE)), 2), 0)
                        AS venta_neta_credito,
                    COALESCE(ROUND(SUM(u.venta_neta - u.costo) FILTER (WHERE COALESCE(v.pago_parcial, FALSE)), 2), 0)
                        AS utilidad_credito,
                """ + ALERTAS + FROM + forma(filtro.getForma());
        MapSqlParameterSource p = params(filtro, empresaId);
        List<ReporteUtilidadDto> r = jdbc.query(alcanceSede.aplicar(sql, p), p,
                new BeanPropertyRowMapper<>(ReporteUtilidadDto.class));
        return r.isEmpty() ? new ReporteUtilidadDto() : r.get(0);
    }

    /**
     * Saldo que los clientes aún deben de las ventas del período.
     *
     * @param filtro    período y forma de pago
     * @param empresaId empresa de la sesión
     * @return suma de cuentas_cobrar.saldo_pendiente; cero si no hay
     */
    public BigDecimal saldoPorCobrar(ReporteUtilidadFiltroDto filtro, Integer empresaId) {
        if (filtro.getForma() == Forma.CONTADO) return BigDecimal.ZERO;
        String sql = """
                SELECT COALESCE(SUM(cc.saldo_pendiente), 0)
                FROM cuentas_cobrar cc
                INNER JOIN venta v ON v.id = cc.venta_id
                WHERE v.empresa_id = :empresaId /*SEDE:v.sucursal_id*/
                  AND v.estado_venta <> 'ANULADA'
                  AND v.fecha_emision::date BETWEEN :desde AND :hasta
                  AND cc.deleted_at IS NULL
                """;
        MapSqlParameterSource p = params(filtro, empresaId);
        BigDecimal saldo = jdbc.queryForObject(alcanceSede.aplicar(sql, p), p, BigDecimal.class);
        return saldo != null ? saldo : BigDecimal.ZERO;
    }

    /**
     * Las ventas a crédito del período, una por fila: su utilidad y lo que falta por
     * cobrar. Primero las que más deben.
     *
     * @param filtro    período (agrupación y forma no aplican: siempre son las de crédito)
     * @param empresaId empresa de la sesión
     * @return ventas con saldo al venderse (venta.pago_parcial), aunque ya estén pagadas
     */
    public List<ReporteUtilidadCreditoDto> ventasCredito(ReporteUtilidadFiltroDto filtro, Integer empresaId) {
        String sql = """
                SELECT
                    v.id AS venta_id,
                    CASE WHEN NULLIF(TRIM(v.prefijo), '') IS NOT NULL
                         THEN CONCAT(TRIM(v.prefijo), '-', v.consecutivo)
                         ELSE COALESCE(CAST(v.consecutivo AS TEXT), '#' || v.id) END AS documento,
                    cc.numero_cuenta,
                    v.fecha_emision AS fecha,
                    COALESCE(""" + NOMBRE_TERCERO.formatted("tc") + """
                        , 'Consumidor final') AS cliente_nombre,
                    tc.numero_documento AS cliente_documento,
                    COALESCE(l.venta_neta, 0) AS venta_neta,
                    COALESCE(l.costo, 0) AS costo,
                    COALESCE(l.venta_neta, 0) - COALESCE(l.costo, 0) AS utilidad,
                    v.total_pagar AS total_venta,
                    COALESCE(cc.abonado, 0) AS abonado,
                    COALESCE(cc.saldo, 0) AS por_cobrar,
                    cc.estado AS estado_cartera
                FROM venta v
                LEFT JOIN tercero tc ON tc.id = v.cliente_id
                CROSS JOIN LATERAL (
                    SELECT ROUND(SUM(u.venta_neta), 2) AS venta_neta, ROUND(SUM(u.costo), 2) AS costo
                    FROM venta_detalle vd
                    INNER JOIN producto p ON vd.producto_id = p.id
                """ + ReporteAvanzadoQueryRepository.UTILIDAD_LINEA + """
                    WHERE vd.venta_id = v.id AND vd.cantidad > 0
                ) l
                LEFT JOIN LATERAL (
                    SELECT MIN(c.numero_cuenta) AS numero_cuenta,
                           SUM(c.total_abonado) AS abonado,
                           SUM(c.saldo_pendiente) AS saldo,
                           MIN(c.estado) AS estado
                    FROM cuentas_cobrar c
                    WHERE c.venta_id = v.id AND c.deleted_at IS NULL
                ) cc ON TRUE
                WHERE v.empresa_id = :empresaId /*SEDE:v.sucursal_id*/
                  AND v.estado_venta <> 'ANULADA'
                  AND v.fecha_emision::date BETWEEN :desde AND :hasta
                  AND COALESCE(v.pago_parcial, FALSE)
                ORDER BY por_cobrar DESC, v.fecha_emision DESC
                """;
        MapSqlParameterSource p = params(filtro, empresaId);
        return jdbc.query(alcanceSede.aplicar(sql, p), p, new BeanPropertyRowMapper<>(ReporteUtilidadCreditoDto.class));
    }

    // ── Apoyo ─────────────────────────────────────────────────

    /** Clave, nombre y detalle del grupo. Lista cerrada: nada del cliente entra al SQL. */
    private static String[] grupo(Agrupacion agrupar) {
        return switch (agrupar != null ? agrupar : Agrupacion.PRODUCTO) {
            case CATEGORIA -> new String[] {
                    "COALESCE(c.id, 0)::text", "COALESCE(c.nombre, 'Sin categoría')", "NULL::text" };
            case VENDEDOR -> new String[] {
                    "COALESCE(us.id, 0)::text",
                    "COALESCE(" + NOMBRE_TERCERO.formatted("tv") + ", us.username, 'Sin vendedor')",
                    "us.username" };
            case CLIENTE -> new String[] {
                    "COALESCE(tc.id, 0)::text",
                    "COALESCE(" + NOMBRE_TERCERO.formatted("tc") + ", 'Consumidor final')",
                    "tc.numero_documento" };
            case MES -> new String[] {
                    "TO_CHAR(v.fecha_emision, 'YYYY-MM')", "TO_CHAR(v.fecha_emision, 'YYYY-MM')", "NULL::text" };
            case PRODUCTO -> new String[] { "p.id::text", "p.nombre", "p.sku" };
        };
    }

    private static String forma(Forma forma) {
        if (forma == Forma.CONTADO) return " AND NOT COALESCE(v.pago_parcial, FALSE)";
        if (forma == Forma.CREDITO) return " AND COALESCE(v.pago_parcial, FALSE)";
        return "";
    }

    private static MapSqlParameterSource params(ReporteUtilidadFiltroDto filtro, Integer empresaId) {
        return new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("desde", filtro.getDesde())
                .addValue("hasta", filtro.getHasta());
    }
}
