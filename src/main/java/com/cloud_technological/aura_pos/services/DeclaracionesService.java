package com.cloud_technological.aura_pos.services;

import java.time.LocalDate;

import com.cloud_technological.aura_pos.dto.contabilidad.declaraciones.BorradorDeclaracionDto;
import com.cloud_technological.aura_pos.dto.contabilidad.declaraciones.ParametrosIcaDto;

/** Borradores de apoyo para las declaraciones de IVA (300) y retención (350). */
public interface DeclaracionesService {

    BorradorDeclaracionDto iva(Integer empresaId, LocalDate desde, LocalDate hasta);

    BorradorDeclaracionDto retencion(Integer empresaId, LocalDate desde, LocalDate hasta);

    /** Industria y comercio del municipio: la tarifa y las deducciones las pone el contador. */
    BorradorDeclaracionDto ica(Integer empresaId, ParametrosIcaDto p);

    /** Causa el ICA del borrador: nota contable DB 511505 · CR 2412 a nombre del municipio. */
    String causarIca(Integer empresaId, Integer usuarioId, ParametrosIcaDto p);
}
