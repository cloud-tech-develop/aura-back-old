package com.cloud_technological.aura_pos.repositories.nomina_documentos;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.nomina.documento.DetalleLiquidacionDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.LiquidacionEmpleadoDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.ProcesoDocumentoDto;
import com.cloud_technological.aura_pos.entity.ProcesoNominaEntity;

import lombok.RequiredArgsConstructor;

/** Consultas de la liquidación de una prenómina: filas, totales, detalle y corridas. */
@Repository
@RequiredArgsConstructor
public class NominaDocumentoLiquidacionQueryRepository {

    /** Fila a liquidar: id y nombre del empleado (para los errores del proceso). */
    public record FilaALiquidar(Long nominaId, String empleado) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Filas del documento que se van a liquidar.
     *
     * @param nominaIds      vacío = todas (o las pendientes)
     * @param soloPendientes con {@code nominaIds} vacío: solo las no liquidadas
     */
    public List<FilaALiquidar> filasALiquidar(Long documentoId, List<Long> nominaIds, boolean soloPendientes) {
        StringBuilder sql = new StringBuilder("""
                SELECT n.id, CONCAT(e.nombres, ' ', e.apellidos) AS empleado
                  FROM nomina n
                  JOIN empleados e ON e.id = n.empleado_id
                 WHERE n.documento_id = :documentoId
                """);
        MapSqlParameterSource params = new MapSqlParameterSource("documentoId", documentoId);
        if (!nominaIds.isEmpty()) {
            sql.append(" AND n.id IN (:ids) ");
            params.addValue("ids", nominaIds);
        } else if (soloPendientes) {
            sql.append(" AND n.liquidada_at IS NULL ");
        }
        sql.append(" ORDER BY empleado, n.id ");
        return jdbc.query(sql.toString(), params,
                (rs, i) -> new FilaALiquidar(rs.getLong("id"), rs.getString("empleado")));
    }

    /** Deja la fila como no liquidada con la causa (corta a 500 caracteres). */
    public void marcarError(Long nominaId, String causa) {
        String texto = causa == null ? "Error sin detalle" : causa.length() > 500 ? causa.substring(0, 500) : causa;
        jdbc.update("""
                UPDATE nomina SET liquidada_at = NULL, error_liquidacion = :causa, updated_at = now()
                 WHERE id = :id
                """, new MapSqlParameterSource("id", nominaId).addValue("causa", texto));
    }

    /** Recalcula los totales del encabezado con las filas liquidadas. */
    public void recalcularTotales(Long documentoId) {
        jdbc.update("""
                UPDATE nomina_documento d SET
                       total_devengado = COALESCE(t.devengado, 0),
                       total_deducido  = COALESCE(t.deducido, 0),
                       total_neto      = COALESCE(t.neto, 0),
                       total_aportes   = COALESCE(t.aportes, 0),
                       updated_at      = now()
                  FROM (SELECT SUM(n.total_devengado) AS devengado, SUM(n.total_deducciones) AS deducido,
                               SUM(n.neto_pagar) AS neto,
                               SUM(n.aporte_salud + n.aporte_pension + n.aporte_arl
                                   + n.aporte_caja + n.aporte_icbf + n.aporte_sena) AS aportes
                          FROM nomina n
                         WHERE n.documento_id = :documentoId AND n.liquidada_at IS NOT NULL) t
                 WHERE d.id = :documentoId
                """, new MapSqlParameterSource("documentoId", documentoId));
    }

    /**
     * Filas de la pestaña Liquidación: solo las que ya pasaron por una liquidación
     * (liquidadas o con error). Las recién agregadas no aparecen hasta liquidar.
     */
    public List<LiquidacionEmpleadoDto> liquidacion(Long documentoId) {
        return jdbc.query("""
                SELECT n.id, c.codigo, CONCAT(e.nombres, ' ', e.apellidos) AS empleado_nombre, e.numero_documento,
                       n.dias_trabajados, n.total_devengado, n.total_deducciones, n.neto_pagar,
                       n.aporte_salud + n.aporte_pension + n.aporte_arl
                         + n.aporte_caja + n.aporte_icbf + n.aporte_sena AS aportes,
                       n.liquidada_at, n.error_liquidacion, n.estado, n.fecha_pago, n.medio_pago
                  FROM nomina n
                  JOIN contrato_laboral c ON c.id = n.contrato_id
                  JOIN empleados e ON e.id = n.empleado_id
                 WHERE n.documento_id = :documentoId
                   AND (n.liquidada_at IS NOT NULL OR n.error_liquidacion IS NOT NULL)
                 ORDER BY empleado_nombre, c.codigo
                """, new MapSqlParameterSource("documentoId", documentoId), this::mapearLiquidacion);
    }

