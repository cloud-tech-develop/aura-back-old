package com.cloud_technological.aura_pos.repositories.productos;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.productos.CompraProductoHistorialDto;
import com.cloud_technological.aura_pos.dto.productos.CostoHistorialDto;
import com.cloud_technological.aura_pos.dto.productos.HistorialCostoProductoDto;

/** Lecturas del historial de costos de un producto (V226) y de sus compras. */
@Repository
public class ProductoCostoHistorialQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /**
     * Cabecera del historial: producto, costo vigente y stock total. Null si el
     * producto no es de la empresa.
     */
    public HistorialCostoProductoDto cabecera(Long productoId, Integer empresaId) {
        String sql = """
                SELECT p.id AS producto_id, p.nombre AS producto_nombre, p.sku AS producto_sku,
                       um.abreviatura AS unidad_abreviatura,
                       COALESCE(p.costo, 0) AS costo_actual,
                       (SELECT COALESCE(SUM(i.stock_actual), 0) FROM inventario i WHERE i.producto_id = p.id) AS stock_actual
                  FROM producto p
                  LEFT JOIN unidad_medida um ON um.id = p.unidad_medida_base_id
                 WHERE p.id = :productoId AND p.empresa_id = :empresaId
                """;
        List<HistorialCostoProductoDto> r = jdbc.query(sql, params(productoId, empresaId),
                new BeanPropertyRowMapper<>(HistorialCostoProductoDto.class));
        return r.isEmpty() ? null : r.get(0);
    }

    /** Cambios del costo promedio, del más reciente al más antiguo. */
    public List<CostoHistorialDto> cambios(Long productoId, Integer empresaId) {
        String sql = """
                SELECT h.id, h.fecha, h.origen, h.documento_id, h.documento_numero,
                       COALESCE(NULLIF(t.razon_social, ''), NULLIF(TRIM(CONCAT(t.nombres, ' ', t.apellidos)), '')) AS tercero_nombre,
                       h.cantidad, h.costo_entrada, h.stock_antes, h.stock_despues,
                       h.costo_anterior, h.costo_nuevo, u.username AS usuario_nombre, h.observacion
                  FROM producto_costo_historial h
                  LEFT JOIN tercero t ON t.id = h.tercero_id
                  LEFT JOIN usuario u ON u.id = h.usuario_id
                 WHERE h.producto_id = :productoId AND h.empresa_id = :empresaId
                 ORDER BY h.fecha DESC, h.id DESC
                """;
        return jdbc.query(sql, params(productoId, empresaId), new BeanPropertyRowMapper<>(CostoHistorialDto.class));
    }

    /**
     * Líneas de compra y nota crédito del producto, de la más reciente a la más
     * antigua. Incluye las anuladas (con su estado) para que se vea el rastro.
     * El costo neto es el subtotal de la línea (ya sin descuento) entre la
     * cantidad: lo mismo que entra al promedio, sin la parte de los fletes.
     */
    public List<CompraProductoHistorialDto> compras(Long productoId, Integer empresaId) {
        String sql = """
                SELECT c.id AS compra_id, c.numero_compra, c.fecha, c.tipo_documento, c.estado,
                       COALESCE(NULLIF(t.razon_social, ''), NULLIF(TRIM(CONCAT(t.nombres, ' ', t.apellidos)), '')) AS proveedor_nombre,
                       d.cantidad, d.costo_unitario, d.descuento_pct,
                       CASE WHEN COALESCE(d.cantidad, 0) = 0 THEN d.costo_unitario
                            ELSE ROUND(d.subtotal_linea / d.cantidad, 6) END AS costo_neto,
                       d.subtotal_linea,
                       pp.nombre AS presentacion_nombre, d.cantidad_presentacion, d.costo_presentacion
                  FROM compra_detalle d
                  JOIN compra c ON c.id = d.compra_id
                  LEFT JOIN tercero t ON t.id = c.proveedor_id
                  LEFT JOIN producto_presentacion pp ON pp.id = d.producto_presentacion_id
                 WHERE d.producto_id = :productoId AND c.empresa_id = :empresaId
                 ORDER BY c.fecha DESC, c.id DESC
                """;
        return jdbc.query(sql, params(productoId, empresaId),
                new BeanPropertyRowMapper<>(CompraProductoHistorialDto.class));
    }

    private static MapSqlParameterSource params(Long productoId, Integer empresaId) {
        return new MapSqlParameterSource("productoId", productoId).addValue("empresaId", empresaId);
    }
}
