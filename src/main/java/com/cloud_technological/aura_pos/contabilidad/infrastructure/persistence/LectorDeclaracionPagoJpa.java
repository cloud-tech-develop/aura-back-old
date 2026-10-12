package com.cloud_technological.aura_pos.contabilidad.infrastructure.persistence;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.port.LectorDeclaracionPago;
import com.cloud_technological.aura_pos.repositories.contabilidad.DeclaracionPagoQueryRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LectorDeclaracionPagoJpa implements LectorDeclaracionPago {

    private final DeclaracionPagoQueryRepository query;

    @Override
    public PagoDeclaracion cargar(Long pagoId, Integer empresaId) {
        var p = query.pagoContable(pagoId, empresaId)
                .orElseThrow(() -> new IllegalStateException("Pago de declaración #" + pagoId + " no encontrado"));
        var cierres = query.cuentasPago(pagoId).stream()
                .map(c -> new Cierre(c.cuentaId(), c.valor()))
                .toList();
        return new PagoDeclaracion(p.numero(), p.tipo(), p.desde(), p.hasta(), p.fecha(), p.terceroAcreedorId(),
                p.terceroNombre(), p.numeroFormulario(), p.valor(), p.metodoPago(), p.cuentaBancariaId(),
                p.cuentaContableId(), p.ajusteRedondeo(), p.intereses(), p.sancion(), cierres);
    }
}
