package com.cloud_technological.aura_pos.contabilidad.infrastructure.persistence;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.port.LectorAbonos;
import com.cloud_technological.aura_pos.entity.AbonoCobrarEntity;
import com.cloud_technological.aura_pos.entity.AbonoPagarEntity;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.AbonoCobrarJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_pagar.AbonoPagarJPARepository;

import lombok.RequiredArgsConstructor;

/**
 * Proyecta los abonos al snapshot que consumen los generadores RC/EG.
 * La fecha contable es hoy (mismo comportamiento del flujo legacy).
 */
@Component
@RequiredArgsConstructor
public class LectorAbonosJpa implements LectorAbonos {

    private final AbonoCobrarJPARepository abonoCobrarRepo;
    private final AbonoPagarJPARepository abonoPagarRepo;

    @Override
    public AbonoContable cargarCobro(Long abonoId, Integer empresaId) {
        AbonoCobrarEntity abono = abonoCobrarRepo.findById(abonoId)
                .orElseThrow(() -> new IllegalStateException(
                        "Abono de cobro #" + abonoId + " no encontrado para contabilizar"));
        Long terceroId = abono.getCuentaCobrar() != null && abono.getCuentaCobrar().getTercero() != null
                ? abono.getCuentaCobrar().getTercero().getId() : null;
        return new AbonoContable(LocalDate.now(), abono.getMonto(), terceroId,
                abono.getMetodoPago(), null);
    }

    @Override
    public AbonoContable cargarPago(Long abonoId, Integer empresaId) {
        AbonoPagarEntity abono = abonoPagarRepo.findById(abonoId)
                .orElseThrow(() -> new IllegalStateException(
                        "Abono de pago #" + abonoId + " no encontrado para contabilizar"));
        Long terceroId = abono.getCuentaPagar() != null && abono.getCuentaPagar().getTercero() != null
                ? abono.getCuentaPagar().getTercero().getId() : null;
        return new AbonoContable(LocalDate.now(), abono.getMonto(), terceroId,
                abono.getMetodoPago(), abono.getCuentaBancariaId());
    }
}
