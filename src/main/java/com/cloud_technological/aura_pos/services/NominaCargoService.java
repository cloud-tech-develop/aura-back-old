package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.nomina.cargos.CreateNominaCargoDto;
import com.cloud_technological.aura_pos.dto.nomina.cargos.NominaCargoTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

/**
 * Cargos de la empresa (módulo aparte de la vinculación): código, nombre, nivel y
 * tipo de cargo (de los catálogos de nómina), plazas y rango salarial.
 */
public interface NominaCargoService {

    /** Listado paginado con búsqueda. */
    PageImpl<NominaCargoTableDto> listar(PageableDto<Object> pageable, Integer empresaId);

    /** Cargos activos para selects. */
    List<NominaCargoTableDto> activos(Integer empresaId);

    /** Un cargo; 404 si no es de la empresa. */
    NominaCargoTableDto obtener(Long id, Integer empresaId);

    /**
     * Crea un cargo. 400 si el código ya existe, el nivel o el tipo no son del catálogo
     * que corresponde, o el rango salarial no cuadra (mínimo ≤ referencia ≤ máximo).
     */
    NominaCargoTableDto crear(CreateNominaCargoDto dto, Integer empresaId);

    /** Edita un cargo con las mismas validaciones que al crear. */
    NominaCargoTableDto actualizar(Long id, CreateNominaCargoDto dto, Integer empresaId);

    /** Borrado lógico. */
    void eliminar(Long id, Integer empresaId);
}
