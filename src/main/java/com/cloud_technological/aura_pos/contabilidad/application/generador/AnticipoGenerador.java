package com.cloud_technological.aura_pos.contabilidad.application.generador;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorAnticipos;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaPago;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.domain.ReglasAsiento;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.entity.ConceptoContable;

import lombok.RequiredArgsConstructor;

/**
 * Anticipo (E6): el dinero recibido de un cliente sin factura NO es ingreso
 * (DB caja/banco · CR 2805); el entregado a proveedor NO es gasto
 * (DB 1330 · CR caja/banco). Siempre con el tercero en la partida.
 */
@Component
@RequiredArgsConstructor
public class AnticipoGenerador implements GeneradorAsiento {

    private static final String PREFIJO = "AN";

    private final LectorAnticipos anticipos;
    private final ResolucionCuentas cuentas;
    private final ResolucionCuentaPago cuentaPago;

    @Override
    public String tipoOrigen() {
        return "ANTICIPO";
    }

    @Override
    public Asiento generar(ContextoContabilizacion ctx) {
        LectorAnticipos.AnticipoContable a = anticipos.cargar(ctx.origenId(), ctx.empresaId());
        Long cuentaDinero = cuentaPago.resolver(ctx.empresaId(), a.metodoPago(), a.cuentaBancariaId());
        var monto = ReglasAsiento.nz(a.monto());

        var b = Asiento.builder(ctx.origen(), a.fecha()).prefijo(PREFIJO);
        if ("CLIENTE".equals(a.tipo())) {
            b.descripcion("Anticipo de cliente #" + ctx.origenId())
                    .debito(cuentaDinero, "Recaudo anticipo (" + a.metodoPago() + ")", monto)
                    .credito(cuentas.resolver(ctx.empresaId(), ConceptoContable.ANTICIPOS_CLIENTES),
                            "Anticipo recibido de cliente", monto, a.terceroId());
        } else {
            b.descripcion("Anticipo a proveedor #" + ctx.origenId())
                    .debito(cuentas.resolver(ctx.empresaId(), ConceptoContable.ANTICIPOS_PROVEEDORES),
                            "Anticipo entregado a proveedor", monto, a.terceroId())
                    .credito(cuentaDinero, "Egreso anticipo (" + a.metodoPago() + ")", monto);
        }
        return b.build();
    }
}
