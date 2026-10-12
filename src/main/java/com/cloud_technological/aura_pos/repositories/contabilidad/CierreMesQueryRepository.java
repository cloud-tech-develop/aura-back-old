package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.contabilidad.cierre.CierreMesDtos.LineaAsiento;
import com.cloud_technological.aura_pos.dto.contabilidad.cierre.CierreMesDtos.LineaBalance;
import com.cloud_technological.aura_pos.dto.contabilidad.cierre.CierreMesDtos.LineaComparativo;

import lombok.RequiredArgsConstructor;

/**
 * Consultas del cierre de mes: lista de chequeo, cruces auxiliar contra mayor,
 * balance del acta y comparativos. Todo se lee del mayor (asientos
 * CONTABILIZADO); nunca de un contador propio.
 */
@Repository
@RequiredArgsConstructor
public class CierreMesQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static MapSqlParameterSource p(Integer empresaId) {
        return new MapSqlParameterSource("e", empresaId);
    }

    private BigDecimal decimal(String sql, MapSqlParameterSource params) {
        BigDecimal v = jdbc.queryForObject(sql, params, BigDecimal.class);
        return v != null ? v : BigDecimal.ZERO;
    }

    private long contar(String sql, MapSqlParameterSource params) {
        Long n = jdbc.queryForObject(sql, params, Long.class);
        return n != null ? n : 0;
    }

    // ── Requisitos ───────────────────────────────────────────────────────────

    /**
     * Borradores del período. Las notas contables manuales (tipo_origen MANUAL)
     * se aprueban en Notas contables; los automáticos, en Revisión de
     * comprobantes: por eso se piden por separado.
     */
    public List<String> borradores(Integer empresaId, Long periodoId, boolean manuales) {
        return jdbc.queryForList("""
                SELECT COALESCE(numero_comprobante, 'Borrador #' || id) || ' · ' || TO_CHAR(fecha, 'DD/MM/YYYY')
                       || ' · ' || LEFT(COALESCE(descripcion, ''), 60)
                  FROM asiento_contable
                 WHERE empresa_id = :e AND periodo_contable_id = :p AND estado = 'BORRADOR'
                   AND (COALESCE(tipo_origen, '') = 'MANUAL') = :manuales
                 ORDER BY fecha, id
                """, p(empresaId).addValue("p", periodoId).addValue("manuales", manuales), String.class);
    }

    public List<String> extractosAbiertos(Integer empresaId, String periodo) {
        return jdbc.queryForList("""
                SELECT COALESCE(cb.nombre, 'Cuenta #' || x.cuenta_bancaria_id)
                  FROM extracto_bancario x
                  LEFT JOIN cuenta_bancaria cb ON cb.id = x.cuenta_bancaria_id
                 WHERE x.empresa_id = :e AND x.periodo = :per AND x.estado = 'ABIERTO'
                """, p(empresaId).addValue("per", periodo), String.class);
    }

    /** Cuentas bancarias con movimiento en el mes que no tienen extracto conciliado. */
    public List<String> bancosSinConciliar(Integer empresaId, String periodo, LocalDate desde, LocalDate hasta) {
        return jdbc.queryForList("""
                SELECT cb.nombre
                  FROM cuenta_bancaria cb
                 WHERE cb.empresa_id = :e AND cb.cuenta_contable_id IS NOT NULL
                   AND EXISTS (SELECT 1 FROM asiento_detalle ad
                                 JOIN asiento_contable a ON a.id = ad.asiento_id
                                WHERE a.empresa_id = :e AND a.estado = 'CONTABILIZADO'
                                  AND ad.cuenta_id = cb.cuenta_contable_id
                                  AND a.fecha BETWEEN :d AND :h)
                   AND NOT EXISTS (SELECT 1 FROM extracto_bancario x
                                    WHERE x.cuenta_bancaria_id = cb.id AND x.periodo = :per
                                      AND x.estado <> 'ABIERTO')
                 ORDER BY cb.nombre
                """, p(empresaId).addValue("per", periodo).addValue("d", desde).addValue("h", hasta),
                String.class);
    }

    // ── Ajustes del mes ──────────────────────────────────────────────────────

    public List<String> activosSinDepreciar(Integer empresaId, Long periodoId, LocalDate hasta) {
        return jdbc.queryForList("""
                SELECT a.codigo || ' · ' || a.descripcion
                  FROM activo_fijo a
                 WHERE a.empresa_id = :e AND a.estado = 'ACTIVO' AND a.deleted_at IS NULL
                   AND COALESCE(a.fecha_inicio_depreciacion, a.fecha_adquisicion) <= :h
                   AND COALESCE(a.vida_util_meses, 0) > 0
                   AND NOT EXISTS (SELECT 1 FROM depreciacion_periodo d
                                    WHERE d.activo_id = a.id AND d.periodo_id = :p)
                 ORDER BY a.codigo
                """, p(empresaId).addValue("p", periodoId).addValue("h", hasta), String.class);
    }

    /** Diferidos (de gasto o de compra) a los que les falta la cuota de este mes. */
    public long diferidosSinAmortizar(Integer empresaId, String periodo, LocalDate desde, LocalDate hasta) {
        long deCompra = contar("""
                SELECT COUNT(*) FROM diferido d
                 WHERE d.empresa_id = :e AND d.estado = 'VIGENTE' AND d.fecha_inicio <= :h
                   AND (SELECT COUNT(*) FROM diferido_amortizacion x WHERE x.diferido_id = d.id) < d.meses
                   AND NOT EXISTS (SELECT 1 FROM diferido_amortizacion x
                                    WHERE x.diferido_id = d.id AND x.periodo = :per)
                """, p(empresaId).addValue("per", periodo).addValue("h", hasta));
        long deGasto = contar("""
                SELECT COUNT(*) FROM gasto g
                 WHERE g.empresa_id = :e AND g.es_diferido = TRUE AND g.estado <> 'ELIMINADO'
                   AND COALESCE(g.meses_diferido, 0) > 0 AND g.fecha <= :h
                   AND (SELECT COUNT(*) FROM diferido_amortizacion x WHERE x.gasto_id = g.id) < g.meses_diferido
                   AND NOT EXISTS (SELECT 1 FROM diferido_amortizacion x
                                    WHERE x.gasto_id = g.id AND x.periodo = :per)
                """, p(empresaId).addValue("per", periodo).addValue("h", hasta));
        return deCompra + deGasto;
    }

    public long empleadosActivos(Integer empresaId, LocalDate hasta) {
        return contar("""
                SELECT COUNT(*) FROM empleados
                 WHERE empresa_id = :e AND activo = TRUE
                   AND (fecha_ingreso IS NULL OR fecha_ingreso <= :h)
                """, p(empresaId).addValue("h", hasta));
    }

    public long asientosDeOrigen(Integer empresaId, String tipoOrigen, LocalDate desde, LocalDate hasta) {
        return contar("""
                SELECT COUNT(*) FROM asiento_contable
                 WHERE empresa_id = :e AND tipo_origen = :t AND estado = 'CONTABILIZADO'
                   AND fecha BETWEEN :d AND :h
                """, p(empresaId).addValue("t", tipoOrigen).addValue("d", desde).addValue("h", hasta));
    }

    // ── Calidad de la información ────────────────────────────────────────────

    /**
     * Movimientos de cuentas que se llevan por tercero (deudores, proveedores,
     * cuentas por pagar, retenciones, anticipos) sin tercero: no salen en el
     * auxiliar por tercero ni en exógena.
     */
    public List<String> movimientosSinTercero(Integer empresaId, LocalDate desde, LocalDate hasta) {
        return jdbc.queryForList("""
                SELECT COALESCE(a.numero_comprobante, '#' || a.id) || ' · ' || pc.codigo || ' ' || pc.nombre
                  FROM asiento_detalle ad
                  JOIN asiento_contable a ON a.id = ad.asiento_id
                  JOIN plan_cuenta pc ON pc.id = ad.cuenta_id
                 WHERE a.empresa_id = :e AND a.estado = 'CONTABILIZADO'
                   AND a.fecha BETWEEN :d AND :h
                   AND a.tipo_origen NOT IN ('CIERRE', 'SALDO_INICIAL')
                   AND ad.tercero_id IS NULL
                   AND (pc.codigo LIKE '13%' OR pc.codigo LIKE '22%' OR pc.codigo LIKE '23%'
                        OR pc.codigo LIKE '28%')
                   AND pc.codigo NOT LIKE '1399%'
                 ORDER BY a.fecha, a.id
                """, p(empresaId).addValue("d", desde).addValue("h", hasta), String.class);
    }

    /** Cuentas cuyo saldo acumulado al cierre va contra su naturaleza (más de $1). */
    public List<String> saldosContrarios(Integer empresaId, LocalDate hasta) {
        return jdbc.queryForList("""
                SELECT pc.codigo || ' ' || pc.nombre || ': '
                       || TO_CHAR(ABS(SUM(ad.debito - ad.credito)), 'FM999G999G999G990')
                  FROM asiento_detalle ad
                  JOIN asiento_contable a ON a.id = ad.asiento_id
                  JOIN plan_cuenta pc ON pc.id = ad.cuenta_id
                 WHERE a.empresa_id = :e AND a.estado = 'CONTABILIZADO' AND a.fecha <= :h
                 GROUP BY pc.id, pc.codigo, pc.nombre, pc.naturaleza
                HAVING (pc.naturaleza = 'DEBITO'  AND SUM(ad.debito - ad.credito) < -1)
                    OR (pc.naturaleza = 'CREDITO' AND SUM(ad.credito - ad.debito) < -1)
                 ORDER BY pc.codigo
                """, p(empresaId).addValue("h", hasta), String.class);
    }

    // ── Mayor ────────────────────────────────────────────────────────────────

    /** Saldo débito − crédito de las cuentas que empiezan por el prefijo, hasta la fecha (null = a hoy). */
    public BigDecimal saldoPrefijo(Integer empresaId, String prefijo, String excluir, LocalDate hasta) {
        return decimal("""
                SELECT COALESCE(SUM(ad.debito - ad.credito), 0)
                  FROM asiento_detalle ad
                  JOIN asiento_contable a ON a.id = ad.asiento_id
                  JOIN plan_cuenta pc ON pc.id = ad.cuenta_id
                 WHERE a.empresa_id = :e AND a.estado = 'CONTABILIZADO'
                   AND pc.codigo LIKE :pre
                   AND (CAST(:exc AS VARCHAR) IS NULL OR pc.codigo NOT LIKE :exc)
                   AND (CAST(:h AS DATE) IS NULL OR a.fecha <= :h)
                """, p(empresaId).addValue("pre", prefijo + "%")
                .addValue("exc", excluir != null ? excluir + "%" : null)
                .addValue("h", hasta));
    }

    public BigDecimal saldoCuentas(Integer empresaId, List<Long> cuentaIds) {
        if (cuentaIds == null || cuentaIds.isEmpty()) return BigDecimal.ZERO;
        return decimal("""
                SELECT COALESCE(SUM(ad.debito - ad.credito), 0)
                  FROM asiento_detalle ad
                  JOIN asiento_contable a ON a.id = ad.asiento_id
                 WHERE a.empresa_id = :e AND a.estado = 'CONTABILIZADO' AND ad.cuenta_id IN (:ids)
                """, p(empresaId).addValue("ids", cuentaIds));
    }

    // ── Auxiliares (a hoy) ───────────────────────────────────────────────────

    public BigDecimal carteraAuxiliar(Integer empresaId) {
        return decimal("""
                SELECT COALESCE(SUM(saldo_pendiente), 0) FROM cuentas_cobrar
                 WHERE empresa_id = :e AND deleted_at IS NULL
                   AND COALESCE(estado, '') NOT IN ('anulada', 'castigada')
                """, p(empresaId));
    }

    public BigDecimal proveedoresAuxiliar(Integer empresaId) {
        return decimal("""
                SELECT COALESCE(SUM(saldo_pendiente), 0) FROM cuentas_pagar
                 WHERE empresa_id = :e AND deleted_at IS NULL AND COALESCE(estado, '') <> 'anulada'
                """, p(empresaId));
    }

    public BigDecimal inventarioAuxiliar(Integer empresaId) {
        return decimal("""
                SELECT COALESCE(SUM(i.stock_actual * COALESCE(pr.costo, 0)), 0)
                  FROM inventario i
                  JOIN sucursal s ON s.id = i.sucursal_id
                  JOIN producto pr ON pr.id = i.producto_id
                 WHERE s.empresa_id = :e AND pr.deleted_at IS NULL
                   AND pr.clasificacion = 'PRODUCTO' AND i.stock_actual > 0
                """, p(empresaId));
    }

    /** Fichas de activos vivas: costo y depreciación con las cuentas que usan. */
    public Map<String, Object> activosAuxiliar(Integer empresaId) {
        return jdbc.queryForMap("""
                SELECT COALESCE(SUM(valor_compra + COALESCE(valor_adiciones, 0)), 0) AS costo,
                       COALESCE(SUM(depreciacion_acumulada), 0)                      AS depreciacion,
                       COUNT(*)                                                      AS fichas
                  FROM activo_fijo
                 WHERE empresa_id = :e AND deleted_at IS NULL AND estado IN ('ACTIVO', 'DEPRECIADO')
                """, p(empresaId));
    }

    public List<Long> cuentasActivo(Integer empresaId, String columna) {
        String col = "cuenta_depreciacion_id".equals(columna) ? "cuenta_depreciacion_id" : "cuenta_activo_id";
        return jdbc.queryForList("SELECT DISTINCT " + col + " FROM activo_fijo WHERE empresa_id = :e AND "
                + col + " IS NOT NULL AND deleted_at IS NULL", p(empresaId), Long.class);
    }

    public List<Map<String, Object>> bancos(Integer empresaId) {
        return jdbc.queryForList("""
                SELECT id, nombre, cuenta_contable_id, COALESCE(saldo_actual, 0) AS saldo
                  FROM cuenta_bancaria
                 WHERE empresa_id = :e AND cuenta_contable_id IS NOT NULL
                 ORDER BY nombre
                """, p(empresaId));
    }

    // ── Acta ─────────────────────────────────────────────────────────────────

    public List<LineaBalance> balance(Integer empresaId, LocalDate desde, LocalDate hasta) {
        return jdbc.query("""
                SELECT pc.codigo, pc.nombre,
                       COALESCE(SUM(CASE WHEN a.fecha < :d THEN ad.debito - ad.credito END), 0) AS saldo_anterior,
                       COALESCE(SUM(CASE WHEN a.fecha >= :d THEN ad.debito END), 0)              AS debitos,
                       COALESCE(SUM(CASE WHEN a.fecha >= :d THEN ad.credito END), 0)             AS creditos,
                       COALESCE(SUM(ad.debito - ad.credito), 0)                                  AS saldo_final
                  FROM asiento_detalle ad
                  JOIN asiento_contable a ON a.id = ad.asiento_id
                  JOIN plan_cuenta pc ON pc.id = ad.cuenta_id
                 WHERE a.empresa_id = :e AND a.estado = 'CONTABILIZADO' AND a.fecha <= :h
                 GROUP BY pc.codigo, pc.nombre
                HAVING SUM(ad.debito - ad.credito) <> 0
                    OR SUM(CASE WHEN a.fecha >= :d THEN ad.debito + ad.credito ELSE 0 END) <> 0
                 ORDER BY pc.codigo
                """, p(empresaId).addValue("d", desde).addValue("h", hasta),
                new BeanPropertyRowMapper<>(LineaBalance.class));
    }

    /** Asiento de cancelación de resultados del período (null si no hay). */
    public Map<String, Object> asientoCierre(Integer empresaId, Long periodoId) {
        List<Map<String, Object>> r = jdbc.queryForList("""
                SELECT id, numero_comprobante FROM asiento_contable
                 WHERE empresa_id = :e AND tipo_origen = 'CIERRE' AND origen_id = :p
                   AND estado = 'CONTABILIZADO'
                 ORDER BY id DESC LIMIT 1
                """, p(empresaId).addValue("p", periodoId));
        return r.isEmpty() ? null : r.get(0);
    }

    public List<LineaAsiento> lineasAsiento(Long asientoId) {
        return jdbc.query("""
                SELECT pc.codigo, pc.nombre, ad.debito, ad.credito
                  FROM asiento_detalle ad
                  JOIN plan_cuenta pc ON pc.id = ad.cuenta_id
                 WHERE ad.asiento_id = :a
                 ORDER BY pc.codigo
                """, new MapSqlParameterSource("a", asientoId), new BeanPropertyRowMapper<>(LineaAsiento.class));
    }

    public Map<String, Object> empresaYUsuario(Integer empresaId, Long usuarioId) {
        return jdbc.queryForMap("""
                SELECT COALESCE(NULLIF(TRIM(e.nombre_comercial), ''), e.razon_social) AS empresa,
                       e.nit AS nit,
                       (SELECT COALESCE(NULLIF(TRIM(CONCAT_WS(' ', t.nombres, t.apellidos)), ''),
                                        t.razon_social, u.username)
                          FROM usuario u LEFT JOIN tercero t ON t.id = u.tercero_id
                         WHERE u.id = :u) AS usuario
                  FROM empresa e WHERE e.id = :e
                """, p(empresaId).addValue("u", usuarioId));
    }

    // ── Comparativos ─────────────────────────────────────────────────────────

    /**
     * Resultados por grupo (2 dígitos: 41, 51, 61…) en tres rangos. Sin el
     * asiento de cierre, que deja las cuentas de resultado en cero.
     */
    public List<LineaComparativo> comparativo(Integer empresaId, LocalDate d1, LocalDate h1,
            LocalDate d2, LocalDate h2, LocalDate d3, LocalDate h3) {
        return jdbc.query("""
                SELECT x.codigo,
                       COALESCE((SELECT g.nombre FROM plan_cuenta g
                                  WHERE g.empresa_id = :e AND g.codigo = x.codigo LIMIT 1),
                                'Grupo ' || x.codigo) AS nombre,
                       x.seccion, x.actual, x.mes_anterior, x.anio_anterior
                  FROM (
                    SELECT LEFT(pc.codigo, 2) AS codigo,
                           CASE LEFT(pc.codigo, 1) WHEN '4' THEN 'INGRESO' WHEN '5' THEN 'GASTO' ELSE 'COSTO' END AS seccion,
                           COALESCE(SUM(CASE WHEN a.fecha BETWEEN :d1 AND :h1 THEN
                               CASE WHEN LEFT(pc.codigo, 1) = '4' THEN ad.credito - ad.debito ELSE ad.debito - ad.credito END
                               END), 0) AS actual,
                           COALESCE(SUM(CASE WHEN a.fecha BETWEEN :d2 AND :h2 THEN
                               CASE WHEN LEFT(pc.codigo, 1) = '4' THEN ad.credito - ad.debito ELSE ad.debito - ad.credito END
                               END), 0) AS mes_anterior,
                           COALESCE(SUM(CASE WHEN a.fecha BETWEEN :d3 AND :h3 THEN
                               CASE WHEN LEFT(pc.codigo, 1) = '4' THEN ad.credito - ad.debito ELSE ad.debito - ad.credito END
                               END), 0) AS anio_anterior
                      FROM asiento_detalle ad
                      JOIN asiento_contable a ON a.id = ad.asiento_id
                      JOIN plan_cuenta pc ON pc.id = ad.cuenta_id
                     WHERE a.empresa_id = :e AND a.estado = 'CONTABILIZADO' AND a.tipo_origen <> 'CIERRE'
                       AND LEFT(pc.codigo, 1) IN ('4', '5', '6', '7')
                       AND ((a.fecha BETWEEN :d1 AND :h1) OR (a.fecha BETWEEN :d2 AND :h2)
                            OR (a.fecha BETWEEN :d3 AND :h3))
                     GROUP BY LEFT(pc.codigo, 2), LEFT(pc.codigo, 1)
                  ) x
                 ORDER BY x.codigo
                """, p(empresaId).addValue("d1", d1).addValue("h1", h1).addValue("d2", d2).addValue("h2", h2)
                .addValue("d3", d3).addValue("h3", h3), new BeanPropertyRowMapper<>(LineaComparativo.class));
    }

    // ── Snapshot del chequeo al cerrar ───────────────────────────────────────

    public void guardarChequeo(Long periodoId, String json) {
        jdbc.update("UPDATE periodo_contable SET chequeo_cierre = :j WHERE id = :p",
                new MapSqlParameterSource("j", json).addValue("p", periodoId));
    }

    public String chequeoGuardado(Long periodoId) {
        List<String> r = jdbc.queryForList("SELECT chequeo_cierre FROM periodo_contable WHERE id = :p",
                new MapSqlParameterSource("p", periodoId), String.class);
        return r.isEmpty() ? null : r.get(0);
    }
}
