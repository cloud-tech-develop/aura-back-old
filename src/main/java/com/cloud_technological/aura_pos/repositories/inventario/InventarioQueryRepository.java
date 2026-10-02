package com.cloud_technological.aura_pos.repositories.inventario;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.inventario.InventarioTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class InventarioQueryRepository {
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<InventarioTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                i.id,
                i.sucursal_id,
                s.nombre AS sucursal_nombre,
                i.bodega_id,
                b.nombre AS bodega_nombre,
                i.producto_id,
                p.nombre AS producto_nombre,
                p.sku AS producto_sku,
                i.stock_actual,
                i.stock_minimo,
                i.stock_maximo,
                i.punto_reorden,
                i.ubicacion,
                um.abreviatura         AS unidad_abreviatura,
                pres.nombre            AS presentacion_nombre,
                pres.factor_conversion AS presentacion_factor,
                COUNT(*) OVER() AS total_rows
            FROM inventario i
            INNER JOIN sucursal s ON i.sucursal_id = s.id
            LEFT  JOIN bodega   b ON b.id = i.bodega_id
            INNER JOIN producto p ON i.producto_id = p.id
            LEFT  JOIN unidad_medida um ON um.id = p.unidad_medida_base_id
            -- El stock "como se cuenta" (3 Cajas + 4 und): la presentación de
            -- mayor contenido entero. Solo visual; el saldo sigue en unidad base.
            LEFT JOIN LATERAL (
                SELECT pp.nombre, pp.factor_conversion
                  FROM producto_presentacion pp
                 WHERE pp.producto_id = p.id AND pp.activo = TRUE
                   AND pp.factor_conversion >= 2
                   AND pp.factor_conversion = TRUNC(pp.factor_conversion)
                 ORDER BY pp.factor_conversion DESC
                 LIMIT 1
            ) pres ON TRUE
            WHERE s.empresa_id = :empresaId
            AND p.deleted_at IS NULL
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(p.nombre) LIKE :search
                OR LOWER(p.sku) LIKE :search
                OR LOWER(s.nombre) LIKE :search
                OR LOWER(COALESCE(b.nombre, '')) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY i.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<InventarioTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(InventarioTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    // Usado en ventas y compras para verificar stock disponible
    public BigDecimal obtenerStock(Long productoId, Long sucursalId) {
        String sql = """
            SELECT COALESCE(stock_actual, 0)
            FROM inventario
            WHERE producto_id = :productoId
            AND sucursal_id = :sucursalId
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("productoId", productoId);
        params.addValue("sucursalId", sucursalId);
        BigDecimal stock = jdbcTemplate.queryForObject(sql, params, BigDecimal.class);
        return stock != null ? stock : BigDecimal.ZERO;
    }

    /**
     * Sugerido de compra (V185): saldos por bodega en o bajo su punto de
     * reorden (o bajo el mínimo si no tienen punto). Sugiere llenar hasta el
     * máximo; sin máximo, hasta el punto de reorden/mínimo. Trae el último
     * proveedor y el costo para armar el pedido.
     */
    public List<com.cloud_technological.aura_pos.dto.inventario.SugeridoCompraDto> sugeridoCompra(
            Integer empresaId, Long sucursalId, Long bodegaId) {
        StringBuilder sql = new StringBuilder("""
            WITH niveles AS (
                SELECT i.*,
                       COALESCE(NULLIF(i.punto_reorden, 0), NULLIF(i.stock_minimo, 0)) AS nivel
                  FROM inventario i
            )
            SELECT
                i.id                AS inventario_id,
                i.sucursal_id,
                s.nombre            AS sucursal_nombre,
                i.bodega_id,
                b.nombre            AS bodega_nombre,
                p.id                AS producto_id,
                p.nombre            AS producto_nombre,
                p.sku               AS producto_sku,
                um.abreviatura      AS unidad_abreviatura,
                i.stock_actual,
                i.stock_minimo,
                i.punto_reorden,
                i.stock_maximo,
                GREATEST(COALESCE(NULLIF(i.stock_maximo, 0), i.nivel) - i.stock_actual, 0) AS cantidad_sugerida,
                p.costo,
                GREATEST(COALESCE(NULLIF(i.stock_maximo, 0), i.nivel) - i.stock_actual, 0) * COALESCE(p.costo, 0)
                                    AS valor_estimado,
                (i.stock_maximo IS NULL OR i.stock_maximo = 0) AS sin_maximo,
                up.proveedor_id     AS ultimo_proveedor_id,
                up.proveedor_nombre AS ultimo_proveedor_nombre,
                up.fecha            AS ultima_compra
            FROM niveles i
            JOIN sucursal s ON s.id = i.sucursal_id
            LEFT JOIN bodega b ON b.id = i.bodega_id
            JOIN producto p ON p.id = i.producto_id
            LEFT JOIN unidad_medida um ON um.id = p.unidad_medida_base_id
            LEFT JOIN LATERAL (
                SELECT c.proveedor_id,
                       COALESCE(NULLIF(TRIM(COALESCE(t.razon_social, '')), ''),
                                TRIM(CONCAT_WS(' ', t.nombres, t.apellidos))) AS proveedor_nombre,
                       c.fecha::date AS fecha
                  FROM compra_detalle cd
                  JOIN compra c ON c.id = cd.compra_id
                  LEFT JOIN tercero t ON t.id = c.proveedor_id
                 WHERE cd.producto_id = p.id AND c.empresa_id = :empresaId
                   AND c.estado <> 'ANULADA' AND COALESCE(c.tipo_documento, '') <> 'NOTA_CREDITO'
                 ORDER BY c.fecha DESC, c.id DESC
                 LIMIT 1
            ) up ON TRUE
            WHERE s.empresa_id = :empresaId
              AND p.deleted_at IS NULL
              AND p.activo = TRUE
              AND p.clasificacion = 'PRODUCTO'
              AND i.nivel IS NOT NULL
              AND i.stock_actual <= i.nivel
            """);
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        if (sucursalId != null) {
            sql.append(" AND i.sucursal_id = :sucursalId ");
            params.addValue("sucursalId", sucursalId);
        }
        if (bodegaId != null) {
            sql.append(" AND i.bodega_id = :bodegaId ");
            params.addValue("bodegaId", bodegaId);
        }
        sql.append(" ORDER BY up.proveedor_nombre NULLS LAST, p.nombre ");
        return jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(com.cloud_technological.aura_pos.dto.inventario.SugeridoCompraDto.class));
    }

    // Productos con stock bajo el mínimo
    public List<InventarioTableDto> listarStockBajo(Integer empresaId) {
        String sql = """
            SELECT
                i.id,
                i.sucursal_id,
                s.nombre AS sucursal_nombre,
                i.producto_id,
                p.nombre AS producto_nombre,
                p.sku AS producto_sku,
                i.stock_actual,
                i.stock_minimo,
                i.ubicacion
            FROM inventario i
            INNER JOIN sucursal s ON i.sucursal_id = s.id
            INNER JOIN producto p ON i.producto_id = p.id
            WHERE s.empresa_id = :empresaId
            AND i.stock_actual <= i.stock_minimo
            AND p.deleted_at IS NULL
            ORDER BY i.stock_actual ASC
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(InventarioTableDto.class));
    }
}
