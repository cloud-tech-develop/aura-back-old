package com.cloud_technological.aura_pos.repositories.cartera;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.cartera.acuerdo.AcuerdoCuentaDto;
import com.cloud_technological.aura_pos.dto.cartera.acuerdo.AcuerdoCuotaDto;
import com.cloud_technological.aura_pos.dto.cartera.acuerdo.AcuerdoPagoDto;

/**
 * Acuerdos de pago (V170). Todo por JDBC: las tablas no tienen entidad, así que
 * la validación de esquema de Hibernate no las toca.
 */
@Repository
public class AcuerdoPagoQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    // ── Escritura ────────────────────────────────────────────────────────

    public int siguienteConsecutivo(Integer empresaId) {
        MapSqlParameterSource p = new MapSqlParameterSource("empresaId", empresaId);
        jdbc.query("SELECT pg_advisory_xact_lock(170170, :empresaId)", p, rs -> null);
        Integer n = jdbc.queryForObject(
                "SELECT COALESCE(MAX(consecutivo), 0) + 1 FROM acuerdo_pago WHERE empresa_id = :empresaId",
                p, Integer.class);
        return n != null ? n : 1;
    }

    public Long insertarAcuerdo(Integer empresaId, Long terceroId, String numero, int consecutivo,
            BigDecimal valorTotal, int numeroCuotas, String frecuencia, int diasGracia,
            String observaciones, Integer usuarioId) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update("""
            INSERT INTO acuerdo_pago (empresa_id, tercero_id, numero, consecutivo, valor_total, numero_cuotas,
                                      frecuencia, dias_gracia, observaciones, usuario_id, created_at)
            VALUES (:empresaId, :terceroId, :numero, :consecutivo, :valorTotal, :numeroCuotas,
                    :frecuencia, :diasGracia, :observaciones, :usuarioId, :ahora)
            """, new MapSqlParameterSource("empresaId", empresaId)
                .addValue("terceroId", terceroId)
                .addValue("numero", numero)
                .addValue("consecutivo", consecutivo)
                .addValue("valorTotal", valorTotal)
                .addValue("numeroCuotas", numeroCuotas)
                .addValue("frecuencia", frecuencia)
                .addValue("diasGracia", diasGracia)
                .addValue("observaciones", observaciones)
                .addValue("usuarioId", usuarioId)
                .addValue("ahora", Timestamp.valueOf(LocalDateTime.now())),
                kh, new String[] { "id" });
        Number id = kh.getKey();
        if (id == null) throw new IllegalStateException("El acuerdo de pago no devolvió su id");
        return id.longValue();
    }

    public void insertarCuenta(Long acuerdoId, Long cuentaCobrarId, BigDecimal saldoInicial,
            LocalDateTime vencimientoOriginal) {
        jdbc.update("""
            INSERT INTO acuerdo_pago_cuenta (acuerdo_pago_id, cuenta_cobrar_id, saldo_inicial, fecha_vencimiento_original)
            VALUES (:acuerdoId, :cuentaId, :saldo, :vence)
            """, new MapSqlParameterSource("acuerdoId", acuerdoId)
                .addValue("cuentaId", cuentaCobrarId)
                .addValue("saldo", saldoInicial)
                .addValue("vence", vencimientoOriginal != null ? Timestamp.valueOf(vencimientoOriginal) : null));
    }

    public void insertarCuota(Long acuerdoId, int numero, LocalDate fecha, BigDecimal valor) {
        jdbc.update("""
            INSERT INTO acuerdo_pago_cuota (acuerdo_pago_id, numero, fecha_vencimiento, valor)
            VALUES (:acuerdoId, :numero, :fecha, :valor)
            """, new MapSqlParameterSource("acuerdoId", acuerdoId)
                .addValue("numero", numero)
                .addValue("fecha", fecha)
                .addValue("valor", valor));
    }

    /** Acuerdo vivo (vigente o incumplido) en el que ya está la cuenta, si hay. */
    public String acuerdoActivoDeCuenta(Long cuentaCobrarId) {
        List<String> r = jdbc.queryForList("""
            SELECT a.numero FROM acuerdo_pago_cuenta c
            JOIN acuerdo_pago a ON a.id = c.acuerdo_pago_id
            WHERE c.cuenta_cobrar_id = :cuentaId AND c.activo
            LIMIT 1
            """, new MapSqlParameterSource("cuentaId", cuentaCobrarId), String.class);
        return r.isEmpty() ? null : r.get(0);
    }

    /** Estado y datos de control del acuerdo, bloqueado para escribir. */
    public Map<String, Object> bloquear(Long id, Integer empresaId) {
        List<Map<String, Object>> r = jdbc.queryForList("""
            SELECT id, tercero_id, estado FROM acuerdo_pago
            WHERE id = :id AND empresa_id = :empresaId
            FOR UPDATE
            """, new MapSqlParameterSource("id", id).addValue("empresaId", empresaId));
        return r.isEmpty() ? null : r.get(0);
    }

    public void anular(Long id, String motivo, Integer usuarioId) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", id)
                .addValue("motivo", motivo)
                .addValue("usuarioId", usuarioId)
                .addValue("ahora", Timestamp.valueOf(LocalDateTime.now()));
        // Las cuentas con saldo recuperan su vencimiento original: la deuda vuelve a como estaba.
        jdbc.update("""
            UPDATE cuentas_cobrar cc
            SET fecha_vencimiento = c.fecha_vencimiento_original, updated_at = :ahora
            FROM acuerdo_pago_cuenta c
            WHERE c.acuerdo_pago_id = :id AND c.activo AND cc.id = c.cuenta_cobrar_id
              AND cc.saldo_pendiente > 0 AND c.fecha_vencimiento_original IS NOT NULL
            """, p);
        jdbc.update("UPDATE acuerdo_pago_cuenta SET activo = FALSE WHERE acuerdo_pago_id = :id", p);
        jdbc.update("""
            UPDATE acuerdo_pago
            SET estado = 'ANULADO', motivo_anulacion = :motivo, anulado_por = :usuarioId,
                anulado_at = :ahora, updated_at = :ahora
            WHERE id = :id
            """, p);
    }

    // ── Evaluación ───────────────────────────────────────────────────────

    /**
     * Acuerdos a evaluar, en orden de id para bloquear siempre igual: los vivos y
     * los cumplidos que volvieron a tener saldo porque se anuló un pago.
     */
    public List<Map<String, Object>> vivos(Integer empresaId, Long terceroId, Long acuerdoId) {
        return jdbc.queryForList("""
            SELECT a.id, a.empresa_id, a.tercero_id, a.valor_total, a.valor_pagado, a.dias_gracia, a.estado
            FROM acuerdo_pago a
            WHERE (a.estado IN ('VIGENTE','INCUMPLIDO')
                   OR (a.estado = 'CUMPLIDO' AND EXISTS (
                        SELECT 1 FROM acuerdo_pago_cuenta c
                        JOIN cuentas_cobrar cc ON cc.id = c.cuenta_cobrar_id
                        WHERE c.acuerdo_pago_id = a.id AND cc.saldo_pendiente > 0
                          AND cc.deleted_at IS NULL AND cc.estado <> 'anulada'
                          AND NOT EXISTS (SELECT 1 FROM acuerdo_pago_cuenta c2
                                          WHERE c2.cuenta_cobrar_id = c.cuenta_cobrar_id AND c2.activo))))
              AND (CAST(:empresaId AS INTEGER) IS NULL OR a.empresa_id = :empresaId)
              AND (CAST(:terceroId AS BIGINT) IS NULL OR a.tercero_id = :terceroId)
              AND (CAST(:acuerdoId AS BIGINT) IS NULL OR a.id = :acuerdoId)
            ORDER BY a.id
            """, new MapSqlParameterSource("empresaId", empresaId)
                .addValue("terceroId", terceroId)
                .addValue("acuerdoId", acuerdoId));
    }

    /**
     * Cuánto ha bajado la deuda de las cuentas del acuerdo desde que se firmó:
     * abonos, recibos, cruces de anticipo o notas crédito, todo cuenta igual. Una
     * cuenta anulada ya no se debe.
     */
    public BigDecimal reducido(Long acuerdoId) {
        BigDecimal r = jdbc.queryForObject("""
            SELECT COALESCE(SUM(GREATEST(c.saldo_inicial
                     - CASE WHEN cc.deleted_at IS NOT NULL OR cc.estado = 'anulada' THEN 0
                            ELSE COALESCE(cc.saldo_pendiente, 0) END, 0)), 0)
            FROM acuerdo_pago_cuenta c
            JOIN cuentas_cobrar cc ON cc.id = c.cuenta_cobrar_id
            WHERE c.acuerdo_pago_id = :id
            """, new MapSqlParameterSource("id", acuerdoId), BigDecimal.class);
        return r != null ? r : BigDecimal.ZERO;
    }

    public List<AcuerdoCuotaDto> cuotas(Long acuerdoId) {
        return jdbc.query("""
            SELECT id, numero, fecha_vencimiento, valor, valor_pagado, estado, pagada_at,
                   (CURRENT_DATE - fecha_vencimiento) AS dias_vencida
            FROM acuerdo_pago_cuota
            WHERE acuerdo_pago_id = :id
            ORDER BY numero
            """, new MapSqlParameterSource("id", acuerdoId), new BeanPropertyRowMapper<>(AcuerdoCuotaDto.class));
    }

    public void actualizarCuota(Long cuotaId, BigDecimal valorPagado, String estado) {
        jdbc.update("""
            UPDATE acuerdo_pago_cuota
            SET valor_pagado = :pagado, estado = :estado,
                pagada_at = CASE WHEN CAST(:estado AS VARCHAR) = 'PAGADA' THEN COALESCE(pagada_at, :ahora) ELSE NULL END
            WHERE id = :id AND (valor_pagado IS DISTINCT FROM :pagado OR estado IS DISTINCT FROM :estado)
            """, new MapSqlParameterSource("id", cuotaId)
                .addValue("pagado", valorPagado)
                .addValue("estado", estado)
                .addValue("ahora", Timestamp.valueOf(LocalDateTime.now())));
    }

    /**
     * incumplido_at no se borra al ponerse al día: es la marca de que el cliente
     * incumplió, y el score la sigue contando.
     */
    public void actualizarAcuerdo(Long id, BigDecimal valorPagado, String estado) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", id)
                .addValue("pagado", valorPagado)
                .addValue("estado", estado)
                .addValue("ahora", Timestamp.valueOf(LocalDateTime.now()));
        jdbc.update("""
            UPDATE acuerdo_pago
            SET valor_pagado = :pagado, estado = :estado, updated_at = :ahora,
                incumplido_at = CASE WHEN CAST(:estado AS VARCHAR) = 'INCUMPLIDO' AND estado <> 'INCUMPLIDO' THEN :ahora
                                     ELSE incumplido_at END,
                cumplido_at = CASE WHEN CAST(:estado AS VARCHAR) = 'CUMPLIDO' THEN COALESCE(cumplido_at, :ahora) ELSE NULL END
            WHERE id = :id AND (valor_pagado IS DISTINCT FROM :pagado OR estado IS DISTINCT FROM :estado)
            """, p);
        if ("CUMPLIDO".equals(estado)) {
            jdbc.update("UPDATE acuerdo_pago_cuenta SET activo = FALSE WHERE acuerdo_pago_id = :id AND activo", p);
        } else {
            // Un acuerdo cumplido que vuelve a deber (anularon un recibo) recupera sus cuentas,
            // salvo las que ya entraron a otro acuerdo.
            jdbc.update("""
                UPDATE acuerdo_pago_cuenta c SET activo = TRUE
                WHERE c.acuerdo_pago_id = :id AND NOT c.activo
                  AND NOT EXISTS (SELECT 1 FROM acuerdo_pago_cuenta c2
                                  WHERE c2.cuenta_cobrar_id = c.cuenta_cobrar_id AND c2.activo)
                """, p);
        }
    }

    /** Las cuentas con saldo vencen con la primera cuota sin pagar. */
    public void moverVencimientoCuentas(Long acuerdoId, LocalDate fecha) {
        jdbc.update("""
            UPDATE cuentas_cobrar cc
            SET fecha_vencimiento = :vence, updated_at = :ahora
            FROM acuerdo_pago_cuenta c
            WHERE c.acuerdo_pago_id = :id AND c.activo AND cc.id = c.cuenta_cobrar_id
              AND cc.saldo_pendiente > 0
              AND cc.fecha_vencimiento IS DISTINCT FROM :vence
            """, new MapSqlParameterSource("id", acuerdoId)
                .addValue("vence", Timestamp.valueOf(fecha.atStartOfDay()))
                .addValue("ahora", Timestamp.valueOf(LocalDateTime.now())));
    }

    // ── Lectura ──────────────────────────────────────────────────────────

    private static final String SELECT = """
        SELECT a.id, a.numero, a.tercero_id,
               COALESCE(NULLIF(t.razon_social, ''), TRIM(CONCAT(t.nombres, ' ', t.apellidos))) AS tercero_nombre,
               t.numero_documento AS tercero_documento, t.telefono AS tercero_telefono,
               a.valor_total, a.valor_pagado, GREATEST(a.valor_total - a.valor_pagado, 0) AS saldo,
               a.numero_cuotas,
               (SELECT COUNT(*) FROM acuerdo_pago_cuota q WHERE q.acuerdo_pago_id = a.id AND q.estado = 'PAGADA')::INT AS cuotas_pagadas,
               a.frecuencia, a.dias_gracia, a.estado, a.observaciones, a.motivo_anulacion,
               u.username AS usuario_nombre, a.created_at, a.anulado_at, a.cumplido_at, a.incumplido_at,
               px.fecha_vencimiento AS proxima_cuota_fecha,
               (px.valor - px.valor_pagado) AS proxima_cuota_saldo,
               COALESCE((SELECT MAX(CURRENT_DATE - q.fecha_vencimiento) FROM acuerdo_pago_cuota q
                          WHERE q.acuerdo_pago_id = a.id AND q.estado <> 'PAGADA'
                            AND q.fecha_vencimiento < CURRENT_DATE), 0)::INT AS dias_mora
        FROM acuerdo_pago a
        JOIN tercero t ON t.id = a.tercero_id
        LEFT JOIN usuario u ON u.id = a.usuario_id
        LEFT JOIN LATERAL (
            SELECT q.fecha_vencimiento, q.valor, q.valor_pagado FROM acuerdo_pago_cuota q
            WHERE q.acuerdo_pago_id = a.id AND q.estado <> 'PAGADA'
            ORDER BY q.numero LIMIT 1
        ) px ON a.estado IN ('VIGENTE','INCUMPLIDO')
        """;

    public AcuerdoPagoDto obtener(Long id, Integer empresaId) {
        List<AcuerdoPagoDto> r = jdbc.query(SELECT + " WHERE a.id = :id AND a.empresa_id = :empresaId",
                new MapSqlParameterSource("id", id).addValue("empresaId", empresaId),
                new BeanPropertyRowMapper<>(AcuerdoPagoDto.class));
        if (r.isEmpty()) return null;
        AcuerdoPagoDto dto = r.get(0);
        dto.setCuotas(cuotas(id));
        dto.setCuentas(cuentas(id));
        return dto;
    }

    public List<AcuerdoCuentaDto> cuentas(Long acuerdoId) {
        return jdbc.query("""
            SELECT cc.id AS cuenta_cobrar_id, cc.numero_cuenta,
                   CASE WHEN v.id IS NULL THEN NULL
                        ELSE CONCAT(COALESCE(v.prefijo, ''), v.consecutivo) END AS numero_venta,
                   c.saldo_inicial, cc.saldo_pendiente AS saldo_actual, c.fecha_vencimiento_original
            FROM acuerdo_pago_cuenta c
            JOIN cuentas_cobrar cc ON cc.id = c.cuenta_cobrar_id
            LEFT JOIN venta v ON v.id = cc.venta_id
            WHERE c.acuerdo_pago_id = :id
            ORDER BY cc.id
            """, new MapSqlParameterSource("id", acuerdoId), new BeanPropertyRowMapper<>(AcuerdoCuentaDto.class));
    }

    /** Acuerdos del cliente con sus cuotas, para la ficha. */
    public List<AcuerdoPagoDto> delCliente(Long terceroId, Integer empresaId) {
        List<AcuerdoPagoDto> r = jdbc.query(SELECT + """
             WHERE a.tercero_id = :terceroId AND a.empresa_id = :empresaId
            ORDER BY CASE a.estado WHEN 'INCUMPLIDO' THEN 0 WHEN 'VIGENTE' THEN 1 ELSE 2 END, a.created_at DESC
            LIMIT 30
            """, new MapSqlParameterSource("terceroId", terceroId).addValue("empresaId", empresaId),
                new BeanPropertyRowMapper<>(AcuerdoPagoDto.class));
        for (AcuerdoPagoDto a : r) {
            a.setCuotas(cuotas(a.getId()));
            a.setCuentas(cuentas(a.getId()));
        }
        return r;
    }

    public PageImpl<AcuerdoPagoDto> listar(Integer empresaId, String estado, String search, int page, int rows) {
        MapSqlParameterSource p = new MapSqlParameterSource("empresaId", empresaId)
                .addValue("estado", estado)
                .addValue("search", search != null && !search.isBlank() ? "%" + search.trim().toLowerCase() + "%" : null)
                .addValue("limit", rows)
                .addValue("offset", page * rows);
        String where = """
             WHERE a.empresa_id = :empresaId
              AND (CAST(:estado AS VARCHAR) IS NULL OR a.estado = :estado)
              AND (CAST(:search AS VARCHAR) IS NULL
                   OR LOWER(a.numero) LIKE :search
                   OR LOWER(COALESCE(t.razon_social, '')) LIKE :search
                   OR LOWER(CONCAT(t.nombres, ' ', t.apellidos)) LIKE :search
                   OR LOWER(COALESCE(t.numero_documento, '')) LIKE :search)
            """;
        List<AcuerdoPagoDto> contenido = jdbc.query(SELECT + where + """
            ORDER BY CASE a.estado WHEN 'INCUMPLIDO' THEN 0 WHEN 'VIGENTE' THEN 1 ELSE 2 END,
                     px.fecha_vencimiento NULLS LAST, a.created_at DESC
            LIMIT :limit OFFSET :offset
            """, p, new BeanPropertyRowMapper<>(AcuerdoPagoDto.class));
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM acuerdo_pago a JOIN tercero t ON t.id = a.tercero_id" + where,
                p, Long.class);
        return new PageImpl<>(contenido, PageRequest.of(page, rows), total != null ? total : 0);
    }

    // ── Alertas ──────────────────────────────────────────────────────────

    /** Acuerdos incumplidos: cantidad y lo que está vencido de sus cuotas. */
    public Map<String, Object> resumenIncumplidos(Integer empresaId) {
        return jdbc.queryForMap("""
            SELECT COUNT(DISTINCT a.id)::INT AS cantidad,
                   COALESCE(SUM(q.valor - q.valor_pagado), 0) AS valor
            FROM acuerdo_pago a
            JOIN acuerdo_pago_cuota q ON q.acuerdo_pago_id = a.id AND q.estado = 'VENCIDA'
            WHERE a.empresa_id = :empresaId AND a.estado = 'INCUMPLIDO'
            """, new MapSqlParameterSource("empresaId", empresaId));
    }

    /** Cuotas de acuerdos vivos que vencen de hoy a :dias, incluidas las que están en gracia. */
    public Map<String, Object> resumenCuotasPorVencer(Integer empresaId, int dias) {
        return jdbc.queryForMap("""
            SELECT COUNT(*)::INT AS cantidad,
                   COALESCE(SUM(q.valor - q.valor_pagado), 0) AS valor
            FROM acuerdo_pago a
            JOIN acuerdo_pago_cuota q ON q.acuerdo_pago_id = a.id
            WHERE a.empresa_id = :empresaId AND a.estado IN ('VIGENTE','INCUMPLIDO')
              AND q.estado IN ('PENDIENTE','PARCIAL')
              AND q.fecha_vencimiento <= CURRENT_DATE + :dias
            """, new MapSqlParameterSource("empresaId", empresaId).addValue("dias", dias));
    }
}
