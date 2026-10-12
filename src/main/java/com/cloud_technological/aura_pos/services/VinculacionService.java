package com.cloud_technological.aura_pos.services;

import org.springframework.data.domain.PageImpl;
import org.springframework.web.multipart.MultipartFile;

import com.cloud_technological.aura_pos.dto.nomina.vinculacion.CreateVinculacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.FiltroVinculacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionTableDto;
import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.utils.PageableDto;

/**
 * Vinculaciones (rediseño R2): el contrato completo del empleado con la empresa,
 * con tipo de vinculación, cargo y grupo del catálogo, jornada, período de prueba,
 * ubicación, jefe directo y soportes. Se guarda en {@code contrato_laboral}, así el
 * motor de liquidación, PILA y la nómina electrónica la siguen leyendo igual.
 *
 * <p>El salario y la terminación no se cambian aquí: usan
 * {@code /api/contrato/{id}/salario} (conserva el histórico) y
 * {@code /api/contrato/{id}/terminar}.
 */
public interface VinculacionService {

    /** Listado paginado con búsqueda y filtros (estado, tipo, cargo, grupo). */
    PageImpl<VinculacionTableDto> listar(PageableDto<FiltroVinculacionDto> pageable, Integer empresaId);

    /** Detalle; 404 si no es de la empresa. */
    VinculacionDto obtener(Long id, Integer empresaId);

    /**
     * Crea la vinculación con su consecutivo y abre el histórico salarial.
     * 400/409 si incumple una regla: fecha fin exigida por el tipo, tipo único por
     * empleado, plazas del cargo, salario fuera del rango del cargo, período de prueba,
     * jornada legal o catálogos de otra empresa.
     */
    VinculacionDto crear(CreateVinculacionDto dto, Integer empresaId);

    /** Edita la vinculación (no el empleado ni el salario); 409 si está terminada. */
    VinculacionDto actualizar(Long id, CreateVinculacionDto dto, Integer empresaId);

    /**
     * Sube el soporte de la vinculación.
     *
     * @param tipo {@code interno} (acto administrativo / documento interno) o
     *             {@code externo} (contrato firmado)
     */
    VinculacionDto subirSoporte(Long id, String tipo, MultipartFile archivo, Integer empresaId);

    /**
     * Completa un contrato creado fuera del formulario de vinculación (alta vieja de
     * empleado): consecutivo VIN-, tipo de vinculación deducido del tipo de contrato
     * (como en V210) y cargo del catálogo buscado por nombre o creado si no existe.
     * No guarda el contrato; quien llama lo crea después.
     */
    void completarContratoHeredado(ContratoLaboralEntity contrato, Integer empresaId);
}
