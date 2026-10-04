package com.cloud_technological.aura_pos.config;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import com.cloud_technological.aura_pos.repositories.permisos.PermisoBloqueoLogRepository;
import com.cloud_technological.aura_pos.services.permisos.AccionPermiso;
import com.cloud_technological.aura_pos.services.permisos.BitacoraService;
import com.cloud_technological.aura_pos.services.permisos.PermisoRutas;
import com.cloud_technological.aura_pos.services.permisos.PermisoUsuarioService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PoliticaRoles;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Revisa cada petición al API contra el perfil del usuario (fase P3 de
 * docs/PLAN_PERMISOS.md). Ocultar el menú no protege nada: esto sí.
 *
 * <p>Modo ({@code app.permisos.modo}):
 * <ul>
 * <li>APAGADO: no revisa.</li>
 * <li>OBSERVAR: revisa y anota en permiso_bloqueo_log lo que habría bloqueado, sin bloquear.</li>
 * <li>BLOQUEAR: responde 403 y lo anota.</li>
 * </ul>
 *
 * <p>Una ruta sin submódulo en {@link PermisoRutas} no se bloquea: se anota para completarla.
 *
 * <p>Segunda etapa (P6–P9):
 * <ul>
 * <li>{@code @RequerirPermiso(accion = "REABRIR")} con una acción que no es VER/CREAR/EDITAR/ANULAR
 * se revisa como acción especial {@code modulo.submodulo:REABRIR}.</li>
 * <li>Toda petición de editar, anular o acción especial que termina bien queda en la
 * bitácora (en cualquier modo), salvo que el servicio ya haya dejado su línea con detalle.</li>
 * <li>Un {@code {sucursalId}} en la ruta que no sea de las sedes del usuario se trata como
 * permiso faltante (los parámetros y el cuerpo los revisan {@code AlcanceSedeFilter} y
 * {@code AlcanceSedeBodyAdvice}).</li>
 * </ul>
 */
