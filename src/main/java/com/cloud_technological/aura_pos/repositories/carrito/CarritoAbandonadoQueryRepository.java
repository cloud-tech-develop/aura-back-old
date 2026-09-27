package com.cloud_technological.aura_pos.repositories.carrito;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.carrito.CarritoAbandonadoDtos.Carrito;
import com.cloud_technological.aura_pos.dto.carrito.CarritoAbandonadoDtos.Item;

/** Lectura de los carritos abandonados para el reporte. */
@Repository
public class CarritoAbandonadoQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /** Carritos vaciados en el rango, con sus productos; el más reciente primero. */
    public List<CarritoConCajero> carritos(Integer empresaId, LocalDate desde, LocalDate hasta,
            Integer sucursalId) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("desde", Timestamp.valueOf(desde.atStartOfDay()))
                .addValue("hasta", Timestamp.valueOf(hasta.plusDays(1).atStartOfDay()))
                .addValue("sucursalId", sucursalId);
        String sql = """
            SELECT c.id, c.iniciado_at, c.vaciado_at, c.duracion_segundos, c.motivo, c.items,
                   c.total, c.usuario_id, u.username AS usuario, s.nombre AS sucursal,
                   COALESCE(NULLIF(TRIM(COALESCE(t.razon_social, '')), ''),
                            TRIM(COALESCE(t.nombres, '') || ' ' || COALESCE(t.apellidos, ''))) AS cliente
            FROM carrito_abandonado c
            LEFT JOIN usuario u  ON u.id = c.usuario_id
            LEFT JOIN sucursal s ON s.id = c.sucursal_id
            LEFT JOIN tercero t  ON t.id = c.cliente_id
            WHERE c.empresa_id = :empresaId
              AND c.vaciado_at >= :desde AND c.vaciado_at < :hasta
              AND (CAST(:sucursalId AS INTEGER) IS NULL OR c.sucursal_id = :sucursalId)
            ORDER BY c.vaciado_at DESC
            """;
        List<CarritoConCajero> out = jdbc.query(sql, p, (rs, i) -> {
            Carrito c = new Carrito();
            c.setId(rs.getLong("id"));
            c.setIniciadoAt(rs.getTimestamp("iniciado_at").toLocalDateTime());
            c.setVaciadoAt(rs.getTimestamp("vaciado_at").toLocalDateTime());
            c.setMinutos(java.math.BigDecimal.valueOf(rs.getInt("duracion_segundos"))
                    .divide(java.math.BigDecimal.valueOf(60), 1, java.math.RoundingMode.HALF_UP));
            c.setMotivo(rs.getString("motivo"));
            c.setUsuario(rs.getString("usuario"));
            c.setSucursal(rs.getString("sucursal"));
            String cliente = rs.getString("cliente");
            c.setCliente(cliente != null && !cliente.isBlank() ? cliente : null);
            c.setItems(rs.getInt("items"));
            c.setTotal(rs.getBigDecimal("total"));
            return new CarritoConCajero(c, (Integer) rs.getObject("usuario_id", Integer.class),
                    rs.getInt("duracion_segundos"));
        });
        if (out.isEmpty()) return out;

        Map<Long, Carrito> porId = new HashMap<>();
        List<Long> ids = new ArrayList<>();
        for (CarritoConCajero c : out) {
            porId.put(c.carrito().getId(), c.carrito());
            ids.add(c.carrito().getId());
        }
        jdbc.query("""
            SELECT carrito_id, producto_id, presentacion_id, nombre, cantidad, precio, subtotal, agregado_at
            FROM carrito_abandonado_item WHERE carrito_id IN (:ids) ORDER BY id
            """, new MapSqlParameterSource("ids", ids), rs -> {
                Item it = new Item();
                it.setProductoId((Long) rs.getObject("producto_id", Long.class));
                it.setPresentacionId((Long) rs.getObject("presentacion_id", Long.class));
                it.setNombre(rs.getString("nombre"));
                it.setCantidad(rs.getBigDecimal("cantidad"));
                it.setPrecio(rs.getBigDecimal("precio"));
                it.setSubtotal(rs.getBigDecimal("subtotal"));
                Timestamp ag = rs.getTimestamp("agregado_at");
                it.setAgregadoAt(ag != null ? ag.toLocalDateTime() : null);
                porId.get(rs.getLong("carrito_id")).getDetalle().add(it);
            });
        return out;
    }

    public record CarritoConCajero(Carrito carrito, Integer usuarioId, int segundos) {
    }
}
