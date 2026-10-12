package com.cloud_technological.aura_pos.repositories.nomina_documentos;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.nomina.documento.DocumentoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.FiltroDocumentoNominaDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

import lombok.RequiredArgsConstructor;

/** Consultas de prenóminas y nóminas por documento (listado, encabezado, numeración, período). */
@Repository
@RequiredArgsConstructor
public class NominaDocumentoQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String COLUMNAS = """
            SELECT d.id, d.periodo_id, d.tipo_comprobante_id, t.nombre AS tipo_comprobante_nombre,
                   t.clase AS clase_comprobante, t.prefijo, d.clase_documento, d.numero, d.fecha,
                   d.fecha_desde, d.fecha_hasta, d.fecha_liquidacion, d.fecha_pago, d.simulacion,
                   d.descripcion, d.estado, d.fecha_confirmacion, d.documento_origen_id,
                   d.total_devengado, d.total_deducido, d.total_neto, d.total_aportes,
                   (SELECT COUNT(*) FROM nomina n WHERE n.documento_id = d.id) AS empleados,
                   (SELECT COUNT(*) FROM nomina n WHERE n.documento_id = d.id AND n.estado = 'PAGADO') AS pagados,
                   (SELECT x.id FROM nomina_documento x
                     WHERE x.documento_origen_id = d.id AND x.estado <> 'ANULADO'
                     ORDER BY x.id DESC LIMIT 1) AS nomina_generada_id,
                   (SELECT tx.prefijo || '-' || LPAD(x.numero::TEXT, 5, '0')
                      FROM nomina_documento x JOIN nomina_tipo_comprobante tx ON tx.id = x.tipo_comprobante_id
                     WHERE x.documento_origen_id = d.id AND x.estado <> 'ANULADO'
                     ORDER BY x.id DESC LIMIT 1) AS nomina_generada_numero,
                   (SELECT 'PRE-' || tok.prefijo || '-' || LPAD(o.numero::TEXT, 5, '0')
                      FROM nomina_documento o JOIN nomina_tipo_comprobante tok ON tok.id = o.tipo_comprobante_id
                     WHERE o.id = d.documento_origen_id) AS documento_origen_numero,
                   COUNT(*) OVER() AS total_rows
              FROM nomina_documento d
              JOIN nomina_tipo_comprobante t ON t.id = d.tipo_comprobante_id
            """;

    /**
     * Listado paginado, del más reciente al más antiguo.
     *
     * @param pageable  página, filas, búsqueda (número, tipo o descripción) y filtros en {@code params}
     * @param empresaId empresa
     */
    public PageImpl<DocumentoNominaDto> listar(PageableDto<FiltroDocumentoNominaDto> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";
        FiltroDocumentoNominaDto f = pageable.getParams() != null ? pageable.getParams()
                : new FiltroDocumentoNominaDto();

        StringBuilder sql = new StringBuilder(COLUMNAS).append(" WHERE d.empresa_id = :empresaId ");
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        if (!search.isEmpty()) {
            sql.append("""
                     AND (LOWER(t.nombre) LIKE :search OR LOWER(COALESCE(d.descripcion, '')) LIKE :search
                          OR LOWER(CASE WHEN d.clase_documento = 'PRENOMINA' THEN 'PRE-' ELSE '' END
                                   || t.prefijo || '-' || LPAD(d.numero::TEXT, 5, '0')) LIKE :search)
                    """);
            params.addValue("search", "%" + search + "%");
        }
        if (f.getClaseDocumento() != null) {
            sql.append(" AND d.clase_documento = :claseDocumento ");
            params.addValue("claseDocumento", f.getClaseDocumento());
        }
        if (f.getTipoComprobanteId() != null) {
            sql.append(" AND d.tipo_comprobante_id = :tipoComprobanteId ");
            params.addValue("tipoComprobanteId", f.getTipoComprobanteId());
        }
        if (f.getEstado() != null) {
            sql.append(" AND d.estado = :estado ");
            params.addValue("estado", f.getEstado());
        }
        if (f.getDesde() != null) {
            sql.append(" AND d.fecha_hasta >= :desde ");
            params.addValue("desde", f.getDesde());
        }
        if (f.getHasta() != null) {
            sql.append(" AND d.fecha_desde <= :hasta ");
            params.addValue("hasta", f.getHasta());
        }
        sql.append(" ORDER BY d.fecha_desde DESC, d.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size).addValue("limit", size);

        List<DocumentoNominaDto> lista = jdbc.query(sql.toString(), params, this::mapear);
        long total = lista.isEmpty() ? 0 : lista.get(0).getTotalRows();
        return new PageImpl<>(lista, PageRequest.of(page, size), total);
    }

    /**
     * Encabezado de un documento.
     *
     * @return el documento, o vacío si no es de la empresa
     */
    public Optional<DocumentoNominaDto> obtener(Long id, Integer empresaId) {
        return jdbc.query(COLUMNAS + " WHERE d.id = :id AND d.empresa_id = :empresaId ",
                new MapSqlParameterSource("id", id).addValue("empresaId", empresaId), this::mapear)
                .stream().findFirst();
    }

    /**
     * Siguiente número del tipo de comprobante para esa clase de documento. El índice
     * único (empresa, tipo, clase, número) frena dos altas simultáneas.
     */
    public int siguienteNumero(Integer empresaId, Long tipoComprobanteId, String claseDocumento) {
        Integer ultimo = jdbc.queryForObject("""
                SELECT COALESCE(MAX(numero), 0) FROM nomina_documento
                 WHERE empresa_id = :empresaId AND tipo_comprobante_id = :tipo AND clase_documento = :clase
                """, new MapSqlParameterSource("empresaId", empresaId).addValue("tipo", tipoComprobanteId)
                .addValue("clase", claseDocumento), Integer.class);
        return (ultimo != null ? ultimo : 0) + 1;
    }

    /**
     * Período (no anulado) con exactamente ese rango de fechas.
     *
     * @return su id, o vacío si no existe
     */
    public Optional<Long> periodoConRango(Integer empresaId, LocalDate desde, LocalDate hasta) {
        return jdbc.queryForList("""
                SELECT id FROM periodo_nomina
                 WHERE empresa_id = :empresaId AND fecha_inicio = :desde AND fecha_fin = :hasta
                   AND estado <> 'ANULADO'
                 ORDER BY id LIMIT 1
                """, new MapSqlParameterSource("empresaId", empresaId).addValue("desde", desde)
                .addValue("hasta", hasta), Long.class).stream().findFirst();
    }

    /** Ids de las filas (empleados) del documento. */
    public List<Long> filas(Long documentoId) {
        return jdbc.queryForList("SELECT id FROM nomina WHERE documento_id = :documentoId ORDER BY id",
                new MapSqlParameterSource("documentoId", documentoId), Long.class);
    }

    /** Ids de las filas del documento en un estado (APROBADO = pendientes de pago en una nómina). */
    public List<Long> filasEnEstado(Long documentoId, String estado) {
        return jdbc.queryForList("""
                SELECT id FROM nomina WHERE documento_id = :documentoId AND estado = :estado ORDER BY id
                """, new MapSqlParameterSource("documentoId", documentoId).addValue("estado", estado), Long.class);
    }

    /** Ids de las filas liquidadas del documento (las que entran a la proyección contable). */
    public List<Long> filasLiquidadas(Long documentoId) {
        return jdbc.queryForList("""
                SELECT id FROM nomina WHERE documento_id = :documentoId AND liquidada_at IS NOT NULL ORDER BY id
                """, new MapSqlParameterSource("documentoId", documentoId), Long.class);
    }

    /**
     * Código y nombre de las cuentas.
     *
     * @return id → [código, nombre]
     */
    public Map<Long, String[]> cuentas(Collection<Long> ids) {
        Map<Long, String[]> out = new HashMap<>();
        if (ids.isEmpty()) return out;
        jdbc.query("SELECT id, codigo, nombre FROM plan_cuenta WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", ids),
                rs -> {
                    out.put(rs.getLong("id"), new String[] { rs.getString("codigo"), rs.getString("nombre") });
                });
        return out;
    }

    /**
     * Nombre de los terceros (razón social o nombre completo).
     *
     * @return id → nombre
     */
    public Map<Long, String> nombresTerceros(Collection<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        if (ids.isEmpty()) return out;
        jdbc.query("""
                SELECT id, COALESCE(NULLIF(razon_social, ''), TRIM(CONCAT(nombres, ' ', apellidos))) AS nombre
                  FROM tercero WHERE id IN (:ids)
                """, new MapSqlParameterSource("ids", ids),
                rs -> {
                    out.put(rs.getLong("id"), rs.getString("nombre"));
                });
        return out;
    }

    /** Filas del documento sin liquidar (nuevas o con error en el último intento). */
    public int filasSinLiquidar(Long documentoId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM nomina WHERE documento_id = :documentoId AND liquidada_at IS NULL
                """, new MapSqlParameterSource("documentoId", documentoId), Integer.class);
        return n != null ? n : 0;
    }

    /**
     * Nómina no anulada generada desde una prenómina.
     *
     * @return su id, o vacío si aún no se genera (o se anuló)
     */
    public Optional<Long> nominaActivaDeOrigen(Long prenominaId) {
        return jdbc.queryForList("""
                SELECT id FROM nomina_documento
                 WHERE documento_origen_id = :prenominaId AND estado <> 'ANULADO'
                 ORDER BY id DESC LIMIT 1
                """, new MapSqlParameterSource("prenominaId", prenominaId), Long.class).stream().findFirst();
    }

    private DocumentoNominaDto mapear(ResultSet rs, int fila) throws SQLException {
        DocumentoNominaDto dto = new DocumentoNominaDto();
        dto.setId(rs.getLong("id"));
        dto.setPeriodoId(rs.getLong("periodo_id"));
        dto.setTipoComprobanteId(rs.getLong("tipo_comprobante_id"));
        dto.setTipoComprobanteNombre(rs.getString("tipo_comprobante_nombre"));
        dto.setClaseComprobante(rs.getString("clase_comprobante"));
        dto.setClaseDocumento(rs.getString("clase_documento"));
        dto.setNumero(rs.getInt("numero"));
        dto.setNumeroCompleto(numeroCompleto(rs.getString("clase_documento"), rs.getString("prefijo"),
                rs.getInt("numero")));
        dto.setFecha(rs.getObject("fecha", LocalDate.class));
        dto.setFechaDesde(rs.getObject("fecha_desde", LocalDate.class));
        dto.setFechaHasta(rs.getObject("fecha_hasta", LocalDate.class));
        dto.setFechaLiquidacion(rs.getObject("fecha_liquidacion", LocalDate.class));
        dto.setFechaPago(rs.getObject("fecha_pago", LocalDate.class));
        dto.setSimulacion(rs.getBoolean("simulacion"));
        dto.setDescripcion(rs.getString("descripcion"));
        dto.setEstado(rs.getString("estado"));
        dto.setFechaConfirmacion(rs.getObject("fecha_confirmacion", LocalDateTime.class));
        dto.setDocumentoOrigenId(rs.getObject("documento_origen_id", Long.class));
        dto.setTotalDevengado(rs.getBigDecimal("total_devengado"));
        dto.setTotalDeducido(rs.getBigDecimal("total_deducido"));
        dto.setTotalNeto(rs.getBigDecimal("total_neto"));
        dto.setTotalAportes(rs.getBigDecimal("total_aportes"));
        dto.setEmpleados(rs.getInt("empleados"));
        dto.setPagados(rs.getInt("pagados"));
        dto.setNominaGeneradaId(rs.getObject("nomina_generada_id", Long.class));
        dto.setNominaGeneradaNumero(rs.getString("nomina_generada_numero"));
        dto.setDocumentoOrigenNumero(rs.getString("documento_origen_numero"));
        dto.setTotalRows(rs.getLong("total_rows"));
        return dto;
    }

    /** PRE-NOM-00001 para la prenómina, NOM-00001 para la nómina. */
    private static String numeroCompleto(String claseDocumento, String prefijo, int numero) {
        String base = prefijo + "-" + String.format("%05d", numero);
        return "PRENOMINA".equals(claseDocumento) ? "PRE-" + base : base;
    }
}
