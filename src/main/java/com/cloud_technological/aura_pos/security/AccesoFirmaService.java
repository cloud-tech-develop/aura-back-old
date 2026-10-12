package com.cloud_technological.aura_pos.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.repositories.firma_contable.FirmaContableQueryRepository;

import lombok.RequiredArgsConstructor;

/**
 * ¿Sigue vigente el acceso de un usuario de la firma al cliente? (docs/PLAN_FIRMA_CONTABLE.md, F3).
 * Se pregunta en cada petición que trae {@code vinculoId}; se relee cada
 * {@value #TTL_MS} ms, así que quitar el acceso tarda como mucho eso en cortar la sesión.
 */
@Service
@RequiredArgsConstructor
public class AccesoFirmaService {

    private static final long TTL_MS = 30_000L;

    private final FirmaContableQueryRepository query;

    /** Llave "usuarioId:vinculoId" → [vigente (1/0), expira]. */
    private final Map<String, long[]> cache = new ConcurrentHashMap<>();

    public boolean vigente(Long usuarioId, Long vinculoId) {
        if (usuarioId == null || vinculoId == null) return false;
        String llave = usuarioId + ":" + vinculoId;
        long[] c = cache.get(llave);
        if (c != null && c[1] > System.currentTimeMillis()) return c[0] == 1;
        boolean v = query.accesoVigente(usuarioId.intValue(), vinculoId);
        cache.put(llave, new long[] { v ? 1 : 0, System.currentTimeMillis() + TTL_MS });
        return v;
    }

    /** Tras quitar o cambiar accesos en este servidor: que se note de inmediato. */
    public void invalidar() {
        cache.clear();
    }
}
