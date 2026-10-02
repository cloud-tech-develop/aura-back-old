package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.contabilidad.VistaPreviaAsientoDto;
import com.cloud_technological.aura_pos.entity.AsientoDetalleEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;
import com.cloud_technological.aura_pos.services.implementations.ContabilidadAutoServiceImpl.DatosCompraAsiento;
import com.cloud_technological.aura_pos.services.implementations.ContabilidadAutoServiceImpl.LineaCompraAsiento;
import com.cloud_technological.aura_pos.services.implementations.ContabilidadAutoServiceImpl.PagoCompraAsiento;

import lombok.RequiredArgsConstructor;

/**
 * Vista previa del asiento (Fase 4): arma las partidas con el mismo código
 * del asiento real, sin guardar el documento ni tocar la contabilidad.
 */
@Service
@RequiredArgsConstructor
public class VistaPreviaAsientoService {

    private final ContabilidadAutoServiceImpl contabilidad;
    private final PlanCuentaJPARepository planRepo;

    public VistaPreviaAsientoDto compra(VistaPreviaAsientoDto.CompraRequest r, Integer empresaId) {
        List<LineaCompraAsiento> lineas = new ArrayList<>();
        BigDecimal neto = BigDecimal.ZERO;
        BigDecimal iva = BigDecimal.ZERO;
        for (var l : r.getLineas() != null ? r.getLineas() : List.<VistaPreviaAsientoDto.LineaCompra>of()) {
            BigDecimal n = nz(l.getNeto());
            BigDecimal i = nz(l.getIva());
            lineas.add(new LineaCompraAsiento(l.getProductoId(), n, i));
            neto = neto.add(n);
            iva = iva.add(i);
        }
        BigDecimal fletes = nz(r.getFletes());
        // Mismas fórmulas que CompraServiceImpl: retefuente e ICA sobre el neto, reteIVA sobre el IVA.
        BigDecimal retefuente = pct(neto, r.getRetefuentePct());
        BigDecimal reteiva = pct(iva, r.getReteivaPct());
        BigDecimal reteica = pct(neto, r.getReteicaPct());
        BigDecimal netaAPagar = neto.add(iva).add(fletes).subtract(retefuente).subtract(reteiva).subtract(reteica);

        List<PagoCompraAsiento> pagos = new ArrayList<>();
        if (!"CREDITO".equalsIgnoreCase(r.getFormaPago())) {
            if (r.getPagos() != null && !r.getPagos().isEmpty()) {
                r.getPagos().forEach(p -> pagos.add(new PagoCompraAsiento(p.getMetodoPago(),
                        p.getCuentaBancariaId(), p.getCuentaContableId(), nz(p.getMonto()))));
            } else {
                pagos.add(new PagoCompraAsiento("EFECTIVO", null, null, netaAPagar));
            }
        }

        boolean esNota = "NOTA_CREDITO".equalsIgnoreCase(r.getTipoDocumento());
        List<AsientoDetalleEntity> partidas = contabilidad.lineasCompra(empresaId, new DatosCompraAsiento(
                neto, BigDecimal.ZERO, fletes, iva, netaAPagar, r.getProveedorId(), esNota,
                r.getCuentaContableId(), lineas, retefuente, reteiva, reteica, pagos));
        return armar(partidas, empresaId);
    }

    private VistaPreviaAsientoDto armar(List<AsientoDetalleEntity> partidas, Integer empresaId) {
        VistaPreviaAsientoDto dto = new VistaPreviaAsientoDto();
        List<VistaPreviaAsientoDto.Linea> lineas = new ArrayList<>();
        BigDecimal db = BigDecimal.ZERO;
        BigDecimal cr = BigDecimal.ZERO;
        for (AsientoDetalleEntity p : partidas) {
            VistaPreviaAsientoDto.Linea l = new VistaPreviaAsientoDto.Linea();
            l.setCuentaId(p.getCuentaId());
            planRepo.findByIdAndEmpresaId(p.getCuentaId(), empresaId).ifPresent(c -> {
                l.setCuentaCodigo(c.getCodigo());
                l.setCuentaNombre(c.getNombre());
            });
            l.setDescripcion(p.getDescripcion());
            l.setDebito(nz(p.getDebito()));
            l.setCredito(nz(p.getCredito()));
            l.setTerceroId(p.getTerceroId());
            lineas.add(l);
            db = db.add(l.getDebito());
            cr = cr.add(l.getCredito());
        }
        dto.setLineas(lineas);
        dto.setTotalDebito(db);
        dto.setTotalCredito(cr);
        dto.setCuadra(db.compareTo(cr) == 0);
        return dto;
    }

    private static BigDecimal pct(BigDecimal base, BigDecimal pct) {
        if (pct == null || pct.signum() == 0) return BigDecimal.ZERO;
        return base.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
