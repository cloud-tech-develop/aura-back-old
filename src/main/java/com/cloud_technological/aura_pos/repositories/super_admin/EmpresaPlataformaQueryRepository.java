package com.cloud_technological.aura_pos.repositories.super_admin;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.super_admin.DashboardPlataformaDto;
import com.cloud_technological.aura_pos.dto.super_admin.EmpresaPlataformaDto;
import com.cloud_technological.aura_pos.dto.super_admin.EmpresaTableDto;


@Repository
public class EmpresaPlataformaQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    // ── Tabla paginada ────────────────────────────────────────
    public PageImpl<EmpresaTableDto> listar(int page, int size, String search) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                e.id,
                e.razon_social,
                e.nombre_comercial,
                e.nit,
                e.activa,
                e.created_at,
                COUNT(DISTINCT s.id)::INT  AS total_sucursales,
                COUNT(DISTINCT u.id)::INT  AS total_usuarios,
                COUNT(*) OVER()            AS total_rows
            FROM empresa e
            LEFT JOIN sucursal s ON s.empresa_id = e.id AND s.activa = true
            LEFT JOIN usuario  u ON u.empresa_id = e.id AND u.activo = true
            WHERE 1=1
        """);

        MapSqlParameterSource params = new MapSqlParameterSource();

        if (search != null && !search.isBlank()) {
            sql.append("""
                AND (LOWER(e.razon_social)    LIKE :search
                 OR  LOWER(e.nombre_comercial) LIKE :search
                 OR  LOWER(e.nit)              LIKE :search)
            """);
            params.addValue("search", "%" + search.toLowerCase() + "%");
        }

        sql.append(" GROUP BY e.id ORDER BY e.id DESC OFFSET :offset LIMIT :limit");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<EmpresaTableDto> list = jdbc.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(EmpresaTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    // ── Detalle con stats ─────────────────────────────────────
    public Optional<EmpresaPlataformaDto> findById(Integer id) {
        String sql = """
            SELECT
                e.id,
                e.razon_social,
                e.nombre_comercial,
                e.nit,
                e.dv,
                e.logo_url,
                e.telefono,
                e.municipio,
                e.municipio_id,
                e.activa,
                e.created_at,
                COUNT(DISTINCT s.id)::INT      AS total_sucursales,
                COUNT(DISTINCT u.id)::INT      AS total_usuarios,
                COUNT(DISTINCT v.id)           AS total_ventas
            FROM empresa e
            LEFT JOIN sucursal s ON s.empresa_id = e.id AND s.activa = true
            LEFT JOIN usuario  u ON u.empresa_id = e.id AND u.activo = true
            LEFT JOIN venta    v ON v.empresa_id = e.id AND v.estado_venta = 'COMPLETADA'
            WHERE e.id = :id
            GROUP BY e.id
        """;
        List<EmpresaPlataformaDto> list = jdbc.query(sql,
                new MapSqlParameterSource("id", id),
                new BeanPropertyRowMapper<>(EmpresaPlataformaDto.class));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    // ── Dashboard ─────────────────────────────────────────────
    public DashboardPlataformaDto dashboard() {
        // Métricas globales
        String sqlMetricas = """
            SELECT
                COUNT(*)                                                        AS total_empresas,
                COUNT(*) FILTER (WHERE activa = true)                          AS empresas_activas,
                COUNT(*) FILTER (WHERE activa = false)                         AS empresas_inactivas,
                COUNT(*) FILTER (WHERE created_at >= date_trunc('month', NOW())) AS nuevas_este_mes
            FROM empresa
        """;
        Map<String, Object> metricas = jdbc.queryForMap(sqlMetricas, new MapSqlParameterSource());

        // Últimas 5 empresas
        String sqlUltimas = """
            SELECT
                e.id, e.razon_social, e.nombre_comercial, e.nit, e.activa, e.created_at,
                COUNT(DISTINCT s.id)::INT AS total_sucursales,
                COUNT(DISTINCT u.id)::INT AS total_usuarios,
                5 AS total_rows
            FROM empresa e
            LEFT JOIN sucursal s ON s.empresa_id = e.id AND s.activa = true
            LEFT JOIN usuario  u ON u.empresa_id = e.id AND u.activo = true
            GROUP BY e.id
            ORDER BY e.id DESC
            LIMIT 5
        """;
        List<EmpresaTableDto> ultimas = jdbc.query(sqlUltimas,
                new MapSqlParameterSource(),
                new BeanPropertyRowMapper<>(EmpresaTableDto.class));

        DashboardPlataformaDto dto = new DashboardPlataformaDto();
        dto.setTotalEmpresas(toLong(metricas.get("total_empresas")));
        dto.setEmpresasActivas(toLong(metricas.get("empresas_activas")));
        dto.setEmpresasInactivas(toLong(metricas.get("empresas_inactivas")));
        dto.setNuevasEsteMes(toLong(metricas.get("nuevas_este_mes")));
        dto.setUltimasEmpresas(ultimas);
        return dto;
    }

    private Long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Long l) return l;
        return Long.parseLong(val.toString());
    }
}
