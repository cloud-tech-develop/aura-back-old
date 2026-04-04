package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.empleado.CreateEmpleadoDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoTableDto;
import com.cloud_technological.aura_pos.entity.EmpleadoArlEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoQueryRepository;
import com.cloud_technological.aura_pos.services.EmpleadoService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Service
public class EmpleadoServiceImpl implements EmpleadoService {

    @Autowired
    private EmpleadoJPARepository empleadoRepo;

    @Autowired
    private EmpleadoQueryRepository empleadoQueryRepo;

    @Override
    public PageImpl<EmpleadoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return empleadoQueryRepo.listar(pageable, empresaId);
    }

    @Override
    public List<EmpleadoDto> listarVendedores(Integer empresaId) {
        List<EmpleadoEntity> vendedores = empleadoRepo.findByEmpresaIdAndActivoTrueAndCargoIgnoreCase(empresaId, "VENDEDOR");
        return vendedores.stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public EmpleadoDto obtenerPorId(Long id, Integer empresaId) {
        EmpleadoEntity entity = empleadoRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));
        return toDto(entity);
    }

    @Override
    @Transactional
    public EmpleadoDto crear(CreateEmpleadoDto dto, Integer empresaId) {
        EmpleadoEntity entity = new EmpleadoEntity();
        mapFromDto(dto, entity);

        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);
        entity.setEmpresa(empresa);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        entity = empleadoRepo.save(entity);

        EmpleadoArlEntity arl = new EmpleadoArlEntity();
        arl.setEmpleado(entity);
        arl.setEmpresa(empresa);
        arl.setNivelRiesgo(dto.getNivelRiesgoArl() != null ? dto.getNivelRiesgoArl() : 1);
        arl.setPorcentaje(resolverPorcentajeArl(arl.getNivelRiesgo()));
        entity.setArl(arl);

        return toDto(empleadoRepo.save(entity));
    }

    @Override
    @Transactional
    public EmpleadoDto actualizar(Long id, CreateEmpleadoDto dto, Integer empresaId) {
        EmpleadoEntity entity = empleadoRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));
        mapFromDto(dto, entity);
        entity.setUpdatedAt(LocalDateTime.now());

        if (dto.getNivelRiesgoArl() != null) {
            EmpleadoArlEntity arl = entity.getArl();
            if (arl == null) {
                arl = new EmpleadoArlEntity();
                arl.setEmpleado(entity);
                EmpresaEntity empresa = new EmpresaEntity();
                empresa.setId(empresaId);
                arl.setEmpresa(empresa);
                entity.setArl(arl);
            }
            arl.setNivelRiesgo(dto.getNivelRiesgoArl());
            arl.setPorcentaje(resolverPorcentajeArl(dto.getNivelRiesgoArl()));
        }

        return toDto(empleadoRepo.save(entity));
    }

    @Override
    @Transactional
    public void retirar(Long id, Integer empresaId) {
        EmpleadoEntity entity = empleadoRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));
        entity.setActivo(false);
        entity.setFechaRetiro(LocalDate.now());
        entity.setUpdatedAt(LocalDateTime.now());
        empleadoRepo.save(entity);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void mapFromDto(CreateEmpleadoDto dto, EmpleadoEntity entity) {
        entity.setNombres(dto.getNombres());
        entity.setApellidos(dto.getApellidos());
        entity.setTipoDocumento(dto.getTipoDocumento());
        entity.setNumeroDocumento(dto.getNumeroDocumento());
        entity.setCargo(dto.getCargo());
        entity.setFechaIngreso(dto.getFechaIngreso());
        entity.setSalarioBase(dto.getSalarioBase());
        entity.setTipoContrato(dto.getTipoContrato());
        entity.setBanco(dto.getBanco());
        entity.setNumeroCuenta(dto.getNumeroCuenta());
        entity.setTipoCuenta(dto.getTipoCuenta());
    }

    private EmpleadoDto toDto(EmpleadoEntity entity) {
        EmpleadoDto dto = new EmpleadoDto();
        dto.setId(entity.getId());
        dto.setNombres(entity.getNombres());
        dto.setApellidos(entity.getApellidos());
        dto.setTipoDocumento(entity.getTipoDocumento());
        dto.setNumeroDocumento(entity.getNumeroDocumento());
        dto.setCargo(entity.getCargo());
        dto.setFechaIngreso(entity.getFechaIngreso());
        dto.setFechaRetiro(entity.getFechaRetiro());
        dto.setSalarioBase(entity.getSalarioBase());
        dto.setTipoContrato(entity.getTipoContrato());
        dto.setBanco(entity.getBanco());
        dto.setNumeroCuenta(entity.getNumeroCuenta());
        dto.setTipoCuenta(entity.getTipoCuenta());
        dto.setActivo(entity.getActivo());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        if (entity.getArl() != null) {
            dto.setNivelRiesgoArl(entity.getArl().getNivelRiesgo());
            dto.setPorcentajeArl(entity.getArl().getPorcentaje());
        }
        return dto;
    }

    private BigDecimal resolverPorcentajeArl(int nivel) {
        return switch (nivel) {
            case 1 -> new BigDecimal("0.522");
            case 2 -> new BigDecimal("1.044");
            case 3 -> new BigDecimal("2.436");
            case 4 -> new BigDecimal("4.350");
            case 5 -> new BigDecimal("6.960");
            default -> new BigDecimal("0.522");
        };
    }
}
