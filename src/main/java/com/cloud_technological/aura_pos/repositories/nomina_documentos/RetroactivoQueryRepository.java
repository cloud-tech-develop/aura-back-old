package com.cloud_technological.aura_pos.repositories.nomina_documentos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/** Lo que lee la liquidación de un comprobante de retroactivo (R8). */
@Repository
@RequiredArgsConstructor
public class RetroactivoQueryRepository {

    /** Nómina periódica ya confirmada de la vinculación: período y salario con que se pagó. */
    public record NominaPagada(Long id, LocalDate desde, LocalDate hasta, BigDecimal salarioBase,
            BigDecimal salarioProporcional) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Nóminas periódicas confirmadas (aprobadas o pagadas, no prenómina) de la
     * vinculación cuyo período se cruza con el rango, por fecha.
     */
    public List<NominaPagada> nominasPagadas(Long contratoId, LocalDate desde, LocalDate hasta) {
        return jdbc.query("""
                SELECT n.id, p.fecha_inicio, p.fecha_fin, n.salario_base, n.salario_proporcional
                  FROM nomina n
                  JOIN periodo_nomina p ON p.id = n.periodo_id
                  LEFT JOIN nomina_documento d ON d.id = n.documento_id
                  LEFT JOIN nomina_tipo_comprobante t ON t.id = d.tipo_comprobante_id
                 WHERE n.contrato_id = :contratoId AND n.es_prenomina = FALSE AND n.estado IN ('APROBADO', 'PAGADO')
                   AND (n.documento_id IS NULL OR t.clase = 'PERIODICO')
                   AND p.fecha_inicio <= :hasta AND p.fecha_fin >= :desde
                 ORDER BY p.fecha_inicio
                """, new MapSqlParameterSource("contratoId", contratoId).addValue("desde", desde)
                .addValue("hasta", hasta),
                (rs, i) -> new NominaPagada(rs.getLong("id"), rs.getObject("fecha_inicio", LocalDate.class),
                        rs.getObject("fecha_fin", LocalDate.class), rs.getBigDecimal("salario_base"),
                        rs.getBigDecimal("salario_proporcional")));
    }
}
