package com.cloud_technological.aura_pos.services.implementations;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.services.CarteraService;

import lombok.extern.slf4j.Slf4j;

/**
 * Resuelve las promesas de pago contra lo que de verdad entró.
 *
 * <p>Una promesa se cumple con abonos del cliente (de la factura prometida, o de
 * cualquiera si la promesa fue por toda la cartera) registrados después de la
 * gestión y fechados hasta el día prometido: lo que ya se había pagado cuando
 * el cliente prometió no cuenta, y pagar tarde tampoco. Mientras la fecha no
 * haya pasado, una promesa ya cumplida se vuelve a revisar: si anulan el
 * recibo que la cumplió, regresa a pendiente.
 *
 * <p>No hay estado que mantener a mano: se evalúa en la madrugada, al abrir la
 * agenda o la ficha, y cuando entra un pago del cliente.
 */
@Slf4j
@Service
public class PromesaPagoService {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    @Lazy
    private CarteraService carteraService;

    private static final String EVALUAR = """
        WITH pagos AS (
            SELECT g.id, COALESCE(SUM(a.monto), 0) AS pagado
            FROM gestion_cobro g
            LEFT JOIN cuentas_cobrar cc
                   ON cc.empresa_id = g.empresa_id AND cc.tercero_id = g.tercero_id
                  AND (g.cuenta_cobrar_id IS NULL OR cc.id = g.cuenta_cobrar_id)
            LEFT JOIN abonos_cobrar a
                   ON a.cuenta_cobrar_id = cc.id AND a.deleted_at IS NULL
                  AND a.created_at >= g.created_at
                  AND a.fecha_pago::date <= g.fecha_promesa_pago
            WHERE (g.estado_promesa = 'PENDIENTE'
                   OR (g.estado_promesa = 'CUMPLIDA' AND g.fecha_promesa_pago >= CURRENT_DATE))
              AND (CAST(:empresaId AS INTEGER) IS NULL OR g.empresa_id = :empresaId)
              AND (CAST(:terceroId AS BIGINT) IS NULL OR g.tercero_id = :terceroId)
            GROUP BY g.id
        ), nuevos AS (
            SELECT p.id, p.pagado,
                   CASE WHEN p.pagado >= g.monto_prometido THEN 'CUMPLIDA'
                        WHEN CURRENT_DATE > g.fecha_promesa_pago THEN 'INCUMPLIDA'
                        ELSE 'PENDIENTE' END AS estado
            FROM pagos p
            JOIN gestion_cobro g ON g.id = p.id
        )
        UPDATE gestion_cobro g
        SET monto_pagado_promesa = n.pagado,
            estado_promesa = n.estado,
            promesa_resuelta_at = CASE WHEN n.estado = g.estado_promesa THEN g.promesa_resuelta_at
                                       WHEN n.estado = 'PENDIENTE' THEN NULL
                                       ELSE NOW() END
        FROM nuevos n
        WHERE g.id = n.id
          AND (n.estado <> g.estado_promesa OR n.pagado IS DISTINCT FROM g.monto_pagado_promesa)
        RETURNING g.empresa_id, g.tercero_id, g.estado_promesa
        """;

    /**
     * @param empresaId null = todas las empresas (solo el job nocturno).
     * @param terceroId null = todos los clientes de la empresa.
     * @return cuántas promesas cambiaron.
     */
    @Transactional
    public int evaluar(Integer empresaId, Long terceroId) {
        List<Map<String, Object>> cambios = jdbc.queryForList(EVALUAR,
                new MapSqlParameterSource("empresaId", empresaId).addValue("terceroId", terceroId));

        // Incumplir baja el score: se recalcula una vez por cliente afectado.
        Set<String> recalculados = new HashSet<>();
        for (Map<String, Object> c : cambios) {
            if (!"INCUMPLIDA".equals(c.get("estado_promesa"))) continue;
            Integer emp = ((Number) c.get("empresa_id")).intValue();
            Long ter = ((Number) c.get("tercero_id")).longValue();
            if (!recalculados.add(emp + ":" + ter)) continue;
            try {
                carteraService.recalcularScore(ter, emp);
            } catch (Exception e) {
                log.warn("No se pudo recalcular el score del tercero {} (empresa {}): {}", ter, emp, e.getMessage());
            }
        }
        return cambios.size();
    }

    /** La promesa nueva reemplaza a la pendiente del mismo cliente. */
    @Transactional
    public void cancelarPendientes(Long terceroId, Integer empresaId) {
        jdbc.update("""
            UPDATE gestion_cobro
            SET estado_promesa = 'CANCELADA', promesa_resuelta_at = NOW()
            WHERE empresa_id = :empresaId AND tercero_id = :terceroId AND estado_promesa = 'PENDIENTE'
            """, new MapSqlParameterSource("empresaId", empresaId).addValue("terceroId", terceroId));
    }
}
