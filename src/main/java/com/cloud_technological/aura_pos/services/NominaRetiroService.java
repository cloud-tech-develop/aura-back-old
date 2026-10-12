package com.cloud_technological.aura_pos.services;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.nomina.documento.DocumentoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.retiro.FiltroRetiroDto;
import com.cloud_technological.aura_pos.dto.nomina.retiro.RegistrarRetiroDto;
import com.cloud_technological.aura_pos.dto.nomina.retiro.RetiroDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

/**
 * Retiros (rediseño de nómina R5): registrar el retiro de una vinculación, que la
 * termina en esa fecha (PILA reporta RET), y anularlo mientras su liquidación
 * definitiva no esté hecha.
 */
public interface NominaRetiroService {

    /** Listado paginado con el estado de su liquidación. */
    PageImpl<RetiroDto> listar(PageableDto<FiltroRetiroDto> pageable, Integer empresaId);

    /** Un retiro; 404 si no es de la empresa. */
    RetiroDto obtener(Long id, Integer empresaId);

    /**
     * Registra el retiro: termina la vinculación en la fecha de retiro con el motivo
     * como causa y, si el empleado no tiene otra vinculación activa, lo deja inactivo.
     * 400/409 si la vinculación no está activa, ya tiene un retiro, la fecha es anterior
     * al ingreso o el motivo no es del catálogo de motivos de retiro.
     */
    RetiroDto registrar(RegistrarRetiroDto dto, Integer empresaId, Long usuarioId);

    /**
     * Anula el retiro y reactiva la vinculación (y al empleado). 409 si ya tiene una
     * prenómina de liquidación sin anular.
     */
    RetiroDto anular(Long id, Integer empresaId);

    /**
     * Genera la prenómina de liquidación definitiva del retiro (tipo de comprobante de
     * clase LIQUIDACION_DEFINITIVA) con el empleado retirado, desde el día siguiente al
     * último salario pagado hasta la fecha de retiro. 409 si ya tiene una liquidación
     * sin anular; 400 si la empresa no tiene un tipo de comprobante de esa clase.
     *
     * @return la prenómina creada
     */
    DocumentoNominaDto generarLiquidacion(Long id, Integer empresaId, Long usuarioId);
}
