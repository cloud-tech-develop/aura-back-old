package com.cloud_technological.aura_pos.repositories.nomina_retiros;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.nomina.retiro.FiltroRetiroDto;
import com.cloud_technological.aura_pos.dto.nomina.retiro.RetiroDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

import lombok.RequiredArgsConstructor;

/** Consultas de retiros (listado con su estado de liquidación y validaciones). */
@Repository
@RequiredArgsConstructor
public class NominaRetiroQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Columnas del retiro. El estado sale de la liquidación: sin prenómina (o anulada)
     * = PENDIENTE; con prenómina pero sin nómina confirmada = EN_LIQUIDACION; nómina
     * confirmada = LIQUIDADO; todos sus empleados pagados = PAGADO.
     */
    private static final String SELECT = """
            SELECT * FROM (
              SELECT r.id, r.contrato_id, c.codigo, r.empleado_id,
                     CONCAT(e.nombres, ' ', e.apellidos) AS empleado_nombre, e.numero_documento,
                     COALESCE(k.nombre, c.cargo) AS cargo, c.tipo_contrato, c.fecha_inicio,
                     r.fecha_retiro, r.motivo_id, m.nombre AS motivo, r.indemniza, r.fecha_fin_pactada,
                     r.cesantias_corte_hasta, r.observacion, r.created_at,
                     pre.id AS documento_id,
                     'PRE-' || tp.prefijo || '-' || LPAD(pre.numero::TEXT, 5, '0') AS documento_numero,
                     nom.id AS nomina_id,
                     tn.prefijo || '-' || LPAD(nom.numero::TEXT, 5, '0') AS nomina_numero,
                     CASE
                       WHEN r.estado = 'ANULADO' THEN 'ANULADO'
                       WHEN pre.id IS NULL THEN 'PENDIENTE'
                       WHEN nom.id IS NOT NULL AND nom.estado = 'CONFIRMADO'
                            AND NOT EXISTS (SELECT 1 FROM nomina f WHERE f.documento_id = nom.id
                                             AND f.estado <> 'PAGADO') THEN 'PAGADO'
                       WHEN nom.id IS NOT NULL AND nom.estado = 'CONFIRMADO' THEN 'LIQUIDADO'
                       ELSE 'EN_LIQUIDACION'
                     END AS estado_visible,
                     r.empresa_id
                FROM nomina_retiro r
                JOIN contrato_laboral c ON c.id = r.contrato_id
                JOIN empleados e ON e.id = r.empleado_id
                JOIN nomina_catalogo m ON m.id = r.motivo_id
                LEFT JOIN nomina_cargo k ON k.id = c.cargo_id
                LEFT JOIN nomina_documento pre ON pre.id = r.documento_id AND pre.estado <> 'ANULADO'
                LEFT JOIN nomina_tipo_comprobante tp ON tp.id = pre.tipo_comprobante_id
                LEFT JOIN LATERAL (SELECT x.* FROM nomina_documento x
                                    WHERE x.documento_origen_id = pre.id AND x.estado <> 'ANULADO'
                                    ORDER BY x.id DESC LIMIT 1) nom ON TRUE
                LEFT JOIN nomina_tipo_comprobante tn ON tn.id = nom.tipo_comprobante_id
            ) r
            """;

    /**
     * Listado paginado, del retiro más reciente al más antiguo.
     *
     * @param pageable búsqueda por empleado, documento o vinculación y filtro de estado
     */
    public PageImpl<RetiroDto> listar(PageableDto<FiltroRetiroDto> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";
        FiltroRetiroDto f = pageable.getParams() != null ? pageable.getParams() : new FiltroRetiroDto();

        StringBuilder sql = new StringBuilder("SELECT r.*, COUNT(*) OVER() AS total_rows FROM (")
                .append(SELECT).append(") r WHERE r.empresa_id = :empresaId ");
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        if (!search.isEmpty()) {
            sql.append(" AND (LOWER(r.empleado_nombre) LIKE :search OR r.numero_documento LIKE :search")
                    .append(" OR LOWER(COALESCE(r.codigo, '')) LIKE :search) ");
            params.addValue("search", "%" + search + "%");
        }
        if (f.getEstado() != null) {
            sql.append(" AND r.estado_visible = :estado ");
            params.addValue("estado", f.getEstado());
        }
        sql.append(" ORDER BY r.fecha_retiro DESC, r.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size).addValue("limit", size);

        List<RetiroDto> lista = jdbc.query(sql.toString(), params, (rs, i) -> {
            RetiroDto dto = mapear(rs);
            dto.setTotalRows(rs.getLong("total_rows"));
            return dto;
        });
        long total = lista.isEmpty() ? 0 : lista.get(0).getTotalRows();
        return new PageImpl<>(lista, PageRequest.of(page, size), total);
    }

    /** Un retiro con su estado; vacío si no es de la empresa. */
    public Optional<RetiroDto> obtener(Long id, Integer empresaId) {
        return jdbc.query("SELECT * FROM (" + SELECT + ") r WHERE r.id = :id AND r.empresa_id = :empresaId",
                new MapSqlParameterSource("id", id).addValue("empresaId", empresaId), (rs, i) -> mapear(rs))
                .stream().findFirst();
    }

    /** ¿El empleado tiene otra vinculación activa (además de la que se retira)? */
    public boolean tieneOtraVinculacionActiva(Long empleadoId, Long contratoId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM contrato_laboral
                 WHERE empleado_id = :empleadoId AND id <> :contratoId AND estado = 'ACTIVO' AND deleted_at IS NULL
                """, new MapSqlParameterSource("empleadoId", empleadoId).addValue("contratoId", contratoId),
                Integer.class);
        return n != null && n > 0;
    }

    /** ¿La vinculación ya tiene un retiro vigente? */
    public boolean tieneRetiroVigente(Long contratoId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM nomina_retiro WHERE contrato_id = :contratoId AND estado = 'VIGENTE'
                """, new MapSqlParameterSource("contratoId", contratoId), Integer.class);
        return n != null && n > 0;
    }

    /** Retiro vigente cuya liquidación definitiva es este documento. */
    public Optional<Long> retiroDeDocumento(Long documentoId) {
        return jdbc.queryForList("""
                SELECT id FROM nomina_retiro WHERE documento_id = :documentoId AND estado = 'VIGENTE'
                """, new MapSqlParameterSource("documentoId", documentoId), Long.class).stream().findFirst();
    }

    /**
     * Último día con salario ya pagado al contrato en una nómina real (no prenómina,
     * no anulada), sin contar el documento dado.
     *
     * @return la fecha final del último período liquidado, o vacío si nunca se le pagó
     */
    public Optional<LocalDate> salarioPagadoHasta(Long contratoId, Long excluirDocumentoId) {
        return jdbc.queryForList("""
                SELECT MAX(p.fecha_fin)
                  FROM nomina n
                  JOIN periodo_nomina p ON p.id = n.periodo_id
                 WHERE n.contrato_id = :contratoId AND n.es_prenomina = FALSE AND n.estado <> 'ANULADO'
                   AND (CAST(:excluir AS BIGINT) IS NULL OR n.documento_id IS DISTINCT FROM :excluir)
                """, new MapSqlParameterSource("contratoId", contratoId).addValue("excluir", excluirDocumentoId),
                LocalDate.class).stream().filter(Objects::nonNull).findFirst();
    }

    private static RetiroDto mapear(ResultSet rs) throws SQLException {
        RetiroDto dto = new RetiroDto();
        dto.setId(rs.getLong("id"));
        dto.setContratoId(rs.getLong("contrato_id"));
        dto.setCodigoVinculacion(rs.getString("codigo"));
        dto.setEmpleadoId(rs.getLong("empleado_id"));
        dto.setEmpleadoNombre(rs.getString("empleado_nombre"));
        dto.setEmpleadoDocumento(rs.getString("numero_documento"));
        dto.setCargo(rs.getString("cargo"));
        dto.setTipoContrato(rs.getString("tipo_contrato"));
        dto.setFechaInicio(rs.getObject("fecha_inicio", LocalDate.class));
        dto.setFechaRetiro(rs.getObject("fecha_retiro", LocalDate.class));
        dto.setMotivoId(rs.getLong("motivo_id"));
        dto.setMotivo(rs.getString("motivo"));
        dto.setIndemniza(rs.getBoolean("indemniza"));
        dto.setFechaFinPactada(rs.getObject("fecha_fin_pactada", LocalDate.class));
        dto.setCesantiasCorteHasta(rs.getObject("cesantias_corte_hasta", LocalDate.class));
        dto.setObservacion(rs.getString("observacion"));
        dto.setEstado(rs.getString("estado_visible"));
        dto.setDocumentoId(rs.getObject("documento_id", Long.class));
        dto.setDocumentoNumero(rs.getString("documento_numero"));
        dto.setNominaId(rs.getObject("nomina_id", Long.class));
        dto.setNominaNumero(rs.getString("nomina_numero"));
        dto.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        return dto;
    }
}
