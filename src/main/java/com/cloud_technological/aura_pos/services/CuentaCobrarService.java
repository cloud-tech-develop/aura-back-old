package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.cuentas_cobrar.AbonoCobrarDto;
import com.cloud_technological.aura_pos.dto.cuentas_cobrar.CuentaCobrarDto;
import com.cloud_technological.aura_pos.dto.cuentas_cobrar.CuentaCobrarTableDto;
import com.cloud_technological.aura_pos.dto.cuentas_cobrar.CuentaCobrarResumenDto;
import com.cloud_technological.aura_pos.dto.cuentas_cobrar.CreateCuentaCobrarDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface CuentaCobrarService {
    PageImpl<CuentaCobrarTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    PageImpl<CuentaCobrarTableDto> listarConFiltros(PageableDto<Object> pageable, Integer empresaId, 
            String fechaDesde, String fechaHasta, Long clienteId, String estado);
    CuentaCobrarDto obtenerPorId(Long id, Integer empresaId);

    /** Aplica un cruce (abono) desde un documento que no mueve caja (nota crédito, ajuste). */
    default void aplicarCruce(Long cuentaId, java.math.BigDecimal monto, Integer empresaId,
            Integer usuarioId, String referencia) {
        aplicarCruce(cuentaId, monto, empresaId, usuarioId, referencia, null, null);
    }

    /**
     * Aplica un cruce (abono) desde un comprobante, sin generar contabilidad
     * propia — el comprobante ya emitió su asiento.
     *
     * <p>El {@code origen} es lo que mete el recaudo en el cierre de caja: el
     * abono entra al arqueo por su {@code turno_caja_id} y por su método de
     * pago. Sin él, el abono nacía huérfano y el comprobante movía efectivo sin
     * aparecer en el cierre de nadie.
     *
     * @param origen      de dónde entró la plata; null cuando el documento no
     *                    mueve caja (nota crédito de compra, por ejemplo)
     * @param metodoPago  el declarado por el comprobante; si es null se guarda
     *                    el literal histórico {@code COMPROBANTE}
     */
    void aplicarCruce(Long cuentaId, java.math.BigDecimal monto, Integer empresaId,
            Integer usuarioId, String referencia,
            OrigenFondosService.OrigenFondos origen, String metodoPago);

    /**
     * Deshace los cruces que dejó un documento y le devuelve el saldo a la cuenta.
     *
     * <p>La usa la anulación de un comprobante: si el recibo deja de existir, la
     * deuda del cliente vuelve a existir. Busca los abonos por su referencia, no
     * por monto: si el cruce ya se eliminó a mano, la cuenta no se toca —
     * devolverle el saldo dos veces la dejaría inflada.
     */
    void revertirCruce(Long cuentaId, java.math.BigDecimal monto, Integer empresaId, String referencia);

    /**
     * Deshace todos los cruces de un documento sin saber de antemano a qué
     * cuentas tocó. Un mismo comprobante puede abonar varias facturas del mismo
     * cliente, y al anularlo hay que devolverles el saldo a todas.
     */
    void revertirCrucesDeDocumento(String referencia, Integer empresaId);

    CuentaCobrarDto crear(CreateCuentaCobrarDto dto, Integer empresaId, Long usuarioId);
    CuentaCobrarDto actualizar(Long id, CreateCuentaCobrarDto dto, Integer empresaId);
    
    // Abonos
    AbonoCobrarDto registrarAbono(Long cuentaId, AbonoCobrarDto dto, Integer empresaId, Long usuarioId);
    List<AbonoCobrarDto> listarAbonos(Long cuentaId, Integer empresaId);
    void eliminarAbono(Long cuentaId, Long abonoId, Integer empresaId);

    // Anula la cuenta por cobrar asociada a una venta (al anular la venta).
    // Lanza error si la cuenta ya tiene abonos registrados.
    void anularPorVenta(Long ventaId, Integer empresaId);
    
    // Resumen
    CuentaCobrarResumenDto obtenerResumen(Integer empresaId, String fechaDesde, String fechaHasta, Long clienteId, String estado);
    List<CuentaCobrarTableDto> obtenerVencidas(Integer empresaId);
}
