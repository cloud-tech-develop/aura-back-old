package com.cloud_technological.aura_pos.services.implementations;

import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.super_admin.CreateEmpresaPlataformaDto;
import com.cloud_technological.aura_pos.dto.super_admin.DashboardPlataformaDto;
import com.cloud_technological.aura_pos.dto.super_admin.EmpresaPlataformaDto;
import com.cloud_technological.aura_pos.dto.super_admin.EmpresaTableDto;
import com.cloud_technological.aura_pos.dto.super_admin.UpdateEmpresaPlataformaDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.entity.UsuarioSucursalEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.UsuarioSucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.super_admin.EmpresaPlataformaQueryRepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.EmpresaPlataformaService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class EmpresaPlataformaServiceImpl implements EmpresaPlataformaService{
    private final EmpresaPlataformaQueryRepository queryRepo;
    private final EmpresaJPARepository empresaRepo;
    private final SucursalJPARepository sucursalRepo;
    private final TerceroJPARepository terceroRepo;
    private final UsuarioJPARepository usuarioRepo;
    private final UsuarioSucursalJPARepository usuarioSucursalRepo;
    private final PasswordEncoder passwordEncoder;

    public EmpresaPlataformaServiceImpl(EmpresaPlataformaQueryRepository queryRepo,
                                        EmpresaJPARepository empresaRepo,
                                        SucursalJPARepository sucursalRepo,
                                        TerceroJPARepository terceroRepo,
                                        UsuarioJPARepository usuarioRepo,
                                        UsuarioSucursalJPARepository usuarioSucursalRepo,
                                        PasswordEncoder passwordEncoder) {
        this.queryRepo = queryRepo;
        this.empresaRepo = empresaRepo;
        this.sucursalRepo = sucursalRepo;
        this.terceroRepo = terceroRepo;
        this.usuarioRepo = usuarioRepo;
        this.usuarioSucursalRepo = usuarioSucursalRepo;
        this.passwordEncoder = passwordEncoder;
    }
    @Override
    public DashboardPlataformaDto dashboard() {
        return queryRepo.dashboard();
    }

    // ── Listar paginado ───────────────────────────────────────
    @Override
    public PageImpl<EmpresaTableDto> listar(PageableDto<Object> pageable) {
        int page   = pageable.getPage()   != null ? pageable.getPage().intValue()   : 0;
        int size   = pageable.getRows()   != null ? pageable.getRows().intValue()   : 10;
        String search = pageable.getSearch();
        return queryRepo.listar(page, size, search);
    }

    @Override
    public EmpresaPlataformaDto obtenerPorId(Integer id) {
        return queryRepo.findById(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
    }

    @Override
    @Transactional
    public EmpresaPlataformaDto crear(CreateEmpresaPlataformaDto dto) {
        // Validaciones
        if (empresaRepo.existsByNit(dto.getNit()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe una empresa con ese NIT");
        if (usuarioRepo.findByUsername(dto.getEmailAdmin()).isPresent())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El email ya está registrado");

        // 1. Empresa
        EmpresaEntity empresa = EmpresaEntity.builder()
                .razonSocial(dto.getRazonSocial())
                .nombreComercial(dto.getNombreComercial())
                .nit(dto.getNit())
                .dv(dto.getDv())
                .activa(true)
                .build();
        empresa = empresaRepo.save(empresa);

        // 2. Sucursal principal
        SucursalEntity sucursal = SucursalEntity.builder()
                .empresa(empresa)
                .nombre(dto.getNombreSucursal())
                .codigo("001")
                .activa(true)
                .build();
        sucursal = sucursalRepo.save(sucursal);

        // 3. Tercero (datos personales del admin)
        TerceroEntity tercero = TerceroEntity.builder()
                .empresa(empresa)
                .nombres(dto.getNombresAdmin())
                .apellidos(dto.getApellidosAdmin())
                .numeroDocumento(dto.getDocumentoAdmin())
                .email(dto.getEmailAdmin())
                .esEmpleado(true)
                .activo(true)
                .build();
        tercero = terceroRepo.save(tercero);

        // 4. Usuario SUPER_ADMIN de la empresa
        UsuarioEntity usuario = UsuarioEntity.builder()
                .empresa(empresa)
                .tercero(tercero)
                .username(dto.getEmailAdmin())
                .password(passwordEncoder.encode(dto.getPasswordAdmin()))
                .rol("ADMIN")
                .activo(true)
                .build();
        usuario = usuarioRepo.save(usuario);

        // 5. Vincular usuario ↔ sucursal
        UsuarioSucursalEntity vinculo = UsuarioSucursalEntity.builder()
                .usuario(usuario)
                .sucursal(sucursal)
                .esDefault(true)
                .activo(true)
                .build();
        usuarioSucursalRepo.save(vinculo);

        return obtenerPorId(empresa.getId());
    }

    @Override
    @Transactional
    public EmpresaPlataformaDto actualizar(Integer id, UpdateEmpresaPlataformaDto dto) {
        EmpresaEntity empresa = empresaRepo.findById(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));

        if (dto.getRazonSocial()    != null) empresa.setRazonSocial(dto.getRazonSocial());
        if (dto.getNombreComercial() != null) empresa.setNombreComercial(dto.getNombreComercial());
        if (dto.getDv()              != null) empresa.setDv(dto.getDv());
        if (dto.getActiva()          != null) empresa.setActiva(dto.getActiva());
        empresaRepo.save(empresa);

        return obtenerPorId(id);
    }

    @Override
    @Transactional
    public void suspender(Integer id) { cambiarEstado(id, false); }

    @Override
    @Transactional
    public void activar(Integer id)   { cambiarEstado(id, true);  }


    private void cambiarEstado(Integer id, boolean estado) {
        EmpresaEntity empresa = empresaRepo.findById(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
        empresa.setActiva(estado);
        empresaRepo.save(empresa);
    }
}
