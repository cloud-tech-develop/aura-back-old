package com.cloud_technological.aura_pos.services;

import com.cloud_technological.aura_pos.dto.empresas.EmpresaDto;

public interface IEmpresaService {
    EmpresaDto obtenerEmpresaActual(Integer empresaId, Long sucursalId, Long usuarioId);
}
