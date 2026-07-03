package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.asistencia.AutorizacionDto;
import com.cloud_technological.aura_pos.dto.asistencia.CrearAutorizacionDto;
import com.cloud_technological.aura_pos.entity.AutorizacionLiquidacionEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.PeriodoNominaEntity;
import com.cloud_technological.aura_pos.repositories.asistencia.AutorizacionLiquidacionJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.PeriodoNominaJPARepository;
import com.cloud_technological.aura_pos.services.AutorizacionLiquidacionService;
import com.cloud_technological.aura_pos.utils.GlobalException;

@Service
public class AutorizacionLiquidacionServiceImpl implements AutorizacionLiquidacionService {

    @Autowired
    private AutorizacionLiquidacionJPARepository autorizacionRepo;

    @Autowired
    private EmpleadoJPARepository empleadoRepo;

    @Autowired
    private PeriodoNominaJPARepository periodoRepo;

    @Override
    @Transactional
    public AutorizacionDto crear(CrearAutorizacionDto dto, Integer empresaId, Long usuarioId) {
        if (dto.getEmpleadoId() == null || dto.getPeriodoNominaId() == null || dto.getMotivo() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Empleado, período y motivo son obligatorios");

        EmpleadoEntity empleado = empleadoRepo.findByIdAndEmpresaId(dto.getEmpleadoId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));
        PeriodoNominaEntity periodo = periodoRepo.findByIdAndEmpresaId(dto.getPeriodoNominaId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Período no encontrado"));

        AutorizacionLiquidacionEntity a = new AutorizacionLiquidacionEntity();
        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);
        a.setEmpresa(empresa);
        a.setEmpleado(empleado);
        a.setPeriodoNomina(periodo);
        a.setMotivo(dto.getMotivo());
        a.setObservacion(dto.getObservacion());
        a.setUsuarioAutoriza(usuarioId != null ? usuarioId.intValue() : null);
        a.setFechaAutorizacion(LocalDateTime.now());
        a.setEstado("ACTIVA");
        return toDto(autorizacionRepo.save(a));
    }

    @Override
    public List<AutorizacionDto> listarPorPeriodo(Long periodoNominaId, Integer empresaId) {
        return autorizacionRepo.findByEmpresaIdAndPeriodoNominaId(empresaId, periodoNominaId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void anular(Long id, Integer empresaId) {
        AutorizacionLiquidacionEntity a = autorizacionRepo.findById(id)
                .filter(x -> x.getEmpresa().getId().equals(empresaId))
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Autorización no encontrada"));
        a.setEstado("ANULADA");
        autorizacionRepo.save(a);
    }

    private AutorizacionDto toDto(AutorizacionLiquidacionEntity a) {
        AutorizacionDto dto = new AutorizacionDto();
        dto.setId(a.getId());
        dto.setEmpleadoId(a.getEmpleado().getId());
        dto.setEmpleadoNombre(a.getEmpleado().getNombres() + " " + a.getEmpleado().getApellidos());
        dto.setPeriodoNominaId(a.getPeriodoNomina().getId());
        dto.setMotivo(a.getMotivo());
        dto.setObservacion(a.getObservacion());
        dto.setEstado(a.getEstado());
        dto.setFechaAutorizacion(a.getFechaAutorizacion());
        return dto;
    }
}
