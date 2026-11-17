package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.config.NominaConfigDto;
import com.cloud_technological.aura_pos.dto.nomina.config.UpdateNominaConfigDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.NominaConfigEntity;
import com.cloud_technological.aura_pos.repositories.nomina.NominaConfigJPARepository;
import com.cloud_technological.aura_pos.services.NominaConfigService;
import com.cloud_technological.aura_pos.services.ProyectoFrenteService;
import com.cloud_technological.aura_pos.services.asistencia.ModoAsistenciaObra;
import com.cloud_technological.aura_pos.services.nomina.ParametrosLegales;
import com.cloud_technological.aura_pos.utils.GlobalException;

@Service
public class NominaConfigServiceImpl implements NominaConfigService {

    @Autowired
    private NominaConfigJPARepository configRepo;

    @Autowired
    private ParametrosLegales parametrosLegales;

    @Autowired
    private ProyectoFrenteService frentes;

    @Override
    public NominaConfigDto obtener(Integer empresaId) {
        NominaConfigEntity entity = configRepo.findByEmpresaId(empresaId)
                .orElseGet(() -> crearConfigPorDefecto(empresaId));
        return toDto(entity);
    }

    @Override
    @Transactional
    public NominaConfigDto guardar(UpdateNominaConfigDto dto, Integer empresaId) {
        NominaConfigEntity entity = configRepo.findByEmpresaId(empresaId)
                .orElseGet(() -> crearConfigPorDefecto(empresaId));

        if (dto.getModoNomina() != null) entity.setModoNomina(dto.getModoNomina());
        if (dto.getPeriodicidad() != null) entity.setPeriodicidad(dto.getPeriodicidad());
        if (dto.getModoLiquidacion() != null) entity.setModoLiquidacion(dto.getModoLiquidacion());
        if (dto.getSmmlv() != null) entity.setSmmlv(dto.getSmmlv());
        if (dto.getAuxilioTransporte() != null) entity.setAuxilioTransporte(dto.getAuxilioTransporte());
        if (dto.getPctSaludEmpleado() != null) entity.setPctSaludEmpleado(dto.getPctSaludEmpleado());
        if (dto.getPctPensionEmpleado() != null) entity.setPctPensionEmpleado(dto.getPctPensionEmpleado());
        if (dto.getPctSaludEmpleador() != null) entity.setPctSaludEmpleador(dto.getPctSaludEmpleador());
        if (dto.getPctPensionEmpleador() != null) entity.setPctPensionEmpleador(dto.getPctPensionEmpleador());
        if (dto.getPctCajaCompensacion() != null) entity.setPctCajaCompensacion(dto.getPctCajaCompensacion());
        if (dto.getPctIcbf() != null) entity.setPctIcbf(dto.getPctIcbf());
        if (dto.getPctSena() != null) entity.setPctSena(dto.getPctSena());
        if (dto.getAprendizPctLectiva() != null) entity.setAprendizPctLectiva(dto.getAprendizPctLectiva());
        if (dto.getAprendizPctPractica() != null) entity.setAprendizPctPractica(dto.getAprendizPctPractica());
        if (dto.getPermiteVacacionesAnticipadas() != null)
            entity.setPermiteVacacionesAnticipadas(dto.getPermiteVacacionesAnticipadas());
        // Al dejar de usar frentes, cada proyecto pasa a trabajarse en su frente General
        // con los trabajadores y el turno que tenían sus frentes (si no, la digitación
        // pedía un frente y el General quedaba vacío).
        boolean dejaFrentes = Boolean.FALSE.equals(dto.getAsistenciaUsaFrentes());
        if (dto.getAsistenciaUsaFrentes() != null) entity.setAsistenciaUsaFrentes(dto.getAsistenciaUsaFrentes());
        if (dejaFrentes) frentes.consolidarEnGeneral(empresaId, null);
        if (dto.getAsistenciaExigeSoporte() != null) entity.setAsistenciaExigeSoporte(dto.getAsistenciaExigeSoporte());
        if (dto.getAsistenciaRequiereAprobacion() != null)
            entity.setAsistenciaRequiereAprobacion(dto.getAsistenciaRequiereAprobacion());
        if (dto.getAsistenciaRegistro() != null) {
            if (!ModoAsistenciaObra.registroValido(dto.getAsistenciaRegistro()))
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Forma de registro de horas no válida.");
            entity.setAsistenciaRegistro(dto.getAsistenciaRegistro());
        }
        entity.setUpdatedAt(LocalDateTime.now());

        return toDto(configRepo.save(entity));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private NominaConfigEntity crearConfigPorDefecto(Integer empresaId) {
        NominaConfigEntity config = new NominaConfigEntity();
        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);
        config.setEmpresa(empresa);
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        return configRepo.save(config);
    }

    private NominaConfigDto toDto(NominaConfigEntity entity) {
        NominaConfigDto dto = new NominaConfigDto();
        dto.setId(entity.getId());
        dto.setModoNomina(entity.getModoNomina());
        dto.setPeriodicidad(entity.getPeriodicidad());
        dto.setModoLiquidacion(entity.getModoLiquidacion());
        dto.setSmmlv(entity.getSmmlv());
        dto.setAuxilioTransporte(entity.getAuxilioTransporte());
        dto.setPctSaludEmpleado(entity.getPctSaludEmpleado());
        dto.setPctPensionEmpleado(entity.getPctPensionEmpleado());
        dto.setPctSaludEmpleador(entity.getPctSaludEmpleador());
        dto.setPctPensionEmpleador(entity.getPctPensionEmpleador());
        dto.setPctCajaCompensacion(entity.getPctCajaCompensacion());
        dto.setPctIcbf(entity.getPctIcbf());
        dto.setPctSena(entity.getPctSena());
        dto.setAprendizPctLectiva(entity.getAprendizPctLectiva());
        dto.setAprendizPctPractica(entity.getAprendizPctPractica());
        dto.setPermiteVacacionesAnticipadas(entity.getPermiteVacacionesAnticipadas());
        dto.setAsistenciaUsaFrentes(entity.getAsistenciaUsaFrentes());
        dto.setAsistenciaExigeSoporte(entity.getAsistenciaExigeSoporte());
        dto.setAsistenciaRegistro(entity.getAsistenciaRegistro());
        dto.setAsistenciaRequiereAprobacion(entity.getAsistenciaRequiereAprobacion());
        // V239 — lo que se ve es el valor de ley del año en curso (el que usa el motor);
        // el guardado en la configuración solo aplica a un año que no esté cargado.
        int agno = LocalDate.now().getYear();
        parametrosLegales.delAgno(agno).ifPresent(p -> {
            dto.setSmmlv(p.smmlv());
            dto.setAuxilioTransporte(p.auxilioTransporte());
            dto.setParametrosAgno(agno);
            dto.setParametrosNorma(p.norma());
        });
        return dto;
    }
}
