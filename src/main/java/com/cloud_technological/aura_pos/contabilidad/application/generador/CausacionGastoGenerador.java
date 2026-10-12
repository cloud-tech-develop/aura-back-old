package com.cloud_technological.aura_pos.contabilidad.application.generador;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorCausacionGasto;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.domain.ReglasAsiento;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.entity.ConceptoContable;

import lombok.RequiredArgsConstructor;

/**
 * Causación de gasto (V201): DB cada cuenta de gasto (con su centro de costo; el
 * IVA no descontable va al gasto) · DB IVA descontable · CR retenciones
 * practicadas · CR cuenta por pagar por el neto. Siempre con el proveedor.
 */
@Component
@RequiredArgsConstructor
public class CausacionGastoGenerador implements GeneradorAsiento {

    private static final String PREFIJO = "CG";

    private final LectorCausacionGasto causaciones;
    private final ResolucionCuentas cuentas;

    @Override
    public String tipoOrigen() {
        return "CAUSACION_GASTO";
    }

    @Override
    public Asiento generar(ContextoContabilizacion ctx) {
        var c = causaciones.cargar(ctx.origenId(), ctx.empresaId());
        Integer emp = ctx.empresaId();
        Long tercero = c.terceroId();
        String ref = c.numero() + (c.numeroSoporte() != null ? " · fact. " + c.numeroSoporte() : "");

        var b = Asiento.builder(ctx.origen(), c.fecha())
                .prefijo(PREFIJO)
                .descripcion("Causación " + ref + " — " + c.terceroNombre());

        BigDecimal ivaDescontable = BigDecimal.ZERO;
        BigDecimal reteiva = BigDecimal.ZERO;
        BigDecimal reteica = BigDecimal.ZERO;
        Map<Long, BigDecimal> retefuentePorCuenta = new LinkedHashMap<>();

        for (var l : c.lineas()) {
            BigDecimal iva = ReglasAsiento.nz(l.iva());
            BigDecimal gasto = ReglasAsiento.nz(l.base()).add(l.ivaDescontable() ? BigDecimal.ZERO : iva);
            if (l.ivaDescontable()) ivaDescontable = ivaDescontable.add(iva);
            String desc = l.descripcion() != null && !l.descripcion().isBlank() ? l.descripcion() : "Gasto " + ref;
            b.debito(l.cuentaId(), desc, gasto, tercero, l.centroCostoId());

            BigDecimal rf = ReglasAsiento.nz(l.retefuente());
            if (rf.signum() > 0) {
                Long cuenta = l.retefuenteCuentaId() != null ? l.retefuenteCuentaId()
                        : cuentas.resolver(emp, ConceptoContable.RETEFUENTE_PRACTICADA);
                retefuentePorCuenta.merge(cuenta, rf, BigDecimal::add);
            }
            reteiva = reteiva.add(ReglasAsiento.nz(l.reteiva()));
            reteica = reteica.add(ReglasAsiento.nz(l.reteica()));
        }

        if (ivaDescontable.signum() > 0) {
            b.debito(cuentas.resolver(emp, ConceptoContable.IVA_DESCONTABLE), "IVA descontable " + ref, ivaDescontable);
        }
        retefuentePorCuenta.forEach((cuenta, valor) -> b.credito(cuenta, "Retefuente " + ref, valor, tercero));
        if (reteiva.signum() > 0) {
            b.credito(cuentas.resolver(emp, ConceptoContable.RETEIVA_PRACTICADA), "ReteIVA " + ref, reteiva, tercero);
        }
        if (reteica.signum() > 0) {
            b.credito(cuentas.resolver(emp, ConceptoContable.RETEICA_PRACTICADA), "ReteICA " + ref, reteica, tercero);
        }
        Long cuentaPagar = c.cuentaPagarId() != null ? c.cuentaPagarId()
                : cuentas.resolver(emp, ConceptoContable.PROVEEDORES);
        b.credito(cuentaPagar, "Por pagar " + ref, c.totalPagar(), tercero);
        return b.build();
    }
}
