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

    /** Aplica un cruce (abono) desde un comprobante, sin generar contabilidad propia. */
    void aplicarCruce(Long cuentaId, java.math.BigDecimal monto, Integer empresaId, Integer usuarioId, String referencia);

    /**
     * Deshace el cruce que dejó un documento y le devuelve el saldo a la cuenta.
     *
     * <p>La usa la anulación de una nota crédito de compra: si el crédito
     * desaparece, la deuda con el proveedor tiene que volver a existir. A
     * diferencia de {@code eliminarAbono}, no exige que el abono sea del día —
     * una nota crédito se puede anular semanas después y la deuda no puede
     * quedarse rebajada por eso.
     */
    void revertirCruce(Long cuentaId, java.math.BigDecimal monto, Integer empresaId, String referencia);
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
