package com.cloud_technological.aura_pos.contabilidad.application.generador;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorNota;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.domain.AsientoBuilder;
import com.cloud_technological.aura_pos.contabilidad.domain.ReglasAsiento;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.entity.ConceptoContable;

import lombok.RequiredArgsConstructor;

/**
 * Nota débito electrónica (F5): cobro adicional sobre una factura. Espejo de la
 * nota crédito. DB Clientes · CR Ingresos por ventas + CR IVA generado.
 */
@Component
@RequiredArgsConstructor
public class NotaDebitoGenerador implements GeneradorAsiento {

    private static final String PREFIJO = "ND";

    private final LectorNota notas;
    private final ResolucionCuentas cuentas;

    @Override
    public String tipoOrigen() {
        return "NOTA_DEBITO";
    }

    @Override
    public Asiento generar(ContextoContabilizacion ctx) {
        LectorNota.NotaContable n = notas.cargar(ctx.origenId(), ctx.empresaId());

        AsientoBuilder b = Asiento.builder(ctx.origen(), n.fecha())
                .prefijo(PREFIJO)
                .descripcion("Nota débito electrónica" + n.documento())
                .debito(cuentas.resolver(ctx.empresaId(), ConceptoContable.CLIENTES),
                        "Cobro adicional al cliente", ReglasAsiento.nz(n.total()), n.clienteId())
                .credito(cuentas.resolver(ctx.empresaId(), ConceptoContable.INGRESOS_VENTAS),
                        "Ingreso adicional por venta", ReglasAsiento.nz(n.base()));

        if (n.iva() != null && n.iva().signum() != 0) {
            b.credito(cuentas.resolver(ctx.empresaId(), ConceptoContable.IVA_GENERADO),
                    "IVA generado adicional", ReglasAsiento.nz(n.iva()));
        }

        return b.build();
    }
}
