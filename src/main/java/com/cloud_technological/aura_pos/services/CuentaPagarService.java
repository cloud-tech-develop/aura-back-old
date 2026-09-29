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

    /** Aplica un cruce (abono) desde un documento que no mueve caja (nota crédito, ajuste). */
    default void aplicarCruce(Long cuentaId, java.math.BigDecimal monto, Integer empresaId,
            Integer usuarioId, String referencia) {
        aplicarCruce(cuentaId, monto, empresaId, usuarioId, referencia, null, null);
    }

    /**
     * Aplica un cruce (abono) desde un comprobante, sin generar contabilidad
     * propia — el comprobante ya emitió su asiento.
     *
     * <p>El {@code origen} es lo que mete el pago en el cierre de caja: el abono
     * entra al arqueo por su {@code turno_caja_id} y por su método de pago. Sin
     * él, el abono nacía huérfano y el comprobante sacaba efectivo sin aparecer
     * en el cierre de nadie.
     *
     * @param origen      de dónde salió la plata; null cuando el documento no
     *                    mueve caja (nota crédito de compra, por ejemplo)
     * @param metodoPago  el declarado por el comprobante; si es null se guarda
     *                    el literal histórico {@code COMPROBANTE}
     */
    void aplicarCruce(Long cuentaId, java.math.BigDecimal monto, Integer empresaId,
            Integer usuarioId, String referencia,
            OrigenFondosService.OrigenFondos origen, String metodoPago);

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

    /**
     * Deshace todos los cruces de un documento sin saber de antemano a qué
     * cuentas tocó. Un mismo comprobante puede abonar varias facturas del mismo
     * proveedor, y al anularlo hay que devolverles el saldo a todas.
     */
    void revertirCrucesDeDocumento(String referencia, Integer empresaId);
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
