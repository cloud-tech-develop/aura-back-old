package com.cloud_technological.aura_pos.repositories.nomina_vinculaciones;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionDetalleDtos.CentroCostoVinculacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionDetalleDtos.NovedadPermitidaDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionDetalleDtos.VinculacionConceptoDto;

import lombok.RequiredArgsConstructor;

/**
 * Consultas de las pestañas de la vinculación: centros de costo, conceptos
 * propios y novedades permitidas, con los nombres resueltos.
 */
@Repository
@RequiredArgsConstructor
public class VinculacionDetalleQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Versión vigente del concepto para la empresa: la propia antes que la de ley y
     * la de vigencia más reciente. Se usa como LATERAL con {@code x.concepto_codigo}.
     */
    private static final String CONCEPTO_VIGENTE = """
            LEFT JOIN LATERAL (
                SELECT cn.nombre, cn.clase FROM concepto_nomina cn
                 WHERE cn.codigo = x.concepto_codigo AND cn.activo = TRUE
                   AND (cn.empresa_id = :empresaId OR cn.empresa_id IS NULL)
                 ORDER BY cn.empresa_id NULLS LAST, cn.vigente_desde DESC
                 LIMIT 1
            ) cn ON TRUE
            """;

    // ── Centros de costo ─────────────────────────────────────────────────────

    /** Distribución del costo laboral de la vinculación por centro de costo. */
    public List<CentroCostoVinculacionDto> centrosCosto(Long contratoId) {
        return jdbc.query("""
                SELECT x.centro_costo_id, cc.codigo, cc.nombre, x.porcentaje
                  FROM contrato_centro_costo x
                  LEFT JOIN centros_costos cc ON cc.id = x.centro_costo_id
                 WHERE x.contrato_id = :id
                 ORDER BY x.porcentaje DESC, cc.codigo
                """, new MapSqlParameterSource("id", contratoId), (rs, i) -> {
            CentroCostoVinculacionDto d = new CentroCostoVinculacionDto();
            d.setCentroCostoId(rs.getLong("centro_costo_id"));
            d.setCentroCostoCodigo(rs.getString("codigo"));
            d.setCentroCostoNombre(rs.getString("nombre"));
            d.setPorcentaje(rs.getBigDecimal("porcentaje"));
            return d;
        });
    }

    /** ¿El centro de costo es de la empresa, está activo y admite movimientos? */
    public boolean centroCostoValido(Long centroCostoId, Integer empresaId) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM centros_costos
                 WHERE id = :id AND empresa_id = :empresaId AND deleted_at IS NULL
                   AND COALESCE(activo, TRUE) AND COALESCE(permite_movimientos, TRUE)
                """, new MapSqlParameterSource("id", centroCostoId).addValue("empresaId", empresaId), Long.class);
        return n != null && n > 0;
    }

    /** Reemplaza la distribución por la lista dada (ya validada). */
    public void reemplazarCentrosCosto(Long contratoId, List<CentroCostoVinculacionDto> centros) {
        jdbc.update("DELETE FROM contrato_centro_costo WHERE contrato_id = :id",
                new MapSqlParameterSource("id", contratoId));
        for (CentroCostoVinculacionDto c : centros)
            jdbc.update("""
                    INSERT INTO contrato_centro_costo (contrato_id, centro_costo_id, porcentaje)
                    VALUES (:id, :centro, :porcentaje)
                    """, new MapSqlParameterSource("id", contratoId).addValue("centro", c.getCentroCostoId())
                    .addValue("porcentaje", c.getPorcentaje()));
    }

    // ── Conceptos ────────────────────────────────────────────────────────────

    /** ¿Existe el concepto (propio o de ley) y está activo para la empresa? */
    public boolean conceptoExiste(String codigo, Integer empresaId) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM concepto_nomina
                 WHERE codigo = :codigo AND activo = TRUE AND (empresa_id = :empresaId OR empresa_id IS NULL)
                """, new MapSqlParameterSource("codigo", codigo).addValue("empresaId", empresaId), Long.class);
        return n != null && n > 0;
    }

    /** Conceptos propios de la vinculación con su nombre y clase vigentes. */
    public List<VinculacionConceptoDto> conceptos(Long contratoId, Integer empresaId) {
        return jdbc.query("""
                SELECT x.*, cn.nombre AS concepto_nombre, cn.clase
                  FROM vinculacion_concepto x
                """ + CONCEPTO_VIGENTE + """
                 WHERE x.contrato_id = :id AND x.deleted_at IS NULL
                 ORDER BY x.fecha_desde DESC, x.concepto_codigo
                """, new MapSqlParameterSource("id", contratoId).addValue("empresaId", empresaId), (rs, i) -> {
            VinculacionConceptoDto d = new VinculacionConceptoDto();
            d.setId(rs.getLong("id"));
            d.setConceptoCodigo(rs.getString("concepto_codigo"));
            d.setConceptoNombre(rs.getString("concepto_nombre"));
            d.setClase(rs.getString("clase"));
            d.setCantidad(rs.getBigDecimal("cantidad"));
            d.setValor(rs.getBigDecimal("valor"));
            d.setPorcentaje(rs.getBigDecimal("porcentaje"));
            d.setFechaDesde(rs.getObject("fecha_desde", LocalDate.class));
            d.setFechaHasta(rs.getObject("fecha_hasta", LocalDate.class));
            d.setObservacion(rs.getString("observacion"));
            return d;
        });
    }

    /**
     * ¿El mismo concepto ya está vigente en la vinculación en un rango que se cruza?
     *
     * @param hasta     fin del rango nuevo (null = abierto)
     * @param excluirId registro que se edita (null al crear)
     */
    public boolean conceptoSeCruza(Long contratoId, String codigo, LocalDate desde, LocalDate hasta, Long excluirId) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM vinculacion_concepto
                 WHERE contrato_id = :id AND concepto_codigo = :codigo AND deleted_at IS NULL
                   AND (CAST(:excluir AS BIGINT) IS NULL OR id <> :excluir)
                   AND fecha_desde <= COALESCE(CAST(:hasta AS DATE), DATE '9999-12-31')
                   AND COALESCE(fecha_hasta, DATE '9999-12-31') >= :desde
                """, new MapSqlParameterSource("id", contratoId).addValue("codigo", codigo).addValue("desde", desde)
                .addValue("hasta", hasta).addValue("excluir", excluirId), Long.class);
        return n != null && n > 0;
    }

    // ── Novedades permitidas ─────────────────────────────────────────────────

    /** Novedades permitidas de la vinculación con el nombre del concepto. */
    public List<NovedadPermitidaDto> novedadesPermitidas(Long contratoId, Integer empresaId) {
        return jdbc.query("""
                SELECT x.*, cn.nombre AS concepto_nombre, cn.clase
                  FROM vinculacion_novedad_permitida x
                """ + CONCEPTO_VIGENTE + """
                 WHERE x.contrato_id = :id
                 ORDER BY x.concepto_codigo
                """, new MapSqlParameterSource("id", contratoId).addValue("empresaId", empresaId), (rs, i) -> {
            NovedadPermitidaDto d = new NovedadPermitidaDto();
            d.setId(rs.getLong("id"));
            d.setConceptoCodigo(rs.getString("concepto_codigo"));
            d.setConceptoNombre(rs.getString("concepto_nombre"));
            d.setClase(rs.getString("clase"));
            d.setTopeCantidad(rs.getBigDecimal("tope_cantidad"));
            d.setTopeValor(rs.getBigDecimal("tope_valor"));
            d.setRequiereAutorizacion(rs.getBoolean("requiere_autorizacion"));
            return d;
        });
    }

    /** ¿El concepto ya está permitido en la vinculación? (sin contar el que se edita). */
    public boolean novedadDuplicada(Long contratoId, String codigo, Long excluirId) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM vinculacion_novedad_permitida
                 WHERE contrato_id = :id AND concepto_codigo = :codigo
                   AND (CAST(:excluir AS BIGINT) IS NULL OR id <> :excluir)
                """, new MapSqlParameterSource("id", contratoId).addValue("codigo", codigo)
                .addValue("excluir", excluirId), Long.class);
        return n != null && n > 0;
    }
}
