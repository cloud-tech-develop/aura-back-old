package com.cloud_technological.aura_pos.contabilidad.application.generador;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorDiferido;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.domain.ReglasAsiento;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.entity.ConceptoContable;

import lombok.RequiredArgsConstructor;

/**
 * Cuota mensual de un gasto diferido (E6): reconoce el gasto del mes
 * (DB cuenta de gasto del documento · CR 1705).
 */
@Component
@RequiredArgsConstructor
public class DiferidoAmortizacionGenerador implements GeneradorAsiento {

    private static final String PREFIJO = "DF";

    private final LectorDiferido diferidos;
    private final ResolucionCuentas cuentas;

    @Override
    public String tipoOrigen() {
        return "DIFERIDO";
    }

    @Override
    public Asiento generar(ContextoContabilizacion ctx) {
        LectorDiferido.CuotaDiferido cuota = diferidos.cargar(ctx.origenId(), ctx.empresaId());
        Long cuentaGasto = cuota.cuentaGastoId() != null
                ? cuota.cuentaGastoId()
                : cuentas.resolver(ctx.empresaId(), ConceptoContable.GASTO_GENERAL);
        var monto = ReglasAsiento.nz(cuota.monto());

        return Asiento.builder(ctx.origen(), cuota.fecha())
                .prefijo(PREFIJO)
                .descripcion("Amortización diferido " + cuota.periodo()
                        + " — gasto #" + cuota.gastoId())
                .debito(cuentaGasto, "Gasto del mes (diferido)", monto, cuota.terceroId(),
                        cuota.centroCostoId())
                .credito(cuentas.resolver(ctx.empresaId(), ConceptoContable.GASTOS_PAGADOS_ANTICIPADO),
                        "Amortización gasto pagado por anticipado", monto)
                .build();
    }
}
