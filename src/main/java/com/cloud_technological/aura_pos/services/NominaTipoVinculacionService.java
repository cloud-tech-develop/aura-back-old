package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.nomina.vinculacion.CreateTipoVinculacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.TipoVinculacionTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

/**
 * Tipos de vinculación: agrupan a los empleados (y sirven de "tipo de lista" en la
 * nómina) y proponen contrato, remuneración y frecuencia al vincular.
 */
public interface NominaTipoVinculacionService {

    /** Listado paginado; siembra los tipos iniciales si la empresa no tiene ninguno. */
    PageImpl<TipoVinculacionTableDto> listar(PageableDto<Object> pageable, Integer empresaId);

    /** Activos para selects (también siembra). */
    List<TipoVinculacionTableDto> activos(Integer empresaId);

    /** Un tipo; 404 si no es de la empresa. */
    TipoVinculacionTableDto obtener(Long id, Integer empresaId);

    /**
     * Crea un tipo. 400 si el código existe o si el contrato exige fecha de fin y el
     * tipo dice lo contrario (fijo, obra o aprendizaje).
     */
    TipoVinculacionTableDto crear(CreateTipoVinculacionDto dto, Integer empresaId);

    /** Edita un tipo con las mismas validaciones. */
    TipoVinculacionTableDto actualizar(Long id, CreateTipoVinculacionDto dto, Integer empresaId);

    /** Borrado lógico. */
    void eliminar(Long id, Integer empresaId);
}
