package com.cloud_technological.aura_pos.sync.local;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.sync.dto.SyncDtos;

/** Cola de salida del nodo ({@code sync_outbox}) y su estado ({@code sync_estado}). */
@Repository
public class SyncOutboxQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /** Fila de la cola lista para enviar. */
    public record Pendiente(Long id, String uuid, String tipo, Long idLocal, Integer empresaId, Long usuarioId,
            LocalDateTime fecha, String payload) {
    }

    /**
     * Encola un comando. Corre en la transacción de la operación: si la venta se
     * revierte, el comando también.
     */
    public void insertar(String uuid, String tipo, Long idLocal, Integer empresaId, Long usuarioId,
            LocalDateTime fecha, String payload) {
        jdbc.update("""
                INSERT INTO sync_outbox (uuid, tipo, id_local, empresa_id, usuario_id, fecha, payload)
                VALUES (:uuid, :tipo, :idLocal, :empresaId, :usuarioId, :fecha, :payload)
                """, new MapSqlParameterSource()
                .addValue("uuid", uuid)
                .addValue("tipo", tipo)
                .addValue("idLocal", idLocal)
                .addValue("empresaId", empresaId)
                .addValue("usuarioId", usuarioId)
                .addValue("fecha", Timestamp.valueOf(fecha))
                .addValue("payload", payload));
    }

    /**
     * Comandos por enviar, en el orden en que ocurrieron.
     *
     * @param limite tamaño del lote
     */
    public List<Pendiente> pendientes(int limite) {
        return jdbc.query("""
                SELECT id, uuid, tipo, id_local, empresa_id, usuario_id, fecha, payload
                FROM sync_outbox
                WHERE estado IN ('PENDIENTE', 'ERROR')
                ORDER BY id
                LIMIT :limite
                """, new MapSqlParameterSource("limite", limite),
                (rs, i) -> new Pendiente(rs.getLong("id"), rs.getString("uuid"), rs.getString("tipo"),
                        rs.getObject("id_local", Long.class), rs.getObject("empresa_id", Integer.class),
                        rs.getObject("usuario_id", Long.class),
                        rs.getTimestamp("fecha").toLocalDateTime(), rs.getString("payload")));
    }

    public void marcarEnviado(Long id, String respuesta) {
        jdbc.update("""
                UPDATE sync_outbox
                SET estado = 'ENVIADO', enviado_at = now(), respuesta = :respuesta, ultimo_error = NULL,
                    intentos = intentos + 1
                WHERE id = :id
                """, new MapSqlParameterSource("id", id).addValue("respuesta", respuesta));
    }

    public void marcarError(Long id, String error) {
        jdbc.update("""
                UPDATE sync_outbox
                SET estado = 'ERROR', ultimo_error = :error, intentos = intentos + 1
                WHERE id = :id
                """, new MapSqlParameterSource("id", id).addValue("error", error));
    }

    /**
     * Saca un comando de la cola sin enviarlo. Solo para errores que no se van a
     * resolver reintentando; queda registrado como DESCARTADO.
     *
     * @return true si estaba en ERROR y se descartó
     */
    public boolean descartar(Long id) {
        return jdbc.update("UPDATE sync_outbox SET estado = 'DESCARTADO' WHERE id = :id AND estado = 'ERROR'",
                new MapSqlParameterSource("id", id)) > 0;
    }

    /** Comandos que todavía no llegan a la nube (PENDIENTE o ERROR). */
    public long contarPorEnviar() {
        Long n = jdbc.queryForObject(
                "SELECT count(*) FROM sync_outbox WHERE estado IN ('PENDIENTE', 'ERROR')",
                new MapSqlParameterSource(), Long.class);
        return n == null ? 0 : n;
    }

    public long contarConError() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM sync_outbox WHERE estado = 'ERROR'",
                new MapSqlParameterSource(), Long.class);
        return n == null ? 0 : n;
    }

    /**
     * Últimos comandos de la cola, los pendientes primero.
     *
     * @param limite cuántos
     */
    public List<SyncDtos.FilaCola> ultimos(int limite) {
        return jdbc.query("""
                SELECT id, uuid, tipo, id_local, estado, intentos, ultimo_error, fecha, enviado_at
                FROM sync_outbox
                ORDER BY CASE WHEN estado IN ('PENDIENTE', 'ERROR') THEN 0 ELSE 1 END, id DESC
                LIMIT :limite
                """, new MapSqlParameterSource("limite", limite), new BeanPropertyRowMapper<>(SyncDtos.FilaCola.class));
    }

    // ── sync_estado ────────────────────────────────────────────────────

    public Optional<String> leerEstado(String clave) {
        List<String> v = jdbc.queryForList("SELECT valor FROM sync_estado WHERE clave = :clave",
                new MapSqlParameterSource("clave", clave), String.class);
        return v.isEmpty() ? Optional.empty() : Optional.ofNullable(v.get(0));
    }

    public void guardarEstado(String clave, String valor) {
        jdbc.update("""
                INSERT INTO sync_estado (clave, valor, updated_at) VALUES (:clave, :valor, now())
                ON CONFLICT (clave) DO UPDATE SET valor = EXCLUDED.valor, updated_at = now()
                """, new MapSqlParameterSource("clave", clave).addValue("valor", valor));
    }
}
