package com.cloud_technological.aura_pos.services.nube;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nube.NubeDtos.ResponderSolicitudDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.SolicitudEspacioDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.SolicitudPlataformaDto;
import com.cloud_technological.aura_pos.entity.nube.NubeEspacioEntity;
import com.cloud_technological.aura_pos.entity.nube.NubeSolicitudEspacioEntity;
import com.cloud_technological.aura_pos.mappers.NubeMapper;
import com.cloud_technological.aura_pos.repositories.nube.NubeEspacioJPARepository;
import com.cloud_technological.aura_pos.repositories.nube.NubeQueryRepository;
import com.cloud_technological.aura_pos.repositories.nube.NubeQueryRepository.Espacio;
import com.cloud_technological.aura_pos.repositories.nube.NubeQueryRepository.Uso;
import com.cloud_technological.aura_pos.repositories.nube.NubeSolicitudEspacioJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

/**
 * Solicitudes de más espacio (las responde la plataforma, docs/PLAN_NUBE_DOCUMENTOS.md
 * §10) y el estado del espacio para la campana. El acceso de plataforma lo exige el
 * controlador ({@code hasRole('PLATFORM_ADMIN')}); aquí no se lee ningún documento.
 */
@Service
public class NubeEspacioServiceImpl implements NubeEspacioService {

    /** Tope de cuota que la plataforma puede asignar (100 GB). */
    static final int MAX_CUOTA_MB = 102_400;

    private static final int DIAS_MOSTRAR_RESPUESTA = 30;
    private static final int MAX_OBSERVACION = 300;

    private final NubeQueryRepository query;
    private final NubeEspacioJPARepository espacios;
    private final NubeSolicitudEspacioJPARepository solicitudes;
    private final NubeBitacora bitacora;
    private final NubeMapper mapper;

    public NubeEspacioServiceImpl(NubeQueryRepository query, NubeEspacioJPARepository espacios,
            NubeSolicitudEspacioJPARepository solicitudes, NubeBitacora bitacora, NubeMapper mapper) {
        this.query = query;
        this.espacios = espacios;
        this.solicitudes = solicitudes;
        this.bitacora = bitacora;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SolicitudPlataformaDto> pendientes() {
        return mapper.toPlataforma(query.solicitudesPendientes());
    }

    @Override
    @Transactional(readOnly = true)
    public int contarPendientes() {
        return query.contarSolicitudesPendientes();
    }

    @Override
    @Transactional
    public void aprobar(Long id, ResponderSolicitudDto dto, Integer actorId) {
        NubeSolicitudEspacioEntity s = pendiente(id);
        Espacio espacio = query.bloquearEspacio(s.getEmpresaId());
        int cuota = dto != null && dto.cuotaMb() != null ? dto.cuotaMb() : s.getCuotaSolicitadaMb();
        if (cuota <= espacio.cuotaMb())
            throw error(HttpStatus.BAD_REQUEST, "La nueva cuota debe ser mayor a la actual (" + espacio.cuotaMb() + " MB).");
        if (cuota > MAX_CUOTA_MB)
            throw error(HttpStatus.BAD_REQUEST, "La cuota máxima es " + MAX_CUOTA_MB + " MB.");

        NubeEspacioEntity e = espacios.findById(s.getEmpresaId()).orElseThrow();
        e.setCuotaMb(cuota);
        espacios.save(e);

        s.setCuotaSolicitadaMb(cuota);
        responder(s, NubeSolicitudEspacioEntity.APROBADA, dto == null ? null : dto.observacion(), actorId);
        bitacora.registrar(contexto(s.getEmpresaId(), actorId), "AMPLIAR_CUOTA", null, null, false,
                detalle("solicitudId", id, "antesMb", espacio.cuotaMb(), "despuesMb", cuota));
    }

    @Override
    @Transactional
    public void rechazar(Long id, ResponderSolicitudDto dto, Integer actorId) {
        String motivo = dto == null || dto.observacion() == null ? "" : dto.observacion().trim();
        if (motivo.isEmpty()) throw error(HttpStatus.BAD_REQUEST, "Escribe el motivo del rechazo: la empresa lo verá.");
        NubeSolicitudEspacioEntity s = pendiente(id);
        responder(s, NubeSolicitudEspacioEntity.RECHAZADA, motivo, actorId);
        bitacora.registrar(contexto(s.getEmpresaId(), actorId), "RECHAZAR_ESPACIO", null, null, false,
                detalle("solicitudId", id));
    }

    @Override
    @Transactional(readOnly = true)
    public SolicitudEspacioDto solicitudVigente(Integer empresaId) {
        LocalDateTime limite = LocalDateTime.now().minusDays(DIAS_MOSTRAR_RESPUESTA);
        return query.ultimaSolicitud(empresaId)
                .filter(s -> NubeSolicitudEspacioEntity.PENDIENTE.equals(s.estado())
                        || (s.atendidoAt() != null && s.atendidoAt().isAfter(limite)))
                .map(mapper::toSolicitud).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public EstadoEspacio estado(Integer empresaId) {
        Espacio espacio = query.espacio(empresaId);
        Uso uso = query.uso(empresaId);
        return new EstadoEspacio(NubeCuota.porcentaje(uso, espacio), NubeCuota.aviso(uso, espacio),
                solicitudVigente(empresaId));
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    /** Bloquea la solicitud: dos personas de plataforma no la responden a la vez. */
    private NubeSolicitudEspacioEntity pendiente(Long id) {
        query.bloquearSolicitudPendiente(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "La solicitud no existe o ya fue respondida."));
        return solicitudes.findById(id).orElseThrow();
    }

    private void responder(NubeSolicitudEspacioEntity s, String estado, String observacion, Integer actorId) {
        s.setEstado(estado);
        s.setAtendidoPor(actorId);
        s.setAtendidoAt(LocalDateTime.now());
        String obs = observacion == null ? null : observacion.trim();
        s.setObservacion(obs == null || obs.isEmpty() ? null
                : obs.length() > MAX_OBSERVACION ? obs.substring(0, MAX_OBSERVACION) : obs);
        solicitudes.save(s);
    }

    /** La plataforma actúa sobre la empresa sin entrar a ella: queda en su bitácora. */
    private static NubeContexto contexto(Integer empresaId, Integer actorId) {
        return new NubeContexto(empresaId, actorId == null ? 0 : actorId, null, "plataforma", null);
    }

    private static Map<String, Object> detalle(Object... pares) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pares.length; i += 2) m.put(String.valueOf(pares[i]), pares[i + 1]);
        return m;
    }

    private static GlobalException error(HttpStatus status, String mensaje) {
        return new GlobalException(status, mensaje);
    }
}
