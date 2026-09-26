package com.cloud_technological.aura_pos.contabilidad.infrastructure.persistence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.port.LectorObsequio;
import com.cloud_technological.aura_pos.contabilidad.domain.ReglasAsiento;
import com.cloud_technological.aura_pos.entity.InventarioConsumoComponenteEntity;
import com.cloud_technological.aura_pos.entity.ObsequioDetalleEntity;
import com.cloud_technological.aura_pos.entity.ObsequioEntity;
import com.cloud_technological.aura_pos.repositories.inventario_consumo.InventarioConsumoComponenteJPARepository;
import com.cloud_technological.aura_pos.repositories.obsequio.ObsequioDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.obsequio.ObsequioJPARepository;
import com.cloud_technological.aura_pos.services.ConsumoComposicionService;

import lombok.RequiredArgsConstructor;

/** Proyecta el obsequio (cabecera + líneas por producto) al snapshot contable. */
@Component
@RequiredArgsConstructor
public class LectorObsequioJpa implements LectorObsequio {

    private final ObsequioJPARepository obsequioRepo;
    private final ObsequioDetalleJPARepository detalleRepo;
    private final InventarioConsumoComponenteJPARepository consumoRepo;

    @Override
    public ObsequioContable cargar(Long obsequioId, Integer empresaId) {
        ObsequioEntity obsequio = obsequioRepo.findByIdAndEmpresaId(obsequioId, empresaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Obsequio #" + obsequioId + " no encontrado para contabilizar"));

        LocalDate fecha = obsequio.getFecha() != null
                ? obsequio.getFecha().toLocalDate() : LocalDate.now();

        // El obsequio hereda el centro de costo de su sucursal, igual que la venta.
        Long centroCostoId = obsequio.getSucursal() != null
                ? obsequio.getSucursal().getCentroCostoId() : null;

        Long terceroId = obsequio.getTercero() != null ? obsequio.getTercero().getId() : null;

        List<LineaObsequio> lineas = new ArrayList<>();
        for (ObsequioDetalleEntity d : detalleRepo.findByObsequioId(obsequioId)) {
            Long productoId = d.getProducto() != null ? d.getProducto().getId() : null;
            BigDecimal iva = ReglasAsiento.nz(d.getIvaValor());

            List<InventarioConsumoComponenteEntity> consumos = consumoRepo
                    .findByOrigenAndDetalleIdOrderByIdAsc(ConsumoComposicionService.ORIGEN_OBSEQUIO, d.getId());

            if (consumos.isEmpty()) {
                lineas.add(new LineaObsequio(productoId,
                        ReglasAsiento.nz(d.getCantidad())
                                .multiply(ReglasAsiento.nz(d.getCostoUnitario()))
                                .setScale(2, RoundingMode.HALF_UP),
                        iva));
                continue;
            }

            // Producto con receta: el IVA es del producto regalado, pero el
            // inventario que se descarga es el de cada componente.
            lineas.add(new LineaObsequio(productoId, BigDecimal.ZERO, iva));
            for (InventarioConsumoComponenteEntity c : consumos) {
                lineas.add(new LineaObsequio(c.getProductoHijo().getId(),
                        ReglasAsiento.nz(c.getCantidad())
                                .multiply(ReglasAsiento.nz(c.getCostoUnitario()))
                                .setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.ZERO));
            }
        }

        return new ObsequioContable(fecha, obsequio.getMotivo(), terceroId, centroCostoId,
                Boolean.TRUE.equals(obsequio.getGeneraIva()), lineas);
    }
}
