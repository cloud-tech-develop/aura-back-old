package com.cloud_technological.aura_pos.contabilidad.application.generador;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorNota;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.domain.ReglasAsiento;
import com.cloud_technological.aura_pos.contabilidad.domain.AsientoBuilder;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.entity.ConceptoContable;

import lombok.RequiredArgsConstructor;

/**
 * Nota crédito electrónica (F5): reversa de la venta. DB Ingresos por ventas +
 * DB IVA generado · CR Clientes (queda a favor del cliente / reduce cartera).
 * El costo/inventario no se reversa aquí: la nota no trae el detalle de costo.
 */
@Component
@RequiredArgsConstructor
public class NotaCreditoGenerador implements GeneradorAsiento {

    private static final String PREFIJO = "NC";

    private final LectorNota notas;
    private final ResolucionCuentas cuentas;

    @Override
    public String tipoOrigen() {
        return "NOTA_CREDITO";
    }

    @Override
    public Asiento generar(ContextoContabilizacion ctx) {
        LectorNota.NotaContable n = notas.cargar(ctx.origenId(), ctx.empresaId());

        AsientoBuilder b = Asiento.builder(ctx.origen(), n.fecha())
                .prefijo(PREFIJO)
                .descripcion("Nota crédito electrónica" + n.documento())
                .debito(cuentas.resolver(ctx.empresaId(), ConceptoContable.INGRESOS_VENTAS),
                        "Reversa ingreso por venta", ReglasAsiento.nz(n.base()));

        if (n.iva() != null && n.iva().signum() != 0) {
            b.debito(cuentas.resolver(ctx.empresaId(), ConceptoContable.IVA_GENERADO),
                    "Reversa IVA generado", ReglasAsiento.nz(n.iva()));
        }

        return b.credito(cuentas.resolver(ctx.empresaId(), ConceptoContable.CLIENTES),
                        "Nota crédito a favor del cliente", ReglasAsiento.nz(n.total()), n.clienteId())
                .build();
    }
}
