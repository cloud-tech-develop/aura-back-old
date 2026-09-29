package com.cloud_technological.aura_pos.repositories.productos_composicion;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.producto_composicion.ProductoComposicionTableDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.RecetaComponenteDetalleDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.RecetaResumenTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class ProductoComposicionQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<ProductoComposicionTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                pc.id,
                pc.producto_padre_id,
                pp.nombre AS producto_padre_nombre,
                pc.producto_hijo_id,
                ph.nombre AS producto_hijo_nombre,
                pc.cantidad,
                pc.tipo,
                pc.cantidad_receta,
                um.abreviatura AS unidad_medida_abreviatura,
                pc.factor_unidad,
                pc.merma_porcentaje,
                pp.rendimiento_receta AS rendimiento,
                COUNT(*) OVER() AS total_rows
            FROM producto_composicion pc
            INNER JOIN producto pp ON pc.producto_padre_id = pp.id
            INNER JOIN producto ph ON pc.producto_hijo_id = ph.id
            LEFT  JOIN unidad_medida um ON pc.unidad_medida_id = um.id
            WHERE pp.empresa_id = :empresaId
            AND pp.deleted_at IS NULL
            AND ph.deleted_at IS NULL
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(pp.nombre) LIKE :search
                OR LOWER(ph.nombre) LIKE :search
                OR LOWER(pc.tipo) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY pc.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<ProductoComposicionTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(ProductoComposicionTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    /**
     * Listado agrupado: una fila por producto con receta.
     *
     * `costo_estimado` es plano a propósito — suma `producto.costo` de cada
     * componente sin explotar subrecetas. Sirve para la grilla; el número fino
     * lo da {@code /padre/{id}/costeo}.
     */
    public PageImpl<RecetaResumenTableDto> listarRecetas(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                pp.id                    AS producto_padre_id,
                pp.nombre                AS producto_padre_nombre,
                pp.sku                   AS producto_padre_sku,
                MIN(pc.tipo)             AS tipo,
                pp.rendimiento_receta    AS rendimiento,
                COUNT(pc.id)             AS total_componentes,
                SUM(pc.cantidad * COALESCE(ph.costo, 0)) AS costo_estimado,
                pp.costo                 AS costo_actual,
                pp.precio                AS precio,
                COUNT(*) OVER()          AS total_rows
            FROM producto_composicion pc
            INNER JOIN producto pp ON pc.producto_padre_id = pp.id
            INNER JOIN producto ph ON pc.producto_hijo_id = ph.id
            WHERE pp.empresa_id = :empresaId
            AND pp.deleted_at IS NULL
            AND ph.deleted_at IS NULL
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(pp.nombre) LIKE :search OR LOWER(pp.sku) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        // El COUNT(*) OVER() de arriba cuenta grupos porque la ventana se evalúa
        // después del GROUP BY: da el total de recetas, que es lo que pagina.
        sql.append("""
            GROUP BY pp.id, pp.nombre, pp.sku, pp.rendimiento_receta, pp.costo, pp.precio
            ORDER BY pp.nombre ASC
            OFFSET :offset LIMIT :limit
        """);
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<RecetaResumenTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(RecetaResumenTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public List<ProductoComposicionTableDto> listarPorPadre(Long productoPadreId) {
        String sql = """
            SELECT
                pc.id,
                pc.producto_padre_id,
                pp.nombre AS producto_padre_nombre,
                pc.producto_hijo_id,
                ph.nombre AS producto_hijo_nombre,
                pc.cantidad,
                pc.tipo,
                pc.cantidad_receta,
                um.abreviatura AS unidad_medida_abreviatura,
                pc.factor_unidad,
                pc.merma_porcentaje,
                pp.rendimiento_receta AS rendimiento
            FROM producto_composicion pc
            INNER JOIN producto pp ON pc.producto_padre_id = pp.id
            INNER JOIN producto ph ON pc.producto_hijo_id = ph.id
            LEFT  JOIN unidad_medida um ON pc.unidad_medida_id = um.id
            WHERE pc.producto_padre_id = :productoPadreId
            AND pp.deleted_at IS NULL
            AND ph.deleted_at IS NULL
            ORDER BY pc.orden ASC NULLS LAST, pc.id ASC
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("productoPadreId", productoPadreId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(ProductoComposicionTableDto.class));
    }

    /**
     * Líneas de la receta ya resueltas para pintar la grilla de edición:
     * nombres, unidad escrita, unidad base del componente y stock disponible.
     *
     * El stock se suma sobre todas las sucursales de la empresa: la pantalla de
     * receta es de configuración, no de operación de una sucursal puntual.
     */
    public List<RecetaComponenteDetalleDto> obtenerComponentes(Long productoPadreId, Integer empresaId) {
        String sql = """
            SELECT
                pc.id,
                pc.producto_hijo_id,
                ph.nombre AS producto_hijo_nombre,
                ph.sku    AS producto_hijo_sku,
                pc.cantidad_receta,
                pc.unidad_medida_id,
                um.nombre      AS unidad_medida_nombre,
                um.abreviatura AS unidad_medida_abreviatura,
                pc.producto_presentacion_id,
                pres.nombre    AS producto_presentacion_nombre,
                pc.factor_unidad,
                pc.merma_porcentaje,
                pc.cantidad,
                umb.abreviatura AS unidad_base_abreviatura,
                ph.maneja_inventario,
                COALESCE((
                    SELECT SUM(i.stock_actual)
                      FROM inventario i
                     INNER JOIN sucursal s ON i.sucursal_id = s.id
                     WHERE i.producto_id = ph.id
                       AND s.empresa_id = :empresaId
                ), 0) AS stock_disponible,
                pc.orden,
                pc.nota
            FROM producto_composicion pc
            INNER JOIN producto ph ON pc.producto_hijo_id = ph.id
            LEFT  JOIN unidad_medida um  ON pc.unidad_medida_id = um.id
            LEFT  JOIN unidad_medida umb ON ph.unidad_medida_base_id = umb.id
            LEFT  JOIN producto_presentacion pres ON pc.producto_presentacion_id = pres.id
            WHERE pc.producto_padre_id = :productoPadreId
            AND ph.deleted_at IS NULL
            ORDER BY pc.orden ASC NULLS LAST, pc.id ASC
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("productoPadreId", productoPadreId)
                .addValue("empresaId", empresaId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(RecetaComponenteDetalleDto.class));
    }
}
