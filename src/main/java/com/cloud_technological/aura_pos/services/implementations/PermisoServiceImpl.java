package com.cloud_technological.aura_pos.services.implementations;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.permisos.ModuloPermisoDto;
import com.cloud_technological.aura_pos.dto.permisos.ModuloPermisoUpdateDto;
import com.cloud_technological.aura_pos.dto.permisos.PermisosEmpresaDto;
import com.cloud_technological.aura_pos.dto.permisos.SubmoduloPermisoDto;
import com.cloud_technological.aura_pos.dto.permisos.SubmoduloPermisoUpdateDto;
import com.cloud_technological.aura_pos.dto.permisos.UpdatePermisosDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.EmpresaModuloEntity;
import com.cloud_technological.aura_pos.entity.EmpresaSubmoduloEntity;
import com.cloud_technological.aura_pos.entity.ModuloEntity;
import com.cloud_technological.aura_pos.entity.SubmoduloEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.platform.EmpresaModuloJPARepository;
import com.cloud_technological.aura_pos.repositories.platform.EmpresaSubmoduloJPARepository;
import com.cloud_technological.aura_pos.repositories.platform.ModuloQueryRepository;
import com.cloud_technological.aura_pos.repositories.platform.ModuloJPARepository;
import com.cloud_technological.aura_pos.repositories.platform.SubmoduloJPARepository;
import com.cloud_technological.aura_pos.services.PermisoService;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PermisoServiceImpl implements PermisoService {

    private final EmpresaJPARepository empresaRepository;
    private final EmpresaModuloJPARepository empresaModuloRepository;
    private final EmpresaSubmoduloJPARepository empresaSubmoduloRepository;
    private final ModuloJPARepository moduloRepository;
    private final SubmoduloJPARepository submoduloRepository;
    private final ModuloQueryRepository moduloQueryRepository;

    @Override
    public PermisosEmpresaDto obtenerPermisosPorEmpresa(Integer empresaId) {
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));

        List<ModuloPermisoDto> permisos = moduloQueryRepository.listarPermisosPorEmpresa(empresaId);
        
        PermisosEmpresaDto dto = new PermisosEmpresaDto();
        dto.setEmpresaId(empresa.getId());
        dto.setEmpresaNombre(empresa.getRazonSocial());
        dto.setEmpresaNit(empresa.getNit());
        dto.setModulos(permisos);
        
        return dto;
    }

    @Override
    @Transactional
    public PermisosEmpresaDto actualizarPermisos(Integer empresaId, UpdatePermisosDto dto) {
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));

        if (dto.getModulos() != null) {
            for (ModuloPermisoUpdateDto moduloDto : dto.getModulos()) {
                // Actualizar o crear permiso de módulo
                Optional<EmpresaModuloEntity> empModuloOpt = empresaModuloRepository
                        .findByEmpresaIdAndModuloId(empresaId, moduloDto.getModuloId());
                
                EmpresaModuloEntity empModulo;
                if (empModuloOpt.isPresent()) {
                    empModulo = empModuloOpt.get();
                    empModulo.setActivo(moduloDto.getActivo() != null ? moduloDto.getActivo() : false);
                } else {
                    ModuloEntity modulo = moduloRepository.findById(moduloDto.getModuloId())
                            .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Módulo no encontrado"));
                    empModulo = EmpresaModuloEntity.builder()
                            .empresa(empresa)
                            .modulo(modulo)
                            .activo(moduloDto.getActivo() != null ? moduloDto.getActivo() : false)
                            .build();
                }
                empresaModuloRepository.save(empModulo);

                // Actualizar permisos de submódulos
                if (moduloDto.getSubmodulos() != null) {
                    for (SubmoduloPermisoUpdateDto submoduloDto : moduloDto.getSubmodulos()) {
                        Optional<EmpresaSubmoduloEntity> empSubmoduloOpt = empresaSubmoduloRepository
                                .findByEmpresaIdAndSubmoduloId(empresaId, submoduloDto.getSubmoduloId());
                        
                        EmpresaSubmoduloEntity empSubmodulo;
                        if (empSubmoduloOpt.isPresent()) {
                            empSubmodulo = empSubmoduloOpt.get();
                            empSubmodulo.setActivo(submoduloDto.getActivo() != null ? submoduloDto.getActivo() : false);
                        } else {
                            SubmoduloEntity submodulo = submoduloRepository.findById(submoduloDto.getSubmoduloId())
                                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Submódulo no encontrado"));
                            empSubmodulo = EmpresaSubmoduloEntity.builder()
                                    .empresa(empresa)
                                    .submodulo(submodulo)
                                    .activo(submoduloDto.getActivo() != null ? submoduloDto.getActivo() : false)
                                    .build();
                        }
                        empresaSubmoduloRepository.save(empSubmodulo);
                    }
                }
            }
        }

        return obtenerPermisosPorEmpresa(empresaId);
    }

    @Override
    public List<ModuloPermisoDto> obtenerPermisosPublicos(String nit) {
        EmpresaEntity empresa = empresaRepository.findByNit(nit)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
        
        return moduloQueryRepository.listarPermisosPorEmpresa(empresa.getId());
    }

    @Override
    public List<ModuloPermisoDto> obtenerModulosPorEmpresa(Integer empresaId) {
        return moduloQueryRepository.listarPermisosPorEmpresa(empresaId);
    }

    @Override
    public boolean tienePermiso(Integer empresaId, String moduloCodigo, String submoduloCodigo) {
        List<ModuloPermisoDto> permisos = moduloQueryRepository.listarPermisosPorEmpresa(empresaId);
        
        for (ModuloPermisoDto modulo : permisos) {
            if (modulo.getModuloCodigo().equals(moduloCodigo)) {
                // Si solo se requiere permiso de módulo
                if (submoduloCodigo == null || submoduloCodigo.isEmpty()) {
                    return modulo.getActivo() != null && modulo.getActivo();
                }
                
                // Verificar submódulo
                if (modulo.getSubmodulos() != null) {
                    for (SubmoduloPermisoDto submodulo : modulo.getSubmodulos()) {
                        if (submodulo.getSubmoduloCodigo().equals(submoduloCodigo)) {
                            return submodulo.getActivo() != null && submodulo.getActivo();
                        }
                    }
                }
            }
        }
        
        return false;
    }
}
