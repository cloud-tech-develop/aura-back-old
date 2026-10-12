package com.cloud_technological.aura_pos.security;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final SesionService sesiones;
    private final AccesoFirmaService accesoFirma;

    /** La única ruta que acepta el token de paso (elegir empresa tras el login). */
    static final String RUTA_SELECCION = "/api/auth/seleccionar-empresa";

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserDetailsService userDetailsService,
            SesionService sesiones, AccesoFirmaService accesoFirma) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.sesiones = sesiones;
        this.accesoFirma = accesoFirma;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String token = getTokenFromRequest(request);

        try {
            if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {

                String username = jwtTokenProvider.getUsername(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // Sesión revocada (V192): desactivado, clave nueva o "Cerrar sesiones".
                Long usuarioId = jwtTokenProvider.getUsuarioId(token);
                Integer vigente = usuarioId != null ? sesiones.versionActual(usuarioId) : null;
                if (vigente != null && vigente != jwtTokenProvider.getTokenVersion(token)) {
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("{\"error\": \"Su sesión se cerró. Vuelva a iniciar sesión\","
                            + " \"codigo\": \"SESION_REVOCADA\"}");
                    return;
                }

                // Token de paso (docs/PLAN_FIRMA_CONTABLE.md): solo sirve para elegir la empresa.
                String scope = jwtTokenProvider.getScope(token);
                if (scope != null && !RUTA_SELECCION.equals(request.getRequestURI())) {
                    rechazar(response, "Elija la empresa en la que va a trabajar", "ELEGIR_EMPRESA");
                    return;
                }

                // Usuario de una firma trabajando en un cliente: el acceso se puede quitar
                // en cualquier momento y corta solo esta empresa, no sus otros clientes.
                Long vinculoId = jwtTokenProvider.getVinculoId(token);
                if (vinculoId != null && !accesoFirma.vigente(usuarioId, vinculoId)) {
                    rechazar(response, "La empresa quitó el acceso de su firma contable. Elija otra empresa",
                            "ACCESO_FIRMA_REVOCADO");
                    return;
                }

                // En un cliente el rol es el del token (CONTADOR_EXTERNO), nunca el que el
                // usuario tiene en su firma.
                java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> autoridades =
                        vinculoId != null || scope != null
                                ? java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        String.valueOf(jwtTokenProvider.getRol(token))))
                                : userDetails.getAuthorities();

                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        token,
                        autoridades);
                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            }
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\": \"" + e.getMessage() + "\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static void rechazar(HttpServletResponse response, String mensaje, String codigo) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\": \"" + mensaje + "\", \"codigo\": \"" + codigo + "\"}");
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
