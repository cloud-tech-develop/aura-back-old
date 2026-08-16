package com.cloud_technological.aura_pos.services;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.cotizaciones.CotizacionDto;
import com.cloud_technological.aura_pos.dto.cotizaciones.CotizacionTableDto;
import com.cloud_technological.aura_pos.dto.cotizaciones.CreateCotizacionDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface CotizacionService {
    PageImpl<CotizacionTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    CotizacionDto obtenerPorId(Long id, Integer empresaId);
    CotizacionDto crear(CreateCotizacionDto dto, Integer empresaId, Long usuarioId);
    CotizacionDto actualizar(Long id, CreateCotizacionDto dto, Integer empresaId);
    void anular(Long id, Integer empresaId);
    CotizacionDto convertirAVenta(Long id, Integer empresaId);
    void vencerCotizacionesExpiradas();

    /**
     * Revive una cotización vencida para poder convertirla en venta.
     *
     * <p>Conserva los precios originales: el cliente vuelve por lo que se le
     * cotizó, no por otra cosa. Solo se permite una vez — a la segunda hay que
     * cotizar de nuevo, para que nadie sostenga un precio viejo indefinidamente
     * sin que el negocio lo revise.
     */
    CotizacionDto reactivar(Long id, Integer empresaId, Long usuarioId);
}
