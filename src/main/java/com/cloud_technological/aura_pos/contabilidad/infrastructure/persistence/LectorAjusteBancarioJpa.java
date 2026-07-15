package com.cloud_technological.aura_pos.contabilidad.infrastructure.persistence;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.port.LectorAjusteBancario;
import com.cloud_technological.aura_pos.entity.CuentaBancariaEntity;
import com.cloud_technological.aura_pos.entity.ExtractoBancarioEntity;
import com.cloud_technological.aura_pos.entity.ExtractoLineaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.ExtractoBancarioJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.ExtractoLineaJPARepository;
import com.cloud_technological.aura_pos.repositories.tesoreria.CuentaBancariaJPARepository;

import lombok.RequiredArgsConstructor;

/** Proyecta la línea de extracto marcada como ajuste (E9) para su generador. */
@Component
@RequiredArgsConstructor
public class LectorAjusteBancarioJpa implements LectorAjusteBancario {

    private final ExtractoLineaJPARepository lineaRepo;
    private final ExtractoBancarioJPARepository extractoRepo;
    private final CuentaBancariaJPARepository cuentaBancariaRepo;

    @Override
    public AjusteBancario cargar(Long lineaId, Integer empresaId) {
        ExtractoLineaEntity linea = lineaRepo.findById(lineaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Línea de extracto #" + lineaId + " no encontrada"));
        ExtractoBancarioEntity extracto = extractoRepo
                .findByIdAndEmpresaId(linea.getExtractoId(), empresaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Extracto #" + linea.getExtractoId() + " no encontrado para la empresa"));
        CuentaBancariaEntity cuenta = cuentaBancariaRepo
                .findByIdAndEmpresaId(extracto.getCuentaBancariaId(), empresaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Cuenta bancaria #" + extracto.getCuentaBancariaId() + " no encontrada"));
        if (cuenta.getCuentaContableId() == null) {
            throw new IllegalStateException("La cuenta bancaria '" + cuenta.getNombre()
                    + "' no tiene cuenta contable asociada");
        }
        if (linea.getTipoAjuste() == null) {
            throw new IllegalStateException(
                    "La línea de extracto #" + lineaId + " no está marcada como ajuste");
        }
        return new AjusteBancario(linea.getFecha(), linea.getDescripcion(), linea.getValor(),
                linea.getTipoAjuste(), cuenta.getCuentaContableId(), cuenta.getTerceroId());
    }
}
