package com.cloud_technological.aura_pos.contabilidad.infrastructure.persistence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.port.LectorConsumoInterno;
import com.cloud_technological.aura_pos.contabilidad.domain.ReglasAsiento;
import com.cloud_technological.aura_pos.entity.ConsumoInternoDetalleEntity;
import com.cloud_technological.aura_pos.entity.ConsumoInternoEntity;
import com.cloud_technological.aura_pos.entity.InventarioConsumoComponenteEntity;
import com.cloud_technological.aura_pos.repositories.consumo_interno.ConsumoInternoDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.consumo_interno.ConsumoInternoJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario_consumo.InventarioConsumoComponenteJPARepository;
import com.cloud_technological.aura_pos.services.ConsumoComposicionService;

import lombok.RequiredArgsConstructor;

/** Proyecta el consumo interno (cabecera + líneas por producto) al snapshot contable. */
@Component
@RequiredArgsConstructor
public class LectorConsumoInternoJpa implements LectorConsumoInterno {

    private final ConsumoInternoJPARepository consumoRepo;
    private final ConsumoInternoDetalleJPARepository detalleRepo;
    private final InventarioConsumoComponenteJPARepository componenteRepo;

    @Override
    public ConsumoInternoContable cargar(Long consumoInternoId, Integer empresaId) {
        ConsumoInternoEntity consumo = consumoRepo.findByIdAndEmpresaId(consumoInternoId, empresaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Consumo interno #" + consumoInternoId + " no encontrado para contabilizar"));

        LocalDate fecha = consumo.getFecha() != null
                ? consumo.getFecha().toLocalDate() : LocalDate.now();

        // Hereda el centro de costo de su sucursal, igual que la venta.
        Long centroCostoId = consumo.getSucursal() != null
                ? consumo.getSucursal().getCentroCostoId() : null;

        Long terceroId = consumo.getResponsable() != null ? consumo.getResponsable().getId() : null;

        List<LineaConsumoInterno> lineas = new ArrayList<>();
        for (ConsumoInternoDetalleEntity d : detalleRepo.findByConsumoInternoId(consumoInternoId)) {
            Long productoId = d.getProducto() != null ? d.getProducto().getId() : null;
            BigDecimal iva = ReglasAsiento.nz(d.getIvaValor());

            List<InventarioConsumoComponenteEntity> componentes = componenteRepo
                    .findByOrigenAndDetalleIdOrderByIdAsc(ConsumoComposicionService.ORIGEN_CONSUMO_INTERNO, d.getId());

            if (componentes.isEmpty()) {
                lineas.add(new LineaConsumoInterno(productoId,
                        ReglasAsiento.nz(d.getCantidad())
                                .multiply(ReglasAsiento.nz(d.getCostoUnitario()))
                                .setScale(2, RoundingMode.HALF_UP),
                        iva));
                continue;
            }

            // Producto con receta: el IVA es del producto, pero el inventario
            // que se descarga es el de cada componente.
            lineas.add(new LineaConsumoInterno(productoId, BigDecimal.ZERO, iva));
            for (InventarioConsumoComponenteEntity c : componentes) {
                lineas.add(new LineaConsumoInterno(c.getProductoHijo().getId(),
                        ReglasAsiento.nz(c.getCantidad())
                                .multiply(ReglasAsiento.nz(c.getCostoUnitario()))
                                .setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.ZERO));
            }
        }

        return new ConsumoInternoContable(fecha,
                consumo.getConcepto() != null ? consumo.getConcepto().getNombre() : "",
                consumo.getConcepto() != null ? consumo.getConcepto().getCuentaId() : null,
                terceroId, centroCostoId,
                Boolean.TRUE.equals(consumo.getGeneraIva()), lineas);
    }
}
