package com.cloud_technological.aura_pos.contabilidad.application.generador;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorCarteraMora;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.entity.ConceptoContable;

import lombok.RequiredArgsConstructor;

/**
 * Intereses de mora facturados (cartera C5, V205): DB clientes 1305 · CR
 * ingresos financieros 4210, a nombre del cliente. El interés solo se causa
 * cuando se factura, nunca por el solo paso del tiempo.
 */
@Component
@RequiredArgsConstructor
public class InteresMoraGenerador implements GeneradorAsiento {

    private static final String PREFIJO = "IM";

    private final LectorCarteraMora lector;
    private final ResolucionCuentas cuentas;

    @Override
    public String tipoOrigen() {
        return "INTERES_MORA";
    }

    @Override
    public Asiento generar(ContextoContabilizacion ctx) {
        var i = lector.interes(ctx.origenId(), ctx.empresaId());
        String ref = "Intereses de mora " + i.numero();
        return Asiento.builder(ctx.origen(), i.fecha())
                .prefijo(PREFIJO)
                .descripcion(ref + " — " + i.terceroNombre())
                .debito(cuentas.resolver(ctx.empresaId(), ConceptoContable.CLIENTES), ref, i.valor(), i.terceroId())
                .credito(cuentas.resolver(ctx.empresaId(), ConceptoContable.INTERESES_MORA_CLIENTES), ref, i.valor(),
                        i.terceroId())
                .build();
    }
}
