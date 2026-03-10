package com.cloud_technological.aura_pos.repositories.ventas;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.ventas.VentaDetalleDto;
import com.cloud_technological.aura_pos.dto.ventas.VentaPagoDto;
import com.cloud_technological.aura_pos.dto.ventas.VentaTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class VentaQueryRepository {
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<VentaTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                v.id,
                v.prefijo,
                v.consecutivo,
                COALESCE(NULLIF(t.razon_social, ''), CONCAT(t.nombres, ' ', t.apellidos), 'Consumidor Final') AS cliente_nombre,
                s.nombre AS sucursal_nombre,
                v.fecha_emision,
                v.total_pagar,
                v.estado_venta,
                v.tipo_documento,
                v.estado_dian,
                v.factus_url,
                v.cufe,
                v.factus_numero,
                COUNT(*) OVER() AS total_rows
            FROM venta v
            INNER JOIN sucursal s ON v.sucursal_id = s.id
            LEFT JOIN tercero t ON v.cliente_id = t.id
            WHERE v.empresa_id = :empresaId
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(v.prefijo) LIKE :search
                OR CAST(v.consecutivo AS TEXT) LIKE :search
                OR LOWER(t.razon_social) LIKE :search
                OR LOWER(t.nombres) LIKE :search
                OR LOWER(v.estado_venta) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY v.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<VentaTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(VentaTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public List<VentaDetalleDto> obtenerDetalles(Long ventaId) {
        String sql = """
            SELECT
                vd.id,
                vd.producto_id,
                p.nombre AS producto_nombre,
                p.sku AS producto_sku,
                vd.producto_presentacion_id,
                pp.nombre AS presentacion_nombre,
                vd.lote_id,
                l.codigo_lote,
                vd.cantidad,
                vd.precio_unitario,
                vd.monto_descuento,
                vd.impuesto_valor,
                vd.subtotal_linea
            FROM venta_detalle vd
            INNER JOIN producto p ON vd.producto_id = p.id
            LEFT JOIN producto_presentacion pp ON vd.producto_presentacion_id = pp.id
            LEFT JOIN lote l ON vd.lote_id = l.id
            WHERE vd.venta_id = :ventaId
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("ventaId", ventaId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(VentaDetalleDto.class));
    }

    public List<VentaPagoDto> obtenerPagos(Long ventaId) {
        String sql = """
            SELECT id, metodo_pago, monto, referencia
            FROM venta_pago
            WHERE venta_id = :ventaId
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("ventaId", ventaId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(VentaPagoDto.class));
    }

    // Obtener consecutivo siguiente por sucursal
    public Long obtenerSiguienteConsecutivo(Long sucursalId) {
        try {
            String sql = """
            SELECT COALESCE(MAX(consecutivo), 0) + 1
            FROM venta
            WHERE sucursal_id = :sucursalId
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("sucursalId", sucursalId);
        return jdbcTemplate.queryForObject(sql, params, Long.class);
        } catch (Exception e) {
            return 1L; // Si ocurre un error, retornar 1 como el primer consecutivo
        }
    }
}
