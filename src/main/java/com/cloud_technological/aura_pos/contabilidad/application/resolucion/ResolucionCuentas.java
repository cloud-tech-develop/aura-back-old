package com.cloud_technological.aura_pos.contabilidad.application.resolucion;

import com.cloud_technological.aura_pos.contabilidad.application.exception.CuentaNoParametrizadaException;
import com.cloud_technological.aura_pos.entity.ConceptoContable;

/**
 * Puerto de resolución concepto → cuenta del PUC por empresa
 * (override de {@code cuenta_config} o código default del concepto).
 * Los generadores JAMÁS conocen códigos de cuenta.
 */
public interface ResolucionCuentas {

    /**
     * Id de la cuenta activa que la empresa tiene asignada al concepto.
     *
     * @throws CuentaNoParametrizadaException si nada resuelve
     */
    Long resolver(Integer empresaId, ConceptoContable concepto);
}
