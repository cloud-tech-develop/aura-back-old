package com.cloud_technological.aura_pos.services;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.nomina.nomina.AddNovedadDto;
import com.cloud_technological.aura_pos.dto.nomina.nomina.NominaDto;
import com.cloud_technological.aura_pos.dto.nomina.nomina.NominaTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface NominaService {
    PageImpl<NominaTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    NominaDto obtenerPorId(Long id, Integer empresaId);
    com.cloud_technological.aura_pos.dto.nomina.nomina.PeriodoResumenDto obtenerResumenPeriodo(Long periodoId, Integer empresaId);
    NominaDto liquidar(Long periodoId, Long empleadoId, Integer empresaId);
    void liquidarPeriodoCompleto(Long periodoId, Integer empresaId);
    NominaDto agregarNovedad(Long nominaId, AddNovedadDto dto, Integer empresaId);
    NominaDto eliminarNovedad(Long nominaId, Long novedadId, Integer empresaId);
    NominaDto aprobar(Long id, Integer empresaId);
    NominaDto pagar(Long id, com.cloud_technological.aura_pos.dto.nomina.nomina.PagoNominaDto dto, Integer empresaId);
    void pagarPeriodo(Long periodoId, com.cloud_technological.aura_pos.dto.nomina.nomina.PagoNominaDto dto, Integer empresaId);
    NominaDto anular(Long id, Integer empresaId);
}
