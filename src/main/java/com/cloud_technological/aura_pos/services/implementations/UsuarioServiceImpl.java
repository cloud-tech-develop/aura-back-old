package com.cloud_technological.aura_pos.services.implementations;

import java.util.List;

import org.springframework.data.domain.PageImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.usuarios.CreateUsuarioDto;
import com.cloud_technological.aura_pos.dto.usuarios.UpdateUsuarioDto;
import com.cloud_technological.aura_pos.dto.usuarios.UsuarioDto;
import com.cloud_technological.aura_pos.dto.usuarios.UsuarioTableDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.entity.UsuarioSucursalEntity;
import com.cloud_technological.aura_pos.mappers.UsuarioMapper;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.UsuarioSucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioQueryRepository;
import com.cloud_technological.aura_pos.services.UsuarioService;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioJPARepository usuarioRepo;
    private final UsuarioSucursalJPARepository usuarioSucursalRepo;
    private final SucursalJPARepository sucursalRepo;
    private final TerceroJPARepository terceroRepo;
    private final UsuarioQueryRepository queryRepo;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioMapper usuarioMapper;

    public UsuarioServiceImpl(UsuarioJPARepository usuarioRepo,
                              UsuarioSucursalJPARepository usuarioSucursalRepo,
                              SucursalJPARepository sucursalRepo,
                              TerceroJPARepository terceroRepo,
                              UsuarioQueryRepository queryRepo,
                              PasswordEncoder passwordEncoder,
                              UsuarioMapper usuarioMapper) {
        this.usuarioRepo = usuarioRepo;
        this.usuarioSucursalRepo = usuarioSucursalRepo;
        this.sucursalRepo = sucursalRepo;
        this.terceroRepo = terceroRepo;
        this.queryRepo = queryRepo;
        this.passwordEncoder = passwordEncoder;
        this.usuarioMapper = usuarioMapper;
    }

    @Override
    public PageImpl<UsuarioTableDto> paginar(PageableDto<Object> pageable, Integer empresaId) {
        return queryRepo.paginar(pageable, empresaId);
    }

    @Override
    public UsuarioDto obtenerPorId(Integer id, Integer empresaId) {
        UsuarioEntity entity = usuarioRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        return mapToDtoCompleto(entity);
    }

    @Override
    @Transactional
    public UsuarioDto crear(CreateUsuarioDto dto, Integer empresaId) {
        if (usuarioRepo.existsByUsername(dto.getEmail())) {
            throw new IllegalArgumentException("El username ya está en uso");
        }

        TerceroEntity tercero = usuarioMapper.mapTerceroFromCreateDto(dto);
        tercero = terceroRepo.save(tercero);

        UsuarioEntity usuario = usuarioMapper.toEntity(dto);
        usuario.setUsername(dto.getEmail());
        usuario.setPassword(passwordEncoder.encode(dto.getPassword()));
        if (dto.getPinAccesoRapido() != null) {
            usuario.setPinAccesoRapido(passwordEncoder.encode(dto.getPinAccesoRapido()));
        }
        
        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);
        usuario.setEmpresa(empresa);
        usuario.setTercero(tercero);
        
        usuario = usuarioRepo.save(usuario);

        asignarSucursales(usuario, dto.getSucursales());

        return mapToDtoCompleto(usuario);
    }

    @Override
    @Transactional
    public UsuarioDto actualizar(Integer id, UpdateUsuarioDto dto, Integer empresaId) {
        UsuarioEntity usuario = usuarioRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        if (dto.getUsername() != null &&
                usuarioRepo.existsByUsernameAndIdNot(dto.getUsername(), id)) {
            throw new IllegalArgumentException("El username ya está en uso");
        }

        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            usuario.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        if (dto.getPinAccesoRapido() != null) {
            usuario.setPinAccesoRapido(passwordEncoder.encode(dto.getPinAccesoRapido()));
        }

        usuarioMapper.updateEntityFromDto(dto, usuario);

        TerceroEntity tercero = usuario.getTercero();
        usuarioMapper.updateTerceroFromUpdateDto(dto, tercero);
        terceroRepo.save(tercero);

        if (dto.getSucursales() != null) {
            usuarioSucursalRepo.deleteAllByUsuarioId(id);
            asignarSucursales(usuario, dto.getSucursales());
        }

        return mapToDtoCompleto(usuarioRepo.save(usuario));
    }

    @Override
    @Transactional
    public void desactivar(Integer id, Integer empresaId) {
        UsuarioEntity usuario = usuarioRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        usuario.setActivo(false);
        usuarioRepo.save(usuario);
    }

    private void asignarSucursales(UsuarioEntity usuario,
                                    List<CreateUsuarioDto.SucursalAsignacion> asignaciones) {
        if (asignaciones == null || asignaciones.isEmpty()) return;

        boolean hayDefault = asignaciones.stream().anyMatch(a -> Boolean.TRUE.equals(a.getEsDefault()));

        for (int i = 0; i < asignaciones.size(); i++) {
            CreateUsuarioDto.SucursalAsignacion asig = asignaciones.get(i);

            SucursalEntity sucursal = sucursalRepo.findById(asig.getSucursalId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Sucursal no encontrada: " + asig.getSucursalId()));

            boolean esDefault = hayDefault
                    ? Boolean.TRUE.equals(asig.getEsDefault())
                    : i == 0;

            UsuarioSucursalEntity us = UsuarioSucursalEntity.builder()
                    .usuario(usuario)
                    .sucursal(sucursal)
                    .esDefault(esDefault)
                    .activo(true)
                    .build();

            usuarioSucursalRepo.save(us);
        }
    }

    private UsuarioDto mapToDtoCompleto(UsuarioEntity entity) {
        UsuarioDto dto = usuarioMapper.toDto(entity);
        dto.setSucursales(queryRepo.sucursalesDeUsuario(entity.getId()));
        return dto;
    }
}
