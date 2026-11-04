package com.cloud_technological.aura_pos.repositories.nomina_vinculaciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * Lo que el motor de liquidación lee de la vinculación (R3.2d) y de su cargo (F6
 * de docs/PLAN_CONCEPTOS_CARGOS_NOMINA.md): conceptos propios vigentes, novedades
 * permitidas con sus topes y topes de horas extra y recargos.
 *
 * <h2>Herencia viva</h2>
 * El cargo es la plantilla y la vinculación guarda excepciones. Por concepto:
 * <ul>
 *   <li>una fila de la vinculación reemplaza la del cargo;</li>
 *   <li>una fila de la vinculación con {@code excluido = TRUE} lo quita;</li>
 *   <li>si la vinculación no dice nada, aplica lo del cargo.</li>
 * </ul>
 * El cargo guarda el número visible del concepto; aquí se traduce a la clave
 * interna con la versión que ve la empresa (la clave es la misma en todas).
 */
@Repository
@RequiredArgsConstructor
public class VinculacionReglasQueryRepository {

    /**
     * Concepto propio vigente: de la vinculación o heredado del cargo con un valor
     * propio (bonificación fija, auxilio de rodamiento…).
     */
    public record ConceptoPropio(String codigo, BigDecimal cantidad, BigDecimal valor, BigDecimal porcentaje,
            String observacion, boolean delCargo) {
    }

    /** Novedad permitida con sus topes por período (null = sin tope). */
    public record NovedadPermitida(String codigo, BigDecimal topeCantidad, BigDecimal topeValor,
            boolean requiereAutorizacion) {

        public NovedadPermitida(String codigo, BigDecimal topeCantidad, BigDecimal topeValor) {
            this(codigo, topeCantidad, topeValor, false);
        }
    }

    /** Tope de horas del cargo para un concepto de hora extra o recargo (null = sin tope). */
    public record TopeHoras(String codigo, String tipoNombre, BigDecimal topeDia, BigDecimal topeSemana,
            BigDecimal topeMes) {
    }

