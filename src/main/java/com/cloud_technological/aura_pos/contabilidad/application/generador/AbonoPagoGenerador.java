package com.cloud_technological.aura_pos.contabilidad.application.generador;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorAbonos;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaPago;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.domain.ReglasAsiento;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.entity.ConceptoContable;

import lombok.RequiredArgsConstructor;

/**
 * Pago a proveedor (E2): DB proveedores con el tercero · CR caja/bancos
 * según el medio de pago. Réplica del asiento EG legacy.
 */
@Component
@RequiredArgsConstructor
public class AbonoPagoGenerador implements GeneradorAsiento {

    private static final String PREFIJO = "EG";

    private final LectorAbonos abonos;
    private final ResolucionCuentas cuentas;
    private final ResolucionCuentaPago cuentaPago;

    @Override
    public String tipoOrigen() {
        return "ABONO_PAGAR";
    }

    @Override
    public Asiento generar(ContextoContabilizacion ctx) {
        LectorAbonos.AbonoContable abono = abonos.cargarPago(ctx.origenId(), ctx.empresaId());

        return Asiento.builder(ctx.origen(), abono.fecha())
                .prefijo(PREFIJO)
                .descripcion("Pago a proveedor — abono #" + ctx.origenId())
                .debito(cuentas.resolver(ctx.empresaId(), ConceptoContable.PROVEEDORES),
                        "Pago a proveedor", ReglasAsiento.nz(abono.monto()), abono.terceroId())
                .credito(cuentaPago.resolver(ctx.empresaId(), abono.metodoPago(), abono.cuentaBancariaId()),
                        "Egreso pago (" + abono.metodoPago() + ")", ReglasAsiento.nz(abono.monto()))
                .build();
    }
}