@Component
public class PermisoInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PermisoInterceptor.class);

    public static final String APAGADO = "APAGADO";
    public static final String OBSERVAR = "OBSERVAR";
    public static final String BLOQUEAR = "BLOQUEAR";

    /** Lo que se anotará en la bitácora si la petición termina bien. */
    private static final String PENDIENTE_BITACORA = PermisoInterceptor.class.getName() + ".bitacora";

    private final PermisoUsuarioService permisos;
    private final PermisoBloqueoLogRepository registro;
    private final SecurityUtils securityUtils;
    private final BitacoraService bitacora;
    private final String modo;

    public PermisoInterceptor(PermisoUsuarioService permisos, PermisoBloqueoLogRepository registro,
            SecurityUtils securityUtils, BitacoraService bitacora,
            @Value("${app.permisos.modo:OBSERVAR}") String modo) {
        this.permisos = permisos;
        this.registro = registro;
        this.securityUtils = securityUtils;
        this.bitacora = bitacora;
        String m = modo == null ? OBSERVAR : modo.trim().toUpperCase();
        this.modo = List.of(APAGADO, OBSERVAR, BLOQUEAR).contains(m) ? m : OBSERVAR;
        log.info(">>> Control de permisos por perfil en modo {}", this.modo);
    }

    public String getModo() {
        return modo;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod metodo)) return true;
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        if (!ruta.startsWith("/api/") || PermisoRutas.esLibre(ruta)) return true;

        Long usuario = securityUtils.getUsuarioId();
        if (usuario == null) return true; // sin sesión: lo decide la seguridad de Spring
        if (PoliticaRoles.PLATFORM_ADMIN.equalsIgnoreCase(securityUtils.getRol())) return true;

        RequerirPermiso anotacion = metodo.getMethodAnnotation(RequerirPermiso.class);
        if (anotacion == null) anotacion = metodo.getBeanType().getAnnotation(RequerirPermiso.class);
        if (anotacion != null && anotacion.libre()) return true;

        String pedida = anotacion != null && !anotacion.accion().isBlank() ? anotacion.accion().trim().toUpperCase() : null;
        AccionPermiso accion = pedida == null ? PermisoRutas.accionDe(request.getMethod(), ruta) : basica(pedida);
        String especial = pedida != null && accion == null ? pedida : null;
        if (accion != null && PermisoRutas.esLecturaLibre(ruta, accion)) return true;

        List<String> claves = anotacion != null && anotacion.claves().length > 0
                ? Arrays.asList(anotacion.claves())
                : PermisoRutas.clavesDe(ruta);

        String patron = patron(request, ruta);
        Integer usuarioId = usuario.intValue();
        Integer empresaId = securityUtils.getEmpresaId();

        // Bitácora: editar, anular y acciones especiales, en cualquier modo.
        if (especial != null || accion == AccionPermiso.EDITAR || accion == AccionPermiso.ANULAR) {
            request.setAttribute(PENDIENTE_BITACORA, new Pendiente(empresaId, usuarioId,
                    claves != null ? claves.get(0) : null, especial != null ? especial : accion.name(),
                    patron, entidadId(request)));
        }
        if (APAGADO.equals(modo)) return true;

        revisarSedeDeLaRuta(request, usuarioId, empresaId, patron);

        if (claves == null) {
            // Ruta sin submódulo asignado: no se bloquea, se anota para completar el mapa.
            registro.registrar(empresaId, usuarioId, modo, request.getMethod(), patron, null,
                    especial != null ? "ESPECIAL" : accion.name());
            return true;
        }
        boolean puede = especial != null
                ? claves.stream().anyMatch(c -> permisos.puedeEspecial(usuarioId, c + ":" + especial))
                : permisos.puedeAlguna(usuarioId, claves, accion);
        if (puede) return true;

        String exigida = especial != null
                ? String.join(",", claves.stream().map(c -> c + ":" + especial).toList())
                : String.join(",", claves);
        registro.registrar(empresaId, usuarioId, modo, request.getMethod(), patron, exigida,
                especial != null ? "ESPECIAL" : accion.name());
        if (BLOQUEAR.equals(modo)) {
            String que = especial != null ? especial.toLowerCase().replace('_', ' ') : verbo(accion);
            throw new GlobalException(HttpStatus.FORBIDDEN, "No tiene permiso para " + que
                    + " en " + claves.get(0).replace('.', '›') + ". Pídale al administrador que lo agregue a su perfil");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        Object p = request.getAttribute(PENDIENTE_BITACORA);
        if (!(p instanceof Pendiente pendiente)) return;
        if (ex != null || response.getStatus() >= 400) return;
        if (request.getAttribute(BitacoraService.YA_REGISTRADA) != null) return;
        if (pendiente.empresaId() == null) return;
        bitacora.registrarAuto(pendiente.empresaId(), pendiente.usuarioId(), pendiente.clave(), pendiente.accion(),
                request.getMethod(), pendiente.patron(), pendiente.entidadId(), request);
    }

    /** Un {sucursalId} en la ruta que no sea de sus sedes. */
    private void revisarSedeDeLaRuta(HttpServletRequest request, Integer usuarioId, Integer empresaId, String patron) {
        Map<String, String> vars = variables(request);
        String valor = vars.get("sucursalId");
        if (valor == null) return;
        Long sede;
        try {
            sede = Long.valueOf(valor);
        } catch (NumberFormatException e) {
            return;
        }
        try {
            permisos.sedePermitida(usuarioId, sede, securityUtils.getSucursalId());
        } catch (GlobalException fuera) {
            registro.registrar(empresaId, usuarioId, modo, request.getMethod(), patron, "sede:" + sede, "SEDE");
            if (BLOQUEAR.equals(modo)) throw fuera;
        }
    }

    private static AccionPermiso basica(String accion) {
        for (AccionPermiso a : AccionPermiso.values()) {
            if (a.name().equals(accion)) return a;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> variables(HttpServletRequest request) {
        Object v = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return v instanceof Map<?, ?> m ? (Map<String, String>) m : Map.of();
    }

    /** El id del documento de la ruta: {id} o, si no hay, la primera variable. */
    private static String entidadId(HttpServletRequest request) {
        Map<String, String> vars = variables(request);
        if (vars.containsKey("id")) return vars.get("id");
        return vars.isEmpty() ? null : vars.values().iterator().next();
    }

    /** El patrón de la ruta (/api/compras/{id}) para que el registro no se llene de ids. */
    private static String patron(HttpServletRequest request, String ruta) {
        Object p = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return p != null ? p.toString() : ruta;
    }

    private static String verbo(AccionPermiso a) {
        return switch (a) {
            case VER -> "ver";
            case CREAR -> "crear";
            case EDITAR -> "editar";
            case ANULAR -> "anular";
        };
    }

    private record Pendiente(Integer empresaId, Integer usuarioId, String clave, String accion, String patron,
            String entidadId) {
    }
}
