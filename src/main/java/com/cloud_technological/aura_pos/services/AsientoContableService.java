package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.contabilidad.AsientoContableTableDto;
import com.cloud_technological.aura_pos.dto.contabilidad.BalanceGeneralDto;
import com.cloud_technological.aura_pos.dto.contabilidad.CreateAsientoDto;
import com.cloud_technological.aura_pos.dto.contabilidad.EstadoResultadosDto;
import com.cloud_technological.aura_pos.dto.contabilidad.FlujoCajaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.LibroMayorLineaDto;

public interface AsientoContableService {
    List<AsientoContableTableDto> listar(Integer empresaId, String desde, String hasta,
            String tipoOrigen, int page, int rows);
    AsientoContableTableDto obtenerConDetalles(Long id, Integer empresaId);
    AsientoContableTableDto crear(Integer empresaId, Integer usuarioId, CreateAsientoDto dto);
    void anular(Long id, Integer empresaId);
    BalanceGeneralDto balanceGeneral(Integer empresaId, String hasta);
    EstadoResultadosDto estadoResultados(Integer empresaId, String desde, String hasta);
    List<LibroMayorLineaDto> libroMayor(Integer empresaId, Long cuentaId, String desde, String hasta);
    FlujoCajaDto flujoCaja(Integer empresaId, String desde, String hasta);
}
