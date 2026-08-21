package com.cloud_technological.aura_pos.contabilidad.infrastructure.persistence;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.port.LectorTrasladoFondos;
import com.cloud_technological.aura_pos.entity.TrasladoFondosEntity;
import com.cloud_technological.aura_pos.repositories.traslado_fondos.TrasladoFondosJPARepository;

import lombok.RequiredArgsConstructor;

/** Proyecta el traslado de fondos para su generador de asiento. */
@Component
@RequiredArgsConstructor
public class LectorTrasladoFondosJpa implements LectorTrasladoFondos {

    private final TrasladoFondosJPARepository trasladoRepo;

    @Override
    public TrasladoFondos cargar(Long trasladoId, Integer empresaId) {
        TrasladoFondosEntity t = trasladoRepo.findByIdAndEmpresaId(trasladoId, empresaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Traslado de fondos #" + trasladoId + " no encontrado para la empresa"));

        // Sin las dos cuentas no hay asiento posible. Es un fallo de datos, no
        // del usuario: el servicio las resuelve y las exige al crear.
        if (t.getOrigenCuentaId() == null || t.getDestinoCuentaId() == null) {
            throw new IllegalStateException("El traslado de fondos #" + trasladoId
                    + " no tiene resueltas las cuentas contables de origen y destino");
        }
        return new TrasladoFondos(t.getFecha(), t.getMonto(),
                t.getOrigenCuentaId(), t.getDestinoCuentaId(),
                t.getConcepto(), t.getObservacion());
    }
}
