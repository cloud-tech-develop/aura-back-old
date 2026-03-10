package com.cloud_technological.aura_pos.repositories.terceros;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.terceros.TerceroTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class TerceroQueryRepository {
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<TerceroTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                t.id,
                t.tipo_documento,
                t.numero_documento,
                COALESCE(NULLIF(t.razon_social, ''), CONCAT(t.nombres, ' ', t.apellidos)) AS nombre_completo,
                t.telefono,
                t.email,
                t.es_cliente,
                t.es_proveedor,
                t.es_empleado,
                t.activo,
                COUNT(*) OVER() AS total_rows
            FROM tercero t
            WHERE t.empresa_id = :empresaId
            AND t.deleted_at IS NULL
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(t.numero_documento) LIKE :search
                OR LOWER(t.razon_social) LIKE :search
                OR LOWER(t.nombres) LIKE :search
                OR LOWER(t.apellidos) LIKE :search
                OR LOWER(t.email) LIKE :search
                OR LOWER(t.telefono) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY t.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<TerceroTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(TerceroTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public boolean existeDocumento(String numeroDocumento, Integer empresaId) {
        String sql = """
            SELECT COUNT(*) FROM tercero
            WHERE numero_documento = :numeroDocumento
            AND empresa_id = :empresaId
            AND deleted_at IS NULL
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("numeroDocumento", numeroDocumento);
        params.addValue("empresaId", empresaId);
        Long count = jdbcTemplate.queryForObject(sql, params, Long.class);
        return count != null && count > 0;
    }

    public boolean existeDocumentoExcluyendo(String numeroDocumento, Integer empresaId, Long id) {
        String sql = """
            SELECT COUNT(*) FROM tercero
            WHERE numero_documento = :numeroDocumento
            AND empresa_id = :empresaId
            AND id != :id
            AND deleted_at IS NULL
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("numeroDocumento", numeroDocumento);
        params.addValue("empresaId", empresaId);
        params.addValue("id", id);
        Long count = jdbcTemplate.queryForObject(sql, params, Long.class);
        return count != null && count > 0;
    }

    // Para selector en ventas - solo clientes
    public List<TerceroTableDto> listarClientes(String search, Integer empresaId) {
        String sql = """
            SELECT
                t.id,
                t.tipo_documento,
                t.numero_documento,
                COALESCE(NULLIF(t.razon_social, ''), CONCAT(t.nombres, ' ', t.apellidos)) AS nombre_completo,
                t.telefono,
                t.email,
                t.es_cliente,
                t.es_proveedor,
                t.es_empleado,
                t.activo
            FROM tercero t
            WHERE t.empresa_id = :empresaId
            AND t.es_cliente = true
            AND t.activo = true
            AND t.deleted_at IS NULL
            AND (LOWER(t.numero_documento) LIKE :search
                OR LOWER(t.razon_social) LIKE :search
                OR LOWER(t.nombres) LIKE :search)
            ORDER BY t.id DESC
            LIMIT 20
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("empresaId", empresaId);
        params.addValue("search", "%" + search.toLowerCase() + "%");
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(TerceroTableDto.class));
    }

    // Para selector en compras - solo proveedores
    public List<TerceroTableDto> listarProveedores(String search, Integer empresaId) {
        String sql = """
            SELECT
                t.id,
                t.tipo_documento,
                t.numero_documento,
                COALESCE(NULLIF(t.razon_social, ''), CONCAT(t.nombres, ' ', t.apellidos)) AS nombre_completo,
                t.telefono,
                t.email,
                t.es_cliente,
                t.es_proveedor,
                t.es_empleado,
                t.activo
            FROM tercero t
            WHERE t.empresa_id = :empresaId
            AND t.es_proveedor = true
            AND t.activo = true
            AND t.deleted_at IS NULL
            AND (LOWER(t.numero_documento) LIKE :search
                OR LOWER(t.razon_social) LIKE :search
                OR LOWER(t.nombres) LIKE :search)
            ORDER BY t.id DESC
            LIMIT 20
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("empresaId", empresaId);
        params.addValue("search", "%" + search.toLowerCase() + "%");
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(TerceroTableDto.class));
    }
}
