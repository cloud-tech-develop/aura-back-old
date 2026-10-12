package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.nomina.documento.DocumentoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.incremento.FiltroIncrementoDto;
import com.cloud_technological.aura_pos.dto.nomina.incremento.IncrementoDetalleDto;
import com.cloud_technological.aura_pos.dto.nomina.incremento.IncrementoDto;
import com.cloud_technological.aura_pos.dto.nomina.incremento.IncrementoRequestDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

/**
 * Incremento salarial (rediseño de nómina R8, §8): pendiente con vista previa →
 * aplicado (historial salarial con la fecha de aplicación) → reversado. Si hay nóminas
 * confirmadas desde esa fecha, genera la prenómina del retroactivo.
 */
public interface NominaIncrementoService {

    /** Listado paginado. */
    PageImpl<IncrementoDto> listar(PageableDto<FiltroIncrementoDto> pageable, Integer empresaId);

    /** Un incremento; 404 si no es de la empresa. */
    IncrementoDto obtener(Long id, Integer empresaId);

    /** Vista previa: salario actual → nuevo por vinculación. */
    List<IncrementoDetalleDto> detalle(Long id, Integer empresaId);

    /**
     * Crea el incremento pendiente y calcula la vista previa. Las vinculaciones a las
     * que no les cambia el salario no entran (se cuentan en {@code sinCambio}).
     * 400 si no queda ninguna o faltan datos.
     */
    IncrementoDto crear(IncrementoRequestDto dto, Integer empresaId, Long usuarioId);

    /** Cambia los datos de un incremento pendiente y recalcula la vista previa. */
    IncrementoDto actualizar(Long id, IncrementoRequestDto dto, Integer empresaId);

    /** Quita una vinculación de la vista previa (solo pendiente). */
    IncrementoDto quitar(Long id, Long detalleId, Integer empresaId);

    /**
     * Aplica el incremento: un cambio de salario con la fecha de aplicación por
     * vinculación (historial salarial). 409 si el salario de alguna cambió desde la
     * vista previa (hay que recalcular) o ya tiene un cambio igual o posterior.
     */
    IncrementoDto aplicar(Long id, Integer empresaId, Long usuarioId);

    /**
     * Reversa un incremento aplicado: borra los cambios de salario y vuelve al anterior.
     * 409 si su retroactivo no está anulado, si hubo otro cambio de salario después o
     * si ya se confirmaron nóminas con el salario nuevo.
     */
    IncrementoDto reversar(Long id, Integer empresaId);

    /** Anula un incremento pendiente (nunca aplicado). */
    IncrementoDto anular(Long id, Integer empresaId);

    /**
     * Genera la prenómina del retroactivo (comprobante de clase RETROACTIVO) con las
     * vinculaciones que tienen nóminas confirmadas con el salario anterior, desde la
     * fecha de aplicación hasta el fin del último período afectado.
     *
     * @return la prenómina creada
     */
    DocumentoNominaDto generarRetroactivo(Long id, Integer empresaId, Long usuarioId);
}
