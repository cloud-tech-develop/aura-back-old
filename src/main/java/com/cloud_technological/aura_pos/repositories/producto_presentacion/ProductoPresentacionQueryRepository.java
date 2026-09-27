package com.cloud_technological.aura_pos.repositories.producto_presentacion;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.producto_presentacion.ProductoPresentacionTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class ProductoPresentacionQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<ProductoPresentacionTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                pp.id,
                pp.producto_id,
                p.nombre AS producto_nombre,
                pp.nombre,
                pp.codigo_barras,
                pp.factor_conversion,
                pp.activo,
                COUNT(*) OVER() AS total_rows
            FROM producto_presentacion pp
            INNER JOIN producto p ON pp.producto_id = p.id
            WHERE p.empresa_id = :empresaId
            AND pp.activo = true
            AND p.deleted_at IS NULL
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(pp.nombre) LIKE :search
                OR LOWER(p.nombre) LIKE :search
                OR LOWER(pp.codigo_barras) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY pp.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<ProductoPresentacionTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(ProductoPresentacionTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    /**
     * Código ya usado dentro de la empresa: por otra presentación activa o por
     * el código de barras o el SKU de un producto. El POS resuelve el escaneo
     * contra los tres, así que un choque vendería el artículo equivocado. Entre
     * empresas el mismo código es válido: el EAN de una caja es igual en todas
     * las droguerías.
     */
    public boolean codigoBarrasEnUso(String codigoBarras, Integer empresaId, Long excluirPresentacionId) {
        String sql = """
            SELECT EXISTS (
                SELECT 1 FROM producto_presentacion pp
                JOIN producto p ON p.id = pp.producto_id
                WHERE p.empresa_id = :empresaId
                  AND p.deleted_at IS NULL
                  AND pp.activo = true
                  AND TRIM(pp.codigo_barras) = :codigo
                  AND pp.id <> COALESCE(CAST(:excluirId AS BIGINT), -1)
            ) OR EXISTS (
                SELECT 1 FROM producto p
                WHERE p.empresa_id = :empresaId
                  AND p.deleted_at IS NULL
                  AND (TRIM(p.codigo_barras) = :codigo OR TRIM(p.sku) = :codigo)
            )
        """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("codigo", codigoBarras.trim())
                .addValue("excluirId", excluirPresentacionId);
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, params, Boolean.class));
    }

    public List<ProductoPresentacionTableDto> listarPorProducto(Long productoId) {
        String sql = """
            SELECT
                pp.id,
                pp.producto_id,
                p.nombre AS producto_nombre,
                pp.nombre,
                pp.codigo_barras,
                pp.factor_conversion,
                pp.precio,
                pp.costo,
                pp.es_default_compra,
                pp.es_default_venta,
                pp.se_vende,
                pp.activo
            FROM producto_presentacion pp
            INNER JOIN producto p ON pp.producto_id = p.id
            WHERE pp.producto_id = :productoId
            AND pp.activo = true
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("productoId", productoId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(ProductoPresentacionTableDto.class));
    }
}