package com.cloud_technological.aura_pos.contabilidad.infrastructure.resolucion;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionImpuesto;
import com.cloud_technological.aura_pos.entity.ConceptoContable;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.ImpuestoJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;

import lombok.RequiredArgsConstructor;

/**
 * Cadena de resolución del impuesto (E5): impuesto del producto → su cuenta
 * generado/descontable; sin impuesto o sin cuenta, cae a los conceptos de la
 * empresa (240801/240802) — idéntico al comportamiento previo.
 */
@Component
@RequiredArgsConstructor
public class ResolucionImpuestoJpa implements ResolucionImpuesto {

    private final ProductoJPARepository productoRepo;
    private final ImpuestoJPARepository impuestoRepo;
    private final ResolucionCuentas cuentas;

    @Override
    public Long resolverGenerado(Long productoId, Integer empresaId) {
        Long cuenta = cuentaImpuesto(productoId, empresaId, true);
        return cuenta != null ? cuenta
                : cuentas.resolver(empresaId, ConceptoContable.IVA_GENERADO);
    }

    @Override
    public Long resolverDescontable(Long productoId, Integer empresaId) {
        Long cuenta = cuentaImpuesto(productoId, empresaId, false);
        return cuenta != null ? cuenta
                : cuentas.resolver(empresaId, ConceptoContable.IVA_DESCONTABLE);
    }

    private Long cuentaImpuesto(Long productoId, Integer empresaId, boolean generado) {
        if (productoId == null) {
            return null;
        }
        return productoRepo.findByIdAndEmpresaId(productoId, empresaId)
                .map(ProductoEntity::getImpuestoId)
                .flatMap(impuestoId -> impuestoRepo.findByIdAndEmpresaId(impuestoId, empresaId))
                .filter(i -> Boolean.TRUE.equals(i.getActivo()))
                .map(i -> generado ? i.getCuentaGeneradoId() : i.getCuentaDescontableId())
                .orElse(null);
    }
}
