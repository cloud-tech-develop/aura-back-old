package com.cloud_technological.aura_pos.services;

import java.time.LocalDate;
import java.time.YearMonth;

import org.springframework.web.multipart.MultipartFile;

import com.cloud_technological.aura_pos.dto.asistencia_frente.AsistenciaFrenteDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.CalendarioAsistenciaDtos.CalendarioMesDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.GuardarBorradorDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.MarcajeExcelDtos.ResultadoRangoDto;

public interface AsistenciaFrenteService {

    /** Devuelve la asistencia del frente en la fecha; si no existe, precarga los trabajadores asignados. */
    AsistenciaFrenteDto obtener(Long frenteId, LocalDate fecha, Integer empresaId);

    /** Crea/actualiza el borrador de asistencia y regenera alertas. */
    AsistenciaFrenteDto guardarBorrador(Long frenteId, GuardarBorradorDto dto, Integer empresaId, Long usuarioId);

    /**
     * Igual que {@link #guardarBorrador} pero para la importación de Excel: no
     * exige que la empresa permita la digitación manual (V243).
     */
    AsistenciaFrenteDto guardarBorradorImportado(Long frenteId, GuardarBorradorDto dto, Integer empresaId, Long usuarioId);

    /** Sube el PDF soporte (firmado/escaneado) a R2 y lo vincula a la asistencia del frente/fecha. */
    AsistenciaFrenteDto subirSoporte(Long frenteId, LocalDate fecha, MultipartFile file, Integer empresaId, Long usuarioId);

    /** Envía la asistencia a revisión (valida PDF soporte, detalles y alertas críticas). */
    void enviarRevision(Long asistenciaId, Integer empresaId, Long usuarioId);

    /**
     * Envía a revisión todos los días en borrador o en corrección del frente en el
     * rango. Los que no cumplen (sin soporte, alertas críticas) quedan con su causa.
     */
    ResultadoRangoDto enviarRevisionRango(Long frenteId, LocalDate desde, LocalDate hasta,
            Integer empresaId, Long usuarioId);

    /**
     * Asistencia del frente en el mes por trabajador y día (calendario). Incluye a
     * los trabajadores asignados que aún no tienen nada registrado.
     */
    CalendarioMesDto calendario(Long frenteId, YearMonth mes, Integer empresaId);
}
