package com.cloud_technological.aura_pos.repositories.permisos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Registro de lo que el control de permisos bloqueó o habría bloqueado (V191).
 *
 * <p>Una fila por usuario, ruta (patrón), día y modo, con contador. Para no
 * escribir en la base en cada petición repetida (el POS consulta seguido), una
 * misma fila se escribe como mucho una vez por minuto y suma las veces acumuladas.
 * Un fallo al registrar nunca tumba la petición.
 */
@Repository
public class PermisoBloqueoLogRepository {

    private static final Logger log = LoggerFactory.getLogger(PermisoBloqueoLogRepository.class);
    private static final long INTERVALO_MS = 60_000L;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private final Map<String, Pendiente> pendientes = new ConcurrentHashMap<>();

    public void registrar(Integer empresaId, Integer usuarioId, String modo, String metodo, String ruta,
            String clave, String accion) {
        String llave = usuarioId + "|" + metodo + "|" + ruta + "|" + modo + "|" + java.time.LocalDate.now();
        Pendiente p = pendientes.computeIfAbsent(llave, k -> new Pendiente());
        int acumuladas = p.veces.incrementAndGet();
        long ahora = System.currentTimeMillis();
        if (p.ultimaEscritura != 0 && ahora - p.ultimaEscritura < INTERVALO_MS) return;
        p.ultimaEscritura = ahora;
        p.veces.addAndGet(-acumuladas);
        try {
            jdbc.update("""
                INSERT INTO permiso_bloqueo_log (empresa_id, usuario_id, modo, metodo, ruta, clave, accion, veces)
                VALUES (:empresaId, :usuarioId, :modo, :metodo, :ruta, :clave, :accion, :veces)
                ON CONFLICT (usuario_id, metodo, ruta, fecha, modo)
                DO UPDATE SET veces = permiso_bloqueo_log.veces + EXCLUDED.veces, ultima_vez = now(),
                              clave = EXCLUDED.clave, accion = EXCLUDED.accion
                """, new MapSqlParameterSource()
                    .addValue("empresaId", empresaId)
                    .addValue("usuarioId", usuarioId)
                    .addValue("modo", modo)
                    .addValue("metodo", metodo)
                    .addValue("ruta", ruta.length() > 300 ? ruta.substring(0, 300) : ruta)
                    .addValue("clave", clave)
                    .addValue("accion", accion)
                    .addValue("veces", acumuladas));
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar el control de permisos de {} {}: {}", metodo, ruta, e.getMessage());
        }
        if (pendientes.size() > 20_000) pendientes.clear(); // de un día para otro, las llaves viejas sobran
    }

    private static final class Pendiente {
        final AtomicInteger veces = new AtomicInteger();
        volatile long ultimaEscritura;
    }
}
