package com.cloud_technological.aura_pos.contabilidad.application.generador;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorDeterioro;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.domain.ReglasAsiento;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.entity.ConceptoContable;

import lombok.RequiredArgsConstructor;

/**
 * Deterioro de cartera por edades (E6, NIIF pymes secc. 11): DB gasto 5199 ·
 * CR provisión 1399. Nace SIEMPRE en BORRADOR — nunca automático directo.
 */
@Component
@RequiredArgsConstructor
public class DeterioroGenerador implements GeneradorAsiento {

    private static final String PREFIJO = "DT";

    private final LectorDeterioro deterioros;
    private final ResolucionCuentas cuentas;

    @Override
    public String tipoOrigen() {
        return "DETERIORO";
    }

    @Override
    public boolean siempreBorrador() {
        return true;
    }

    @Override
    public Asiento generar(ContextoContabilizacion ctx) {
        LectorDeterioro.DeterioroContable d = deterioros.cargar(ctx.origenId(), ctx.empresaId());
        var monto = ReglasAsiento.nz(d.monto());

        return Asiento.builder(ctx.origen(), d.fecha())
                .prefijo(PREFIJO)
                .descripcion("Deterioro de cartera — propuesta #" + ctx.origenId()
                        + (d.detalle() != null ? " (" + d.detalle() + ")" : ""))
                .debito(cuentas.resolver(ctx.empresaId(), ConceptoContable.DETERIORO_CARTERA),
                        "Gasto por deterioro de cartera", monto)
                .credito(cuentas.resolver(ctx.empresaId(), ConceptoContable.PROVISION_CARTERA),
                        "Provisión de cartera", monto)
                .build();
    }
}
