package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.config.NominaConfigDto;
import com.cloud_technological.aura_pos.dto.nomina.config.UpdateNominaConfigDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.NominaConfigEntity;
import com.cloud_technological.aura_pos.repositories.nomina.NominaConfigJPARepository;
import com.cloud_technological.aura_pos.services.NominaConfigService;

@Service
public class NominaConfigServiceImpl implements NominaConfigService {

    @Autowired
    private NominaConfigJPARepository configRepo;

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
        if (dto.getSmmlv() != null) entity.setSmmlv(dto.getSmmlv());
        if (dto.getAuxilioTransporte() != null) entity.setAuxilioTransporte(dto.getAuxilioTransporte());
        if (dto.getPctSaludEmpleado() != null) entity.setPctSaludEmpleado(dto.getPctSaludEmpleado());
        if (dto.getPctPensionEmpleado() != null) entity.setPctPensionEmpleado(dto.getPctPensionEmpleado());
        if (dto.getPctSaludEmpleador() != null) entity.setPctSaludEmpleador(dto.getPctSaludEmpleador());
        if (dto.getPctPensionEmpleador() != null) entity.setPctPensionEmpleador(dto.getPctPensionEmpleador());
        if (dto.getPctCajaCompensacion() != null) entity.setPctCajaCompensacion(dto.getPctCajaCompensacion());
        if (dto.getPctIcbf() != null) entity.setPctIcbf(dto.getPctIcbf());
        if (dto.getPctSena() != null) entity.setPctSena(dto.getPctSena());
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
        dto.setSmmlv(entity.getSmmlv());
        dto.setAuxilioTransporte(entity.getAuxilioTransporte());
        dto.setPctSaludEmpleado(entity.getPctSaludEmpleado());
        dto.setPctPensionEmpleado(entity.getPctPensionEmpleado());
        dto.setPctSaludEmpleador(entity.getPctSaludEmpleador());
        dto.setPctPensionEmpleador(entity.getPctPensionEmpleador());
        dto.setPctCajaCompensacion(entity.getPctCajaCompensacion());
        dto.setPctIcbf(entity.getPctIcbf());
        dto.setPctSena(entity.getPctSena());
        return dto;
    }
}
