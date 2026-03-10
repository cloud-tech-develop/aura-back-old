package com.cloud_technological.aura_pos.repositories.turno_caja;

import java.math.BigDecimal;
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

import com.cloud_technological.aura_pos.dto.caja.TurnoCajaDto;
import com.cloud_technological.aura_pos.dto.caja.TurnoCajaTableDto;
import com.cloud_technological.aura_pos.dto.caja.VentaCategoriaDto;
import com.cloud_technological.aura_pos.dto.caja.VentaMetodoPagoDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class TurnoCajaQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<TurnoCajaTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                t.id,
                t.caja_id,
                c.nombre AS caja_nombre,
                u.username AS usuario_nombre,
                t.fecha_apertura,
                t.fecha_cierre,
                t.base_inicial,
                t.total_efectivo_sistema,
                t.total_efectivo_real,
                CASE 
                    WHEN t.estado = 'CERRADA' OR t.estado = 'CERRADA_AUTO' THEN t.diferencia
                    ELSE t.base_inicial + COALESCE((
                        SELECT SUM(vp.monto)
                        FROM venta_pago vp
                        INNER JOIN venta v ON vp.venta_id = v.id
                        WHERE v.turno_caja_id = t.id
                        AND vp.metodo_pago = 'EFECTIVO'
                        AND v.estado_venta = 'COMPLETADA'
                    ), 0) + COALESCE((
                        SELECT SUM(ac.monto)
                        FROM abonos_cobrar ac
                        WHERE ac.turno_caja_id = t.id
                    ), 0) - COALESCE((
                        SELECT SUM(ap.monto)
                        FROM abonos_pagar ap
                        WHERE ap.turno_caja_id = t.id
                    ), 0)
                END AS diferencia,
                t.estado,
                COUNT(*) OVER() AS total_rows
            FROM turno_caja t
            INNER JOIN caja c ON t.caja_id = c.id
            INNER JOIN sucursal s ON c.sucursal_id = s.id
            INNER JOIN usuario u ON t.usuario_id = u.id
            WHERE s.empresa_id = :empresaId
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(c.nombre) LIKE :search
                OR LOWER(u.username) LIKE :search
                OR LOWER(t.estado) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY t.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<TurnoCajaTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(TurnoCajaTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    // Para el cierre: sumar todo el efectivo recibido en el turno
    public BigDecimal calcularTotalEfectivoSistema(Long turnoId) {
        String sql = """
            SELECT COALESCE(SUM(vp.monto), 0)
            FROM venta_pago vp
            INNER JOIN venta v ON vp.venta_id = v.id
            WHERE v.turno_caja_id = :turnoId
            AND vp.metodo_pago = 'EFECTIVO'
            AND v.estado_venta = 'COMPLETADA'
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("turnoId", turnoId);
        BigDecimal total = jdbcTemplate.queryForObject(sql, params, BigDecimal.class);
        return total != null ? total : BigDecimal.ZERO;
    }

    // Turno activo del usuario para el POS
    public Optional<TurnoCajaDto> obtenerTurnoActivo(Long usuarioId) {
        String sql = """
            SELECT
                t.id,
                t.caja_id,
                c.nombre AS caja_nombre,
                t.usuario_id,
                u.username AS usuario_nombre,
                t.fecha_apertura,
                t.fecha_cierre,
                t.base_inicial,
                t.total_efectivo_sistema,
                t.total_efectivo_real,
                t.diferencia,
                t.estado
            FROM turno_caja t
            INNER JOIN caja c ON t.caja_id = c.id
            INNER JOIN usuario u ON t.usuario_id = u.id
            WHERE t.usuario_id = :usuarioId
            AND t.estado = 'ABIERTA'
            LIMIT 1
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("usuarioId", usuarioId);
        List<TurnoCajaDto> list = jdbcTemplate.query(sql, params,
                new BeanPropertyRowMapper<>(TurnoCajaDto.class));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    // Ventas agrupadas por categoría del turno
    public List<VentaCategoriaDto> ventasPorCategoria(Long turnoId) {
        String sql = """
            SELECT
                cat.id                                AS categoria_id,
                COALESCE(cat.nombre, 'Sin categoría') AS categoria_nombre,
                SUM(vd.cantidad)::INT                 AS total_productos_vendidos,
                SUM(vd.precio_unitario * vd.cantidad) AS total_bruto,
                SUM(vd.monto_descuento)               AS total_descuentos,
                SUM(vd.subtotal_linea)                AS total_neto
            FROM venta v
            JOIN venta_detalle vd  ON vd.venta_id   = v.id
            JOIN producto      p   ON p.id           = vd.producto_id
            LEFT JOIN categoria cat ON cat.id        = p.categoria_id
            WHERE v.turno_caja_id = :turnoId
              AND v.estado_venta  = 'COMPLETADA'
            GROUP BY cat.id, cat.nombre
            ORDER BY total_neto DESC
            """;
        return jdbcTemplate.query(sql,
            new MapSqlParameterSource("turnoId", turnoId),
            new BeanPropertyRowMapper<>(VentaCategoriaDto.class));
    }

    // Ventas agrupadas por método de pago del turno
    public List<VentaMetodoPagoDto> ventasPorMetodoPago(Long turnoId) {
        String sql = """
            SELECT
                vp.metodo_pago,
                COUNT(vp.id)::INT AS total_pagos,
                SUM(vp.monto)     AS total_monto
            FROM venta v
            JOIN venta_pago vp ON vp.venta_id   = v.id
            WHERE v.turno_caja_id = :turnoId
              AND v.estado_venta  = 'COMPLETADA'
            GROUP BY vp.metodo_pago
            ORDER BY total_monto DESC
            """;
        return jdbcTemplate.query(sql,
            new MapSqlParameterSource("turnoId", turnoId),
            new BeanPropertyRowMapper<>(VentaMetodoPagoDto.class));
    }

    // Totales generales del turno
    public Map<String, Object> totalesGenerales(Long turnoId) {
        String sql = """
            SELECT
                COALESCE(SUM(v.subtotal),        0) AS total_ventas_bruto,
                COALESCE(SUM(v.descuento_total), 0) AS total_descuentos,
                COALESCE(SUM(v.impuestos_total), 0) AS total_impuestos,
                COALESCE(SUM(v.total_pagar),     0) AS total_neto,
                COUNT(v.id)::INT                    AS total_transacciones
            FROM venta v
            WHERE v.turno_caja_id = :turnoId
              AND v.estado_venta  = 'COMPLETADA'
            """;
        return jdbcTemplate.queryForMap(sql, new MapSqlParameterSource("turnoId", turnoId));
    }
}
