package com.cloud_technological.aura_pos.repositories.nomina_documentos;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/** Lo que lee la liquidación de prima, cesantías e intereses por comprobante (R6). */
@Repository
@RequiredArgsConstructor
public class NominaPrestacionDocumentoQueryRepository {

    /** Encabezado mínimo del documento para liquidar una prestación. */
    public record Documento(Long id, String claseComprobante, LocalDate fechaDesde, LocalDate fechaHasta) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    /** Clase del comprobante y fechas del documento. */
    public Optional<Documento> documento(Long documentoId) {
        return jdbc.query("""
                SELECT d.id, t.clase, d.fecha_desde, d.fecha_hasta
                  FROM nomina_documento d JOIN nomina_tipo_comprobante t ON t.id = d.tipo_comprobante_id
                 WHERE d.id = :id
                """, new MapSqlParameterSource("id", documentoId),
                (rs, i) -> new Documento(rs.getLong("id"), rs.getString("clase"),
                        rs.getObject("fecha_desde", LocalDate.class), rs.getObject("fecha_hasta", LocalDate.class)))
                .stream().findFirst();
    }

    /**
     * Hasta qué fecha ya se le pagó la prestación al contrato en una nómina por
     * documento confirmada de esa clase (sin contar el documento dado).
     */
    public Optional<LocalDate> pagadoHastaEnDocumentos(Long contratoId, String claseComprobante, Long excluirDocumentoId) {
        return jdbc.queryForList("""
                SELECT MAX(d.fecha_hasta)
                  FROM nomina n
                  JOIN nomina_documento d ON d.id = n.documento_id
                  JOIN nomina_tipo_comprobante t ON t.id = d.tipo_comprobante_id
                 WHERE n.contrato_id = :contratoId AND n.es_prenomina = FALSE AND n.estado <> 'ANULADO'
                   AND d.clase_documento = 'NOMINA' AND d.estado = 'CONFIRMADO' AND t.clase = :clase
                   AND d.id <> :excluir
                """, new MapSqlParameterSource("contratoId", contratoId).addValue("clase", claseComprobante)
                .addValue("excluir", excluirDocumentoId), LocalDate.class)
                .stream().filter(Objects::nonNull).findFirst();
    }
}
