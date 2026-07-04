package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.contabilidad.CuentaConfigDto;
import com.cloud_technological.aura_pos.entity.ConceptoContable;
import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;

/**
 * Resuelve conceptos contables a cuentas del PUC por empresa, con override
 * configurable y fallback al código por defecto del concepto.
 */
public interface ConfiguracionContableService {

    /**
     * Devuelve la cuenta del PUC asignada al concepto para la empresa.
     * Usa el override de {@code cuenta_config} si existe; si no, cae al código
     * por defecto del concepto. Lanza excepción si no encuentra ninguna cuenta
     * activa.
     */
    PlanCuentaEntity resolverCuenta(Integer empresaId, ConceptoContable concepto);

    /** Lista el mapeo de todos los conceptos para la empresa (incluye los que usan default). */
    List<CuentaConfigDto> listar(Integer empresaId);

    /** Crea o actualiza el mapeo de un concepto a una cuenta. */
    CuentaConfigDto actualizar(Integer empresaId, ConceptoContable concepto, Long cuentaId);

    /**
     * Siembra los mapeos por defecto (concepto → cuenta del código por defecto)
     * para una empresa, omitiendo los que ya existan. Idempotente.
     */
    void seedDefaults(Integer empresaId);
}
