package com.cloud_technological.aura_pos.repositories.nomina_vinculaciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * Lo que el motor de liquidación lee de la vinculación (R3.2d): conceptos propios
 * vigentes y novedades permitidas con sus topes.
 */
@Repository
@RequiredArgsConstructor
public class VinculacionReglasQueryRepository {

    /** Concepto propio de la vinculación (bonificación fija, auxilio de rodamiento…). */
    public record ConceptoPropio(String codigo, BigDecimal cantidad, BigDecimal valor, BigDecimal porcentaje,
            String observacion) {
    }

    /** Novedad permitida con sus topes por período (null = sin tope). */
    public record NovedadPermitida(String codigo, BigDecimal topeCantidad, BigDecimal topeValor) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Conceptos propios del contrato vigentes en algún día del período.
     *
     * @param desde inicio del período liquidado
     * @param hasta fin del período liquidado
     */
    public List<ConceptoPropio> conceptosVigentes(Long contratoId, LocalDate desde, LocalDate hasta) {
        return jdbc.query("""
                SELECT concepto_codigo, cantidad, valor, porcentaje, observacion
                  FROM vinculacion_concepto
                 WHERE contrato_id = :contratoId AND deleted_at IS NULL
                   AND fecha_desde <= :hasta AND (fecha_hasta IS NULL OR fecha_hasta >= :desde)
                 ORDER BY concepto_codigo, fecha_desde
                """, new MapSqlParameterSource("contratoId", contratoId).addValue("desde", desde)
                .addValue("hasta", hasta),
                (rs, i) -> new ConceptoPropio(rs.getString("concepto_codigo"), rs.getBigDecimal("cantidad"),
                        rs.getBigDecimal("valor"), rs.getBigDecimal("porcentaje"), rs.getString("observacion")));
    }

    /** Novedades permitidas del contrato (vacío = no restringe). */
    public List<NovedadPermitida> novedadesPermitidas(Long contratoId) {
        return jdbc.query("""
                SELECT concepto_codigo, tope_cantidad, tope_valor
                  FROM vinculacion_novedad_permitida
                 WHERE contrato_id = :contratoId
                 ORDER BY concepto_codigo
                """, new MapSqlParameterSource("contratoId", contratoId),
                (rs, i) -> new NovedadPermitida(rs.getString("concepto_codigo"), rs.getBigDecimal("tope_cantidad"),
                        rs.getBigDecimal("tope_valor")));
    }
}
