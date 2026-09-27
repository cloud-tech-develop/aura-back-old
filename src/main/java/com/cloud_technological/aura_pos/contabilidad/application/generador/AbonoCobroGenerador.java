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
 *
 * <p>Si el cliente retuvo (V181), cada retención va al débito de su subcuenta
 * del anticipo de impuestos (135515 renta, 135517 IVA, 135518 ICA) y clientes
 * se acredita por el pago más lo retenido: la factura queda saldada aunque el
 * dinero recibido sea menor.
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

        var b = Asiento.builder(ctx.origen(), abono.fecha())
                .prefijo(PREFIJO)
                .descripcion("Recaudo cartera — abono #" + ctx.origenId())
                .debito(cuentaPago.resolver(ctx.empresaId(), abono.metodoPago(),
                                abono.cuentaBancariaId(), abono.cuentaContableId()),
                        "Recaudo cartera (" + abono.metodoPago() + ")", ReglasAsiento.nz(abono.monto()));

        java.math.BigDecimal cartera = ReglasAsiento.nz(abono.monto());
        for (LectorAbonos.Retencion r : abono.retenciones()) {
            java.math.BigDecimal valor = ReglasAsiento.nz(r.valor());
            if (valor.signum() == 0) continue;
            b.debito(cuentas.resolver(ctx.empresaId(), conceptoDe(r.tipo())),
                    "Retención que nos practicó el cliente (" + r.tipo() + ")", valor, abono.terceroId());
            cartera = cartera.add(valor);
        }

        return b.credito(cuentas.resolver(ctx.empresaId(), ConceptoContable.CLIENTES),
                        "Abono cartera cliente", cartera, abono.terceroId())
                .build();
    }

    private static ConceptoContable conceptoDe(String tipo) {
        return switch (tipo != null ? tipo.toUpperCase() : "") {
            case "RETEIVA" -> ConceptoContable.RETEIVA_ASUMIDA;
            case "RETEICA" -> ConceptoContable.RETEICA_ASUMIDA;
            default -> ConceptoContable.RETEFUENTE_ASUMIDA;
        };
    }
}
