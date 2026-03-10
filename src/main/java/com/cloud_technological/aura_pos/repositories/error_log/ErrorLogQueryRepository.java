package com.cloud_technological.aura_pos.repositories.error_log;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.error_log.ErrorLogDetalleDto;
import com.cloud_technological.aura_pos.dto.error_log.ErrorLogGrupoDto;
import com.cloud_technological.aura_pos.dto.error_log.ErrorLogPageParamsDto;
import com.cloud_technological.aura_pos.dto.error_log.ErrorLogTableDto;

@Repository
public class ErrorLogQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    // ── Lista paginada ────────────────────────────────────────
    public PageImpl<ErrorLogTableDto> listar(int page, int size, ErrorLogPageParamsDto params) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    el.id,
                    el.metodo,
                    el.endpoint,
                    el.status_code,
                    el.categoria,
                    el.mensaje,
                    el.grupo_hash,
                    el.empresa_id,
                    e.razon_social AS empresa_nombre,
                    el.usuario_nombre,
                    el.ip_origen,
                    el.created_at,
                    COUNT(*) OVER() AS total_rows
                FROM error_log el
                LEFT JOIN empresa e ON e.id = el.empresa_id
                WHERE 1=1
                """);

        MapSqlParameterSource p = new MapSqlParameterSource();
        applyFiltros(sql, p, params);

        sql.append(" ORDER BY el.created_at DESC OFFSET :offset LIMIT :limit");
        p.addValue("offset", page * size);
        p.addValue("limit", size);

        List<ErrorLogTableDto> list = jdbc.query(sql.toString(), p,
                new BeanPropertyRowMapper<>(ErrorLogTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    // ── Vista agrupada ────────────────────────────────────────
    public PageImpl<ErrorLogGrupoDto> listarGrupos(int page, int size, ErrorLogPageParamsDto params) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    grupo_hash,
                    metodo,
                    endpoint,
                    status_code,
                    categoria,
                    COUNT(*)                    AS total_ocurrencias,
                    MAX(created_at)             AS ultima_ocurrencia,
                    COUNT(DISTINCT empresa_id)  AS empresas_afectadas,
                    COUNT(*) OVER()             AS total_rows
                FROM error_log
                WHERE 1=1
                """);

        MapSqlParameterSource p = new MapSqlParameterSource();
        applyFiltrosGrupo(sql, p, params);

        sql.append("""
                GROUP BY grupo_hash, metodo, endpoint, status_code, categoria
                ORDER BY MAX(created_at) DESC
                OFFSET :offset LIMIT :limit
                """);
        p.addValue("offset", page * size);
        p.addValue("limit", size);

        List<ErrorLogGrupoDto> list = jdbc.query(sql.toString(), p,
                new BeanPropertyRowMapper<>(ErrorLogGrupoDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    // ── Detalle de un registro ────────────────────────────────
    public Optional<ErrorLogDetalleDto> findById(Long id) {
        String sql = """
                SELECT
                    el.id,
                    el.metodo,
                    el.endpoint,
                    el.status_code,
                    el.categoria,
                    el.mensaje,
                    el.detalle,
                    el.grupo_hash,
                    el.empresa_id,
                    e.razon_social AS empresa_nombre,
                    el.usuario_nombre,
                    el.ip_origen,
                    el.created_at
                FROM error_log el
                LEFT JOIN empresa e ON e.id = el.empresa_id
                WHERE el.id = :id
                """;
        List<ErrorLogDetalleDto> list = jdbc.query(sql,
                new MapSqlParameterSource("id", id),
                new BeanPropertyRowMapper<>(ErrorLogDetalleDto.class));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    // ── Filtros comunes para lista ────────────────────────────
    private void applyFiltros(StringBuilder sql, MapSqlParameterSource p, ErrorLogPageParamsDto params) {
        if (params == null) return;

        if (params.getCategoria() != null && !params.getCategoria().isBlank()) {
            sql.append(" AND el.categoria = :categoria");
            p.addValue("categoria", params.getCategoria());
        }
        if (params.getEmpresaId() != null) {
            sql.append(" AND el.empresa_id = :empresaId");
            p.addValue("empresaId", params.getEmpresaId());
        }
        if (params.getStatusCode() != null) {
            sql.append(" AND el.status_code = :statusCode");
            p.addValue("statusCode", params.getStatusCode());
        }
        if (params.getEndpoint() != null && !params.getEndpoint().isBlank()) {
            sql.append(" AND LOWER(el.endpoint) LIKE :endpoint");
            p.addValue("endpoint", "%" + params.getEndpoint().toLowerCase() + "%");
        }
        if (params.getGrupoHash() != null && !params.getGrupoHash().isBlank()) {
            sql.append(" AND el.grupo_hash = :grupoHash");
            p.addValue("grupoHash", params.getGrupoHash());
        }
        if (params.getDesde() != null && !params.getDesde().isBlank()) {
            sql.append(" AND el.created_at >= :desde");
            p.addValue("desde", LocalDate.parse(params.getDesde()).atStartOfDay());
        }
        if (params.getHasta() != null && !params.getHasta().isBlank()) {
            sql.append(" AND el.created_at < :hasta");
            p.addValue("hasta", LocalDate.parse(params.getHasta()).plusDays(1).atStartOfDay());
        }
    }

    // ── Filtros para vista agrupada (sin empresa ni endpoint) ─
    private void applyFiltrosGrupo(StringBuilder sql, MapSqlParameterSource p, ErrorLogPageParamsDto params) {
        if (params == null) return;

        if (params.getCategoria() != null && !params.getCategoria().isBlank()) {
            sql.append(" AND categoria = :categoria");
            p.addValue("categoria", params.getCategoria());
        }
        if (params.getDesde() != null && !params.getDesde().isBlank()) {
            sql.append(" AND created_at >= :desde");
            p.addValue("desde", LocalDate.parse(params.getDesde()).atStartOfDay());
        }
        if (params.getHasta() != null && !params.getHasta().isBlank()) {
            sql.append(" AND created_at < :hasta");
            p.addValue("hasta", LocalDate.parse(params.getHasta()).plusDays(1).atStartOfDay());
        }
    }
}
