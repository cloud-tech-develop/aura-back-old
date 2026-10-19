package com.cloud_technological.aura_pos.repositories.firma_contable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Lecturas de las solicitudes de activación electrónica de los clientes gestionados
 * (V228, docs/PLAN_FIRMA_CONTABLE.md §10).
 */
@Repository
public class ActivacionElectronicaQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private static final String SELECT = """
            SELECT s.id, s.cliente_empresa_id, c.razon_social AS cliente_nombre, c.nit AS cliente_nit,
                   s.firma_empresa_id, f.razon_social AS firma_nombre, f.nit AS firma_nit,
                   s.servicios, s.observacion, s.estado, s.solicitado_at, s.respondido_at, s.motivo,
                   COALESCE(NULLIF(TRIM(CONCAT_WS(' ', t.nombres, t.apellidos)), ''), u.username) AS solicitado_por_nombre,
                   c.factura_electronica
            FROM solicitud_activacion_electronica s
            JOIN empresa c ON c.id = s.cliente_empresa_id
            JOIN empresa f ON f.id = s.firma_empresa_id
            LEFT JOIN usuario u ON u.id = s.solicitado_por
            LEFT JOIN tercero t ON t.id = u.tercero_id
            """;

    /** La última solicitud del cliente (pendiente o respondida), o null. */
    public SolicitudActivacionFila ultimaDelCliente(Integer clienteEmpresaId) {
        List<SolicitudActivacionFila> r = jdbc.query(SELECT + """
            WHERE s.cliente_empresa_id = :clienteId
            ORDER BY s.id DESC
            LIMIT 1
            """, new MapSqlParameterSource("clienteId", clienteEmpresaId), ActivacionElectronicaQueryRepository::fila);
        return r.isEmpty() ? null : r.get(0);
    }

    /** Id de la solicitud pendiente del cliente, o null. */
    public Long pendienteDelCliente(Integer clienteEmpresaId) {
        List<Long> r = jdbc.queryForList("""
            SELECT id FROM solicitud_activacion_electronica
            WHERE cliente_empresa_id = :clienteId AND estado = 'PENDIENTE'
            """, new MapSqlParameterSource("clienteId", clienteEmpresaId), Long.class);
        return r.isEmpty() ? null : r.get(0);
    }

    /** Pendientes de todas las firmas, la más antigua primero (panel de plataforma). */
    public List<SolicitudActivacionFila> pendientes() {
        return jdbc.query(SELECT + """
            WHERE s.estado = 'PENDIENTE'
            ORDER BY s.solicitado_at
            """, new MapSqlParameterSource(), ActivacionElectronicaQueryRepository::fila);
    }

    /** Cuántas esperan respuesta de la plataforma (campana). */
    public int contarPendientes() {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM solicitud_activacion_electronica WHERE estado = 'PENDIENTE'",
                new MapSqlParameterSource(), Integer.class);
        return n != null ? n : 0;
    }

    /** Las que la plataforma respondió a la firma en los últimos días (campana de la firma). */
    public List<SolicitudActivacionFila> respondidasALaFirma(Integer firmaEmpresaId, int dias) {
        return jdbc.query(SELECT + """
            WHERE s.firma_empresa_id = :firmaId
              AND s.estado IN ('ATENDIDA', 'RECHAZADA')
              AND s.respondido_at >= now() - make_interval(days => :dias)
            ORDER BY s.respondido_at DESC
            """, new MapSqlParameterSource("firmaId", firmaEmpresaId).addValue("dias", dias),
                ActivacionElectronicaQueryRepository::fila);
    }

    private static SolicitudActivacionFila fila(ResultSet rs, int i) throws SQLException {
        String servicios = rs.getString("servicios");
        return new SolicitudActivacionFila(rs.getLong("id"), rs.getInt("cliente_empresa_id"),
                rs.getString("cliente_nombre"), rs.getString("cliente_nit"), rs.getInt("firma_empresa_id"),
                rs.getString("firma_nombre"), rs.getString("firma_nit"),
                servicios == null || servicios.isBlank() ? List.of() : Arrays.asList(servicios.split(",")),
                rs.getString("observacion"), rs.getString("estado"),
                rs.getObject("solicitado_at", LocalDateTime.class), rs.getObject("respondido_at", LocalDateTime.class),
                rs.getString("motivo"), rs.getString("solicitado_por_nombre"), rs.getBoolean("factura_electronica"));
    }

    /** Solicitud de activación electrónica de un cliente gestionado (V228). */
    public record SolicitudActivacionFila(Long id, Integer clienteEmpresaId, String clienteNombre, String clienteNit,
            Integer firmaEmpresaId, String firmaNombre, String firmaNit, List<String> servicios, String observacion,
            String estado, LocalDateTime solicitadoAt, LocalDateTime respondidoAt, String motivo,
            String solicitadoPorNombre, boolean facturaElectronicaActiva) {
    }
}
