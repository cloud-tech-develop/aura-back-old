package com.cloud_technological.aura_pos.security;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.web.filter.OncePerRequestFilter;

import com.cloud_technological.aura_pos.config.PermisoInterceptor;
import com.cloud_technological.aura_pos.dto.permisos.PermisosUsuarioDto;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoBloqueoLogRepository;
import com.cloud_technological.aura_pos.services.permisos.PermisoUsuarioService;
import com.cloud_technological.aura_pos.utils.PoliticaRoles;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Alcance por sede en los parámetros de la URL (fase P9 de docs/PLAN_PERMISOS.md).
 * Solo actúa sobre usuarios cuyo perfil NO tiene "todas las sedes":
 *
 * <ul>
 * <li>{@code ?sucursalId=} de una sede que no es suya → OBSERVAR lo anota, BLOQUEAR responde 403.</li>
 * <li>Sin {@code sucursalId} (= todas) → en BLOQUEAR se completa con la sede de su sesión,
 * para que los listados y reportes que filtran por ese parámetro no le muestren las demás.</li>
 * </ul>
 *
 * <p>Va en la cadena de seguridad después del filtro JWT (necesita la sesión). El
 * cuerpo de las peticiones lo revisa {@code AlcanceSedeBodyAdvice}; las variables
 * de la ruta, el {@code PermisoInterceptor}. No se registra como @Component: lo
 * crea SecurityConfig para que no corra dos veces.
 */
public class AlcanceSedeFilter extends OncePerRequestFilter {

    private static final String PARAM = "sucursalId";

    private final PermisoUsuarioService permisos;
    private final PermisoBloqueoLogRepository registro;
    private final SecurityUtils securityUtils;
    private final PermisoInterceptor control;

    public AlcanceSedeFilter(PermisoUsuarioService permisos, PermisoBloqueoLogRepository registro,
            SecurityUtils securityUtils, PermisoInterceptor control) {
        this.permisos = permisos;
        this.registro = registro;
        this.securityUtils = securityUtils;
        this.control = control;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String modo = control.getModo();
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        if (PermisoInterceptor.APAGADO.equals(modo) || !ruta.startsWith("/api/") || ruta.startsWith("/api/auth")
                || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        Long usuario = securityUtils.getUsuarioId();
        if (usuario == null || PoliticaRoles.PLATFORM_ADMIN.equalsIgnoreCase(securityUtils.getRol())) {
            chain.doFilter(request, response);
            return;
        }
        PermisosUsuarioDto p = permisos.efectivos(usuario.intValue());
        if (p.isTodasLasSedes()) {
            chain.doFilter(request, response);
            return;
        }

        String valor = request.getParameter(PARAM);
        if (valor != null && !valor.isBlank()) {
            Long sede;
            try {
                sede = Long.valueOf(valor.trim());
            } catch (NumberFormatException e) {
                chain.doFilter(request, response);
                return;
            }
            if (!p.getSucursales().contains(sede)) {
                registro.registrar(securityUtils.getEmpresaId(), usuario.intValue(), modo, request.getMethod(), ruta,
                        "sede:" + sede, "SEDE");
                if (PermisoInterceptor.BLOQUEAR.equals(modo)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("{\"status\": 403, \"message\": \"Su perfil solo le permite trabajar en"
                            + " sus sedes asignadas\", \"error\": true}");
                    return;
                }
            }
            chain.doFilter(request, response);
            return;
        }
        Long sesion = securityUtils.getSucursalId();
        if (PermisoInterceptor.BLOQUEAR.equals(modo) && sesion != null) {
            chain.doFilter(new ConParametro(request, PARAM, sesion.toString()), response);
            return;
        }
        chain.doFilter(request, response);
    }

    /** La petición con un parámetro de más. */
    private static final class ConParametro extends HttpServletRequestWrapper {
        private final Map<String, String[]> parametros;

        ConParametro(HttpServletRequest request, String nombre, String valor) {
            super(request);
            Map<String, String[]> m = new HashMap<>(request.getParameterMap());
            m.put(nombre, new String[] { valor });
            this.parametros = Collections.unmodifiableMap(m);
        }

        @Override
        public String getParameter(String name) {
            String[] v = parametros.get(name);
            return v != null && v.length > 0 ? v[0] : null;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return parametros;
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(parametros.keySet());
        }

        @Override
        public String[] getParameterValues(String name) {
            return parametros.get(name);
        }
    }
}
