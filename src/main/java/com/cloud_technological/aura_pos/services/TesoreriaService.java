package com.cloud_technological.aura_pos.services;

import java.time.LocalDate;
import java.util.List;

import com.cloud_technological.aura_pos.dto.tesoreria.ConciliacionResumenDto;
import com.cloud_technological.aura_pos.dto.tesoreria.CreateMovimientoDto;
import com.cloud_technological.aura_pos.dto.tesoreria.TesoreriaMovimientoDto;

public interface TesoreriaService {
    List<TesoreriaMovimientoDto> listarEgresos(Integer empresaId, Long cuentaId, LocalDate desde, LocalDate hasta);
    List<TesoreriaMovimientoDto> listarRecaudos(Integer empresaId, Long cuentaId, LocalDate desde, LocalDate hasta);
    TesoreriaMovimientoDto crearEgreso(Integer empresaId, Integer usuarioId, CreateMovimientoDto dto);
    TesoreriaMovimientoDto crearRecaudo(Integer empresaId, Integer usuarioId, CreateMovimientoDto dto);
    void anular(Long id, Integer empresaId);
    List<TesoreriaMovimientoDto> listarParaConciliacion(Integer empresaId, Long cuentaId, LocalDate desde, LocalDate hasta);
    void toggleConciliado(Long id, Integer empresaId);
    void conciliarLote(List<Long> ids, Integer empresaId);
    ConciliacionResumenDto getResumen(Integer empresaId, Long cuentaId, LocalDate desde, LocalDate hasta);
}
