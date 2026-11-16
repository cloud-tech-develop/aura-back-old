package com.cloud_technological.aura_pos.services.nube;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.entity.nube.NubeEventoEntity;
import com.cloud_technological.aura_pos.repositories.nube.NubeEventoJPARepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Escribe la bitácora de la nube (nube_evento, solo INSERT).
 *
 * <p>{@link #registrar} va en la misma transacción de la acción: si la acción no
 * se confirma, tampoco queda el evento. {@link #registrarDenegado} va en una
 * transacción propia porque la petición que lo causa termina en error y se
 * revierte, y el intento debe quedar como evidencia.
 */
@Slf4j
@Component
public class NubeBitacora {

    private final NubeEventoJPARepository eventos;
    private final ObjectMapper json;

    public NubeBitacora(NubeEventoJPARepository eventos, ObjectMapper json) {
        this.eventos = eventos;
        this.json = json;
    }

    /**
     * Anota un evento en la transacción actual.
     *
     * @param soloFirma el elemento está en una carpeta SOLO_FIRMA (el cliente no verá el evento)
     * @param detalle   datos para entender el evento (nombre, origen, destino…)
     */
    public void registrar(NubeContexto ctx, String accion, Long archivoId, Long carpetaId, boolean soloFirma,
            Map<String, Object> detalle) {
        eventos.save(evento(ctx, accion, archivoId, carpetaId, soloFirma, detalle));
    }

    /** Anota un intento sobre algo que no existe o no es visible para quien lo pidió. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarDenegado(NubeContexto ctx, String que, Long id) {
        try {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("tipo", que);
            d.put("idPedido", id);
            // El id puede ser de otra empresa: no se guarda en archivo_id/carpeta_id.
            eventos.save(evento(ctx, NubeEventoEntity.ACCESO_DENEGADO, null, null, false, d));
        } catch (RuntimeException e) {
            log.error("No se pudo anotar el acceso denegado a la nube: {}", e.getMessage());
        }
    }

    private NubeEventoEntity evento(NubeContexto ctx, String accion, Long archivoId, Long carpetaId,
            boolean soloFirma, Map<String, Object> detalle) {
        Map<String, Object> d = detalle != null ? new LinkedHashMap<>(detalle) : new LinkedHashMap<>();
        if (soloFirma) d.put("soloFirma", true);
        NubeEventoEntity e = new NubeEventoEntity();
        e.setEmpresaId(ctx.empresaId());
        e.setUsuarioId(ctx.usuarioId());
        e.setFirmaEmpresaId(ctx.firmaId());
        e.setAccion(accion);
        e.setArchivoId(archivoId);
        e.setCarpetaId(carpetaId);
        e.setDetalle(d.isEmpty() ? null : aJson(d));
        e.setIp(recortar(ctx.ip(), 45));
        e.setUserAgent(recortar(ctx.userAgent(), 255));
        return e;
    }

    private String aJson(Map<String, Object> d) {
        try {
            return json.writeValueAsString(d);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private static String recortar(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max);
    }
}
