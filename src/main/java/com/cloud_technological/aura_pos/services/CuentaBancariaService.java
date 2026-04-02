package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.tesoreria.CreateCuentaBancariaDto;
import com.cloud_technological.aura_pos.dto.tesoreria.CuentaBancariaDto;

public interface CuentaBancariaService {
    List<CuentaBancariaDto> listar(Integer empresaId);
    CuentaBancariaDto crear(Integer empresaId, CreateCuentaBancariaDto dto);
    CuentaBancariaDto actualizar(Long id, Integer empresaId, CreateCuentaBancariaDto dto);
    void toggleActiva(Long id, Integer empresaId);
}
