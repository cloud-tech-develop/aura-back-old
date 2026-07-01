package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.obligaciones.CreateObligacionDto;
import com.cloud_technological.aura_pos.dto.obligaciones.CuotaAmortizacionDto;
import com.cloud_technological.aura_pos.dto.obligaciones.ObligacionDto;
import com.cloud_technological.aura_pos.dto.obligaciones.PagarCuotaDto;

public interface ObligacionFinancieraService {
    ObligacionDto crear(CreateObligacionDto dto, Integer empresaId, Long usuarioId);
    List<ObligacionDto> listar(Integer empresaId);
    ObligacionDto obtenerPorId(Long id, Integer empresaId);
    CuotaAmortizacionDto pagarCuota(Long obligacionId, Long cuotaId, PagarCuotaDto pago,
            Integer empresaId, Long usuarioId);
    void anular(Long id, Integer empresaId);
}
