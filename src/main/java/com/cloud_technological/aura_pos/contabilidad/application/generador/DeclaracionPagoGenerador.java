package com.cloud_technological.aura_pos.contabilidad.application.generador;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorDeclaracionPago;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaPago;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.entity.ConceptoContable;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;

import lombok.RequiredArgsConstructor;

/**
 * Pago de una declaración (V204): DB cada cuenta del impuesto por su saldo del
 * período (CR las que tenían saldo débito: IVA descontable, retenciones que le
 * practicaron) · CR caja o banco por el total. Todo a nombre del acreedor
 * (DIAN o la secretaría de hacienda del municipio).
 */
@Component
@RequiredArgsConstructor
public class DeclaracionPagoGenerador implements GeneradorAsiento {

    private static final String PREFIJO = "PD";
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LectorDeclaracionPago pagos;
    private final ResolucionCuentaPago cuentaPago;
    private final ResolucionCuentas cuentas;

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    @Override
    public String tipoOrigen() {
        return "DECLARACION_PAGO";
    }

    @Override
    public Asiento generar(ContextoContabilizacion ctx) {
        var p = pagos.cargar(ctx.origenId(), ctx.empresaId());
        Long acreedor = p.terceroAcreedorId();
        String declaracion = switch (p.tipo()) {
            case "IVA" -> "IVA";
            case "RETEICA" -> "reteICA";
            case "ICA" -> "industria y comercio";
            default -> "retención en la fuente";
        };
        String ref = p.numero() + " · " + declaracion + " " + p.desde().format(DMY) + "–" + p.hasta().format(DMY)
                + (p.numeroFormulario() != null ? " · form. " + p.numeroFormulario() : "");

        var b = Asiento.builder(ctx.origen(), p.fecha())
                .prefijo(PREFIJO)
                .descripcion("Pago " + ref + " — " + p.terceroNombre());

        for (var c : p.cierres()) {
            BigDecimal v = c.valor();
            if (v.signum() > 0) {
                b.debito(c.cuentaId(), "Pago " + ref, v, acreedor);
            } else if (v.signum() < 0) {
                b.credito(c.cuentaId(), "Pago " + ref, v.negate(), acreedor);
            }
        }
        // Redondeo a miles, intereses y sanción (V207): lo que sale del banco es
        // el saldo exacto más estos tres.
        BigDecimal ajuste = nz(p.ajusteRedondeo());
        BigDecimal intereses = nz(p.intereses());
        BigDecimal sancion = nz(p.sancion());
        if (ajuste.signum() > 0) {
            b.debito(cuentas.resolver(ctx.empresaId(), ConceptoContable.AJUSTE_PESO_GASTO),
                    "Ajuste al peso " + ref, ajuste, acreedor);
        } else if (ajuste.signum() < 0) {
            b.credito(cuentas.resolver(ctx.empresaId(), ConceptoContable.AJUSTE_PESO_INGRESO),
                    "Ajuste al peso " + ref, ajuste.negate(), acreedor);
        }
        if (intereses.signum() > 0) {
            b.debito(cuentas.resolver(ctx.empresaId(), ConceptoContable.INTERESES_MORA_FISCAL),
                    "Intereses de mora " + ref, intereses, acreedor);
        }
        if (sancion.signum() > 0) {
            b.debito(cuentas.resolver(ctx.empresaId(), ConceptoContable.SANCIONES_FISCALES),
                    "Sanción " + ref, sancion, acreedor);
        }
        Long salida = cuentaPago.resolver(ctx.empresaId(), p.metodoPago(), p.cuentaBancariaId(),
                p.cuentaContableId());
        b.credito(salida, "Pago " + ref, p.valor().add(ajuste).add(intereses).add(sancion), acreedor);
        return b.build();
    }
}
