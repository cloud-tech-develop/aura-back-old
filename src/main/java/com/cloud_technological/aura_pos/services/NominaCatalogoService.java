package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.nomina.catalogos.CreateNominaCatalogoDto;
import com.cloud_technological.aura_pos.dto.nomina.catalogos.NominaCatalogoTableDto;
import com.cloud_technological.aura_pos.dto.nomina.catalogos.TipoCatalogoNominaDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

/**
 * Catálogos simples de nómina (grupos, niveles y tipos de cargo, tipos de embargo,
 * motivos de retiro y de ausentismo, riesgos ARL). El catálogo se identifica por el
 * segmento de la URL ({@code ruta}); uno que no existe responde 404.
 */
public interface NominaCatalogoService {

    /** Definición de todos los tipos de catálogo (título y campos propios) para el front. */
    List<TipoCatalogoNominaDto> tipos();

    /** Definición de un tipo de catálogo. */
    TipoCatalogoNominaDto tipo(String ruta);

    /**
     * Listado paginado. Si la empresa aún no tiene el catálogo, primero le siembra los
     * valores iniciales.
     */
    PageImpl<NominaCatalogoTableDto> listar(String ruta, PageableDto<Object> pageable, Integer empresaId);

    /** Elementos activos para selects (también siembra si está vacío). */
    List<NominaCatalogoTableDto> activos(String ruta, Integer empresaId);

    /** Un elemento; 404 si no es de la empresa o de otro catálogo. */
    NominaCatalogoTableDto obtener(String ruta, Long id, Integer empresaId);

    /** Crea un elemento; 400 si el código ya existe en el catálogo o los atributos no son válidos. */
    NominaCatalogoTableDto crear(String ruta, CreateNominaCatalogoDto dto, Integer empresaId);

    /** Edita un elemento con las mismas validaciones que al crear. */
    NominaCatalogoTableDto actualizar(String ruta, Long id, CreateNominaCatalogoDto dto, Integer empresaId);

    /** Borrado lógico; 409 si un cargo lo usa (desactívelo en su lugar). */
    void eliminar(String ruta, Long id, Integer empresaId);
}