    /** Concepto del cargo por número → clave interna, con la versión que ve la empresa del contrato. */
    private static final String CLAVE_DEL_NUMERO = """
            JOIN LATERAL (
                SELECT cn.codigo FROM concepto_nomina cn
                 WHERE cn.numero = %s
                   AND (cn.empresa_id = ctr.empresa_id OR cn.empresa_id IS NULL)
                 ORDER BY cn.empresa_id NULLS LAST, cn.vigente_desde DESC
                 LIMIT 1
            ) cn ON TRUE
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Conceptos propios vigentes en algún día del período: los de la vinculación
     * (no excluidos) más los del cargo que traen valor, porcentaje o cantidad y que
     * la vinculación no reemplaza ni excluye.
     *
     * @param desde inicio del período liquidado
     * @param hasta fin del período liquidado
     */
    public List<ConceptoPropio> conceptosVigentes(Long contratoId, LocalDate desde, LocalDate hasta) {
        return jdbc.query("""
                WITH ctr AS (SELECT id, cargo_id, empresa_id FROM contrato_laboral WHERE id = :contratoId),
                vinc AS (
                    SELECT concepto_codigo, cantidad, valor, porcentaje, observacion, excluido, fecha_desde
                      FROM vinculacion_concepto
                     WHERE contrato_id = :contratoId AND deleted_at IS NULL
                       AND fecha_desde <= :hasta AND (fecha_hasta IS NULL OR fecha_hasta >= :desde)
                )
                SELECT concepto_codigo, cantidad, valor, porcentaje, observacion, FALSE AS del_cargo
                  FROM vinc WHERE NOT excluido
                UNION ALL
                SELECT cn.codigo, cc.cantidad, cc.valor, cc.porcentaje, cc.observacion, TRUE
                  FROM ctr
                  JOIN nomina_cargo_concepto cc ON cc.cargo_id = ctr.cargo_id
                """ + CLAVE_DEL_NUMERO.formatted("cc.concepto_numero") + """
                 WHERE (cc.cantidad IS NOT NULL OR cc.valor IS NOT NULL OR cc.porcentaje IS NOT NULL)
                   AND NOT EXISTS (SELECT 1 FROM vinc v WHERE v.concepto_codigo = cn.codigo)
                 ORDER BY 1
                """, new MapSqlParameterSource("contratoId", contratoId).addValue("desde", desde)
                .addValue("hasta", hasta),
                (rs, i) -> new ConceptoPropio(rs.getString("concepto_codigo"), rs.getBigDecimal("cantidad"),
                        rs.getBigDecimal("valor"), rs.getBigDecimal("porcentaje"), rs.getString("observacion"),
                        rs.getBoolean("del_cargo")));
    }

    /**
     * Novedades permitidas efectivas: las de la vinculación (no excluidas) más las
     * del cargo que la vinculación no reemplaza ni excluye. Vacío = no restringe.
     */
    public List<NovedadPermitida> novedadesPermitidas(Long contratoId) {
        return jdbc.query("""
                WITH ctr AS (SELECT id, cargo_id, empresa_id FROM contrato_laboral WHERE id = :contratoId)
                SELECT concepto_codigo, tope_cantidad, tope_valor, requiere_autorizacion
                  FROM vinculacion_novedad_permitida
                 WHERE contrato_id = :contratoId AND NOT excluido
                UNION ALL
                SELECT cn.codigo, cv.tope_cantidad, cv.tope_valor, cv.requiere_autorizacion
                  FROM ctr
                  JOIN nomina_cargo_novedad cv ON cv.cargo_id = ctr.cargo_id
                """ + CLAVE_DEL_NUMERO.formatted("cv.concepto_numero") + """
                 WHERE NOT EXISTS (SELECT 1 FROM vinculacion_novedad_permitida v
                                    WHERE v.contrato_id = :contratoId AND v.concepto_codigo = cn.codigo)
                 ORDER BY 1
                """, new MapSqlParameterSource("contratoId", contratoId),
                (rs, i) -> new NovedadPermitida(rs.getString("concepto_codigo"), rs.getBigDecimal("tope_cantidad"),
                        rs.getBigDecimal("tope_valor"), rs.getBoolean("requiere_autorizacion")));
    }

    /**
     * Topes de horas extra y recargos del cargo del contrato, por concepto: cada
     * tope (tipo de hora 3–9) aplica a los conceptos de ese tipo que ve la empresa.
     */
    public List<TopeHoras> topesHoras(Long contratoId) {
        return jdbc.query("""
                WITH ctr AS (SELECT id, cargo_id, empresa_id FROM contrato_laboral WHERE id = :contratoId)
                SELECT DISTINCT cn.codigo, t.nombre AS tipo_nombre, h.tope_dia, h.tope_semana, h.tope_mes
                  FROM ctr
                  JOIN nomina_cargo_tope_horas h ON h.cargo_id = ctr.cargo_id
                  JOIN nomina_concepto_tipo t ON t.id = h.tipo_concepto_id
                  JOIN concepto_nomina cn ON cn.tipo_id = h.tipo_concepto_id
                                         AND (cn.empresa_id = ctr.empresa_id OR cn.empresa_id IS NULL)
                 ORDER BY cn.codigo
                """, new MapSqlParameterSource("contratoId", contratoId),
                (rs, i) -> new TopeHoras(rs.getString("codigo"), rs.getString("tipo_nombre"),
                        rs.getBigDecimal("tope_dia"), rs.getBigDecimal("tope_semana"), rs.getBigDecimal("tope_mes")));
    }

    /**
     * Claves de los conceptos que el cargo del contrato liquida (su pestaña
     * Conceptos), sin los que la vinculación excluye. Vacío = el cargo no tiene
     * plantilla de conceptos y el motor no restringe.
     */
    public Set<String> conceptosDelCargo(Long contratoId) {
        return new HashSet<>(jdbc.queryForList("""
                WITH ctr AS (SELECT id, cargo_id, empresa_id FROM contrato_laboral WHERE id = :contratoId)
                SELECT cn.codigo
                  FROM ctr
                  JOIN nomina_cargo_concepto cc ON cc.cargo_id = ctr.cargo_id
                """ + CLAVE_DEL_NUMERO.formatted("cc.concepto_numero"),
                new MapSqlParameterSource("contratoId", contratoId), String.class));
    }

    /**
     * Claves que la vinculación excluye de lo heredado (vigentes en el período).
     */
    public Set<String> conceptosExcluidos(Long contratoId, LocalDate desde, LocalDate hasta) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT concepto_codigo FROM vinculacion_concepto
                 WHERE contrato_id = :contratoId AND deleted_at IS NULL AND excluido
                   AND fecha_desde <= :hasta AND (fecha_hasta IS NULL OR fecha_hasta >= :desde)
                """, new MapSqlParameterSource("contratoId", contratoId).addValue("desde", desde)
                .addValue("hasta", hasta), String.class));
    }
}
