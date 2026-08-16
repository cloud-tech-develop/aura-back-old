package com.cloud_technological.aura_pos.contabilidad.application.generador;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorObsequio;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaProducto;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionImpuesto;
import com.cloud_technological.aura_pos.contabilidad.domain.AsientoBuilder;
import com.cloud_technological.aura_pos.contabilidad.domain.ReglasAsiento;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.entity.ConceptoContable;

import lombok.RequiredArgsConstructor;

/**
 * Obsequio: sale inventario sin que entre nada a cambio.
 *
 * <pre>
 * DB 523550 Obsequios y muestras  ·  CR inventario del producto   (costo)
 * DB 529505 IVA asumido en retiro ·  CR IVA generado del producto (si genera_iva)
 * </pre>
 *
 * No hay ingreso ni cartera: regalar no es vender. El segundo par existe
 * porque el retiro de inventario se considera venta para efectos de IVA, así
 * que el impuesto se causa sobre el valor comercial aunque no se cobre — y lo
 * asume la empresa.
 *
 * La cuenta de inventario se resuelve por producto (misma cadena que usa la
 * venta), para que lo regalado descargue exactamente el mismo inventario que
 * habría descargado de venderse.
 */
@Component
@RequiredArgsConstructor
public class ObsequioGenerador implements GeneradorAsiento {

    private static final String PREFIJO = "OB";

    private final LectorObsequio obsequios;
    private final ResolucionCuentas cuentas;
    private final ResolucionCuentaProducto cuentaProducto;
    private final ResolucionImpuesto impuesto;

    @Override
    public String tipoOrigen() {
        return "OBSEQUIO";
    }

    @Override
    public Asiento generar(ContextoContabilizacion ctx) {
        LectorObsequio.ObsequioContable obsequio = obsequios.cargar(ctx.origenId(), ctx.empresaId());
        Integer empresaId = ctx.empresaId();
        Long cc = obsequio.centroCostoId();

        AsientoBuilder b = Asiento.builder(ctx.origen(), obsequio.fecha())
                .prefijo(PREFIJO)
                .descripcion("Obsequio #" + ctx.origenId() + " — " + obsequio.motivo());

        // Agrupación por cuenta: un producto de categoría distinta descarga
        // otro inventario, igual que en la venta.
        Map<Long, BigDecimal> inventarioPorCuenta = new LinkedHashMap<>();
        Map<Long, BigDecimal> ivaPorCuenta = new LinkedHashMap<>();
        BigDecimal costoTotal = BigDecimal.ZERO;
        BigDecimal ivaTotal = BigDecimal.ZERO;

        for (LectorObsequio.LineaObsequio linea : obsequio.lineas()) {
            ResolucionCuentaProducto.CuentasProducto cp =
                    cuentaProducto.resolver(linea.productoId(), empresaId);

            // Un servicio no tiene inventario que descargar: regalar una hora
            // de mano de obra no mueve el almacén.
            BigDecimal costo = ReglasAsiento.nz(linea.costo());
            if (!cp.esServicio() && costo.signum() > 0) {
                inventarioPorCuenta.merge(cp.inventarioId(), costo, BigDecimal::add);
                costoTotal = costoTotal.add(costo);
            }

            BigDecimal iva = ReglasAsiento.nz(linea.iva());
            if (obsequio.generaIva() && iva.signum() > 0) {
                ivaPorCuenta.merge(impuesto.resolverGenerado(linea.productoId(), empresaId),
                        iva, BigDecimal::add);
                ivaTotal = ivaTotal.add(iva);
            }
        }

        b.debito(cuentas.resolver(empresaId, ConceptoContable.GASTO_OBSEQUIOS),
                "Costo de mercancía entregada en obsequio", costoTotal,
                obsequio.terceroId(), cc);
        inventarioPorCuenta.forEach((cuentaId, monto) ->
                b.credito(cuentaId, "Salida de inventario por obsequio", monto, null, cc));

        b.debito(cuentas.resolver(empresaId, ConceptoContable.IVA_ASUMIDO_RETIRO),
                "IVA asumido por retiro de inventario", ivaTotal,
                obsequio.terceroId(), cc);
        ivaPorCuenta.forEach((cuentaId, monto) ->
                b.credito(cuentaId, "IVA generado por retiro de inventario", monto, null, cc));

        return b.build();
    }
}
