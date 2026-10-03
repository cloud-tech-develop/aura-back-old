package com.cloud_technological.aura_pos.repositories.compras;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.compras.CompraAcreditableDto;
import com.cloud_technological.aura_pos.dto.compras.CompraAcreditableItemDto;
import com.cloud_technological.aura_pos.dto.compras.CompraDetalleDto;
import com.cloud_technological.aura_pos.dto.compras.CompraDetalleLoteDto;
import com.cloud_technological.aura_pos.dto.compras.CompraTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class CompraQueryRepository {
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    /** Notas crédito no anuladas que cuelgan de la compra. */
    public long notasCreditoVigentes(Long compraId, Integer empresaId) {
        Long n = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM compra
            WHERE compra_origen_id = :compraId AND empresa_id = :empresaId AND estado <> 'ANULADA'
            """, new MapSqlParameterSource("compraId", compraId).addValue("empresaId", empresaId), Long.class);
        return n != null ? n : 0;
    }

    /** true si la compra tiene un documento soporte ya aceptado por la DIAN. */
    public boolean tieneDocumentoSoporteAceptado(Long compraId, Integer empresaId) {
        Long n = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM documento_soporte
            WHERE origen_tipo = 'COMPRA' AND origen_id = :compraId AND empresa_id = :empresaId
              AND estado = 'ACEPTADO'
            """, new MapSqlParameterSource("compraId", compraId).addValue("empresaId", empresaId), Long.class);
        return n != null && n > 0;
    }

    public PageImpl<CompraTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                c.id,
                c.numero_compra,
                COALESCE(NULLIF(t.razon_social, ''), CONCAT(t.nombres, ' ', t.apellidos)) AS proveedor_nombre,
                s.nombre AS sucursal_nombre,
                c.fecha,
                c.total,
                c.estado,
                COALESCE(c.tipo_documento, 'FACTURA_COMPRA') AS tipo_documento,
                c.compra_origen_id,
                COUNT(*) OVER() AS total_rows
            FROM compra c
            INNER JOIN tercero t ON c.proveedor_id = t.id
            INNER JOIN sucursal s ON c.sucursal_id = s.id
            WHERE c.empresa_id = :empresaId
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(c.numero_compra) LIKE :search
                OR LOWER(t.razon_social) LIKE :search
                OR LOWER(t.nombres) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY c.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<CompraTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(CompraTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    /**
     * Las facturas de compra de un proveedor sobre las que todavía se puede
     * emitir una nota crédito.
     *
     * <p>Quedan fuera las anuladas y las notas crédito: acreditar una nota
     * crédito no significa nada. El saldo pendiente viene de la cuenta por
     * pagar, porque de él depende si la NC puede cruzarse contra la deuda.
     *
     * <p>Va paginada y con la búsqueda en el servidor: un proveedor de años
     * tiene miles de facturas, y traerlas todas para filtrarlas en el navegador
     * tumba el formulario.
     *
     * <p>El filtro de proveedor y sucursal viaja en {@code params} porque el
     * selector solo tiene sentido dentro de ellos: la nota crédito debe ser del
     * mismo proveedor y la mercancía sale de la sucursal donde entró.
     */
    public PageImpl<CompraAcreditableDto> facturasAcreditables(PageableDto<Object> pageable,
            Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        Long proveedorId = null;
        Integer sucursalId = null;
        if (pageable.getParams() instanceof java.util.Map<?, ?> paramMap) {
            Object prov = paramMap.get("proveedorId");
            if (prov instanceof Number n) {
                proveedorId = n.longValue();
            } else if (prov != null && !prov.toString().isBlank()) {
                proveedorId = Long.parseLong(prov.toString().trim());
            }
            Object suc = paramMap.get("sucursalId");
            if (suc instanceof Number n) {
                sucursalId = n.intValue();
            } else if (suc != null && !suc.toString().isBlank()) {
                sucursalId = Integer.parseInt(suc.toString().trim());
            }
        }
        if (proveedorId == null) {
            // Sin proveedor no hay nada que listar: devolver "todas las compras
            // de la empresa" sería peor que devolver vacío.
            return new PageImpl<>(List.of(), PageRequest.of(page, size), 0);
        }

        StringBuilder sql = new StringBuilder("""
            SELECT
                c.id,
                c.numero_compra,
                c.fecha,
                c.total,
                c.forma_pago,
                COALESCE((
                    SELECT cp.saldo_pendiente
                    FROM cuentas_pagar cp
                    WHERE cp.compra_id = c.id
                      AND cp.empresa_id = c.empresa_id
                      AND LOWER(COALESCE(cp.estado, '')) <> 'anulada'
                      AND cp.deleted_at IS NULL
                    ORDER BY cp.id DESC
                    LIMIT 1
                ), 0) AS saldo_pendiente,
                COUNT(*) OVER() AS total_rows
            FROM compra c
            WHERE c.empresa_id = :empresaId
              AND c.proveedor_id = :proveedorId
              AND c.estado <> 'ANULADA'
              AND COALESCE(c.tipo_documento, 'FACTURA_COMPRA') <> 'NOTA_CREDITO'
              AND (CAST(:sucursalId AS INTEGER) IS NULL OR c.sucursal_id = :sucursalId)
        """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("proveedorId", proveedorId)
                .addValue("sucursalId", sucursalId);

        if (!search.isEmpty()) {
            // Se busca por el número del proveedor y por el consecutivo interno:
            // el usuario tiene el papel en la mano y teclea lo que ve en él.
            sql.append("""
                AND (LOWER(COALESCE(c.numero_compra, '')) LIKE :search
                     OR CAST(c.id AS TEXT) LIKE :search
                     OR LOWER(COALESCE(c.observaciones, '')) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY c.fecha DESC, c.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", (long) page * size);
        params.addValue("limit", size);

        List<CompraAcreditableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(CompraAcreditableDto.class));
        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    /**
     * Lo que queda por acreditar de cada producto de una factura.
     *
     * <p>El truco está en agrupar la factura junto con sus notas crédito: las
     * cantidades de una NC se guardan en negativo, así que la suma por producto
     * ya es "lo que sigue disponible" sin restar nada a mano.
     */
    public List<CompraAcreditableItemDto> itemsAcreditables(Integer empresaId, Long compraId) {
        String sql = """
            SELECT
                base.producto_id,
                p.nombre AS producto_nombre,
                p.sku AS producto_sku,
                COALESCE(p.maneja_serial, false) AS maneja_serial,
                base.cantidad_disponible,
                base.costo_unitario,
                base.iva_pct,
                base.descuento_pct
            FROM (
                SELECT
                    cd.producto_id,
                    SUM(cd.cantidad) AS cantidad_disponible,
                    MAX(CASE WHEN c.compra_origen_id IS NULL
                             THEN cd.costo_unitario END) AS costo_unitario,
                    MAX(CASE WHEN c.compra_origen_id IS NULL
                                  AND COALESCE(cd.subtotal_linea, 0) <> 0
                             THEN ROUND(COALESCE(cd.impuesto_valor, 0) * 100
                                        / cd.subtotal_linea, 2) END) AS iva_pct,
                    MAX(CASE WHEN c.compra_origen_id IS NULL
                             THEN COALESCE(cd.descuento_pct, 0) END) AS descuento_pct
                FROM compra c
                INNER JOIN compra_detalle cd ON cd.compra_id = c.id
                WHERE c.empresa_id = :empresaId
                  AND c.estado <> 'ANULADA'
                  AND (c.id = :compraId OR c.compra_origen_id = :compraId)
                GROUP BY cd.producto_id
            ) base
            INNER JOIN producto p ON p.id = base.producto_id
            WHERE base.cantidad_disponible > 0
            ORDER BY p.nombre
        """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("compraId", compraId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(CompraAcreditableItemDto.class));
    }

    public List<CompraDetalleDto> obtenerDetalles(Long compraId) {
        String sql = """
            SELECT
                cd.id,
                cd.producto_id,
                p.nombre AS producto_nombre,
                p.sku AS producto_sku,
                cd.cantidad,
                cd.costo_unitario,
                cd.impuesto_valor,
                cd.subtotal_linea,
                cd.descuento_pct,
                cd.descuento_valor,
                cd.precio_venta1,
                cd.precio_venta2,
                cd.precio_venta3,
                cd.producto_presentacion_id,
                pp.nombre            AS presentacion_nombre,
                pp.factor_conversion AS presentacion_factor,
                cd.cantidad_presentacion,
                cd.cantidad_suelta,
                COALESCE(cd.clasificacion, p.clasificacion) AS clasificacion,
                cd.costo_presentacion,
                COALESCE(p.maneja_lotes, false) AS maneja_lotes,
                COALESCE(p.maneja_serial, false) AS maneja_serial,
                um.abreviatura AS unidad_abreviatura
            FROM compra_detalle cd
            INNER JOIN producto p ON cd.producto_id = p.id
            LEFT JOIN producto_presentacion pp ON pp.id = cd.producto_presentacion_id
            LEFT JOIN unidad_medida um ON um.id = p.unidad_medida_base_id
            WHERE cd.compra_id = :compraId
            ORDER BY cd.id
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("compraId", compraId);
        List<CompraDetalleDto> detalles = jdbcTemplate.query(sql, params,
                new BeanPropertyRowMapper<>(CompraDetalleDto.class));

        // Lotes de cada línea, para mostrarlos y para volver a editar la compra.
        List<CompraDetalleLoteDto> lotes = jdbcTemplate.query("""
            SELECT cdl.compra_detalle_id, cdl.lote_id, l.codigo_lote, l.fecha_vencimiento,
                   l.fecha_fabricacion, cdl.cantidad_base
            FROM compra_detalle_lote cdl
            INNER JOIN compra_detalle cd ON cd.id = cdl.compra_detalle_id
            INNER JOIN lote l ON l.id = cdl.lote_id
            WHERE cd.compra_id = :compraId
            ORDER BY cdl.id
        """, params, new BeanPropertyRowMapper<>(CompraDetalleLoteDto.class));
        java.util.Map<Long, List<CompraDetalleLoteDto>> porDetalle = new java.util.HashMap<>();
        for (CompraDetalleLoteDto l : lotes)
            porDetalle.computeIfAbsent(l.getCompraDetalleId(), k -> new java.util.ArrayList<>()).add(l);
        detalles.forEach(d -> d.setLotes(porDetalle.getOrDefault(d.getId(), new java.util.ArrayList<>())));

        // Seriales de cada línea: los que creó la compra o sacó la nota crédito.
        jdbcTemplate.query("""
            SELECT ds.detalle_id, sp.id AS serial_id, sp.serial
            FROM documento_serial ds
            INNER JOIN compra_detalle cd ON cd.id = ds.detalle_id
            INNER JOIN serial_producto sp ON sp.id = ds.serial_id
            WHERE cd.compra_id = :compraId
              AND ds.origen IN ('COMPRA', 'NOTA_CREDITO_COMPRA')
            ORDER BY ds.id
        """, params, rs -> {
            Long detalleId = rs.getLong("detalle_id");
            detalles.stream().filter(d -> d.getId().equals(detalleId)).findFirst().ifPresent(d -> {
                try {
                    d.getSeriales().add(rs.getString("serial"));
                    d.getSerialIds().add(rs.getLong("serial_id"));
                } catch (java.sql.SQLException e) {
                    throw new IllegalStateException(e);
                }
            });
        });
        return detalles;
    }
}
