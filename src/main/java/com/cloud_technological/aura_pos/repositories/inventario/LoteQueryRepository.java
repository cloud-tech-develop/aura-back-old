package com.cloud_technological.aura_pos.repositories.inventario;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.inventario.LoteTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class LoteQueryRepository {
    /** Solo sus sedes, si el perfil no tiene todas (PLAN_PERMISOS P9). */
    @org.springframework.beans.factory.annotation.Autowired
    private com.cloud_technological.aura_pos.services.permisos.AlcanceSede alcanceSede;

    
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<LoteTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                l.id,
                l.producto_id,
                p.nombre AS producto_nombre,
                l.sucursal_id,
                s.nombre AS sucursal_nombre,
                l.codigo_lote,
                l.fecha_vencimiento,
                l.fecha_fabricacion,
                (l.fecha_vencimiento - CURRENT_DATE) AS dias_para_vencer,
                l.stock_actual,
                l.costo_unitario,
                l.activo,
                um.abreviatura AS unidad_abreviatura,
                c.id AS compra_id,
                COALESCE(c.numero_compra, '#' || c.id) AS compra_numero,
                NULLIF(COALESCE(t.razon_social, TRIM(CONCAT(t.nombres, ' ', t.apellidos))), '') AS proveedor_nombre,
                COUNT(*) OVER() AS total_rows
            FROM lote l
            INNER JOIN producto p ON l.producto_id = p.id
            INNER JOIN sucursal s ON l.sucursal_id = s.id
            LEFT JOIN unidad_medida um ON um.id = p.unidad_medida_base_id
            -- La primera compra vigente que metió mercancía al lote. Se busca por
            -- compra_detalle_lote y no por lote.compra_detalle_id: editar la
            -- compra borra y recrea sus líneas.
            LEFT JOIN LATERAL (
                SELECT co.id, co.numero_compra, co.proveedor_id
                  FROM compra_detalle_lote cdl
                  JOIN compra_detalle cd ON cd.id = cdl.compra_detalle_id
                  JOIN compra co ON co.id = cd.compra_id
                 WHERE cdl.lote_id = l.id
                   AND COALESCE(co.tipo_documento, '') <> 'NOTA_CREDITO'
                   AND co.estado <> 'ANULADA'
                 ORDER BY cdl.id
                 LIMIT 1
            ) c ON true
            LEFT JOIN tercero t ON t.id = c.proveedor_id
            WHERE s.empresa_id = :empresaId /*SEDE:s.id*/
            AND l.activo = true
            AND p.deleted_at IS NULL
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(p.nombre) LIKE :search
                OR LOWER(l.codigo_lote) LIKE :search
                OR LOWER(s.nombre) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY (l.stock_actual > 0) DESC, l.fecha_vencimiento ASC NULLS LAST, l.id OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<LoteTableDto> list = jdbcTemplate.query(alcanceSede.aplicar(sql.toString(), params), params,
                new BeanPropertyRowMapper<>(LoteTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    // Lotes próximos a vencer (próximos 30 días)
    public List<LoteTableDto> listarPorVencer(Integer empresaId) {
        String sql = """
            SELECT
                l.id,
                l.producto_id,
                p.nombre AS producto_nombre,
                l.sucursal_id,
                s.nombre AS sucursal_nombre,
                l.codigo_lote,
                l.fecha_vencimiento,
                l.stock_actual,
                l.costo_unitario,
                l.activo,
                (l.fecha_vencimiento - CURRENT_DATE) AS dias_para_vencer
            FROM lote l
            INNER JOIN producto p ON l.producto_id = p.id
            INNER JOIN sucursal s ON l.sucursal_id = s.id
            WHERE s.empresa_id = :empresaId /*SEDE:s.id*/
            AND l.activo = true
            AND l.stock_actual > 0
            -- Incluye los ya vencidos con stock: son los que más urge sacar.
            AND l.fecha_vencimiento <= CURRENT_DATE
                + (SELECT COALESCE(e.lotes_dias_alerta, 30) FROM empresa e WHERE e.id = s.empresa_id)
            ORDER BY l.fecha_vencimiento ASC
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        return jdbcTemplate.query(alcanceSede.aplicar(sql, params), params, new BeanPropertyRowMapper<>(LoteTableDto.class));
    }

    // Lotes disponibles para un producto en una sucursal (usado en ventas)
    public List<LoteTableDto> listarDisponiblesPorProducto(Long productoId, Long sucursalId, Integer empresaId) {
        String sql = """
            SELECT
                l.id,
                l.producto_id,
                p.nombre AS producto_nombre,
                l.sucursal_id,
                s.nombre AS sucursal_nombre,
                l.codigo_lote,
                l.fecha_vencimiento,
                l.stock_actual,
                l.costo_unitario,
                l.activo,
                (l.fecha_vencimiento - CURRENT_DATE) AS dias_para_vencer
            FROM lote l
            INNER JOIN producto p ON l.producto_id = p.id
            INNER JOIN sucursal s ON l.sucursal_id = s.id
            WHERE l.producto_id = :productoId
            AND l.sucursal_id = :sucursalId
            AND s.empresa_id = :empresaId
            AND l.activo = true
            AND l.stock_actual > 0
            ORDER BY l.fecha_vencimiento ASC NULLS LAST, l.id
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("productoId", productoId);
        params.addValue("sucursalId", sucursalId);
        params.addValue("empresaId", empresaId);
        return jdbcTemplate.query(alcanceSede.aplicar(sql, params), params, new BeanPropertyRowMapper<>(LoteTableDto.class));
    }

    /**
     * Lotes con stock que ya vencieron o vencen en {@code dias}. Sin sucursal,
     * los de toda la empresa. Sin días, la ventana de alerta de la empresa.
     */
    public List<com.cloud_technological.aura_pos.dto.inventario.VencimientoLoteDto> vencimientos(Integer empresaId, Long sucursalId,
            Integer dias) {
        String sql = """
            SELECT
                l.id AS lote_id,
                l.producto_id,
                p.nombre AS producto_nombre,
                p.sku AS producto_sku,
                c.nombre AS categoria_nombre,
                l.sucursal_id,
                s.nombre AS sucursal_nombre,
                l.codigo_lote,
                l.fecha_vencimiento,
                (l.fecha_vencimiento - CURRENT_DATE) AS dias_para_vencer,
                l.stock_actual,
                um.abreviatura AS unidad_abreviatura,
                COALESCE(l.costo_unitario, p.costo, 0) AS costo_unitario,
                ROUND(l.stock_actual * COALESCE(l.costo_unitario, p.costo, 0), 2) AS valor_costo,
                COALESCE(p.precio, 0) AS precio_venta,
                ROUND(l.stock_actual * COALESCE(p.precio, 0), 2) AS valor_venta
            FROM lote l
            INNER JOIN producto p ON p.id = l.producto_id
            INNER JOIN sucursal s ON s.id = l.sucursal_id
            LEFT JOIN categoria c ON c.id = p.categoria_id
            LEFT JOIN unidad_medida um ON um.id = p.unidad_medida_base_id
            WHERE s.empresa_id = :empresaId /*SEDE:s.id*/
              AND (CAST(:sucursalId AS INTEGER) IS NULL OR l.sucursal_id = :sucursalId)
              AND COALESCE(l.activo, true)
              AND l.stock_actual > 0
              AND p.deleted_at IS NULL
              AND l.fecha_vencimiento IS NOT NULL
              AND l.fecha_vencimiento <= CURRENT_DATE + COALESCE(CAST(:dias AS INTEGER),
                    (SELECT COALESCE(e.lotes_dias_alerta, 30) FROM empresa e WHERE e.id = s.empresa_id))
            ORDER BY l.fecha_vencimiento ASC, p.nombre
            """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId)
                .addValue("sucursalId", sucursalId)
                .addValue("dias", dias);
        return jdbcTemplate.query(alcanceSede.aplicar(sql, params), params,
                new BeanPropertyRowMapper<>(com.cloud_technological.aura_pos.dto.inventario.VencimientoLoteDto.class));
    }
}
