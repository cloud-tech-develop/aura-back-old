package com.cloud_technological.aura_pos.contabilidad.application.generador;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorCarteraMora;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.domain.ReglasAsiento;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.entity.ConceptoContable;

import lombok.RequiredArgsConstructor;

/**
 * Castigo de cartera (C9, V205): la factura incobrable sale contra la provisión
 * (DB 1399) y lo que la provisión no alcanza va al gasto por deterioro (DB 5199)
 * · CR clientes 1305 a nombre del cliente.
 */
@Component
@RequiredArgsConstructor
public class CastigoCarteraGenerador implements GeneradorAsiento {

    private static final String PREFIJO = "CK";

    private final LectorCarteraMora lector;
    private final ResolucionCuentas cuentas;

    @Override
    public String tipoOrigen() {
        return "CASTIGO_CARTERA";
    }

    @Override
    public Asiento generar(ContextoContabilizacion ctx) {
        var k = lector.castigo(ctx.origenId(), ctx.empresaId());
        String ref = "Castigo " + k.numero() + " · cuenta " + k.numeroCuenta();
        var b = Asiento.builder(ctx.origen(), k.fecha())
                .prefijo(PREFIJO)
                .descripcion(ref);
        if (ReglasAsiento.nz(k.contraProvision()).signum() > 0)
            b.debito(cuentas.resolver(ctx.empresaId(), ConceptoContable.PROVISION_CARTERA),
                    ref + " (contra provisión)", k.contraProvision(), k.terceroId());
        if (ReglasAsiento.nz(k.contraGasto()).signum() > 0)
            b.debito(cuentas.resolver(ctx.empresaId(), ConceptoContable.DETERIORO_CARTERA),
                    ref + " (sin provisión)", k.contraGasto(), k.terceroId());
        return b.credito(cuentas.resolver(ctx.empresaId(), ConceptoContable.CLIENTES), ref, k.valor(), k.terceroId())
                .build();
    }
}
