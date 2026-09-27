package com.cloud_technological.aura_pos.services;

import java.time.LocalDate;

import com.cloud_technological.aura_pos.dto.contabilidad.libros.LibroAuxiliarDto;
import com.cloud_technological.aura_pos.dto.contabilidad.libros.LibroDiarioDto;

/** Libros oficiales: auxiliar por tercero y libro diario. */
public interface LibrosContablesService {

    /**
     * @param cuentaDesde prefijo PUC inicial (opcional, ej. "13")
     * @param cuentaHasta prefijo PUC final (opcional, ej. "13")
     * @param terceroId   solo ese tercero (opcional)
     */
    LibroAuxiliarDto auxiliarPorTercero(Integer empresaId, LocalDate desde, LocalDate hasta,
            String cuentaDesde, String cuentaHasta, Long terceroId);

    LibroDiarioDto diario(Integer empresaId, LocalDate desde, LocalDate hasta);
}
