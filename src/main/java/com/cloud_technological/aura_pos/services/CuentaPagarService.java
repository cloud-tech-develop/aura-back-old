package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.cuentas_pagar.AbonoPagarDto;
import com.cloud_technological.aura_pos.dto.cuentas_pagar.CuentaPagarDto;
import com.cloud_technological.aura_pos.dto.cuentas_pagar.CuentaPagarTableDto;
import com.cloud_technological.aura_pos.dto.cuentas_pagar.CuentaPagarResumenDto;
import com.cloud_technological.aura_pos.dto.cuentas_pagar.CreateCuentaPagarDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface CuentaPagarService {
    PageImpl<CuentaPagarTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    PageImpl<CuentaPagarTableDto> listarConFiltros(PageableDto<Object> pageable, Integer empresaId, 
            String fechaDesde, String fechaHasta, Long proveedorId, String estado);
    CuentaPagarDto obtenerPorId(Long id, Integer empresaId);
    CuentaPagarDto crear(CreateCuentaPagarDto dto, Integer empresaId, Long usuarioId);
    CuentaPagarDto actualizar(Long id, CreateCuentaPagarDto dto, Integer empresaId);
    
    // Abonos
    AbonoPagarDto registrarAbono(Long cuentaId, AbonoPagarDto dto, Integer empresaId, Long usuarioId);
    List<AbonoPagarDto> listarAbonos(Long cuentaId, Integer empresaId);
    void eliminarAbono(Long cuentaId, Long abonoId, Integer empresaId);
    
    // Resumen
    CuentaPagarResumenDto obtenerResumen(Integer empresaId, String fechaDesde, String fechaHasta, Long proveedorId, String estado);
    List<CuentaPagarTableDto> obtenerVencidas(Integer empresaId);
}
