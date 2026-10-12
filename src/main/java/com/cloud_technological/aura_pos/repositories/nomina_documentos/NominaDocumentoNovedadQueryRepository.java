package com.cloud_technological.aura_pos.repositories.nomina_documentos;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.nomina.documento.NovedadDocumentoDto;

import lombok.RequiredArgsConstructor;

/** Consultas de la pestaña Novedades de la prenómina. */
@Repository
@RequiredArgsConstructor
public class NominaDocumentoNovedadQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** Novedades de los empleados del documento, por empleado y fecha. */
    public List<NovedadDocumentoDto> novedades(Long documentoId) {
        return jdbc.query("""
                SELECT v.id, v.nomina_id, CONCAT(e.nombres, ' ', e.apellidos) AS empleado_nombre, e.numero_documento,
                       v.tipo, v.subtipo, v.descripcion, v.fecha_inicio, v.fecha_fin, v.dias, v.cantidad,
                       v.valor_unitario, v.valor_total, v.es_deduccion, v.origen
                  FROM nomina_novedad v
                  JOIN nomina n ON n.id = v.nomina_id
                  JOIN empleados e ON e.id = n.empleado_id
                 WHERE n.documento_id = :documentoId
                 ORDER BY empleado_nombre, v.fecha_inicio NULLS LAST, v.id
                """, new MapSqlParameterSource("documentoId", documentoId), this::mapear);
    }

    /** ¿La fila es de ese documento? */
    public boolean filaDelDocumento(Long documentoId, Long nominaId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM nomina WHERE id = :nominaId AND documento_id = :documentoId
                """, new MapSqlParameterSource("nominaId", nominaId).addValue("documentoId", documentoId),
                Integer.class);
        return n != null && n > 0;
    }

    /**
     * Fila del documento a la que pertenece la novedad.
     *
     * @return el id de la fila, o null si la novedad no es de ese documento
     */
    public Long filaDeLaNovedad(Long documentoId, Long novedadId) {
        return jdbc.queryForList("""
                SELECT v.nomina_id FROM nomina_novedad v JOIN nomina n ON n.id = v.nomina_id
                 WHERE v.id = :novedadId AND n.documento_id = :documentoId
                """, new MapSqlParameterSource("novedadId", novedadId).addValue("documentoId", documentoId),
                Long.class).stream().findFirst().orElse(null);
    }

    /** La fila vuelve a "sin liquidar": cambió una novedad y hay que reliquidarla. */
    public void marcarPendiente(Long nominaId) {
        jdbc.update("""
                UPDATE nomina SET liquidada_at = NULL, error_liquidacion = NULL, updated_at = now() WHERE id = :id
                """, new MapSqlParameterSource("id", nominaId));
    }

    private NovedadDocumentoDto mapear(ResultSet rs, int fila) throws SQLException {
        NovedadDocumentoDto dto = new NovedadDocumentoDto();
        dto.setId(rs.getLong("id"));
        dto.setNominaId(rs.getLong("nomina_id"));
        dto.setEmpleadoNombre(rs.getString("empleado_nombre"));
        dto.setEmpleadoDocumento(rs.getString("numero_documento"));
        dto.setTipo(rs.getString("tipo"));
        dto.setSubtipo(rs.getString("subtipo"));
        dto.setDescripcion(rs.getString("descripcion"));
        dto.setFechaInicio(rs.getObject("fecha_inicio", LocalDate.class));
        dto.setFechaFin(rs.getObject("fecha_fin", LocalDate.class));
        dto.setDias(rs.getObject("dias", Integer.class));
        dto.setCantidad(rs.getBigDecimal("cantidad"));
        dto.setValorUnitario(rs.getBigDecimal("valor_unitario"));
        dto.setValorTotal(rs.getBigDecimal("valor_total"));
        dto.setEsDeduccion(rs.getBoolean("es_deduccion"));
        dto.setOrigen(rs.getString("origen"));
        return dto;
    }
}
