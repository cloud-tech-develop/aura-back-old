package com.cloud_technological.aura_pos.services;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.tipo_empleado.CreateTipoEmpleadoDto;
import com.cloud_technological.aura_pos.dto.tipo_empleado.TipoEmpleadoDto;
import com.cloud_technological.aura_pos.dto.tipo_empleado.UpdateTipoEmpleadoDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.TipoEmpleadoEntity;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoJPARepository;
import com.cloud_technological.aura_pos.repositories.tipo_empleado.TipoEmpleadoJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@Service
public class TipoEmpleadoService {

    @Autowired
    private TipoEmpleadoJPARepository tipoEmpleadoRepository;

    @Autowired
    private EmpleadoJPARepository empleadoRepository;

    @Autowired
    private SecurityUtils securityUtils;

    public List<TipoEmpleadoDto> findAll() {
        Integer empresaId = securityUtils.getEmpresaId();
        return tipoEmpleadoRepository.findByEmpresaIdAndActivoTrue(empresaId.longValue())
                .stream()
                .map(this::toDto)
                .toList();
    }

    public TipoEmpleadoDto findById(Long id) {
        TipoEmpleadoEntity entity = tipoEmpleadoRepository.findById(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Tipo de empleado no encontrado"));

        if (!entity.getEmpresa().getId().equals(securityUtils.getEmpresaId().longValue())) {
            throw new GlobalException(HttpStatus.NOT_FOUND, "Tipo de empleado no encontrado");
        }

        return toDto(entity);
    }

    @Transactional
    public TipoEmpleadoDto create(CreateTipoEmpleadoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();

        if (tipoEmpleadoRepository.existsByEmpresaIdAndNombreAndActivoTrue(empresaId.longValue(), dto.getNombre())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe un tipo de empleado con este nombre");
        }

        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);

        TipoEmpleadoEntity entity = new TipoEmpleadoEntity();
        entity.setEmpresa(empresa);
        entity.setNombre(dto.getNombre());
        entity.setDescripcion(dto.getDescripcion());
        entity.setActivo(true);
        entity.setCreatedAt(LocalDateTime.now());

        return toDto(tipoEmpleadoRepository.save(entity));
    }

    @Transactional
    public TipoEmpleadoDto update(Long id, UpdateTipoEmpleadoDto dto) {
        TipoEmpleadoEntity entity = tipoEmpleadoRepository.findById(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Tipo de empleado no encontrado"));

        if (!entity.getEmpresa().getId().equals(securityUtils.getEmpresaId().longValue())) {
            throw new GlobalException(HttpStatus.NOT_FOUND, "Tipo de empleado no encontrado");
        }

        if (dto.getNombre() != null && !dto.getNombre().equals(entity.getNombre())) {
            if (tipoEmpleadoRepository.existsByEmpresaIdAndNombreAndActivoTrueAndIdNot(
                    entity.getEmpresa().getId().longValue(), dto.getNombre(), id)) {
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe un tipo de empleado con este nombre");
            }
            entity.setNombre(dto.getNombre());
        }

        if (dto.getDescripcion() != null) {
            entity.setDescripcion(dto.getDescripcion());
        }

        if (dto.getActivo() != null) {
            entity.setActivo(dto.getActivo());
        }

        entity.setUpdatedAt(LocalDateTime.now());

        return toDto(tipoEmpleadoRepository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        TipoEmpleadoEntity entity = tipoEmpleadoRepository.findById(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Tipo de empleado no encontrado"));

        if (!entity.getEmpresa().getId().equals(securityUtils.getEmpresaId().longValue())) {
            throw new GlobalException(HttpStatus.NOT_FOUND, "Tipo de empleado no encontrado");
        }

        // Validar que no haya empleados asociados a este tipo
        // if (empleadoRepository.existsByTipoEmpleadoId(id)) {
        //     throw new GlobalException(HttpStatus.BAD_REQUEST, "No se puede eliminar, existen empleados asociados a este tipo");
        // }
        entity.setActivo(false);
        entity.setUpdatedAt(LocalDateTime.now());
        tipoEmpleadoRepository.save(entity);
    }

    private TipoEmpleadoDto toDto(TipoEmpleadoEntity entity) {
        TipoEmpleadoDto dto = new TipoEmpleadoDto();
        dto.setId(entity.getId());
        dto.setEmpresaId(entity.getEmpresa().getId().longValue());
        dto.setNombre(entity.getNombre());
        dto.setDescripcion(entity.getDescripcion());
        dto.setActivo(entity.getActivo());
        return dto;
    }
}
