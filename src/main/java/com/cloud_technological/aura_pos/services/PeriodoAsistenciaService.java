package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.asistencia.CrearPeriodoAsistenciaDto;
import com.cloud_technological.aura_pos.dto.asistencia.PeriodoAsistenciaDto;

public interface PeriodoAsistenciaService {
    List<PeriodoAsistenciaDto> listar(Integer empresaId);
    PeriodoAsistenciaDto crear(CrearPeriodoAsistenciaDto dto, Integer empresaId, Long usuarioId);
    PeriodoAsistenciaDto cerrar(Long id, Integer empresaId, Long usuarioId);       // ABIERTO -> EN_REVISION
    PeriodoAsistenciaDto aprobar(Long id, Integer empresaId, Long usuarioId);      // EN_REVISION -> APROBADO
    PeriodoAsistenciaDto enviarANomina(Long id, Integer empresaId);               // APROBADO -> ENVIADO_A_NOMINA
}
