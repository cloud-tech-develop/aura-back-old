package com.cloud_technological.aura_pos.contabilidad.infrastructure.persistence;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.port.LectorCierreAnual;
import com.cloud_technological.aura_pos.entity.CierreAnualEntity;
import com.cloud_technological.aura_pos.entity.DistribucionUtilidadesEntity;
import com.cloud_technological.aura_pos.entity.DividendoPagoEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.CierreAnualJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.DistribucionUtilidadesJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.DividendoPagoJPARepository;

import lombok.RequiredArgsConstructor;

/** Proyecta las operaciones del cierre anual (E8) para sus generadores. */
@Component
@RequiredArgsConstructor
public class LectorCierreAnualJpa implements LectorCierreAnual {

    private final CierreAnualJPARepository cierreRepo;
    private final DistribucionUtilidadesJPARepository distribucionRepo;
    private final DividendoPagoJPARepository pagoRepo;

    @Override
    public OperacionContable cargarOperacion(Long id, Integer empresaId) {
        CierreAnualEntity c = cierreRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Operación de cierre anual #" + id + " no encontrada"));
        return new OperacionContable(c.getTipo(), c.getAnio(), c.getFecha(),
                c.getMonto(), c.getDetalle());
    }

    @Override
    public DistribucionContable cargarDistribucion(Long id, Integer empresaId) {
        DistribucionUtilidadesEntity d = distribucionRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Distribución de utilidades #" + id + " no encontrada"));
        return new DistribucionContable(d.getAnio(), d.getFecha(),
                d.getReservaLegal(), d.getDividendos());
    }

    @Override
    public PagoDividendoContable cargarPago(Long id, Integer empresaId) {
        DividendoPagoEntity p = pagoRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Pago de dividendos #" + id + " no encontrado"));
        return new PagoDividendoContable(p.getFecha(), p.getMonto(),
                p.getMetodoPago(), p.getCuentaBancariaId(), p.getTerceroId());
    }
}
