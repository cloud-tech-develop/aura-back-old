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

    /** Solo sus sedes, si el perfil no tiene todas (PLAN_PERMISOS P9). */
    @Autowired
    private com.cloud_technological.aura_pos.services.permisos.AlcanceSede alcanceSede;

    public PageImpl<VentaTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                v.id,
                v.prefijo,
                v.consecutivo,
                CASE
                    WHEN NULLIF(TRIM(v.prefijo), '') IS NOT NULL
                        THEN CONCAT(v.prefijo, '-', v.consecutivo)
                    ELSE CAST(v.consecutivo AS TEXT)
                END AS numero_venta,
                COALESCE(
                    NULLIF(TRIM(t.razon_social), ''),
                    NULLIF(TRIM(CONCAT(COALESCE(t.nombres, ''), ' ', COALESCE(t.apellidos, ''))), ''),
                    'Consumidor Final'
                ) AS cliente_nombre,
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
        sql.append(alcanceSede.filtro("v.sucursal_id", params));

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(v.prefijo) LIKE :search
                OR CAST(v.consecutivo AS TEXT) LIKE :search
                OR LOWER(v.factus_numero) LIKE :search
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
                vd.subtotal_linea,
                um.nombre AS unidad_medida_nombre
            FROM venta_detalle vd
            INNER JOIN producto p ON vd.producto_id = p.id
            LEFT JOIN unidad_medida um ON p.unidad_medida_base_id = um.id
            LEFT JOIN producto_presentacion pp ON vd.producto_presentacion_id = pp.id
            LEFT JOIN lote l ON vd.lote_id = l.id
            WHERE vd.venta_id = :ventaId
              AND vd.cantidad > 0
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("ventaId", ventaId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(VentaDetalleDto.class));
    }

    public List<VentaPagoDto> obtenerPagos(Long ventaId) {
        String sql = """
            SELECT id, metodo_pago, monto, monto_recibido, referencia
            FROM venta_pago
            WHERE venta_id = :ventaId
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("ventaId", ventaId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(VentaPagoDto.class));
    }

    /**
     * Reserva la venta para enviarla a la DIAN: la pasa a ENVIANDO solo si no
     * está emitida ni en envío. Devuelve 0 si otro envío ya la tomó. La fila
     * queda bloqueada hasta el fin de la transacción, así que un doble clic
     * espera y después encuentra la venta ya EMITIDA.
     */
    public int reservarEnvioFe(Long ventaId, Integer empresaId) {
        return jdbcTemplate.update("""
            UPDATE venta SET estado_dian = 'ENVIANDO'
            WHERE id = :ventaId AND empresa_id = :empresaId
              AND (estado_dian IS NULL OR estado_dian NOT IN ('EMITIDA', 'ENVIANDO'))
            """, new MapSqlParameterSource("ventaId", ventaId).addValue("empresaId", empresaId));
    }

    /** Pagos de la venta (método y monto), para la forma y el medio de pago de la FE. */
    public record PagoVenta(String metodoPago, java.math.BigDecimal monto) {}

    public List<PagoVenta> pagosDeVenta(Long ventaId) {
        return jdbcTemplate.query(
                "SELECT metodo_pago, monto FROM venta_pago WHERE venta_id = :ventaId ORDER BY monto DESC",
                new MapSqlParameterSource("ventaId", ventaId),
                (rs, i) -> new PagoVenta(rs.getString("metodo_pago"), rs.getBigDecimal("monto")));
    }

    /** Vencimiento de la venta a crédito: el de su cuenta por cobrar (o null). */
    public java.time.LocalDate vencimientoCredito(Long ventaId) {
        List<java.time.LocalDate> r = jdbcTemplate.query("""
            SELECT fecha_vencimiento::date AS vence FROM cuentas_cobrar
            WHERE venta_id = :ventaId AND fecha_vencimiento IS NOT NULL
            ORDER BY id DESC LIMIT 1
            """, new MapSqlParameterSource("ventaId", ventaId),
            (rs, i) -> rs.getObject("vence", java.time.LocalDate.class));
        return r.isEmpty() ? null : r.get(0);
    }

    /** Devoluciones no anuladas de la venta. */
    public long devolucionesVigentes(Long ventaId) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM devolucion WHERE venta_id = :ventaId AND estado <> 'ANULADA'",
                new MapSqlParameterSource("ventaId", ventaId), Long.class);
        return n != null ? n : 0;
    }

    /** Comisiones de la venta que ya entraron en una liquidación. */
    public long comisionesLiquidadas(Long ventaId) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comision_venta WHERE venta_id = :ventaId AND liquidacion_id IS NOT NULL",
                new MapSqlParameterSource("ventaId", ventaId), Long.class);
        return n != null ? n : 0;
    }

    /** Pago de la venta que entró a una cuenta bancaria. */
    public record PagoBancario(Long cuentaBancariaId, java.math.BigDecimal monto, String referencia) {}

    public List<PagoBancario> pagosBancarios(Long ventaId) {
        return jdbcTemplate.query("""
            SELECT cuenta_bancaria_id, monto, referencia
            FROM venta_pago
            WHERE venta_id = :ventaId AND cuenta_bancaria_id IS NOT NULL AND monto > 0
            """, new MapSqlParameterSource("ventaId", ventaId),
            (rs, i) -> new PagoBancario(rs.getLong("cuenta_bancaria_id"),
                    rs.getBigDecimal("monto"), rs.getString("referencia")));
    }

    /**
     * Siguiente consecutivo de venta de la sucursal, con candado de transacción
     * por sucursal: dos cajas cobrando a la vez leían el mismo MAX y salían dos
     * facturas con el mismo número. El candado se suelta al terminar la
     * transacción de la venta.
     *
     * <p>Antes, si la consulta fallaba devolvía 1 y la venta salía con un
     * número repetido; ahora el error sube y la venta no se guarda.
     */
    public Long obtenerSiguienteConsecutivo(Long sucursalId) {
        MapSqlParameterSource params = new MapSqlParameterSource("sucursalId", sucursalId);
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(175175, CAST(:sucursalId AS INTEGER))", params, rs -> null);
        String sql = """
            SELECT COALESCE(MAX(consecutivo), 0) + 1
            FROM venta
            WHERE sucursal_id = :sucursalId
        """;
        return jdbcTemplate.queryForObject(sql, params, Long.class);
    }
}
