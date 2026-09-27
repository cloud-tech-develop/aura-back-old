package com.cloud_technological.aura_pos.repositories.cartera;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.cartera.agenda.AgendaResumenDto;
import com.cloud_technological.aura_pos.dto.cartera.agenda.ClienteAgendaDto;
import com.cloud_technological.aura_pos.dto.cartera.agenda.FacturaAgendaDto;
import com.cloud_technological.aura_pos.dto.cartera.agenda.PromesaAgendaDto;

@Repository
public class AgendaCobroQueryRepository {

    /** Sin gestión en estos días, un cliente vencido pasa a la lista de "nadie lo ha llamado". */
    public static final int DIAS_SIN_GESTION = 15;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private static final String SELECT_PROMESA = """
        SELECT g.id AS gestion_id, g.tercero_id,
               COALESCE(NULLIF(t.razon_social, ''), TRIM(CONCAT(t.nombres, ' ', t.apellidos))) AS tercero_nombre, t.telefono,
               g.fecha_promesa_pago, g.monto_prometido,
               COALESCE(g.monto_pagado_promesa, 0) AS monto_pagado,
               g.estado_promesa, g.cuenta_cobrar_id, cc.numero_cuenta, g.nota,
               u.username AS usuario_nombre, g.created_at,
               (CURRENT_DATE - g.fecha_promesa_pago) AS dias,
               (SELECT COALESCE(SUM(c2.saldo_pendiente), 0) FROM cuentas_cobrar c2
                 WHERE c2.empresa_id = g.empresa_id AND c2.tercero_id = g.tercero_id
                   AND c2.deleted_at IS NULL AND c2.estado NOT IN ('pagada','anulada')) AS saldo_cliente
        FROM gestion_cobro g
        JOIN tercero t ON t.id = g.tercero_id
        LEFT JOIN cuentas_cobrar cc ON cc.id = g.cuenta_cobrar_id
        LEFT JOIN usuario u ON u.id = g.usuario_id
        """;

    public List<PromesaAgendaDto> promesasHoy(Integer empresaId) {
        return jdbc.query(SELECT_PROMESA + """
            WHERE g.empresa_id = :empresaId AND g.estado_promesa = 'PENDIENTE'
              AND g.fecha_promesa_pago = CURRENT_DATE
            ORDER BY g.monto_prometido DESC
            """, p(empresaId), new BeanPropertyRowMapper<>(PromesaAgendaDto.class));
    }

    public List<PromesaAgendaDto> promesasProximas(Integer empresaId, int dias) {
        return jdbc.query(SELECT_PROMESA + """
            WHERE g.empresa_id = :empresaId AND g.estado_promesa = 'PENDIENTE'
              AND g.fecha_promesa_pago > CURRENT_DATE AND g.fecha_promesa_pago <= CURRENT_DATE + :dias
            ORDER BY g.fecha_promesa_pago, g.monto_prometido DESC
            """, p(empresaId).addValue("dias", dias), new BeanPropertyRowMapper<>(PromesaAgendaDto.class));
    }

    /**
     * Incumplidas de los últimos 60 días a las que nadie les ha hecho seguimiento:
     * si después de incumplir ya hubo otra gestión con ese cliente, sale de la lista.
     */
    public List<PromesaAgendaDto> promesasIncumplidas(Integer empresaId) {
        return jdbc.query(SELECT_PROMESA + """
            WHERE g.empresa_id = :empresaId AND g.estado_promesa = 'INCUMPLIDA'
              AND g.fecha_promesa_pago >= CURRENT_DATE - 60
              AND NOT EXISTS (
                  SELECT 1 FROM gestion_cobro g2
                  WHERE g2.empresa_id = g.empresa_id AND g2.tercero_id = g.tercero_id
                    AND g2.created_at > COALESCE(g.promesa_resuelta_at, g.fecha_promesa_pago + 1))
            ORDER BY g.fecha_promesa_pago DESC
            """, p(empresaId), new BeanPropertyRowMapper<>(PromesaAgendaDto.class));
    }

    public List<FacturaAgendaDto> porVencer(Integer empresaId, int dias) {
        return jdbc.query("""
            SELECT cc.id AS cuenta_cobrar_id, cc.numero_cuenta, cc.tercero_id,
                   COALESCE(NULLIF(t.razon_social, ''), TRIM(CONCAT(t.nombres, ' ', t.apellidos))) AS tercero_nombre, t.telefono,
                   cc.fecha_vencimiento, cc.saldo_pendiente,
                   (cc.fecha_vencimiento::date - CURRENT_DATE) AS dias_para_vencer
            FROM cuentas_cobrar cc
            JOIN tercero t ON t.id = cc.tercero_id
            WHERE cc.empresa_id = :empresaId AND cc.deleted_at IS NULL
              AND cc.estado NOT IN ('pagada','anulada') AND cc.saldo_pendiente > 0
              AND cc.fecha_vencimiento::date BETWEEN CURRENT_DATE AND CURRENT_DATE + :dias
            ORDER BY cc.fecha_vencimiento, cc.saldo_pendiente DESC
            LIMIT 100
            """, p(empresaId).addValue("dias", dias), new BeanPropertyRowMapper<>(FacturaAgendaDto.class));
    }

