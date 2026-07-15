package com.cloud_technological.aura_pos.contabilidad.application.generador;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorAnticipos;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.domain.ReglasAsiento;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.entity.ConceptoContable;

import lombok.RequiredArgsConstructor;

/**
 * Cruce de anticipo contra factura (E6): cancela el anticipo contra la
 * cartera (DB 2805 · CR 1305) o contra el proveedor (DB 2205 · CR 1330),
 * sin mover caja.
 */
@Component
@RequiredArgsConstructor
public class AnticipoCruceGenerador implements GeneradorAsiento {

    private static final String PREFIJO = "AC";

    private final LectorAnticipos anticipos;
    private final ResolucionCuentas cuentas;

    @Override
    public String tipoOrigen() {
        return "ANTICIPO_CRUCE";
    }

    @Override
    public Asiento generar(ContextoContabilizacion ctx) {
        LectorAnticipos.CruceContable c = anticipos.cargarCruce(ctx.origenId(), ctx.empresaId());
        var monto = ReglasAsiento.nz(c.monto());

        var b = Asiento.builder(ctx.origen(), c.fecha()).prefijo(PREFIJO);
        if ("CLIENTE".equals(c.tipo())) {
            b.descripcion("Cruce anticipo de cliente — cruce #" + ctx.origenId())
                    .debito(cuentas.resolver(ctx.empresaId(), ConceptoContable.ANTICIPOS_CLIENTES),
                            "Aplicación de anticipo", monto, c.terceroId())
                    .credito(cuentas.resolver(ctx.empresaId(), ConceptoContable.CLIENTES),
                            "Abono a cartera con anticipo", monto, c.terceroId());
        } else {
            b.descripcion("Cruce anticipo a proveedor — cruce #" + ctx.origenId())
                    .debito(cuentas.resolver(ctx.empresaId(), ConceptoContable.PROVEEDORES),
                            "Abono a proveedor con anticipo", monto, c.terceroId())
                    .credito(cuentas.resolver(ctx.empresaId(), ConceptoContable.ANTICIPOS_PROVEEDORES),
                            "Aplicación de anticipo", monto, c.terceroId());
        }
        return b.build();
    }
}
