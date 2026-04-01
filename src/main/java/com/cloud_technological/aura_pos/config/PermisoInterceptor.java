package com.cloud_technological.aura_pos.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.cloud_technological.aura_pos.services.PermisoService;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Interceptor para validar permisos de módulo/submódulo en controladores
 * HU-031: Validación de Permisos en Controladores
 */
@Component
public class PermisoInterceptor implements HandlerInterceptor {

    private final PermisoService permisoService;
    private final SecurityUtils securityUtils;

    @Autowired
    public PermisoInterceptor(PermisoService permisoService, SecurityUtils securityUtils) {
        this.permisoService = permisoService;
        this.securityUtils = securityUtils;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Obtener la anotación del método o clase
        RequerirPermiso annotation = obtenerAnotacion(handler);
        
        // Si no hay anotación, permitir acceso
        if (annotation == null) {
            return true;
        }

        // Obtener empresa del token JWT
        Integer empresaId = securityUtils.getEmpresaId();
        if (empresaId == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "No autenticado");
            return false;
        }

        // Validar permiso
        String moduloCodigo = annotation.modulo();
        String submoduloCodigo = annotation.submodulo();
        
        boolean tienePermiso = permisoService.tienePermiso(empresaId, moduloCodigo, submoduloCodigo);
        
        if (!tienePermiso) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, annotation.mensaje());
            return false;
        }

        return true;
    }

    private RequerirPermiso obtenerAnotacion(Object handler) {
        if (handler instanceof org.springframework.web.method.HandlerMethod) {
            org.springframework.web.method.HandlerMethod handlerMethod = (org.springframework.web.method.HandlerMethod) handler;
            
            // Buscar en el método
            RequerirPermiso annotation = handlerMethod.getMethodAnnotation(RequerirPermiso.class);
            if (annotation != null) {
                return annotation;
            }
            
            // Buscar en la clase
            return handlerMethod.getBeanType().getAnnotation(RequerirPermiso.class);
        }
        return null;
    }
}
