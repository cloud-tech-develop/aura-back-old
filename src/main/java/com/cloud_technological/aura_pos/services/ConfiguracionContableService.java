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

    /**
     * Crea o actualiza el mapeo de un concepto a una cuenta, con guardarraíles:
     * la cuenta debe existir, estar activa, ser de movimiento (auxiliar) y su
     * código debe empezar por un prefijo permitido del concepto. Todo cambio
     * queda en {@code contabilidad_config_log}.
     */
    CuentaConfigDto actualizar(Integer empresaId, ConceptoContable concepto, Long cuentaId,
            Long usuarioId);

    /** Historial de cambios del mapeo (auditoría), más reciente primero. */
    List<com.cloud_technological.aura_pos.dto.contabilidad.ConfigLogDto> listarLog(Integer empresaId);

    /** Modo de contabilización de la empresa: AUTOMATICO | REVISION (E3). */
    String obtenerModo(Integer empresaId);

    /** Cambia el modo de contabilización. Solo acepta AUTOMATICO o REVISION. */
    String actualizarModo(Integer empresaId, String modo);

    /**
     * Siembra los mapeos por defecto (concepto → cuenta del código por defecto)
     * para una empresa, omitiendo los que ya existan. Idempotente.
     */
    void seedDefaults(Integer empresaId);
}
