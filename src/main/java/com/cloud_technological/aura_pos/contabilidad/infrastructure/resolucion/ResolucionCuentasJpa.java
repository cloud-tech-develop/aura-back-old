package com.cloud_technological.aura_pos.contabilidad.infrastructure.resolucion;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.contabilidad.application.exception.CuentaNoParametrizadaException;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.entity.ConceptoContable;
import com.cloud_technological.aura_pos.services.ConfiguracionContableService;

import lombok.RequiredArgsConstructor;

/**
 * Adapter sobre la resolución existente (override de cuenta_config →
 * código default del concepto), traduciendo su error HTTP a la excepción
 * tipada del módulo.
 */
@Component
@RequiredArgsConstructor
public class ResolucionCuentasJpa implements ResolucionCuentas {

    private final ConfiguracionContableService config;

    @Override
    public Long resolver(Integer empresaId, ConceptoContable concepto) {
        try {
            return config.resolverCuenta(empresaId, concepto).getId();
        } catch (ResponseStatusException ex) {
            throw new CuentaNoParametrizadaException(ex.getReason() != null
                    ? ex.getReason()
                    : "No hay cuenta contable configurada para el concepto '" + concepto.name() + "'");
        }
    }
}
