package com.cloud_technological.aura_pos.services.nube;

import java.util.List;

import com.cloud_technological.aura_pos.dto.nube.NubeDtos.ResponderSolicitudDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.SolicitudEspacioDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.SolicitudPlataformaDto;

/**
 * Espacio de la nube visto desde fuera de la pantalla de documentos: el panel de
 * plataforma (aprobar o rechazar solicitudes de más espacio) y la campana.
 *
 * <p>Nada de aquí entrega documentos ni nombres de archivos: solo cuotas y uso.
 */
public interface NubeEspacioService {

    /** Solicitudes pendientes de todas las empresas, la más vieja primero. */
    List<SolicitudPlataformaDto> pendientes();

    int contarPendientes();

    /** Aprueba: la empresa queda con {@code dto.cuotaMb()} (o lo solicitado). */
    void aprobar(Long id, ResponderSolicitudDto dto, Integer actorId);

    /** Rechaza con un motivo que verá la empresa. */
    void rechazar(Long id, ResponderSolicitudDto dto, Integer actorId);

    /** La pendiente, o la respondida en los últimos 30 días (para mostrar la respuesta). */
    SolicitudEspacioDto solicitudVigente(Integer empresaId);

    /** Para la campana de la empresa: cómo va su espacio y su última solicitud. */
    EstadoEspacio estado(Integer empresaId);

    /**
     * @param porcentaje uso sobre la cuota
     * @param aviso      null, "80", "95" o "LLENO"
     * @param solicitud  pendiente o respondida hace poco; null si no hay
     */
    record EstadoEspacio(double porcentaje, String aviso, SolicitudEspacioDto solicitud) {
    }
}