    /**
     * Detalle por concepto de una fila del documento.
     *
     * @return vacío si la fila no es del documento o aún no se liquida
     */
    public List<DetalleLiquidacionDto> detalle(Long documentoId, Long nominaId) {
        return jdbc.query("""
                SELECT d.id, c.codigo, c.nombre, c.clase, d.cantidad, d.base, d.porcentaje, d.valor,
                       d.traza::TEXT AS traza
                  FROM nomina_detalle d
                  JOIN nomina n ON n.id = d.nomina_id
                  JOIN concepto_nomina c ON c.id = d.concepto_id
                 WHERE d.nomina_id = :nominaId AND n.documento_id = :documentoId
                 ORDER BY CASE c.clase WHEN 'DEVENGADO' THEN 1 WHEN 'DEDUCCION' THEN 2
                                       WHEN 'APORTE_EMPLEADOR' THEN 3 ELSE 4 END, c.orden, d.id
                """, new MapSqlParameterSource("nominaId", nominaId).addValue("documentoId", documentoId),
                this::mapearDetalle);
    }

    /** Corridas de liquidación del documento, de la más reciente a la más antigua. */
    public List<ProcesoDocumentoDto> procesos(Integer empresaId, Long documentoId) {
        return jdbc.query("""
                SELECT p.id, p.estado, p.progreso, p.mensaje, p.total_items, p.items_ok, p.items_error,
                       p.errores::TEXT AS errores, u.username, p.iniciado_at, p.finalizado_at
                  FROM proceso_nomina p
                  LEFT JOIN usuario u ON u.id = p.usuario_id
                 WHERE p.empresa_id = :empresaId AND p.referencia_id = :documentoId AND p.tipo = :tipo
                 ORDER BY p.iniciado_at DESC, p.id DESC
                """, new MapSqlParameterSource("empresaId", empresaId).addValue("documentoId", documentoId)
                .addValue("tipo", ProcesoNominaEntity.Tipo.LIQUIDACION_DOCUMENTO), this::mapearProceso);
    }

    private LiquidacionEmpleadoDto mapearLiquidacion(ResultSet rs, int fila) throws SQLException {
        LiquidacionEmpleadoDto dto = new LiquidacionEmpleadoDto();
        dto.setNominaId(rs.getLong("id"));
        dto.setCodigoVinculacion(rs.getString("codigo"));
        dto.setEmpleadoNombre(rs.getString("empleado_nombre"));
        dto.setEmpleadoDocumento(rs.getString("numero_documento"));
        dto.setDiasTrabajados(rs.getObject("dias_trabajados", Integer.class));
        dto.setDevengado(rs.getBigDecimal("total_devengado"));
        dto.setDeducido(rs.getBigDecimal("total_deducciones"));
        dto.setNeto(rs.getBigDecimal("neto_pagar"));
        dto.setAportes(rs.getBigDecimal("aportes"));
        dto.setLiquidadaAt(rs.getObject("liquidada_at", LocalDateTime.class));
        dto.setLiquidada(dto.getLiquidadaAt() != null);
        dto.setError(rs.getString("error_liquidacion"));
        dto.setEstado(rs.getString("estado"));
        dto.setFechaPago(rs.getObject("fecha_pago", LocalDateTime.class));
        dto.setMedioPago(rs.getString("medio_pago"));
        return dto;
    }

    private DetalleLiquidacionDto mapearDetalle(ResultSet rs, int fila) throws SQLException {
        DetalleLiquidacionDto dto = new DetalleLiquidacionDto();
        dto.setId(rs.getLong("id"));
        dto.setConceptoCodigo(rs.getString("codigo"));
        dto.setConceptoNombre(rs.getString("nombre"));
        dto.setClase(rs.getString("clase"));
        dto.setCantidad(rs.getBigDecimal("cantidad"));
        dto.setBase(rs.getBigDecimal("base"));
        dto.setPorcentaje(rs.getBigDecimal("porcentaje"));
        dto.setValor(rs.getBigDecimal("valor"));
        dto.setTraza(rs.getString("traza"));
        return dto;
    }

    private ProcesoDocumentoDto mapearProceso(ResultSet rs, int fila) throws SQLException {
        ProcesoDocumentoDto dto = new ProcesoDocumentoDto();
        dto.setId(rs.getLong("id"));
        dto.setEstado(rs.getString("estado"));
        dto.setProgreso(rs.getInt("progreso"));
        dto.setMensaje(rs.getString("mensaje"));
        dto.setTotalItems(rs.getInt("total_items"));
        dto.setItemsOk(rs.getInt("items_ok"));
        dto.setItemsError(rs.getInt("items_error"));
        dto.setErrores(rs.getString("errores"));
        dto.setUsuario(rs.getString("username"));
        dto.setIniciadoAt(rs.getObject("iniciado_at", LocalDateTime.class));
        dto.setFinalizadoAt(rs.getObject("finalizado_at", LocalDateTime.class));
        return dto;
    }
}
