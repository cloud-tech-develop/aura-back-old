package com.cloud_technological.aura_pos.repositories.factura_venta;

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

import com.cloud_technological.aura_pos.dto.factura_venta.FacturaVentaDtos;
import com.cloud_technological.aura_pos.services.permisos.AlcanceSede;
import com.cloud_technological.aura_pos.utils.PageableDto;

/**
 * Consultas de Facturación. Cuidado: los RowMapper son por nombre de columna,
 * así que una columna del SELECT que no esté en el DTO no viaja, sin error.
 */
@Repository
public class FacturaVentaQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /** Solo sus sedes, si el perfil no tiene todas (PLAN_PERMISOS P9). */
    @Autowired
    private AlcanceSede alcanceSede;

    private static final String CLIENTE = """
        COALESCE(NULLIF(TRIM(COALESCE(t.razon_social, '')), ''),
                 TRIM(COALESCE(t.nombres, '') || ' ' || COALESCE(t.apellidos, '')))
        """;

    public PageImpl<FacturaVentaDtos.Fila> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";
        String estado = null;
        if (pageable.getParams() instanceof Map<?, ?> p && p.get("estado") != null
                && !String.valueOf(p.get("estado")).isBlank()) {
            estado = String.valueOf(p.get("estado")).trim().toUpperCase();
        }

        StringBuilder sql = new StringBuilder("""
            SELECT f.id, f.estado,
                   CASE WHEN v.id IS NOT NULL THEN COALESCE(v.prefijo, '') || v.consecutivo END AS numero,
                   COALESCE(v.fecha_emision, f.created_at) AS fecha,
            """ + CLIENTE + """
                   AS cliente_nombre,
                   t.numero_documento AS cliente_documento,
                   s.nombre AS sucursal_nombre,
                   f.forma_pago,
                   cp.nombre AS condicion_pago_nombre,
                   f.fecha_vencimiento,
                   COALESCE(v.total_pagar, f.total) AS total,
                   v.saldo_pendiente,
                   v.estado_dian,
                   f.venta_id,
                   COUNT(*) OVER() AS total_rows
              FROM factura_venta f
              JOIN tercero t   ON t.id = f.cliente_id
              JOIN sucursal s  ON s.id = f.sucursal_id
              LEFT JOIN condicion_pago cp ON cp.id = f.condicion_pago_id
              LEFT JOIN venta v ON v.id = f.venta_id
             WHERE f.empresa_id = :empresaId
            """);
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        sql.append(alcanceSede.filtro("f.sucursal_id", params));

        if (estado != null) {
            sql.append(" AND f.estado = :estado ");
            params.addValue("estado", estado);
        }
        if (!search.isEmpty()) {
            sql.append(" AND (LOWER(").append(CLIENTE).append(") LIKE :search")
               .append(" OR t.numero_documento LIKE :search")
               .append(" OR LOWER(COALESCE(v.prefijo, '') || COALESCE(v.consecutivo::text, '')) LIKE :search")
               .append(" OR LOWER(COALESCE(f.orden_compra, '')) LIKE :search) ");
            params.addValue("search", "%" + search + "%");
        }
        sql.append(" ORDER BY f.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<FacturaVentaDtos.Fila> list = jdbc.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(FacturaVentaDtos.Fila.class));
        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public Optional<FacturaVentaDtos.Detalle> detalle(Long id, Integer empresaId) {
        String sql = """
            SELECT f.id, f.estado, f.sucursal_id, s.nombre AS sucursal_nombre,
                   f.bodega_id, b.nombre AS bodega_nombre,
                   f.cliente_id,
            """ + CLIENTE + """
                   AS cliente_nombre,
                   t.numero_documento AS cliente_documento,
                   f.vendedor_id,
                   COALESCE(NULLIF(TRIM(COALESCE(tv.razon_social, '')), ''),
                            NULLIF(TRIM(COALESCE(tv.nombres, '') || ' ' || COALESCE(tv.apellidos, '')), ''),
                            u.username) AS vendedor_nombre,
                   f.condicion_pago_id, cp.nombre AS condicion_pago_nombre,
                   f.forma_pago, f.metodo_pago,
                   f.cuenta_bancaria_id, cb.nombre AS cuenta_bancaria_nombre,
                   cb.banco AS cuenta_bancaria_banco, cb.tipo AS cuenta_bancaria_tipo,
                   cb.numero_cuenta AS cuenta_bancaria_numero,
                   f.fecha_vencimiento, f.orden_compra, f.notas,
                   f.centro_costo_id, cc.nombre AS centro_costo_nombre,
                   f.cotizacion_id, cot.numero AS cotizacion_numero,
                   f.pedido_vendedor_id, pv.numero_pedido AS pedido_numero,
                   f.aiu, f.aiu_administracion_pct, f.aiu_imprevistos_pct, f.aiu_utilidad_pct, f.aiu_iva_pct,
                   f.subtotal, f.descuento_total, f.impuestos_total, f.total,
                   f.created_at, f.emitida_at,
                   f.venta_id,
                   CASE WHEN v.id IS NOT NULL THEN COALESCE(v.prefijo, '') || v.consecutivo END AS numero,
                   v.estado_venta, v.saldo_pendiente, v.cufe, v.estado_dian, v.factus_numero,
                   v.qr_data, v.factus_url, v.fecha_emision
              FROM factura_venta f
              JOIN tercero t   ON t.id = f.cliente_id
              JOIN sucursal s  ON s.id = f.sucursal_id
              LEFT JOIN bodega b ON b.id = f.bodega_id
              LEFT JOIN usuario u ON u.id = f.vendedor_id
              LEFT JOIN tercero tv ON tv.id = u.tercero_id
              LEFT JOIN condicion_pago cp ON cp.id = f.condicion_pago_id
              LEFT JOIN cuenta_bancaria cb ON cb.id = f.cuenta_bancaria_id
              LEFT JOIN venta v ON v.id = f.venta_id
              LEFT JOIN centros_costos cc ON cc.id = f.centro_costo_id
              LEFT JOIN cotizacion cot ON cot.id = f.cotizacion_id
              LEFT JOIN pedido_vendedor pv ON pv.id = f.pedido_vendedor_id
             WHERE f.id = :id AND f.empresa_id = :empresaId
            """;
        List<FacturaVentaDtos.Detalle> r = jdbc.query(sql,
                new MapSqlParameterSource("id", id).addValue("empresaId", empresaId),
                new BeanPropertyRowMapper<>(FacturaVentaDtos.Detalle.class));
        return r.stream().findFirst();
    }

    public List<FacturaVentaDtos.LineaDto> lineas(Long facturaId) {
        return jdbc.query("""
            SELECT d.id, d.producto_id, p.nombre AS producto_nombre, p.sku AS producto_sku,
                   um.abreviatura AS unidad_abreviatura, p.maneja_inventario,
                   d.producto_presentacion_id, pp.nombre AS presentacion_nombre,
                   d.descripcion, d.cantidad, d.precio_unitario, d.descuento_valor,
                   d.impuesto_porcentaje, d.impuesto_valor, d.subtotal_linea, d.aiu_tipo,
                   d.cotizacion_detalle_id
              FROM factura_venta_detalle d
              JOIN producto p ON p.id = d.producto_id
              LEFT JOIN unidad_medida um ON um.id = p.unidad_medida_base_id
              LEFT JOIN producto_presentacion pp ON pp.id = d.producto_presentacion_id
             WHERE d.factura_venta_id = :id
             ORDER BY d.orden, d.id
            """, new MapSqlParameterSource("id", facturaId),
                new BeanPropertyRowMapper<>(FacturaVentaDtos.LineaDto.class));
    }

    public List<Long> idsLineas(Long facturaId) {
        return jdbc.queryForList("SELECT id FROM factura_venta_detalle WHERE factura_venta_id = :id",
                new MapSqlParameterSource("id", facturaId), Long.class);
    }

    public List<FacturaVentaDtos.CondicionPago> condiciones(Integer empresaId, boolean soloActivas) {
        return jdbc.query("""
            SELECT id, nombre, dias, activa
              FROM condicion_pago
             WHERE empresa_id = :empresaId
            """ + (soloActivas ? " AND activa = TRUE" : "") + " ORDER BY dias, nombre",
                new MapSqlParameterSource("empresaId", empresaId),
                new BeanPropertyRowMapper<>(FacturaVentaDtos.CondicionPago.class));
    }

    public boolean existeCondicion(Integer empresaId, String nombre, Long excepto) {
        Boolean b = jdbc.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM condicion_pago
                            WHERE empresa_id = :e AND LOWER(nombre) = LOWER(:n)
                              AND (CAST(:x AS BIGINT) IS NULL OR id <> :x))
            """, new MapSqlParameterSource("e", empresaId).addValue("n", nombre.trim()).addValue("x", excepto),
                Boolean.class);
        return Boolean.TRUE.equals(b);
    }
}
