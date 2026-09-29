package com.cloud_technological.aura_pos.services;

import java.time.LocalDate;

import com.cloud_technological.aura_pos.dto.contabilidad.declaraciones.BorradorDeclaracionDto;

/** Borradores de apoyo para las declaraciones de IVA (300) y retención (350). */
public interface DeclaracionesService {

    BorradorDeclaracionDto iva(Integer empresaId, LocalDate desde, LocalDate hasta);

    BorradorDeclaracionDto retencion(Integer empresaId, LocalDate desde, LocalDate hasta);
}
