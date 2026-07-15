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
 * Recaudo de cartera (E2): DB caja/bancos según el medio de pago ·
 * CR clientes con el tercero. Réplica del asiento RC legacy.
 */
@Component
@RequiredArgsConstructor
public class AbonoCobroGenerador implements GeneradorAsiento {

    private static final String PREFIJO = "RC";

    private final LectorAbonos abonos;
    private final ResolucionCuentas cuentas;
    private final ResolucionCuentaPago cuentaPago;

    @Override
    public String tipoOrigen() {
        return "ABONO_COBRAR";
    }

    @Override
    public Asiento generar(ContextoContabilizacion ctx) {
        LectorAbonos.AbonoContable abono = abonos.cargarCobro(ctx.origenId(), ctx.empresaId());

        return Asiento.builder(ctx.origen(), abono.fecha())
                .prefijo(PREFIJO)
                .descripcion("Recaudo cartera — abono #" + ctx.origenId())
                .debito(cuentaPago.resolver(ctx.empresaId(), abono.metodoPago(), abono.cuentaBancariaId()),
                        "Recaudo cartera (" + abono.metodoPago() + ")", ReglasAsiento.nz(abono.monto()))
                .credito(cuentas.resolver(ctx.empresaId(), ConceptoContable.CLIENTES),
                        "Abono cartera cliente", ReglasAsiento.nz(abono.monto()), abono.terceroId())
                .build();
    }
}
