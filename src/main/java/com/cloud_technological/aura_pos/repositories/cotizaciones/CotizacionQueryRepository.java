package com.cloud_technological.aura_pos.repositories.cotizaciones;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.cotizaciones.CotizacionDetalleDto;
import com.cloud_technological.aura_pos.dto.cotizaciones.CotizacionTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class CotizacionQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<CotizacionTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                c.id,
                c.numero,
                COALESCE(NULLIF(t.razon_social, ''), CONCAT(t.nombres, ' ', t.apellidos), 'Consumidor Final') AS tercero_nombre,
                t.numero_documento AS tercero_documento,
                c.fecha,
                c.fecha_vencimiento,
                c.total,
                c.estado,
                COUNT(*) OVER() AS total_rows
            FROM cotizacion c
            LEFT JOIN tercero t ON c.tercero_id = t.id
            WHERE c.empresa_id = :empresaId
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(c.numero) LIKE :search
                OR LOWER(c.estado) LIKE :search
                OR LOWER(t.razon_social) LIKE :search
                OR LOWER(t.nombres) LIKE :search
                OR LOWER(t.numero_documento) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY c.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<CotizacionTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(CotizacionTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public List<CotizacionDetalleDto> obtenerDetalles(Long cotizacionId) {
        String sql = """
            SELECT
                cd.id,
                cd.producto_id,
                p.nombre AS producto_nombre,
                p.sku AS producto_sku,
                cd.descripcion,
                cd.cantidad,
                cd.precio_unitario,
                cd.iva_porcentaje,
                cd.descuento_valor,
                cd.subtotal,
                COALESCE(ap.aplicado, 0) AS cantidad_aplicada,
                GREATEST(cd.cantidad - COALESCE(ap.aplicado, 0), 0) AS cantidad_pendiente
            FROM cotizacion_detalle cd
            INNER JOIN producto p ON cd.producto_id = p.id
            LEFT JOIN (
                SELECT origen_linea_id, SUM(cantidad) AS aplicado
                FROM documento_relacion
                WHERE origen_tipo = 'COTIZACION' AND estado = 'VIGENTE'
                GROUP BY origen_linea_id
            ) ap ON ap.origen_linea_id = cd.id
            WHERE cd.cotizacion_id = :cotizacionId
            ORDER BY cd.id
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("cotizacionId", cotizacionId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(CotizacionDetalleDto.class));
    }

    /**
     * Bloquea la cotización (FOR UPDATE) mientras se convierte a venta: dos cajas
     * vendiendo la misma cotización a la vez no pueden pasarse del pendiente.
     * Devuelve null si no existe en la empresa.
     */
    public CabeceraBloqueada bloquear(Long cotizacionId, Integer empresaId) {
        String sql = """
            SELECT id, numero, estado, fecha_vencimiento
            FROM cotizacion
            WHERE id = :id AND empresa_id = :empresaId
            FOR UPDATE
        """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", cotizacionId)
                .addValue("empresaId", empresaId);
        List<CabeceraBloqueada> r = jdbcTemplate.query(sql, params, (rs, i) -> new CabeceraBloqueada(
                rs.getLong("id"),
                rs.getString("numero"),
                rs.getString("estado"),
                rs.getDate("fecha_vencimiento") != null ? rs.getDate("fecha_vencimiento").toLocalDate() : null));
        return r.isEmpty() ? null : r.get(0);
    }

    /** Líneas de la cotización con lo justo para validar la conversión. */
    public List<LineaCotizacion> lineas(Long cotizacionId) {
        String sql = """
            SELECT cd.id, cd.producto_id, p.nombre AS producto_nombre, cd.cantidad
            FROM cotizacion_detalle cd
            INNER JOIN producto p ON p.id = cd.producto_id
            WHERE cd.cotizacion_id = :cotizacionId
            ORDER BY cd.id
        """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("cotizacionId", cotizacionId),
                (rs, i) -> new LineaCotizacion(
                        rs.getLong("id"),
                        rs.getLong("producto_id"),
                        rs.getString("producto_nombre"),
                        rs.getBigDecimal("cantidad")));
    }

    /**
     * Vence las cotizaciones PENDIENTE o PARCIAL cuya vigencia ya pasó. Antes se
     * traía la tabla entera (findAll) de todas las empresas y se filtraba en memoria.
     * Una PARCIAL también vence: lo que le falta se cotizó a un precio que ya no rige.
     */
    public int vencerExpiradas(LocalDate hoy) {
        String sql = """
            UPDATE cotizacion SET estado = 'VENCIDA'
            WHERE estado IN ('PENDIENTE', 'PARCIAL')
              AND fecha_vencimiento IS NOT NULL
              AND fecha_vencimiento < :hoy
        """;
        return jdbcTemplate.update(sql, new MapSqlParameterSource("hoy", java.sql.Date.valueOf(hoy)));
    }

    public record CabeceraBloqueada(Long id, String numero, String estado, LocalDate fechaVencimiento) {
    }

    public record LineaCotizacion(Long id, Long productoId, String productoNombre, java.math.BigDecimal cantidad) {
    }

    /** Siguiente número de cotización con candado por empresa (ver VentaQueryRepository). */
    public Long obtenerSiguienteConsecutivo(Integer empresaId) {
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(176176, :empresaId)", params, rs -> null);
        String sql = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(numero, 5) AS BIGINT)), 0) + 1
            FROM cotizacion
            WHERE empresa_id = :empresaId
        """;
        return jdbcTemplate.queryForObject(sql, params, Long.class);
    }
}
