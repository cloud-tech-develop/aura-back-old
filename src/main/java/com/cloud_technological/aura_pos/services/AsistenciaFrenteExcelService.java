package com.cloud_technological.aura_pos.services;

import org.springframework.web.multipart.MultipartFile;

import com.cloud_technological.aura_pos.dto.asistencia_frente.MarcajeExcelDtos.ImportarMarcajeDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.MarcajeExcelDtos.ResultadoMarcajeDto;

/**
 * Marcaje del mes desde la plantilla de Excel (V242): valida las filas y, al
 * confirmar, guarda cada día como borrador del frente con el Excel como soporte.
 */
public interface AsistenciaFrenteExcelService {

    /** Valida y clasifica las horas de cada fila sin guardar nada. */
    ResultadoMarcajeDto validar(Long frenteId, ImportarMarcajeDto dto, Integer empresaId);

    /**
     * Valida y, si no hay errores, guarda todo (todo o nada). Los trabajadores que
     * ya estaban digitados en un día y no vienen en el archivo se conservan.
     *
     * @param archivo el Excel subido; queda como soporte de los días importados (puede faltar)
     */
    ResultadoMarcajeDto confirmar(Long frenteId, ImportarMarcajeDto dto, MultipartFile archivo,
            Integer empresaId, Long usuarioId);
}
