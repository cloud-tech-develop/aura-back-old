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

    /** Para que los usuarios vean el cambio de módulos sin esperar a que venza la caché. */
    @Autowired
    @org.springframework.context.annotation.Lazy
    private com.cloud_technological.aura_pos.services.permisos.PermisoUsuarioService permisosUsuario;

    @Override
    @Transactional
    public PermisosEmpresaDto actualizarPermisos(Integer empresaId, UpdatePermisosDto dto) {
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));

        normalizar(dto);

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

        permisosUsuario.invalidarTodo();
        return obtenerPermisosPorEmpresa(empresaId);
    }

    /**
     * Deja el árbol coherente antes de guardar:
     * <ul>
     * <li>una pantalla activa dentro de un grupo (RRHH → Gestión → Empleados)
     * enciende su grupo: sin él, la pantalla no se vería;</li>
     * <li>un grupo apagado apaga sus pantallas;</li>
     * <li>un módulo con algo activo queda activo; uno sin nada activo, apagado.</li>
     * </ul>
     */
    private void normalizar(UpdatePermisosDto dto) {
        if (dto.getModulos() == null) return;
        java.util.Map<Integer, Integer> padreDe = new java.util.HashMap<>();
        for (ModuloPermisoDto m : moduloQueryRepository.listarPermisosPorEmpresa(null)) {
            for (SubmoduloPermisoDto s : m.getSubmodulos()) {
                if (s.getPadreId() != null) padreDe.put(s.getSubmoduloId(), s.getPadreId());
            }
        }
        for (ModuloPermisoUpdateDto m : dto.getModulos()) {
            if (m.getSubmodulos() == null) m.setSubmodulos(new ArrayList<>());
            java.util.Map<Integer, SubmoduloPermisoUpdateDto> porId = new java.util.LinkedHashMap<>();
            for (SubmoduloPermisoUpdateDto s : m.getSubmodulos()) porId.put(s.getSubmoduloId(), s);

            // Pantalla activa → su grupo activo.
            for (SubmoduloPermisoUpdateDto s : new ArrayList<>(porId.values())) {
                Integer padre = padreDe.get(s.getSubmoduloId());
                if (Boolean.TRUE.equals(s.getActivo()) && padre != null) {
                    SubmoduloPermisoUpdateDto g = porId.get(padre);
                    if (g == null) {
                        g = new SubmoduloPermisoUpdateDto();
                        g.setSubmoduloId(padre);
                        porId.put(padre, g);
                        m.getSubmodulos().add(g);
                    }
                    g.setActivo(true);
                }
            }
            // Grupo apagado → sus pantallas apagadas.
            for (SubmoduloPermisoUpdateDto s : porId.values()) {
                Integer padre = padreDe.get(s.getSubmoduloId());
                if (padre != null && porId.containsKey(padre) && !Boolean.TRUE.equals(porId.get(padre).getActivo())) {
                    s.setActivo(false);
                }
            }
            boolean alguno = porId.values().stream().anyMatch(s -> Boolean.TRUE.equals(s.getActivo()));
            if (alguno) m.setActivo(true);
            else if (!porId.isEmpty()) m.setActivo(false);
            if (!Boolean.TRUE.equals(m.getActivo())) porId.values().forEach(s -> s.setActivo(false));
        }
    }

    /**
     * Activa para una empresa exactamente los submódulos indicados (y sus grupos
     * y módulos). Lo usa la creación de empresas.
     */
    @Override
    @Transactional
    public PermisosEmpresaDto activarSubmodulos(Integer empresaId, java.util.Collection<Integer> submodulos) {
        java.util.Set<Integer> elegidos = new java.util.HashSet<>(submodulos);
        UpdatePermisosDto dto = new UpdatePermisosDto();
        dto.setModulos(new ArrayList<>());
        for (ModuloPermisoDto m : moduloQueryRepository.listarPermisosPorEmpresa(null)) {
            ModuloPermisoUpdateDto mu = new ModuloPermisoUpdateDto();
            mu.setModuloId(m.getModuloId());
            mu.setSubmodulos(new ArrayList<>());
            boolean alguno = false;
            for (SubmoduloPermisoDto s : m.getSubmodulos()) {
                SubmoduloPermisoUpdateDto su = new SubmoduloPermisoUpdateDto();
                su.setSubmoduloId(s.getSubmoduloId());
                su.setActivo(elegidos.contains(s.getSubmoduloId()));
                alguno |= su.getActivo();
                mu.getSubmodulos().add(su);
            }
            mu.setActivo(alguno);
            dto.getModulos().add(mu);
        }
        return actualizarPermisos(empresaId, dto);
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
