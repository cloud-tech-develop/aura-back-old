package com.cloud_technological.aura_pos.repositories.inventario;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.inventario.SerialProductoTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class SerialQueryRepository {
    
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<SerialProductoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                sp.id,
                sp.producto_id,
                p.nombre AS producto_nombre,
                sp.sucursal_id,
                s.nombre AS sucursal_nombre,
                sp.serial,
                sp.estado,
                sp.costo,
                sp.fecha_ingreso,
                sp.garantia_cliente_hasta,
                COALESCE(c.numero_compra, '#' || c.id) AS compra_numero,
                COUNT(*) OVER() AS total_rows
            FROM serial_producto sp
            INNER JOIN producto p ON sp.producto_id = p.id
            INNER JOIN sucursal s ON sp.sucursal_id = s.id
            LEFT JOIN compra_detalle cd ON cd.id = sp.compra_detalle_id
            LEFT JOIN compra c ON c.id = cd.compra_id
            WHERE s.empresa_id = :empresaId
            AND p.deleted_at IS NULL
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(p.nombre) LIKE :search
                OR LOWER(sp.serial) LIKE :search
                OR LOWER(sp.estado) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY sp.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<SerialProductoTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(SerialProductoTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    // Seriales disponibles por producto y sucursal (usado en ventas)
    public List<SerialProductoTableDto> listarDisponiblesPorProducto(Long productoId, Long sucursalId, Integer empresaId) {
        String sql = """
            SELECT
                sp.id,
                sp.producto_id,
                p.nombre AS producto_nombre,
                sp.sucursal_id,
                s.nombre AS sucursal_nombre,
                sp.serial,
                sp.estado
            FROM serial_producto sp
            INNER JOIN producto p ON sp.producto_id = p.id
            INNER JOIN sucursal s ON sp.sucursal_id = s.id
            WHERE sp.producto_id = :productoId
            AND sp.sucursal_id = :sucursalId
            AND s.empresa_id = :empresaId
            AND sp.estado = 'DISPONIBLE'
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("productoId", productoId);
        params.addValue("sucursalId", sucursalId);
        params.addValue("empresaId", empresaId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(SerialProductoTableDto.class));
    }

    /** Serial DISPONIBLE en la sucursal con ese texto (el POS lo escanea como si fuera un código). */
    public java.util.List<com.cloud_technological.aura_pos.dto.inventario.SerialBuscadoDto> buscarDisponible(String codigo,
            Long sucursalId, Integer empresaId) {
        return jdbcTemplate.query("""
            SELECT sp.id AS serial_id, sp.serial, sp.producto_id, p.nombre AS producto_nombre
            FROM serial_producto sp
            INNER JOIN producto p ON p.id = sp.producto_id
            INNER JOIN sucursal s ON s.id = sp.sucursal_id
            WHERE s.empresa_id = :empresaId
              AND sp.sucursal_id = :sucursalId
              AND sp.estado = 'DISPONIBLE'
              AND UPPER(TRIM(sp.serial)) = UPPER(TRIM(:codigo))
              AND p.deleted_at IS NULL
            """, new MapSqlParameterSource("empresaId", empresaId)
                .addValue("sucursalId", sucursalId).addValue("codigo", codigo),
            new BeanPropertyRowMapper<>(com.cloud_technological.aura_pos.dto.inventario.SerialBuscadoDto.class));
    }

    /** Seriales vendidos en una línea que siguen VENDIDOS: los que se pueden devolver. */
    public List<SerialProductoTableDto> vendidosEnLinea(Long ventaDetalleId, Integer empresaId) {
        return jdbcTemplate.query("""
            SELECT sp.id, sp.producto_id, p.nombre AS producto_nombre, sp.sucursal_id,
                   s.nombre AS sucursal_nombre, sp.serial, sp.estado, sp.garantia_cliente_hasta
            FROM venta_detalle_serial vds
            INNER JOIN serial_producto sp ON sp.id = vds.serial_producto_id
            INNER JOIN producto p ON p.id = sp.producto_id
            INNER JOIN sucursal s ON s.id = sp.sucursal_id
            WHERE vds.venta_detalle_id = :ventaDetalleId
              AND s.empresa_id = :empresaId
              AND sp.estado = 'VENDIDO'
            ORDER BY sp.serial
            """, new MapSqlParameterSource("ventaDetalleId", ventaDetalleId).addValue("empresaId", empresaId),
            new BeanPropertyRowMapper<>(SerialProductoTableDto.class));
    }

    /** El serial (puede estar en varios productos) con lo que le pasó, en orden. */
    public List<com.cloud_technological.aura_pos.dto.inventario.SerialTrazaDto> trazabilidad(String serial, Integer empresaId) {
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId).addValue("serial", serial);
        List<com.cloud_technological.aura_pos.dto.inventario.SerialTrazaDto> seriales = jdbcTemplate.query("""
            SELECT sp.id AS serial_id, sp.serial, sp.producto_id, p.nombre AS producto_nombre,
                   s.nombre AS sucursal_nombre, sp.estado, sp.costo, sp.fecha_ingreso, sp.garantia_cliente_hasta
            FROM serial_producto sp
            INNER JOIN producto p ON p.id = sp.producto_id
            INNER JOIN sucursal s ON s.id = sp.sucursal_id
            WHERE s.empresa_id = :empresaId
              AND UPPER(TRIM(sp.serial)) LIKE '%' || UPPER(TRIM(:serial)) || '%'
            ORDER BY sp.serial
            LIMIT 20
            """, params, new BeanPropertyRowMapper<>(com.cloud_technological.aura_pos.dto.inventario.SerialTrazaDto.class));
        if (seriales.isEmpty()) return seriales;

        params.addValue("ids", seriales.stream().map(com.cloud_technological.aura_pos.dto.inventario.SerialTrazaDto::getSerialId).toList());
        List<com.cloud_technological.aura_pos.dto.inventario.SerialEventoDto> eventos = jdbcTemplate.query("""
            SELECT ds.serial_id, c.fecha,
                   CASE ds.origen WHEN 'COMPRA' THEN 'Compra' ELSE 'Nota crédito a proveedor' END AS tipo,
                   COALESCE(c.numero_compra, '#' || c.id) AS documento,
                   NULLIF(COALESCE(t.razon_social, TRIM(CONCAT(t.nombres, ' ', t.apellidos))), '') AS tercero,
                   su.nombre AS sucursal, c.estado AS estado_documento
              FROM documento_serial ds
              JOIN compra_detalle cd ON cd.id = ds.detalle_id
              JOIN compra c ON c.id = cd.compra_id
              LEFT JOIN tercero t ON t.id = c.proveedor_id
              LEFT JOIN sucursal su ON su.id = c.sucursal_id
             WHERE ds.origen IN ('COMPRA', 'NOTA_CREDITO_COMPRA') AND ds.serial_id IN (:ids)
            UNION ALL
            SELECT vds.serial_producto_id, v.fecha_emision, 'Venta',
                   COALESCE(v.prefijo, '') || v.consecutivo,
                   NULLIF(COALESCE(t.razon_social, TRIM(CONCAT(t.nombres, ' ', t.apellidos))), ''),
                   su.nombre, v.estado_venta
              FROM venta_detalle_serial vds
              JOIN venta_detalle vd ON vd.id = vds.venta_detalle_id
              JOIN venta v ON v.id = vd.venta_id
              LEFT JOIN tercero t ON t.id = v.cliente_id
              LEFT JOIN sucursal su ON su.id = v.sucursal_id
             WHERE vds.serial_producto_id IN (:ids)
            UNION ALL
            SELECT ds.serial_id, d.created_at, 'Devolución de cliente', 'DEV-' || d.consecutivo,
                   NULLIF(COALESCE(t.razon_social, TRIM(CONCAT(t.nombres, ' ', t.apellidos))), ''),
                   su.nombre, d.estado
              FROM documento_serial ds
              JOIN devolucion_detalle dd ON dd.id = ds.detalle_id
              JOIN devolucion d ON d.id = dd.devolucion_id
              LEFT JOIN tercero t ON t.id = d.cliente_id
              LEFT JOIN sucursal su ON su.id = d.sucursal_id
             WHERE ds.origen = 'DEVOLUCION' AND ds.serial_id IN (:ids)
            UNION ALL
            SELECT ds.serial_id, m.fecha, 'Merma', '#' || m.id, NULL, su.nombre, m.estado
              FROM documento_serial ds
              JOIN merma_detalle md ON md.id = ds.detalle_id
              JOIN merma m ON m.id = md.merma_id
              LEFT JOIN sucursal su ON su.id = m.sucursal_id
             WHERE ds.origen = 'MERMA' AND ds.serial_id IN (:ids)
            UNION ALL
            SELECT ds.serial_id, o.fecha, 'Obsequio', '#' || o.id,
                   NULLIF(COALESCE(t.razon_social, TRIM(CONCAT(t.nombres, ' ', t.apellidos))), ''),
                   su.nombre, o.estado
              FROM documento_serial ds
              JOIN obsequio_detalle od ON od.id = ds.detalle_id
              JOIN obsequio o ON o.id = od.obsequio_id
              LEFT JOIN tercero t ON t.id = o.tercero_id
              LEFT JOIN sucursal su ON su.id = o.sucursal_id
             WHERE ds.origen = 'OBSEQUIO' AND ds.serial_id IN (:ids)
            UNION ALL
            SELECT ds.serial_id, ci.fecha, 'Consumo interno', '#' || ci.id,
                   NULLIF(COALESCE(t.razon_social, TRIM(CONCAT(t.nombres, ' ', t.apellidos))), ''),
                   su.nombre, ci.estado
              FROM documento_serial ds
              JOIN consumo_interno_detalle cid ON cid.id = ds.detalle_id
              JOIN consumo_interno ci ON ci.id = cid.consumo_interno_id
              LEFT JOIN tercero t ON t.id = ci.responsable_tercero_id
              LEFT JOIN sucursal su ON su.id = ci.sucursal_id
             WHERE ds.origen = 'CONSUMO_INTERNO' AND ds.serial_id IN (:ids)
            UNION ALL
            SELECT ds.serial_id, tr.fecha, 'Traslado', '#' || tr.id,
                   so.nombre || ' → ' || sd.nombre, so.nombre, tr.estado
              FROM documento_serial ds
              JOIN traslado_detalle td ON td.id = ds.detalle_id
              JOIN traslado tr ON tr.id = td.traslado_id
              LEFT JOIN sucursal so ON so.id = tr.sucursal_origen_id
              LEFT JOIN sucursal sd ON sd.id = tr.sucursal_destino_id
             WHERE ds.origen = 'TRASLADO' AND ds.serial_id IN (:ids)
            ORDER BY 2
            """, params, new BeanPropertyRowMapper<>(com.cloud_technological.aura_pos.dto.inventario.SerialEventoDto.class));
        for (var s : seriales)
            s.setEventos(eventos.stream().filter(e -> e.getSerialId().equals(s.getSerialId())).toList());
        return seriales;
    }
}
