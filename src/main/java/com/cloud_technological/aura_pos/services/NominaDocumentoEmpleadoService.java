package com.cloud_technological.aura_pos.services;

import java.math.BigDecimal;
import java.util.List;

import com.cloud_technological.aura_pos.dto.nomina.documento.AgregarEmpleadosDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.CandidatoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.EmpleadoDocumentoDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.ResultadoAgregarEmpleadosDto;

/**
 * Pestaña Empleados de la prenómina (rediseño R3.2b): agregar por "tipo de lista",
 * listar y quitar. Cada empleado es una fila {@code nomina} del documento marcada
 * como prenómina.
 */
public interface NominaDocumentoEmpleadoService {

    /** Empleados (filas) del documento. */
    List<EmpleadoDocumentoDto> listar(Long documentoId, Integer empresaId);

    /**
     * Todas las vinculaciones vigentes en el período, cada una con la causa por la que
     * no entraría (vacía = se puede agregar). Alimenta el selector de vinculaciones.
     */
    List<CandidatoNominaDto> candidatos(Long documentoId, Integer empresaId);

    /**
     * Agrega las vinculaciones que caen en el tipo de lista y no tienen causa para
     * omitirse. 409 si la prenómina no está preliminar; 400 si el tipo de lista exige
     * elegir algo y no vino nada, o si no se encontró ninguna vinculación vigente.
     */
    ResultadoAgregarEmpleadosDto agregar(Long documentoId, AgregarEmpleadosDto dto, Integer empresaId);

    /**
     * Agrega la vinculación retirada a su prenómina de liquidación definitiva (R5), sin
     * las reglas de candidatos (la vinculación ya está terminada).
     */
    void agregarRetirado(Long documentoId, Long contratoId, Long empleadoId, BigDecimal salarioBase,
            Integer empresaId);

    /** Quita filas del documento; 409 si la prenómina no está preliminar. */
    void quitar(Long documentoId, List<Long> nominaIds, Integer empresaId);
}
