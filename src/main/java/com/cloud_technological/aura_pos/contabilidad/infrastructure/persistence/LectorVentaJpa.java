package com.cloud_technological.aura_pos.contabilidad.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.port.LectorVenta;
import com.cloud_technological.aura_pos.contabilidad.domain.ReglasAsiento;
import com.cloud_technological.aura_pos.entity.VentaEntity;
import com.cloud_technological.aura_pos.repositories.venta_detalle.VentaDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.venta_pago.VentaPagoJPARepository;
import com.cloud_technological.aura_pos.repositories.ventas.VentaJPARepository;

import lombok.RequiredArgsConstructor;

/**
 * Proyecta la venta (cabecera + pagos + líneas por producto) al snapshot de
 * solo lectura que consume {@code VentaGenerador}. Las entities JPA no salen
 * de aquí.
 */
@Component
@RequiredArgsConstructor
public class LectorVentaJpa implements LectorVenta {

    private final VentaJPARepository ventaRepo;
    private final VentaPagoJPARepository ventaPagoRepo;
    private final VentaDetalleJPARepository ventaDetalleRepo;

    @Override
    public VentaContable cargar(Long ventaId, Integer empresaId) {
        VentaEntity venta = ventaRepo.findByIdAndEmpresaId(ventaId, empresaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Venta #" + ventaId + " no encontrada para contabilizar"));

        LocalDate fecha = venta.getFechaEmision().toLocalDate();
        String documento = venta.getConsecutivo() != null
                ? " — " + (venta.getPrefijo() != null ? venta.getPrefijo() + "-" : "")
                        + venta.getConsecutivo()
                : "";
        Long clienteId = venta.getCliente() != null ? venta.getCliente().getId() : null;

        // Base gravable por línea (subtotal − impuesto); la diferencia por
        // descuento general/redondeo la ajusta el generador contra el total.
        List<LineaVenta> lineas = ventaDetalleRepo.findByVentaId(ventaId).stream()
                .map(d -> new LineaVenta(
                        d.getProducto() != null ? d.getProducto().getId() : null,
                        ReglasAsiento.nz(d.getSubtotalLinea())
                                .subtract(ReglasAsiento.nz(d.getImpuestoValor())),
                        ReglasAsiento.nz(d.getImpuestoValor()),
                        ReglasAsiento.nz(d.getCostoLinea())))
                .toList();

        List<PagoVenta> pagos = ventaPagoRepo.findByVentaId(ventaId).stream()
                .map(p -> new PagoVenta(p.getMetodoPago(), p.getMonto(), p.getCuentaBancariaId()))
                .toList();

        Long centroCostoId = venta.getSucursal() != null
                ? venta.getSucursal().getCentroCostoId() : null;

        return new VentaContable(fecha, documento, clienteId,
                venta.getTotalPagar(), venta.getImpuestosTotal(),
                venta.getSaldoPendiente(), centroCostoId, lineas, pagos);
    }
}
