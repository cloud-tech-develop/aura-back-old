package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionDetalleDtos.CentroCostoVinculacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionDetalleDtos.CreateNovedadPermitidaDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionDetalleDtos.CreateVinculacionConceptoDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionDetalleDtos.NovedadPermitidaDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionDetalleDtos.VinculacionConceptoDto;

/**
 * Pestañas de la vinculación que antes vivían en la ficha del empleado o no
 * existían: distribución por centro de costo, conceptos propios (fijos con
 * vigencia) y novedades permitidas con tope. En una vinculación retirada solo se
 * consultan (409 al modificar).
 */
public interface VinculacionDetalleService {

    /** Distribución del costo laboral por centro de costo. */
    List<CentroCostoVinculacionDto> centrosCosto(Long vinculacionId, Integer empresaId);

    /**
     * Reemplaza la distribución. Vacía = sin distribución; si trae centros, deben ser
     * de la empresa, sin repetir y sumar exactamente 100 %.
     */
    List<CentroCostoVinculacionDto> guardarCentrosCosto(Long vinculacionId, List<CentroCostoVinculacionDto> centros,
            Integer empresaId);

    List<VinculacionConceptoDto> conceptos(Long vinculacionId, Integer empresaId);

    /**
     * Agrega un concepto propio. 400 si el concepto no existe, no trae valor,
     * porcentaje ni cantidad, empieza antes que la vinculación o se cruza con otra
     * vigencia del mismo concepto.
     */
    List<VinculacionConceptoDto> crearConcepto(Long vinculacionId, CreateVinculacionConceptoDto dto, Integer empresaId);

    List<VinculacionConceptoDto> actualizarConcepto(Long vinculacionId, Long id, CreateVinculacionConceptoDto dto,
            Integer empresaId);

    /** Borrado lógico del concepto propio. */
    List<VinculacionConceptoDto> eliminarConcepto(Long vinculacionId, Long id, Integer empresaId);

    List<NovedadPermitidaDto> novedadesPermitidas(Long vinculacionId, Integer empresaId);

    /** Permite un concepto como novedad; 400 si no existe o ya está permitido. */
    List<NovedadPermitidaDto> crearNovedad(Long vinculacionId, CreateNovedadPermitidaDto dto, Integer empresaId);

    List<NovedadPermitidaDto> actualizarNovedad(Long vinculacionId, Long id, CreateNovedadPermitidaDto dto,
            Integer empresaId);

    List<NovedadPermitidaDto> eliminarNovedad(Long vinculacionId, Long id, Integer empresaId);
}
