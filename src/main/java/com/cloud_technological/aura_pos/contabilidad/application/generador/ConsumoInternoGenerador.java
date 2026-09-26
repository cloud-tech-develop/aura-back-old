package com.cloud_technological.aura_pos.contabilidad.application.generador;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorConsumoInterno;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaProducto;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionImpuesto;
import com.cloud_technological.aura_pos.contabilidad.domain.AsientoBuilder;
import com.cloud_technological.aura_pos.contabilidad.domain.ReglasAsiento;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.entity.ConceptoContable;

import lombok.RequiredArgsConstructor;

/**
 * Consumo interno: el negocio usa su propio inventario.
 *
 * <pre>
 * DB cuenta del concepto (gasto o activo) ·  CR inventario del producto   (costo)
 * DB 529505 IVA asumido en retiro         ·  CR IVA generado del producto (si genera_iva)
 * </pre>
 *
 * Igual que el obsequio, no hay ingreso ni cartera. La diferencia es a dónde
 * va el costo: a la cuenta del concepto (aseo, mantenimiento…) y no a
 * publicidad. Si el concepto no tiene cuenta, se usa GASTO_CONSUMO_INTERNO.
 */
@Component
@RequiredArgsConstructor
public class ConsumoInternoGenerador implements GeneradorAsiento {

    private static final String PREFIJO = "CI";

    private final LectorConsumoInterno consumos;
    private final ResolucionCuentas cuentas;
    private final ResolucionCuentaProducto cuentaProducto;
    private final ResolucionImpuesto impuesto;

    @Override
    public String tipoOrigen() {
        return "CONSUMO_INTERNO";
    }

    @Override
    public Asiento generar(ContextoContabilizacion ctx) {
        LectorConsumoInterno.ConsumoInternoContable consumo = consumos.cargar(ctx.origenId(), ctx.empresaId());
        Integer empresaId = ctx.empresaId();
        Long cc = consumo.centroCostoId();

        AsientoBuilder b = Asiento.builder(ctx.origen(), consumo.fecha())
                .prefijo(PREFIJO)
                .descripcion("Consumo interno #" + ctx.origenId() + " — " + consumo.concepto());

        Map<Long, BigDecimal> inventarioPorCuenta = new LinkedHashMap<>();
        Map<Long, BigDecimal> ivaPorCuenta = new LinkedHashMap<>();
        BigDecimal costoTotal = BigDecimal.ZERO;
        BigDecimal ivaTotal = BigDecimal.ZERO;

        for (LectorConsumoInterno.LineaConsumoInterno linea : consumo.lineas()) {
            ResolucionCuentaProducto.CuentasProducto cp =
                    cuentaProducto.resolver(linea.productoId(), empresaId);

            BigDecimal costo = ReglasAsiento.nz(linea.costo());
            if (!cp.esServicio() && costo.signum() > 0) {
                inventarioPorCuenta.merge(cp.inventarioId(), costo, BigDecimal::add);
                costoTotal = costoTotal.add(costo);
            }

            BigDecimal iva = ReglasAsiento.nz(linea.iva());
            if (consumo.generaIva() && iva.signum() > 0) {
                ivaPorCuenta.merge(impuesto.resolverGenerado(linea.productoId(), empresaId),
                        iva, BigDecimal::add);
                ivaTotal = ivaTotal.add(iva);
            }
        }

        Long cuentaGasto = consumo.cuentaGastoId() != null
                ? consumo.cuentaGastoId()
                : cuentas.resolver(empresaId, ConceptoContable.GASTO_CONSUMO_INTERNO);

        b.debito(cuentaGasto, "Consumo interno de inventario — " + consumo.concepto(), costoTotal,
                consumo.terceroId(), cc);
        inventarioPorCuenta.forEach((cuentaId, monto) ->
                b.credito(cuentaId, "Salida de inventario por consumo interno", monto, null, cc));

        b.debito(cuentas.resolver(empresaId, ConceptoContable.IVA_ASUMIDO_RETIRO),
                "IVA asumido por retiro de inventario para uso propio", ivaTotal,
                consumo.terceroId(), cc);
        ivaPorCuenta.forEach((cuentaId, monto) ->
                b.credito(cuentaId, "IVA generado por retiro de inventario", monto, null, cc));

        return b.build();
    }
}
