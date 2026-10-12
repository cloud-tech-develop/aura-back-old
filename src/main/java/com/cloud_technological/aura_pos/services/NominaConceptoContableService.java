package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.nomina.contabilidad.ConceptoContableNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.contabilidad.GuardarConceptoContableDto;

/**
 * Cuenta y tercero por concepto de nómina (rediseño R4c-2, brecha N3): el contador
 * cambia, por empresa, la cuenta del débito, la del crédito o a nombre de quién va
 * el crédito de cada concepto. El asiento de nómina lo aplica sobre sus reglas.
 */
public interface NominaConceptoContableService {

    /** Todos los conceptos con su valor por defecto y el cambio de la empresa, si lo hay. */
    List<ConceptoContableNominaDto> listar(Integer empresaId);

    /**
     * Guarda el cambio de un concepto. 400 si el concepto no existe, si se cambia un
     * lado que el concepto no tiene, si una cuenta no es auxiliar activa de la empresa o
     * si el tercero fijo falta o no es de la empresa.
     */
    ConceptoContableNominaDto guardar(String codigo, GuardarConceptoContableDto dto, Integer empresaId);

    /** Quita el cambio del concepto: vuelve al valor por defecto. */
    ConceptoContableNominaDto restablecer(String codigo, Integer empresaId);
}
