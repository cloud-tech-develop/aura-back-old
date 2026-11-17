package com.cloud_technological.aura_pos.services;

import java.util.List;
import java.time.LocalDate;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.asistencia_frente.AsistenciaFrenteDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.AsistenciaFrenteTableDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.MarcajeExcelDtos.ResultadoRangoDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.PreliquidacionFrenteItemDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.RevisarDetallesDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.RevisionAccionDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.RevisionFilterDto;

public interface AsistenciaRevisionService {

    /**
     * Empresa sin revisión de asistencia (V245): los días registrados del empleado
     * en el rango (borrador, en corrección o en revisión) se aprueban y se envían a
     * nómina solos, para que la prenómina tome sus horas al liquidar. Corre en su
     * propia transacción; si un día tiene alertas críticas, lanza la causa.
     *
     * @return cuántos días se consolidaron
     */
    int consolidarParaNomina(Integer empresaId, Long empleadoId, LocalDate desde, LocalDate hasta, Long usuarioId);


    /**
     * Aprueba todos los días enviados a revisión del frente en el rango y, si se
     * pide, los envía a nómina (genera las horas extra y recargos). Los que no se
     * pueden aprobar quedan con su causa.
     */
    ResultadoRangoDto aprobarRango(
            Long frenteId, LocalDate desde, LocalDate hasta, boolean enviarNomina,
            Integer empresaId, Long usuarioId);


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
