package com.cloud_technological.aura_pos.services;

import java.time.LocalDate;

import org.springframework.web.multipart.MultipartFile;

import com.cloud_technological.aura_pos.dto.asistencia_frente.AsistenciaFrenteDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.GuardarBorradorDto;

public interface AsistenciaFrenteService {

    /** Devuelve la asistencia del frente en la fecha; si no existe, precarga los trabajadores asignados. */
    AsistenciaFrenteDto obtener(Long frenteId, LocalDate fecha, Integer empresaId);

    /** Crea/actualiza el borrador de asistencia y regenera alertas. */
    AsistenciaFrenteDto guardarBorrador(Long frenteId, GuardarBorradorDto dto, Integer empresaId, Long usuarioId);

    /** Sube el PDF soporte (firmado/escaneado) a R2 y lo vincula a la asistencia del frente/fecha. */
    AsistenciaFrenteDto subirSoporte(Long frenteId, LocalDate fecha, MultipartFile file, Integer empresaId, Long usuarioId);

    /** Envía la asistencia a revisión (valida PDF soporte, detalles y alertas críticas). */
    void enviarRevision(Long asistenciaId, Integer empresaId, Long usuarioId);
}
