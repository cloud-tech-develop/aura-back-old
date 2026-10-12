package com.cloud_technological.aura_pos.services.implementations;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.repositories.contabilidad.AcreedorImpuestoQueryRepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/**
 * Tercero acreedor de impuestos y tarifas de retención (V204): a quién se le
 * paga. Las líneas del asiento de cada factura siguen a nombre del cliente o
 * proveedor; el acreedor se usa en la declaración y en su pago.
 */
@Service
@RequiredArgsConstructor
public class AcreedorImpuestoService {

    private final AcreedorImpuestoQueryRepository repo;

    /** El acreedor elegido debe ser un tercero vivo de la empresa (null = sin acreedor). */
    public Long validar(Integer empresaId, Long terceroId) {
        if (terceroId == null) return null;
        return repo.tercero(terceroId, empresaId)
                .map(AcreedorImpuestoQueryRepository.Tercero::id)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                        "El tercero acreedor no existe o está borrado."));
    }

    /** "DIRECCIÓN DE IMPUESTOS… · 800197268-4" para mostrar en las listas. */
    public String etiqueta(Integer empresaId, Long terceroId) {
        if (terceroId == null) return null;
        return repo.tercero(terceroId, empresaId)
                .map(t -> t.nombre() + " · " + t.documento())
                .orElse(null);
    }

    /** DIAN como tercero y acreedora por defecto de IVA, INC, retefuente y reteIVA. */
    public void asegurarDian(Integer empresaId) {
        repo.asegurarDian(empresaId);
    }
}
