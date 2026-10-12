package com.cloud_technological.aura_pos.repositories.nomina_documentos;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.nomina.documento.CandidatoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.EmpleadoDocumentoDto;
import com.cloud_technological.aura_pos.services.nomina.documento.TipoListaNomina;

import lombok.RequiredArgsConstructor;

/** Consultas de la pestaña Empleados de una prenómina: candidatos, filas y quitar. */
@Repository
@RequiredArgsConstructor
public class NominaDocumentoEmpleadoQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Vinculaciones vigentes en el período (activas, sin prestación de servicios) que
     * caen en el tipo de lista, cada una con la causa por la que NO entraría:
     * <ol>
     *   <li>ya está en este documento;</li>
     *   <li>su tipo de vinculación no lo admite el comprobante (si el comprobante restringe);</li>
     *   <li>ya está en otra prenómina no anulada del mismo tipo con fechas que se cruzan;</li>
     *   <li>en comprobantes periódicos: ya se liquidó por el flujo viejo de períodos.</li>
     * </ol>
     *
     * @param documentoId  prenómina destino
     * @param tipoComprobanteId su tipo de comprobante
     * @param periodico    ¿el comprobante es de clase PERIODICO?
     * @param tipoLista    cómo se eligen las vinculaciones
     * @param ids          terceros, contratos, grupos o tipos (ignorado en TODOS)
     * @return candidatos ordenados por nombre; {@code motivo} null = se puede agregar
     */
    public List<CandidatoNominaDto> candidatos(Integer empresaId, Long documentoId, Long tipoComprobanteId,
            boolean periodico, LocalDate desde, LocalDate hasta, TipoListaNomina tipoLista, List<Long> ids) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id AS contrato_id, c.empleado_id, c.codigo, c.salario_base, e.numero_documento,
                       CONCAT(e.nombres, ' ', e.apellidos) AS empleado_nombre,
                       CASE
                         WHEN EXISTS (SELECT 1 FROM nomina n
                                       WHERE n.documento_id = :documentoId AND n.contrato_id = c.id)
                           THEN 'Ya está en esta prenómina'
                         WHEN EXISTS (SELECT 1 FROM nomina_tipo_comprobante_vinculacion r
                                       WHERE r.tipo_comprobante_id = :tipoComprobanteId)
                              AND NOT EXISTS (SELECT 1 FROM nomina_tipo_comprobante_vinculacion r
                                               WHERE r.tipo_comprobante_id = :tipoComprobanteId
                                                 AND r.tipo_vinculacion_id = c.tipo_vinculacion_id)
                           THEN 'El comprobante no admite su tipo de vinculación'
                         WHEN EXISTS (SELECT 1 FROM nomina n
                                        JOIN nomina_documento d ON d.id = n.documento_id
                                       WHERE n.contrato_id = c.id AND d.id <> :documentoId
                                         AND d.estado <> 'ANULADO' AND d.clase_documento = 'PRENOMINA'
                                         AND d.tipo_comprobante_id = :tipoComprobanteId
                                         AND d.fecha_desde <= :hasta AND d.fecha_hasta >= :desde)
                           THEN 'Ya está en otra prenómina del mismo tipo y período'
                         WHEN CAST(:periodico AS BOOLEAN) AND EXISTS (SELECT 1 FROM nomina n
                                        JOIN periodo_nomina p ON p.id = n.periodo_id
                                       WHERE n.contrato_id = c.id AND n.documento_id IS NULL
                                         AND n.estado <> 'ANULADO' AND p.estado <> 'ANULADO'
                                         AND p.fecha_inicio <= :hasta AND p.fecha_fin >= :desde)
                           THEN 'Ya se liquidó en Liquidación de nómina (por períodos)'
                       END AS motivo
                  FROM contrato_laboral c
                  JOIN empleados e ON e.id = c.empleado_id
                 WHERE c.empresa_id = :empresaId AND c.deleted_at IS NULL
                   AND c.estado = 'ACTIVO' AND c.tipo_contrato <> 'PRESTACION_SERVICIOS'
                   AND c.fecha_inicio <= :hasta AND (c.fecha_fin IS NULL OR c.fecha_fin >= :desde)
                """);
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId)
                .addValue("documentoId", documentoId).addValue("tipoComprobanteId", tipoComprobanteId)
                .addValue("periodico", periodico).addValue("desde", desde).addValue("hasta", hasta);
        switch (tipoLista) {
            case EMPLEADO -> sql.append(" AND e.tercero_id IN (:ids) ");
            case VINCULACION -> sql.append(" AND c.id IN (:ids) ");
            case GRUPO -> sql.append(" AND c.grupo_nomina_id IN (:ids) ");
            case TIPO_VINCULACION -> sql.append(" AND c.tipo_vinculacion_id IN (:ids) ");
            case TODOS -> { }
        }
        if (tipoLista.exigeIds()) params.addValue("ids", ids);
        sql.append(" ORDER BY empleado_nombre, c.codigo ");
        return jdbc.query(sql.toString(), params, this::mapearCandidato);
    }

    /**
     * Empleados (filas) de un documento con su vinculación, cargo, salario y
     * distribución por centro de costo.
     */
    public List<EmpleadoDocumentoDto> empleados(Long documentoId) {
        return jdbc.query("""
                SELECT n.id AS nomina_id, n.contrato_id, c.codigo, n.estado, c.salario_base,
                       CONCAT(e.nombres, ' ', e.apellidos) AS empleado_nombre, e.numero_documento,
                       COALESCE(k.nombre, c.cargo) AS cargo, tv.nombre AS tipo_vinculacion, g.nombre AS grupo,
                       (SELECT string_agg(COALESCE(cc.nombre, 'Centro ' || x.centro_costo_id)
                                          || ' ' || TRIM(TO_CHAR(x.porcentaje, 'FM990.##')) || ' %', ', '
                                          ORDER BY x.porcentaje DESC)
                          FROM contrato_centro_costo x
                          LEFT JOIN centros_costos cc ON cc.id = x.centro_costo_id
                         WHERE x.contrato_id = c.id) AS centros_costo
                  FROM nomina n
                  JOIN contrato_laboral c ON c.id = n.contrato_id
                  JOIN empleados e ON e.id = n.empleado_id
                  LEFT JOIN nomina_cargo k ON k.id = c.cargo_id
                  LEFT JOIN nomina_tipo_vinculacion tv ON tv.id = c.tipo_vinculacion_id
                  LEFT JOIN nomina_catalogo g ON g.id = c.grupo_nomina_id
                 WHERE n.documento_id = :documentoId
                 ORDER BY empleado_nombre, c.codigo
                """, new MapSqlParameterSource("documentoId", documentoId), this::mapearEmpleado);
    }

    /**
     * Borra filas del documento (el detalle, las novedades y los descuentos de embargo
     * se van en cascada). Solo toca filas de ese documento.
     *
     * @return cuántas filas se borraron
     */
    public int quitar(Long documentoId, List<Long> nominaIds) {
        return jdbc.update("DELETE FROM nomina WHERE documento_id = :documentoId AND id IN (:ids)",
                new MapSqlParameterSource("documentoId", documentoId).addValue("ids", nominaIds));
    }

    /**
     * Documento no anulado donde ya está el contrato con fechas que se cruzan con el
     * rango. Lo usa el flujo viejo por período para no liquidar dos veces.
     *
     * @return el número del documento (PRE-NOM-00001…), o vacío si no está en ninguno
     */
    public Optional<String> documentoDelContrato(Long contratoId, LocalDate desde, LocalDate hasta) {
        return jdbc.queryForList("""
                SELECT CASE WHEN d.clase_documento = 'PRENOMINA' THEN 'PRE-' ELSE '' END
                       || t.prefijo || '-' || LPAD(d.numero::TEXT, 5, '0')
                  FROM nomina n
                  JOIN nomina_documento d ON d.id = n.documento_id
                  JOIN nomina_tipo_comprobante t ON t.id = d.tipo_comprobante_id
                 WHERE n.contrato_id = :contratoId AND d.estado <> 'ANULADO'
                   AND d.fecha_desde <= :hasta AND d.fecha_hasta >= :desde
                 ORDER BY d.id LIMIT 1
                """, new MapSqlParameterSource("contratoId", contratoId).addValue("desde", desde)
                .addValue("hasta", hasta), String.class).stream().findFirst();
    }

    private CandidatoNominaDto mapearCandidato(ResultSet rs, int fila) throws SQLException {
        CandidatoNominaDto dto = new CandidatoNominaDto();
        dto.setContratoId(rs.getLong("contrato_id"));
        dto.setEmpleadoId(rs.getLong("empleado_id"));
        dto.setCodigoVinculacion(rs.getString("codigo"));
        dto.setEmpleadoNombre(rs.getString("empleado_nombre"));
        dto.setEmpleadoDocumento(rs.getString("numero_documento"));
        dto.setSalarioBase(rs.getBigDecimal("salario_base"));
        dto.setMotivo(rs.getString("motivo"));
        return dto;
    }

    private EmpleadoDocumentoDto mapearEmpleado(ResultSet rs, int fila) throws SQLException {
        EmpleadoDocumentoDto dto = new EmpleadoDocumentoDto();
        dto.setNominaId(rs.getLong("nomina_id"));
        dto.setContratoId(rs.getLong("contrato_id"));
        dto.setCodigoVinculacion(rs.getString("codigo"));
        dto.setEmpleadoNombre(rs.getString("empleado_nombre"));
        dto.setEmpleadoDocumento(rs.getString("numero_documento"));
        dto.setCargo(rs.getString("cargo"));
        dto.setTipoVinculacion(rs.getString("tipo_vinculacion"));
        dto.setGrupoNomina(rs.getString("grupo"));
        dto.setSalarioBase(rs.getBigDecimal("salario_base"));
        dto.setCentrosCosto(rs.getString("centros_costo"));
        dto.setEstado(rs.getString("estado"));
        return dto;
    }
}
