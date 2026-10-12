package com.cloud_technological.aura_pos.repositories.nomina_documentos;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/** Datos de la consignación de cesantías de una nómina de cesantías confirmada (R6). */
@Repository
@RequiredArgsConstructor
public class CesantiasConsignacionQueryRepository {

    /** Un empleado a consignar: su fondo (vacío si no tiene afiliación) y lo liquidado. */
    public record Consignacion(String fondo, String fondoNit, String tipoDocumento, String numeroDocumento,
            String nombres, String apellidos, BigDecimal salario, BigDecimal dias, BigDecimal valor) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Empleados de la nómina con su fondo de cesantías vigente al cierre del período,
     * ordenados por fondo y nombre.
     */
    public List<Consignacion> consignaciones(Long documentoId) {
        return jdbc.query("""
                SELECT COALESCE(NULLIF(f.razon_social, ''), TRIM(CONCAT(f.nombres, ' ', f.apellidos))) AS fondo,
                       f.numero_documento AS fondo_nit,
                       e.tipo_documento, e.numero_documento, e.nombres, e.apellidos,
                       n.salario_base, det.cantidad AS dias, det.valor
                  FROM nomina n
                  JOIN nomina_documento d ON d.id = n.documento_id
                  JOIN empleados e ON e.id = n.empleado_id
                  JOIN nomina_detalle det ON det.nomina_id = n.id
                  JOIN concepto_nomina c ON c.id = det.concepto_id AND c.codigo = 'CESANTIAS'
                  LEFT JOIN LATERAL (SELECT a.tercero_id FROM contrato_afiliacion a
                                      WHERE a.contrato_id = n.contrato_id AND a.tipo = 'CESANTIAS'
                                        AND a.fecha_desde <= d.fecha_hasta
                                        AND (a.fecha_hasta IS NULL OR a.fecha_hasta >= d.fecha_hasta)
                                      ORDER BY a.fecha_desde DESC LIMIT 1) af ON TRUE
                  LEFT JOIN tercero f ON f.id = af.tercero_id
                 WHERE n.documento_id = :documentoId AND n.estado <> 'ANULADO'
                 ORDER BY fondo NULLS LAST, e.apellidos, e.nombres
                """, new MapSqlParameterSource("documentoId", documentoId),
                (rs, i) -> new Consignacion(rs.getString("fondo"), rs.getString("fondo_nit"),
                        rs.getString("tipo_documento"), rs.getString("numero_documento"), rs.getString("nombres"),
                        rs.getString("apellidos"), rs.getBigDecimal("salario_base"), rs.getBigDecimal("dias"),
                        rs.getBigDecimal("valor")));
    }
}
