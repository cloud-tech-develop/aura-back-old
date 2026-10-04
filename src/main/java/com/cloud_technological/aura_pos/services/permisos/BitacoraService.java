package com.cloud_technological.aura_pos.services.permisos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.cloud_technological.aura_pos.entity.AuditoriaEventoEntity;
import com.cloud_technological.aura_pos.repositories.auditoria.AuditoriaEventoJPARepository;
import com.cloud_technological.aura_pos.utils.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Bitácora de auditoría (fase P7 de docs/PLAN_PERMISOS.md): quién anuló, editó,
 * autorizó o cambió qué y cuándo.
 *
 * <p>Dos fuentes:
 * <ul>
 * <li><b>Servicio</b>: lo anota el servicio que hace el cambio, con antes/después
 * (precio de producto, usuarios, autorizaciones…). Va en la misma transacción:
 * si el cambio se deshace, la línea también.</li>
 * <li><b>Automática</b>: el {@code PermisoInterceptor} anota toda petición de editar,
 * anular o acción especial que terminó bien, salvo que el servicio ya la haya anotado.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class BitacoraService {

    private static final Logger log = LoggerFactory.getLogger(BitacoraService.class);

    /** Marca en la petición: el servicio ya dejó su línea, el interceptor no repite. */
    public static final String YA_REGISTRADA = BitacoraService.class.getName() + ".registrada";

    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final AuditoriaEventoJPARepository repo;
    private final SecurityUtils securityUtils;

    /**
     * Anota un evento del usuario de la sesión.
     *
     * @param clave       submódulo {@code modulo.submodulo}
     * @param accion      EDITAR, ANULAR, AUTORIZAR, CAMBIO_PRECIO…
     * @param entidad     tipo de documento o registro (venta, producto, usuario…)
     * @param entidadId   su id
     * @param descripcion en palabras, para la pantalla
     * @param antes       objeto o texto antes del cambio (se guarda como JSON); null si no aplica
     * @param despues     objeto o texto después del cambio; null si no aplica
     */
    public void registrar(String clave, String accion, String entidad, Object entidadId, String descripcion,
            Object antes, Object despues) {
        registrar(clave, accion, entidad, entidadId, descripcion, antes, despues, null);
    }

    /** Igual, con quien autorizó la acción. */
    public void registrar(String clave, String accion, String entidad, Object entidadId, String descripcion,
            Object antes, Object despues, Integer autorizadoPor) {
        Integer empresaId = securityUtils.getEmpresaId();
        if (empresaId == null) return;
        Long usuario = securityUtils.getUsuarioId();
        AuditoriaEventoEntity e = nuevo(empresaId, usuario != null ? usuario.intValue() : null, clave, accion);
        e.setEntidad(recortar(entidad, 60));
        e.setEntidadId(entidadId != null ? recortar(entidadId.toString(), 60) : null);
        e.setDescripcion(descripcion);
        e.setAntes(aTexto(antes));
        e.setDespues(aTexto(despues));
        e.setAutorizadoPor(autorizadoPor);
        HttpServletRequest req = peticion();
        if (req != null) {
            e.setMetodo(req.getMethod());
            e.setRuta(recortar(req.getRequestURI(), 300));
            e.setIp(ip(req));
            req.setAttribute(YA_REGISTRADA, Boolean.TRUE);
        }
        repo.save(e);
    }

    /**
     * Línea automática del interceptor. Nunca rompe la petición: si falla, solo
     * queda en el log del servidor.
     */
    public void registrarAuto(Integer empresaId, Integer usuarioId, String clave, String accion, String metodo,
            String patron, String entidadId, HttpServletRequest req) {
        try {
            AuditoriaEventoEntity e = nuevo(empresaId, usuarioId, clave, accion);
            e.setOrigen(AuditoriaEventoEntity.ORIGEN_AUTO);
            e.setEntidad(recortar(entidadDe(patron), 60));
            e.setEntidadId(recortar(entidadId, 60));
            e.setDescripcion(metodo + " " + patron);
            e.setMetodo(metodo);
            e.setRuta(recortar(req.getRequestURI(), 300));
            e.setIp(ip(req));
            repo.save(e);
        } catch (RuntimeException ex) {
            log.warn("No se pudo anotar en la bitácora {} {}: {}", metodo, patron, ex.getMessage());
        }
    }

    /** "/api/compras/{id}/anular" → "compras". */
    static String entidadDe(String patron) {
        if (patron == null) return null;
        String[] partes = patron.split("/");
        for (int i = 0; i < partes.length; i++) {
            if ("api".equals(partes[i]) && i + 1 < partes.length) return partes[i + 1];
        }
        return null;
    }

    private static AuditoriaEventoEntity nuevo(Integer empresaId, Integer usuarioId, String clave, String accion) {
        AuditoriaEventoEntity e = new AuditoriaEventoEntity();
        e.setEmpresaId(empresaId);
        e.setUsuarioId(usuarioId);
        e.setClave(recortar(clave, 120));
        e.setAccion(recortar(accion, 40));
        return e;
    }

    private static String aTexto(Object o) {
        if (o == null) return null;
        if (o instanceof String s) return s;
        try {
            return JSON.writeValueAsString(o);
        } catch (Exception ex) {
            return String.valueOf(o);
        }
    }

    private static HttpServletRequest peticion() {
        RequestAttributes a = RequestContextHolder.getRequestAttributes();
        return a instanceof ServletRequestAttributes s ? s.getRequest() : null;
    }

    static String ip(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        String ip = fwd != null && !fwd.isBlank() ? fwd.split(",")[0].trim() : req.getRemoteAddr();
        return recortar(ip, 64);
    }

    private static String recortar(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
