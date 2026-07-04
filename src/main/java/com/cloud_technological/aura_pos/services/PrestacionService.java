package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.nomina.nomina.PagoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.prestacion.CrearPrestacionDto;
import com.cloud_technological.aura_pos.dto.nomina.prestacion.PrestacionDto;

public interface PrestacionService {
    List<PrestacionDto> listar(Integer empresaId);
    PrestacionDto crear(CrearPrestacionDto dto, Integer empresaId);
    List<PrestacionDto> liquidacionDefinitiva(
            com.cloud_technological.aura_pos.dto.nomina.prestacion.LiquidacionDefinitivaDto dto, Integer empresaId);
    PrestacionDto aprobar(Long id, Integer empresaId);
    PrestacionDto pagar(Long id, PagoNominaDto dto, Integer empresaId);
    PrestacionDto anular(Long id, Integer empresaId);
}
