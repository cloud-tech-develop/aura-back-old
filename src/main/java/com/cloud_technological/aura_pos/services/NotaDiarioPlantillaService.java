package com.cloud_technological.aura_pos.services;

import java.time.LocalDate;
import java.util.List;

import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioPlantillaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.SaveNotaDiarioPlantillaDto;

/**
 * Plantillas de notas contables: la nota que se repite se guarda una vez y se
 * genera cuando se necesita. Las recurrentes dejan un BORRADOR cada mes.
 */
public interface NotaDiarioPlantillaService {

    List<NotaDiarioPlantillaDto> listar(Integer empresaId);

    NotaDiarioPlantillaDto obtener(Long id, Integer empresaId);

    NotaDiarioPlantillaDto crear(Integer empresaId, Integer usuarioId, SaveNotaDiarioPlantillaDto dto);

    NotaDiarioPlantillaDto actualizar(Long id, Integer empresaId, SaveNotaDiarioPlantillaDto dto);

    void eliminar(Long id, Integer empresaId);

    /** Crea una nota en BORRADOR con las líneas de la plantilla. */
    NotaDiarioDto generar(Long id, Integer empresaId, Integer usuarioId, LocalDate fecha);

    /**
     * Pasada del programador para una plantilla: si ya llegó su día del mes y
     * ese mes no se ha generado, deja el borrador. Devuelve true si generó.
     */
    boolean generarProgramada(Long id, LocalDate hoy);

    /** Ids de las plantillas recurrentes activas de todas las empresas. */
    List<Long> recurrentesActivas();
}
