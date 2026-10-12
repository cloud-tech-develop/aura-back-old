package com.cloud_technological.aura_pos.repositories.nomina;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/** Lo que lee el asiento de nómina por concepto y por entidad (rediseño R4c). */
@Repository
@RequiredArgsConstructor
public class AsientoNominaQueryRepository {

    /** Línea del detalle de la nómina con su concepto y sus dimensiones. */
    public record LineaDetalle(String codigo, String nombre, String clase, BigDecimal valor, Long proyectoId,
            Long frenteId, Long centroCostoId) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    /** Detalle por concepto de la fila (vacío en nóminas viejas sin detalle). */
    public List<LineaDetalle> detalle(Long nominaId) {
        return jdbc.query("""
                SELECT c.codigo, c.nombre, c.clase, d.valor, d.proyecto_id, d.frente_id, d.centro_costo_id
                  FROM nomina_detalle d
                  JOIN concepto_nomina c ON c.id = d.concepto_id
                 WHERE d.nomina_id = :nominaId AND d.valor <> 0
                 ORDER BY c.orden, d.id
                """, new MapSqlParameterSource("nominaId", nominaId),
                (rs, i) -> new LineaDetalle(rs.getString("codigo"), rs.getString("nombre"), rs.getString("clase"),
                        rs.getBigDecimal("valor"), rs.getObject("proyecto_id", Long.class),
                        rs.getObject("frente_id", Long.class), rs.getObject("centro_costo_id", Long.class)));
    }

    /**
     * Entidades a las que estaba afiliado el contrato en la fecha.
     *
     * @return tipo (EPS, AFP, ARL, CCF) → tercero
     */
    public Map<String, Long> afiliaciones(Long contratoId, LocalDate fecha) {
        Map<String, Long> out = new HashMap<>();
        jdbc.query("""
                SELECT DISTINCT ON (tipo) tipo, tercero_id
                  FROM contrato_afiliacion
                 WHERE contrato_id = :contratoId AND fecha_desde <= :fecha
                   AND (fecha_hasta IS NULL OR fecha_hasta >= :fecha)
                 ORDER BY tipo, fecha_desde DESC
                """, new MapSqlParameterSource("contratoId", contratoId).addValue("fecha", fecha),
                rs -> {
                    out.put(rs.getString("tipo"), rs.getLong("tercero_id"));
                });
        return out;
    }

    /** Tercero del empleado (su identidad vive en {@code tercero}). */
    public Optional<Long> terceroEmpleado(Long empleadoId) {
        return jdbc.queryForList("SELECT tercero_id FROM empleados WHERE id = :id AND tercero_id IS NOT NULL",
                new MapSqlParameterSource("id", empleadoId), Long.class).stream().findFirst();
    }

    /** Fecha del documento de nómina (la del asiento). */
    public Optional<LocalDate> fechaDocumento(Long documentoId) {
        return jdbc.queryForList("SELECT fecha FROM nomina_documento WHERE id = :id",
                new MapSqlParameterSource("id", documentoId), LocalDate.class).stream().findFirst();
    }

    /** Código de la vinculación, para los avisos. */
    public String codigoContrato(Long contratoId) {
        return jdbc.queryForList("SELECT COALESCE(codigo, 'contrato ' || id) FROM contrato_laboral WHERE id = :id",
                new MapSqlParameterSource("id", contratoId), String.class).stream().findFirst().orElse("");
    }
}
