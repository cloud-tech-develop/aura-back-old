package com.cloud_technological.aura_pos.contabilidad.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.port.LectorObsequio;
import com.cloud_technological.aura_pos.contabilidad.domain.ReglasAsiento;
import com.cloud_technological.aura_pos.entity.ObsequioEntity;
import com.cloud_technological.aura_pos.repositories.obsequio.ObsequioDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.obsequio.ObsequioJPARepository;

import lombok.RequiredArgsConstructor;

/** Proyecta el obsequio (cabecera + líneas por producto) al snapshot contable. */
@Component
@RequiredArgsConstructor
public class LectorObsequioJpa implements LectorObsequio {

    private final ObsequioJPARepository obsequioRepo;
    private final ObsequioDetalleJPARepository detalleRepo;

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

        List<LineaObsequio> lineas = detalleRepo.findByObsequioId(obsequioId).stream()
                .map(d -> new LineaObsequio(
                        d.getProducto() != null ? d.getProducto().getId() : null,
                        ReglasAsiento.nz(d.getCantidad())
                                .multiply(ReglasAsiento.nz(d.getCostoUnitario()))
                                .setScale(2, java.math.RoundingMode.HALF_UP),
                        ReglasAsiento.nz(d.getIvaValor())))
                .toList();

        return new ObsequioContable(fecha, obsequio.getMotivo(), terceroId, centroCostoId,
                Boolean.TRUE.equals(obsequio.getGeneraIva()), lineas);
    }
}
