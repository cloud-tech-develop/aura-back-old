package com.cloud_technological.aura_pos.security;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.repositories.auth.AuthQueryRepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;

import lombok.RequiredArgsConstructor;

/**
 * Control de sesión (fase P10 de docs/PLAN_PERMISOS.md).
 *
 * <ul>
 * <li><b>Revocar</b>: el token lleva {@code tv} = {@code usuario.token_version}. Subirla
 * (desactivar, cambiar la clave o las sedes, "Cerrar sesiones") hace que las
 * sesiones abiertas reciban 401 en su próxima petición.</li>
 * <li><b>Bloqueo</b>: {@value #MAX_INTENTOS} claves erradas seguidas bloquean el
 * usuario {@value #MINUTOS_BLOQUEO} minutos.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SesionService {

    public static final int MAX_INTENTOS = 5;
    public static final int MINUTOS_BLOQUEO = 15;
    /** La versión se relee cada tanto: revocar tarda como mucho esto en otro servidor. */
    private static final long TTL_MS = 30_000L;

    private final AuthQueryRepository query;
    private final UsuarioJPARepository usuarios;

    private final Map<Long, long[]> cache = new ConcurrentHashMap<>();

    /** Versión vigente; un token con otra está revocado. null = el usuario no existe. */
    public Integer versionActual(Long usuarioId) {
        long[] c = cache.get(usuarioId);
        if (c != null && c[1] > System.currentTimeMillis()) return (int) c[0];
        Integer v = query.tokenVersion(usuarioId);
        if (v == null) return null;
        cache.put(usuarioId, new long[] { v, System.currentTimeMillis() + TTL_MS });
        return v;
    }

    /** Cierra todas las sesiones abiertas del usuario. */
    public void revocar(Integer usuarioId) {
        UsuarioEntity u = usuarios.findById(usuarioId).orElse(null);
        if (u == null) return;
        revocar(u);
    }

    /** Igual, sobre la entidad que el llamador ya va a guardar. */
    public void revocar(UsuarioEntity u) {
        u.setTokenVersion((u.getTokenVersion() != null ? u.getTokenVersion() : 0) + 1);
        usuarios.save(u);
        cache.remove(Long.valueOf(u.getId()));
    }

    /** Hasta cuándo está bloqueado, o null si puede entrar. */
    public LocalDateTime bloqueadoHasta(UsuarioEntity u) {
        LocalDateTime h = u.getBloqueadoHasta();
        return h != null && h.isAfter(LocalDateTime.now()) ? h : null;
    }

    /** Suma un intento fallido; al llegar al máximo lo bloquea. Devuelve true si quedó bloqueado. */
    public boolean registrarFallo(UsuarioEntity u) {
        int n = (u.getIntentosFallidos() != null ? u.getIntentosFallidos() : 0) + 1;
        boolean bloquea = n >= MAX_INTENTOS;
        u.setIntentosFallidos(bloquea ? 0 : n);
        if (bloquea) u.setBloqueadoHasta(LocalDateTime.now().plusMinutes(MINUTOS_BLOQUEO));
        usuarios.save(u);
        return bloquea;
    }

    /** Entró bien: borra los intentos fallidos. */
    public void registrarExito(UsuarioEntity u) {
        if ((u.getIntentosFallidos() == null || u.getIntentosFallidos() == 0) && u.getBloqueadoHasta() == null) return;
        u.setIntentosFallidos(0);
        u.setBloqueadoHasta(null);
        usuarios.save(u);
    }
}
