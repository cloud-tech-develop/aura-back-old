package com.cloud_technological.aura_pos.contabilidad.infrastructure.persistence;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.port.LectorCausacionGasto;

import lombok.RequiredArgsConstructor;

/** Proyecta la causación de gasto y sus líneas al snapshot del generador CG. */
@Component
@RequiredArgsConstructor
public class LectorCausacionGastoJpa implements LectorCausacionGasto {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public CausacionGastoContable cargar(Long causacionId, Integer empresaId) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", causacionId).addValue("empresaId", empresaId);
        List<LineaContable> lineas = jdbc.query("""
                SELECT l.cuenta_id, l.descripcion, l.centro_costo_id, l.base, l.valor_iva, l.iva_descontable,
                       l.valor_retefuente, tr.cuenta_contable_id AS retefuente_cuenta_id,
                       l.valor_reteiva, l.valor_reteica
                FROM causacion_gasto_linea l
                JOIN causacion_gasto c ON c.id = l.causacion_id
                LEFT JOIN tarifa_retencion tr ON tr.id = l.tarifa_retencion_id
                WHERE l.causacion_id = :id AND c.empresa_id = :empresaId
                ORDER BY l.id
                """, p, (rs, i) -> new LineaContable(
                        rs.getLong("cuenta_id"),
                        rs.getString("descripcion"),
                        rs.getObject("centro_costo_id", Long.class),
                        rs.getBigDecimal("base"),
                        rs.getBigDecimal("valor_iva"),
                        rs.getBoolean("iva_descontable"),
                        rs.getBigDecimal("valor_retefuente"),
                        rs.getObject("retefuente_cuenta_id", Long.class),
                        rs.getBigDecimal("valor_reteiva"),
                        rs.getBigDecimal("valor_reteica")));

        List<CausacionGastoContable> r = jdbc.query("""
                SELECT 'CG-' || c.consecutivo AS numero, c.fecha, c.tercero_id,
                       COALESCE(NULLIF(t.razon_social, ''), CONCAT(t.nombres, ' ', t.apellidos)) AS tercero_nombre,
                       c.numero_soporte, c.cuenta_pagar_contable_id, c.total_pagar
                FROM causacion_gasto c
                JOIN tercero t ON t.id = c.tercero_id
                WHERE c.id = :id AND c.empresa_id = :empresaId
                """, p, (rs, i) -> new CausacionGastoContable(
                        rs.getString("numero"),
                        rs.getDate("fecha").toLocalDate(),
                        rs.getLong("tercero_id"),
                        rs.getString("tercero_nombre"),
                        rs.getString("numero_soporte"),
                        rs.getObject("cuenta_pagar_contable_id", Long.class),
                        rs.getBigDecimal("total_pagar") != null ? rs.getBigDecimal("total_pagar") : BigDecimal.ZERO,
                        lineas));
        if (r.isEmpty()) {
            throw new IllegalStateException("Causación de gasto #" + causacionId + " no encontrada para contabilizar");
        }
        return r.get(0);
    }
}
