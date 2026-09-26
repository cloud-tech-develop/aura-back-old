package com.cloud_technological.aura_pos.repositories.cartera;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.cartera.ficha.FichaAnticipoDto;
import com.cloud_technological.aura_pos.dto.cartera.ficha.FichaClienteDto;
import com.cloud_technological.aura_pos.dto.cartera.ficha.FichaFacturaDto;
import com.cloud_technological.aura_pos.dto.cartera.ficha.FichaGestionDto;
import com.cloud_technological.aura_pos.dto.cartera.ficha.FichaPagoDto;
import com.cloud_technological.aura_pos.dto.cartera.ficha.FichaResumenDto;
import com.cloud_technological.aura_pos.dto.cartera.recibo.ReciboCajaDto;
import com.cloud_technological.aura_pos.dto.cartera.recibo.ReciboCajaTableDto;

/** Recibos de caja de cartera y la ficha del cliente. */
@Repository
public class ReciboCajaQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /**
     * Siguiente consecutivo con candado de transacción por empresa: dos recibos
     * simultáneos no pueden tomar el mismo número.
     */
    public int siguienteConsecutivo(Integer empresaId) {
        MapSqlParameterSource p = new MapSqlParameterSource("empresaId", empresaId);
        jdbc.query("SELECT pg_advisory_xact_lock(167167, :empresaId)", p, rs -> null);
        Integer n = jdbc.queryForObject(
                "SELECT COALESCE(MAX(consecutivo), 0) + 1 FROM recibo_caja WHERE empresa_id = :empresaId",
                p, Integer.class);
        return n != null ? n : 1;
    }

    private static final String SELECT_TABLA = """
        SELECT
            r.id, r.numero, r.tercero_id,
            COALESCE(NULLIF(t.razon_social, ''), TRIM(CONCAT(t.nombres, ' ', t.apellidos))) AS tercero_nombre,
            t.numero_documento AS tercero_documento,
            r.fecha_pago, r.valor_recibido, r.valor_aplicado, r.valor_anticipo,
            r.metodo_pago, r.referencia, r.estado,
            (SELECT COUNT(*) FROM recibo_caja_aplicacion a WHERE a.recibo_caja_id = r.id)::INT AS facturas,
            u.username AS usuario_nombre,
            COUNT(*) OVER () AS total_rows
        FROM recibo_caja r
        JOIN tercero t ON t.id = r.tercero_id
        LEFT JOIN usuario u ON u.id = r.usuario_id
        """;

    public PageImpl<ReciboCajaTableDto> listar(Integer empresaId, Long terceroId, String estado,
            String search, int page, int rows) {
        StringBuilder sql = new StringBuilder(SELECT_TABLA).append(" WHERE r.empresa_id = :empresaId");
        MapSqlParameterSource p = new MapSqlParameterSource("empresaId", empresaId);
        if (terceroId != null) {
            sql.append(" AND r.tercero_id = :terceroId");
            p.addValue("terceroId", terceroId);
        }
        if (estado != null && !estado.isBlank()) {
            sql.append(" AND r.estado = :estado");
            p.addValue("estado", estado.toUpperCase());
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(r.numero) LIKE :search OR LOWER(COALESCE(NULLIF(t.razon_social, ''), TRIM(CONCAT(t.nombres, ' ', t.apellidos)))) LIKE :search"
                    + " OR t.numero_documento LIKE :search OR LOWER(COALESCE(r.referencia,'')) LIKE :search)");
            p.addValue("search", "%" + search.trim().toLowerCase() + "%");
        }
        sql.append(" ORDER BY r.fecha_pago DESC, r.id DESC OFFSET :offset LIMIT :limit");
        p.addValue("offset", page * rows).addValue("limit", rows);
        List<ReciboCajaTableDto> lista = jdbc.query(sql.toString(), p,
                new BeanPropertyRowMapper<>(ReciboCajaTableDto.class));
        long total = lista.isEmpty() ? 0 : lista.get(0).getTotalRows();
        return new PageImpl<>(lista, PageRequest.of(page, Math.max(rows, 1)), total);
    }

    public ReciboCajaDto obtener(Long id, Integer empresaId) {
        String sql = """
            SELECT
                r.id, r.numero, r.tercero_id,
                COALESCE(NULLIF(t.razon_social, ''), TRIM(CONCAT(t.nombres, ' ', t.apellidos))) AS tercero_nombre,
                t.numero_documento AS tercero_documento,
                r.fecha_pago, r.valor_recibido, r.valor_aplicado, r.valor_anticipo,
                r.metodo_pago, r.referencia, r.caja_otro_dia, r.anticipo_id, r.observaciones,
                r.estado, r.motivo_anulacion, r.anulado_at, r.created_at,
                u.username AS usuario_nombre
            FROM recibo_caja r
            JOIN tercero t ON t.id = r.tercero_id
            LEFT JOIN usuario u ON u.id = r.usuario_id
            WHERE r.id = :id AND r.empresa_id = :empresaId
            """;
        MapSqlParameterSource p = new MapSqlParameterSource("id", id).addValue("empresaId", empresaId);
        List<ReciboCajaDto> r = jdbc.query(sql, p, new BeanPropertyRowMapper<>(ReciboCajaDto.class));
        if (r.isEmpty()) return null;
        ReciboCajaDto dto = r.get(0);
        dto.setAplicaciones(jdbc.query("""
            SELECT a.cuenta_cobrar_id, cc.numero_cuenta, cc.fecha_vencimiento,
                   a.saldo_anterior, a.monto, (a.saldo_anterior - a.monto) AS saldo_despues
            FROM recibo_caja_aplicacion a
            JOIN cuentas_cobrar cc ON cc.id = a.cuenta_cobrar_id
            WHERE a.recibo_caja_id = :id
            ORDER BY a.id
            """, p, new BeanPropertyRowMapper<>(ReciboCajaDto.AplicacionDto.class)));
        return dto;
    }

    // ─── Ficha del cliente ────────────────────────────────────────────────

    public FichaClienteDto cliente(Long terceroId, Integer empresaId) {
        String sql = """
            SELECT
                t.id AS tercero_id,
                COALESCE(NULLIF(t.razon_social, ''), TRIM(CONCAT(t.nombres, ' ', t.apellidos))) AS nombre,
                t.tipo_documento, t.numero_documento, t.telefono, t.email, t.direccion, t.municipio,
                tc.id AS credito_id,
                tc.cupo_credito_actual AS cupo_credito,
                tc.plazo_dias, tc.estado_credito, tc.nivel_riesgo, tc.score_crediticio,
                tc.dias_mora_tolerancia, tc.requiere_autorizacion
            FROM tercero t
            LEFT JOIN tercero_credito tc ON tc.tercero_id = t.id AND tc.empresa_id = t.empresa_id
            WHERE t.id = :terceroId AND t.empresa_id = :empresaId
            """;
        List<FichaClienteDto> r = jdbc.query(sql, params(terceroId, empresaId),
                new BeanPropertyRowMapper<>(FichaClienteDto.class));
        return r.isEmpty() ? null : r.get(0);
    }

    public FichaResumenDto resumen(Long terceroId, Integer empresaId) {
        String sql = """
            WITH abiertas AS (
                SELECT cc.saldo_pendiente AS saldo,
                       CASE WHEN cc.fecha_vencimiento IS NULL THEN NULL
                            ELSE CURRENT_DATE - cc.fecha_vencimiento::date END AS dias
                FROM cuentas_cobrar cc
                WHERE cc.empresa_id = :empresaId AND cc.tercero_id = :terceroId
                  AND cc.deleted_at IS NULL AND cc.estado NOT IN ('pagada','anulada')
                  AND cc.saldo_pendiente > 0
            ), ultimo AS (
                SELECT a.fecha_pago, a.monto
                FROM abonos_cobrar a
                JOIN cuentas_cobrar cc ON cc.id = a.cuenta_cobrar_id
                WHERE cc.empresa_id = :empresaId AND cc.tercero_id = :terceroId AND a.deleted_at IS NULL
                ORDER BY a.fecha_pago DESC, a.id DESC
                LIMIT 1
            )
            SELECT
                COALESCE(SUM(saldo), 0) AS saldo_total,
                COALESCE(SUM(saldo) FILTER (WHERE dias > 0), 0) AS saldo_vencido,
                COALESCE(SUM(saldo) FILTER (WHERE dias IS NULL OR dias <= 0), 0) AS saldo_por_vencer,
                COUNT(*)::INT AS facturas_abiertas,
                (COUNT(*) FILTER (WHERE dias > 0))::INT AS facturas_vencidas,
                COALESCE(MAX(dias) FILTER (WHERE dias > 0), 0)::INT AS dias_mora_maximo,
                COALESCE(SUM(saldo) FILTER (WHERE dias IS NULL OR dias <= 0), 0) AS edad_por_vencer,
                COALESCE(SUM(saldo) FILTER (WHERE dias BETWEEN 1 AND 30), 0) AS edad1a30,
                COALESCE(SUM(saldo) FILTER (WHERE dias BETWEEN 31 AND 60), 0) AS edad31a60,
                COALESCE(SUM(saldo) FILTER (WHERE dias BETWEEN 61 AND 90), 0) AS edad61a90,
                COALESCE(SUM(saldo) FILTER (WHERE dias > 90), 0) AS edad_mas90,
                (SELECT COALESCE(SUM(an.saldo), 0) FROM anticipo an
                  WHERE an.empresa_id = :empresaId AND an.tercero_id = :terceroId
                    AND an.tipo = 'CLIENTE' AND an.estado = 'ACTIVO') AS anticipo_disponible,
                (SELECT COALESCE(SUM(a.monto), 0) FROM abonos_cobrar a
                  JOIN cuentas_cobrar c2 ON c2.id = a.cuenta_cobrar_id
                  WHERE c2.empresa_id = :empresaId AND c2.tercero_id = :terceroId
                    AND a.deleted_at IS NULL AND a.fecha_pago >= NOW() - INTERVAL '90 days') AS recaudado90_dias,
                (SELECT ROUND(AVG(d))::INT FROM (
                    SELECT (MAX(a.fecha_pago)::date - c3.fecha_emision::date) AS d
                    FROM cuentas_cobrar c3
                    JOIN abonos_cobrar a ON a.cuenta_cobrar_id = c3.id AND a.deleted_at IS NULL
                    WHERE c3.empresa_id = :empresaId AND c3.tercero_id = :terceroId
                      AND c3.estado = 'pagada' AND c3.deleted_at IS NULL
                    GROUP BY c3.id, c3.fecha_emision
                    HAVING MAX(a.fecha_pago) >= NOW() - INTERVAL '365 days'
                ) pagadas) AS promedio_dias_pago,
                (SELECT fecha_pago FROM ultimo) AS ultimo_pago_fecha,
                (SELECT monto FROM ultimo) AS ultimo_pago_monto
            FROM abiertas
            """;
        return jdbc.queryForObject(sql, params(terceroId, empresaId),
                new BeanPropertyRowMapper<>(FichaResumenDto.class));
    }

    public List<FichaFacturaDto> facturasAbiertas(Long terceroId, Integer empresaId) {
        String sql = """
            SELECT
                cc.id, cc.numero_cuenta, cc.venta_id,
                CASE WHEN v.id IS NULL THEN NULL
                     ELSE CONCAT(COALESCE(v.prefijo, ''), v.consecutivo) END AS numero_venta,
                cc.fecha_emision, cc.fecha_vencimiento, cc.total_deuda, cc.total_abonado, cc.saldo_pendiente,
                CASE WHEN cc.fecha_vencimiento IS NULL THEN 0
                     ELSE CURRENT_DATE - cc.fecha_vencimiento::date END AS dias_vencida,
                ap.id AS acuerdo_id, ap.numero AS acuerdo_numero
            FROM cuentas_cobrar cc
            LEFT JOIN venta v ON v.id = cc.venta_id
            LEFT JOIN acuerdo_pago_cuenta apc ON apc.cuenta_cobrar_id = cc.id AND apc.activo
            LEFT JOIN acuerdo_pago ap ON ap.id = apc.acuerdo_pago_id
            WHERE cc.empresa_id = :empresaId AND cc.tercero_id = :terceroId
              AND cc.deleted_at IS NULL AND cc.estado NOT IN ('pagada','anulada')
              AND cc.saldo_pendiente > 0
            ORDER BY cc.fecha_vencimiento ASC NULLS LAST, cc.id ASC
            """;
        return jdbc.query(sql, params(terceroId, empresaId), new BeanPropertyRowMapper<>(FichaFacturaDto.class));
    }

    public List<FichaPagoDto> pagos(Long terceroId, Integer empresaId, int limite) {
        String sql = """
            SELECT * FROM (
                SELECT 'ABONO' AS tipo, a.id, a.fecha_pago AS fecha, a.monto, a.metodo_pago, a.referencia,
                       cc.id AS cuenta_cobrar_id, cc.numero_cuenta,
                       a.recibo_caja_id, r.numero AS recibo_numero, u.username AS usuario_nombre
                FROM abonos_cobrar a
                JOIN cuentas_cobrar cc ON cc.id = a.cuenta_cobrar_id
                LEFT JOIN recibo_caja r ON r.id = a.recibo_caja_id
                LEFT JOIN usuario u ON u.id = a.usuario_id
                WHERE cc.empresa_id = :empresaId AND cc.tercero_id = :terceroId AND a.deleted_at IS NULL
                UNION ALL
                SELECT 'CRUCE_ANTICIPO', x.id, x.fecha::timestamp, x.monto, 'ANTICIPO', CONCAT('Anticipo #', x.anticipo_id),
                       cc.id, cc.numero_cuenta, NULL, NULL, u.username
                FROM anticipo_cruce x
                JOIN cuentas_cobrar cc ON cc.id = x.cuenta_cobrar_id
                LEFT JOIN usuario u ON u.id = x.usuario_id
                WHERE x.empresa_id = :empresaId AND cc.tercero_id = :terceroId
            ) p
            ORDER BY fecha DESC, id DESC
            LIMIT :limite
            """;
        return jdbc.query(sql, params(terceroId, empresaId).addValue("limite", limite),
                new BeanPropertyRowMapper<>(FichaPagoDto.class));
    }

    public List<ReciboCajaTableDto> recibos(Long terceroId, Integer empresaId, int limite) {
        return listar(empresaId, terceroId, null, null, 0, limite).getContent();
    }

    public List<FichaGestionDto> gestiones(Long terceroId, Integer empresaId, int limite) {
        String sql = """
            SELECT g.id, g.tipo_gestion, g.resultado, g.nota, g.fecha_promesa_pago, g.monto_prometido,
                   g.estado_promesa, g.monto_pagado_promesa,
                   cc.numero_cuenta, u.username AS usuario_nombre, g.created_at
            FROM gestion_cobro g
            LEFT JOIN cuentas_cobrar cc ON cc.id = g.cuenta_cobrar_id
            LEFT JOIN usuario u ON u.id = g.usuario_id
            WHERE g.empresa_id = :empresaId AND g.tercero_id = :terceroId
            ORDER BY g.created_at DESC
            LIMIT :limite
            """;
        return jdbc.query(sql, params(terceroId, empresaId).addValue("limite", limite),
                new BeanPropertyRowMapper<>(FichaGestionDto.class));
    }

    public List<FichaAnticipoDto> anticiposActivos(Long terceroId, Integer empresaId) {
        String sql = """
            SELECT an.id, an.fecha, an.monto, an.saldo, an.metodo_pago, an.observaciones,
                   r.numero AS recibo_numero
            FROM anticipo an
            LEFT JOIN recibo_caja r ON r.id = an.recibo_caja_id
            WHERE an.empresa_id = :empresaId AND an.tercero_id = :terceroId
              AND an.tipo = 'CLIENTE' AND an.estado = 'ACTIVO' AND an.saldo > 0
            ORDER BY an.fecha ASC, an.id ASC
            """;
        return jdbc.query(sql, params(terceroId, empresaId), new BeanPropertyRowMapper<>(FichaAnticipoDto.class));
    }

    public List<com.cloud_technological.aura_pos.dto.cartera.ficha.FichaHistorialCreditoDto> historialCredito(
            Long terceroId, Integer empresaId, int limite) {
        String sql = """
            SELECT h.id, h.tipo_evento, h.cupo_anterior, h.cupo_nuevo, h.score_anterior, h.score_nuevo,
                   h.motivo, u.username AS usuario_nombre, r.nombre AS regla_nombre, h.created_at
            FROM historial_credito h
            LEFT JOIN usuario u ON u.id = h.usuario_id
            LEFT JOIN regla_credito r ON r.id = h.regla_id
            WHERE h.empresa_id = :empresaId AND h.tercero_id = :terceroId
            ORDER BY h.created_at DESC
            LIMIT :limite
            """;
        return jdbc.query(sql, params(terceroId, empresaId).addValue("limite", limite),
                new BeanPropertyRowMapper<>(com.cloud_technological.aura_pos.dto.cartera.ficha.FichaHistorialCreditoDto.class));
    }

    private MapSqlParameterSource params(Long terceroId, Integer empresaId) {
        return new MapSqlParameterSource("terceroId", terceroId).addValue("empresaId", empresaId);
    }
}
