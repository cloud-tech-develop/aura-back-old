package com.cloud_technological.aura_pos.contabilidad.application.generador;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorVenta;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaPago;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaProducto;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.domain.AsientoBuilder;
import com.cloud_technological.aura_pos.contabilidad.domain.ReglasAsiento;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.entity.ConceptoContable;

import lombok.RequiredArgsConstructor;

/**
 * Asiento de la venta con categorías contables (E4): un crédito de ingreso
 * por cada cuenta resuelta y un par costo/inventario por cada combinación,
 * omitiendo COGS en servicios. La diferencia entre la suma de bases y el
 * ingreso neto (descuento general/redondeo) se ajusta contra el grupo mayor.
 * Sin categorías configuradas, contabiliza idéntico al motor original.
 */
@Component
@RequiredArgsConstructor
public class VentaGenerador implements GeneradorAsiento {

    private static final String PREFIJO = "VT";

    private final LectorVenta ventas;
    private final ResolucionCuentas cuentas;
    private final ResolucionCuentaPago cuentaPago;
    private final ResolucionCuentaProducto cuentaProducto;
    private final com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionImpuesto impuesto;

    @Override
    public String tipoOrigen() {
        return "VENTA";
    }

    @Override
    public Asiento generar(ContextoContabilizacion ctx) {
        LectorVenta.VentaContable venta = ventas.cargar(ctx.origenId(), ctx.empresaId());
        Integer empresaId = ctx.empresaId();

        // E7: la venta hereda el centro de costo de su sucursal en TODAS las líneas.
        Long cc = venta.centroCostoId();
        AsientoBuilder b = Asiento.builder(ctx.origen(), venta.fecha())
                .prefijo(PREFIJO)
                .descripcion("Venta #" + ctx.origenId() + venta.documento());

        // Recaudo de contado: cada pago no-crédito entra a bancos o caja.
        for (LectorVenta.PagoVenta pago : venta.pagos()) {
            if ("CREDITO".equalsIgnoreCase(pago.metodoPago())) {
                continue;
            }
            b.debito(cuentaPago.resolver(empresaId, pago.metodoPago(), pago.cuentaBancariaId()),
                    "Recaudo venta (" + pago.metodoPago() + ")", ReglasAsiento.nz(pago.monto()),
                    null, cc);
        }

        b.debito(cuentas.resolver(empresaId, ConceptoContable.CLIENTES),
                "Cartera venta a crédito", ReglasAsiento.nz(venta.saldoPendiente()),
                venta.clienteId(), cc);

        BigDecimal impuestos = ReglasAsiento.nz(venta.impuestos());
        BigDecimal ingresoNeto = ReglasAsiento.nz(venta.total()).subtract(impuestos);

        // Agrupación por cuenta resuelta (producto → categoría → concepto)
        // e impuestos por la cuenta del impuesto del producto (E5).
        Map<Long, BigDecimal> ingresosPorCuenta = new LinkedHashMap<>();
        Map<Long, BigDecimal> impuestosPorCuenta = new LinkedHashMap<>();
        Map<CogsKey, BigDecimal> costoPorCuentas = new LinkedHashMap<>();
        for (LectorVenta.LineaVenta linea : venta.lineas()) {
            ResolucionCuentaProducto.CuentasProducto cp =
                    cuentaProducto.resolver(linea.productoId(), empresaId);
            ingresosPorCuenta.merge(cp.ingresoId(), ReglasAsiento.nz(linea.base()), BigDecimal::add);
            BigDecimal ivaLinea = ReglasAsiento.nz(linea.impuesto());
            if (ivaLinea.signum() > 0) {
                impuestosPorCuenta.merge(impuesto.resolverGenerado(linea.productoId(), empresaId),
                        ivaLinea, BigDecimal::add);
            }
            BigDecimal costo = ReglasAsiento.nz(linea.costo());
            if (!cp.esServicio() && costo.signum() > 0) {
                costoPorCuentas.merge(new CogsKey(cp.costoId(), cp.inventarioId()),
                        costo, BigDecimal::add);
            }
        }

        if (ingresosPorCuenta.isEmpty()) {
            ingresosPorCuenta.put(cuentas.resolver(empresaId, ConceptoContable.INGRESOS_VENTAS),
                    ingresoNeto);
        } else {
            ajustarDelta(ingresosPorCuenta, ingresoNeto);
        }
        ingresosPorCuenta.forEach((cuentaId, monto) ->
                b.credito(cuentaId, "Ingresos venta", monto, null, cc));

        if (impuestosPorCuenta.isEmpty()) {
            b.credito(cuentas.resolver(empresaId, ConceptoContable.IVA_GENERADO),
                    "IVA generado", impuestos, null, cc);
        } else {
            ajustarDelta(impuestosPorCuenta, impuestos);
            impuestosPorCuenta.forEach((cuentaId, monto) ->
                    b.credito(cuentaId, "Impuesto generado", monto, null, cc));
        }

        costoPorCuentas.forEach((key, costo) -> b
                .debito(key.costoId(), "Costo de venta", costo, null, cc)
                .credito(key.inventarioId(), "Salida de inventario", costo, null, cc));

        return b.build();
    }

    /**
     * El ingreso contable debe ser exactamente total − IVA: el descuento
     * general de cabecera y los redondeos por línea se ajustan contra el
     * grupo de mayor valor.
     */
    private void ajustarDelta(Map<Long, BigDecimal> ingresosPorCuenta, BigDecimal ingresoNeto) {
        BigDecimal suma = ingresosPorCuenta.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal delta = ingresoNeto.subtract(suma);
        if (delta.signum() == 0) {
            return;
        }
        Long cuentaMayor = ingresosPorCuenta.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow();
        ingresosPorCuenta.merge(cuentaMayor, delta, BigDecimal::add);
    }

    private record CogsKey(Long costoId, Long inventarioId) {
    }
}
