package com.cloud_technological.aura_pos.contabilidad.application.generador;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorCierreAnual;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaPago;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.domain.ReglasAsiento;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.entity.ConceptoContable;

import lombok.RequiredArgsConstructor;

/**
 * Pago de dividendos decretados (E8): DB 2360 (con el tercero socio si se
 * indicó) · CR caja/banco según el medio de pago. Nace CONTABILIZADO.
 */
@Component
@RequiredArgsConstructor
public class DividendoPagoGenerador implements GeneradorAsiento {

    private static final String PREFIJO = "PD";

    private final LectorCierreAnual cierres;
    private final ResolucionCuentas cuentas;
    private final ResolucionCuentaPago cuentaPago;

    @Override
    public String tipoOrigen() {
        return "DIVIDENDO_PAGO";
    }

    @Override
    public boolean siempreContabilizado() {
        return true;
    }

    @Override
    public Asiento generar(ContextoContabilizacion ctx) {
        LectorCierreAnual.PagoDividendoContable p = cierres.cargarPago(ctx.origenId(), ctx.empresaId());
        Long cuentaDinero = cuentaPago.resolver(ctx.empresaId(), p.metodoPago(), p.cuentaBancariaId());
        var monto = ReglasAsiento.nz(p.monto());

        return Asiento.builder(ctx.origen(), p.fecha())
                .prefijo(PREFIJO)
                .descripcion("Pago de dividendos #" + ctx.origenId())
                .debito(cuentas.resolver(ctx.empresaId(), ConceptoContable.DIVIDENDOS_POR_PAGAR),
                        "Pago dividendos decretados", monto, p.terceroId())
                .credito(cuentaDinero, "Egreso pago de dividendos (" + p.metodoPago() + ")", monto)
                .build();
    }
}
