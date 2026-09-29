package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.asistencia_frente.AsistenciaFrenteDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.AsistenciaFrenteTableDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.PreliquidacionFrenteItemDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.RevisarDetallesDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.RevisionAccionDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.RevisionFilterDto;

public interface AsistenciaRevisionService {

    PageImpl<AsistenciaFrenteTableDto> listar(RevisionFilterDto filtro, Integer empresaId);

    List<PreliquidacionFrenteItemDto> preliquidacion(Long periodoId, Long proyectoId, Long frenteId, Integer empresaId);

    AsistenciaFrenteDto obtenerPorId(Long asistenciaId, Integer empresaId);

    /** Guarda la decisión de revisión por trabajador (aprobar unas horas y otras no). */
    void revisarDetalles(Long asistenciaId, RevisarDetallesDto dto, Integer empresaId, Long usuarioId);

    void aprobar(Long asistenciaId, RevisionAccionDto dto, Integer empresaId, Long usuarioId);

    void rechazar(Long asistenciaId, RevisionAccionDto dto, Integer empresaId, Long usuarioId);

    void solicitarCorreccion(Long asistenciaId, RevisionAccionDto dto, Integer empresaId, Long usuarioId);

    /** Genera las novedades de nómina (origen PROYECTO_FRENTE) desde una asistencia APROBADA
     *  y la pasa a ENVIADO_NOMINA. Devuelve la cantidad de novedades generadas. */
    int enviarNomina(Long asistenciaId, Integer empresaId, Long usuarioId);
}