    /** Clientes con saldo vencido, sin promesa pendiente y sin gestión reciente. */
    public List<ClienteAgendaDto> vencidosSinGestion(Integer empresaId) {
        return jdbc.query("""
            SELECT cc.tercero_id,
                   MAX(COALESCE(NULLIF(t.razon_social, ''), TRIM(CONCAT(t.nombres, ' ', t.apellidos)))) AS tercero_nombre, MAX(t.telefono) AS telefono,
                   COUNT(*)::INT AS facturas_vencidas,
                   SUM(cc.saldo_pendiente) AS saldo_vencido,
                   MAX(CURRENT_DATE - cc.fecha_vencimiento::date)::INT AS dias_mora_maximo,
                   MAX(ug.created_at) AS ultima_gestion,
                   MAX(ug.resultado) AS ultimo_resultado
            FROM cuentas_cobrar cc
            JOIN tercero t ON t.id = cc.tercero_id
            LEFT JOIN LATERAL (
                SELECT g.created_at, g.resultado FROM gestion_cobro g
                WHERE g.empresa_id = cc.empresa_id AND g.tercero_id = cc.tercero_id
                ORDER BY g.created_at DESC LIMIT 1
            ) ug ON true
            WHERE cc.empresa_id = :empresaId AND cc.deleted_at IS NULL
              AND cc.estado NOT IN ('pagada','anulada') AND cc.saldo_pendiente > 0
              AND cc.fecha_vencimiento::date < CURRENT_DATE
              AND NOT EXISTS (
                  SELECT 1 FROM gestion_cobro g
                  WHERE g.empresa_id = cc.empresa_id AND g.tercero_id = cc.tercero_id
                    AND (g.estado_promesa = 'PENDIENTE' OR g.created_at >= CURRENT_DATE - :diasSinGestion))
            GROUP BY cc.tercero_id
            ORDER BY SUM(cc.saldo_pendiente) DESC
            LIMIT 100
            """, p(empresaId).addValue("diasSinGestion", DIAS_SIN_GESTION),
                new BeanPropertyRowMapper<>(ClienteAgendaDto.class));
    }

    /**
     * Para la campana: facturas vencidas y las que vencen de hoy a :dias, con su
     * valor y cuántos clientes. Las cuentas dentro de un acuerdo de pago vivo no
     * cuentan aquí: las avisa el acuerdo por cuota.
     */
    public java.util.Map<String, Object> resumenVencimientos(Integer empresaId, int dias) {
        return jdbc.queryForMap("""
            SELECT
                (COUNT(*) FILTER (WHERE cc.fecha_vencimiento::date < CURRENT_DATE))::INT AS vencidas,
                COALESCE(SUM(cc.saldo_pendiente) FILTER (WHERE cc.fecha_vencimiento::date < CURRENT_DATE), 0) AS valor_vencidas,
                (COUNT(DISTINCT cc.tercero_id) FILTER (WHERE cc.fecha_vencimiento::date < CURRENT_DATE))::INT AS clientes_vencidas,
                (COUNT(*) FILTER (WHERE cc.fecha_vencimiento::date BETWEEN CURRENT_DATE AND CURRENT_DATE + :dias))::INT AS por_vencer,
                COALESCE(SUM(cc.saldo_pendiente) FILTER (WHERE cc.fecha_vencimiento::date BETWEEN CURRENT_DATE AND CURRENT_DATE + :dias), 0) AS valor_por_vencer,
                (COUNT(*) FILTER (WHERE cc.fecha_vencimiento::date = CURRENT_DATE))::INT AS vencen_hoy
            FROM cuentas_cobrar cc
            WHERE cc.empresa_id = :empresaId AND cc.deleted_at IS NULL
              AND cc.estado NOT IN ('pagada','anulada') AND cc.saldo_pendiente > 0
              AND cc.fecha_vencimiento IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM acuerdo_pago_cuenta apc
                              WHERE apc.cuenta_cobrar_id = cc.id AND apc.activo)
            """, p(empresaId).addValue("dias", dias));
    }

    public AgendaResumenDto resumenMes(Integer empresaId) {
        return jdbc.queryForObject("""
            SELECT
                (COUNT(*) FILTER (WHERE estado_promesa = 'CUMPLIDA'))::INT AS promesas_cumplidas_mes,
                (COUNT(*) FILTER (WHERE estado_promesa IN ('CUMPLIDA','INCUMPLIDA')))::INT AS promesas_resueltas_mes
            FROM gestion_cobro
            WHERE empresa_id = :empresaId
              AND fecha_promesa_pago >= date_trunc('month', CURRENT_DATE)
              AND fecha_promesa_pago < date_trunc('month', CURRENT_DATE) + INTERVAL '1 month'
            """, p(empresaId), new BeanPropertyRowMapper<>(AgendaResumenDto.class));
    }

    private MapSqlParameterSource p(Integer empresaId) {
        return new MapSqlParameterSource("empresaId", empresaId);
    }
}
