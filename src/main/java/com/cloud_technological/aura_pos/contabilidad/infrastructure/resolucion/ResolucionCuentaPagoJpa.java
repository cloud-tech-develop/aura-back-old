package com.cloud_technological.aura_pos.contabilidad.infrastructure.resolucion;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaPago;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.entity.ConceptoContable;
import com.cloud_technological.aura_pos.entity.CuentaBancariaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;
import com.cloud_technological.aura_pos.repositories.tesoreria.CuentaBancariaJPARepository;

import lombok.RequiredArgsConstructor;

/**
 * Resuelve la cuenta de un movimiento de dinero, en este orden (E2):
 * (1) cuenta contable de la cuenta bancaria del pago, si la tiene y está
 * activa; (2) cuenta de la forma de pago parametrizada (forma_pago_contable);
 * (3) fallback por método (efectivo→CAJA, resto→BANCOS).
 */
@Component
@RequiredArgsConstructor
public class ResolucionCuentaPagoJpa implements ResolucionCuentaPago {

    private final CuentaBancariaJPARepository cuentaBancariaRepo;
    private final PlanCuentaJPARepository planRepo;
    private final ResolucionCuentas cuentas;
    private final com.cloud_technological.aura_pos.services.FormaPagoContableService formasPago;

    @Override
    public Long resolver(Integer empresaId, String metodoPago, Long cuentaBancariaId) {
        if (cuentaBancariaId != null) {
            CuentaBancariaEntity cb = cuentaBancariaRepo
                    .findByIdAndEmpresaId(cuentaBancariaId, empresaId).orElse(null);
            if (cb != null && cb.getCuentaContableId() != null) {
                Long cuentaId = planRepo.findByIdAndEmpresaId(cb.getCuentaContableId(), empresaId)
                        .filter(c -> Boolean.TRUE.equals(c.getActiva()))
                        .map(c -> c.getId())
                        .orElse(null);
                if (cuentaId != null) {
                    return cuentaId;
                }
            }
        }
        Long cuentaFormaPago = formasPago.cuentaPara(empresaId, metodoPago);
        if (cuentaFormaPago != null) {
            return cuentaFormaPago;
        }
        return cuentas.resolver(empresaId, conceptoPago(metodoPago));
    }

    private ConceptoContable conceptoPago(String metodoPago) {
        if (metodoPago != null && metodoPago.toUpperCase().contains("EFECTIVO")) {
            return ConceptoContable.CAJA;
        }
        return ConceptoContable.BANCOS;
    }
}
