package com.cloud_technological.aura_pos.config;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Map;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import com.cloud_technological.aura_pos.dto.permisos.PermisosUsuarioDto;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoBloqueoLogRepository;
import com.cloud_technological.aura_pos.services.permisos.PermisoUsuarioService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.PoliticaRoles;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Alcance por sede en el cuerpo de las peticiones (fase P9 de docs/PLAN_PERMISOS.md),
 * para usuarios cuyo perfil NO tiene "todas las sedes".
 *
 * <p>Mira el campo {@code sucursalId} del cuerpo y, en los listados, el de
 * {@code params} del {@link PageableDto} (objeto o mapa):
 * <ul>
 * <li>Una sede que no es suya → OBSERVAR lo anota, BLOQUEAR responde 403.</li>
 * <li>Vacío en un filtro (clase *Filtro* o {@code params} de un listado) → en BLOQUEAR
 * se completa con la sede de su sesión. En un documento que se crea no se toca:
 * ahí el servicio decide (la venta toma la sede del turno).</li>
 * </ul>
 */
@ControllerAdvice
public class AlcanceSedeBodyAdvice extends RequestBodyAdviceAdapter {

    private static final String CAMPO = "sucursalId";

    private final PermisoUsuarioService permisos;
    private final PermisoBloqueoLogRepository registro;
    private final SecurityUtils securityUtils;
    private final PermisoInterceptor control;

    public AlcanceSedeBodyAdvice(PermisoUsuarioService permisos, PermisoBloqueoLogRepository registro,
            SecurityUtils securityUtils, PermisoInterceptor control) {
        this.permisos = permisos;
        this.registro = registro;
        this.securityUtils = securityUtils;
        this.control = control;
    }

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        String modo = control.getModo();
        if (body == null || PermisoInterceptor.APAGADO.equals(modo)) return body;
        // Login, cambio de sede y demás de /api/auth: no hay sede que revisar.
        HttpServletRequest peticion = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a
                ? a.getRequest() : null;
        if (peticion == null || !peticion.getRequestURI().contains("/api/") || peticion.getRequestURI().contains("/api/auth")) {
            return body;
        }
        Long usuario = securityUtils.getUsuarioId();
        if (usuario == null || PoliticaRoles.PLATFORM_ADMIN.equalsIgnoreCase(securityUtils.getRol())) return body;
        PermisosUsuarioDto p = permisos.efectivos(usuario.intValue());
        if (p.isTodasLasSedes()) return body;

        boolean bloquea = PermisoInterceptor.BLOQUEAR.equals(modo);
        Long sesion = securityUtils.getSucursalId();
        if (body instanceof PageableDto<?> pagina && pagina.getParams() != null) {
            revisar(pagina.getParams(), true, p, usuario.intValue(), modo, bloquea, sesion);
        } else {
            boolean esFiltro = body.getClass().getSimpleName().contains("Filtro");
            revisar(body, esFiltro, p, usuario.intValue(), modo, bloquea, sesion);
        }
        return body;
    }

    @SuppressWarnings("unchecked")
    private void revisar(Object objeto, boolean completarVacio, PermisosUsuarioDto p, Integer usuarioId, String modo,
            boolean bloquea, Long sesion) {
        if (objeto instanceof Map<?, ?> mapa) {
            Object v = mapa.get(CAMPO);
            Long sede = aLong(v);
            if (sede != null) {
                fuera(sede, p, usuarioId, modo, bloquea);
            } else if (completarVacio && bloquea && sesion != null && mapa.containsKey(CAMPO)) {
                try {
                    ((Map<String, Object>) mapa).put(CAMPO, sesion);
                } catch (UnsupportedOperationException ignorado) {
                    // Mapa de solo lectura: se deja como vino.
                }
            }
            return;
        }
        Field campo = campo(objeto.getClass());
        if (campo == null) return;
        try {
            campo.setAccessible(true);
            Long sede = aLong(campo.get(objeto));
            if (sede != null) {
                fuera(sede, p, usuarioId, modo, bloquea);
            } else if (completarVacio && bloquea && sesion != null) {
                if (campo.getType() == Long.class) campo.set(objeto, sesion);
                else if (campo.getType() == Integer.class) campo.set(objeto, sesion.intValue());
            }
        } catch (IllegalAccessException | RuntimeException e) {
            if (e instanceof GlobalException g) throw g;
            // Campo que no se puede leer: no se revisa.
        }
    }

    private void fuera(Long sede, PermisosUsuarioDto p, Integer usuarioId, String modo, boolean bloquea) {
        if (p.getSucursales().contains(sede)) return;
        HttpServletRequest req = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes s
                ? s.getRequest() : null;
        registro.registrar(securityUtils.getEmpresaId(), usuarioId, modo, req != null ? req.getMethod() : "?",
                req != null ? req.getRequestURI() : "?", "sede:" + sede, "SEDE");
        if (bloquea) {
            throw new GlobalException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "Su perfil solo le permite trabajar en sus sedes asignadas");
        }
    }

    private static Field campo(Class<?> c) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(CAMPO);
                if (f.getType() == Long.class || f.getType() == Integer.class) return f;
                return null;
            } catch (NoSuchFieldException e) {
                // sigue con la superclase
            }
        }
        return null;
    }

    private static Long aLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.valueOf(v.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
