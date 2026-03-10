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
    void anular(Long id, Integer empresaId);
    CotizacionDto convertirAVenta(Long id, Integer empresaId);
    void vencerCotizacionesExpiradas();
}
