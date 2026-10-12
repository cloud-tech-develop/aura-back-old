package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.nomina.comprobante.CreateTipoComprobanteDto;
import com.cloud_technological.aura_pos.dto.nomina.comprobante.TipoComprobanteTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

/**
 * Tipos de comprobante de nómina (rediseño R3): con qué se crea cada prenómina y
 * nómina — qué liquida (clase), su numeración (prefijo) y qué empleados admite
 * (tipos de vinculación).
 */
public interface NominaTipoComprobanteService {

    /** Listado paginado; siembra los tipos iniciales si la empresa no tiene ninguno. */
    PageImpl<TipoComprobanteTableDto> listar(PageableDto<Object> pageable, Integer empresaId);

    /** Activos para selects (también siembra). */
    List<TipoComprobanteTableDto> activos(Integer empresaId);

    /** Un tipo con sus tipos de vinculación; 404 si no es de la empresa. */
    TipoComprobanteTableDto obtener(Long id, Integer empresaId);

    /**
     * Crea un tipo. 400 si el código o el prefijo ya existen, si un periódico no trae
     * frecuencia o si algún tipo de vinculación no es de la empresa.
     */
    TipoComprobanteTableDto crear(CreateTipoComprobanteDto dto, Integer empresaId);

    /** Edita un tipo con las mismas validaciones y reemplaza sus tipos de vinculación. */
    TipoComprobanteTableDto actualizar(Long id, CreateTipoComprobanteDto dto, Integer empresaId);

    /** Borrado lógico. */
    void eliminar(Long id, Integer empresaId);
}
