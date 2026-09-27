package com.cloud_technological.aura_pos.services;

import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.DocumentosAbiertos;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.PlanCuentas;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.Resultado;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.Saldos;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.Terceros;

/**
 * Importador desde Excel para migrar una empresa. Validar no graba nada;
 * confirmar vuelve a validar y graba todo o nada.
 */
public interface ImportacionService {

    Resultado validarPlan(Integer empresaId, PlanCuentas req);

    Resultado confirmarPlan(Integer empresaId, PlanCuentas req);

    Resultado validarTerceros(Integer empresaId, Terceros req);

    Resultado confirmarTerceros(Integer empresaId, Terceros req);

    Resultado validarSaldos(Integer empresaId, Saldos req);

    Resultado confirmarSaldos(Integer empresaId, Integer usuarioId, Saldos req);

    Resultado validarDocumentos(Integer empresaId, DocumentosAbiertos req);

    Resultado confirmarDocumentos(Integer empresaId, Long usuarioId, DocumentosAbiertos req);
}
