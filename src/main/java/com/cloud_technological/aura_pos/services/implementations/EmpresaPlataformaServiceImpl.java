package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.super_admin.CreateEmpresaPlataformaDto;
import com.cloud_technological.aura_pos.dto.super_admin.CreateEmpresaResponseDto;
import com.cloud_technological.aura_pos.dto.super_admin.DashboardPlataformaDto;
import com.cloud_technological.aura_pos.dto.super_admin.EmpresaPlataformaDto;
import com.cloud_technological.aura_pos.dto.super_admin.EmpresaTableDto;
import com.cloud_technological.aura_pos.dto.super_admin.UpdateEmpresaPlataformaDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.PasswordResetTokenEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.entity.UsuarioSucursalEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.UsuarioSucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.super_admin.EmpresaPlataformaQueryRepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.users.PasswordResetTokenRepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.EmpresaPlataformaService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class EmpresaPlataformaServiceImpl implements EmpresaPlataformaService {

    @Value("${app.frontend.url}")
    private String frontendUrl;

    private final EmpresaPlataformaQueryRepository queryRepo;
    private final EmpresaJPARepository empresaRepo;
    private final SucursalJPARepository sucursalRepo;
    private final TerceroJPARepository terceroRepo;
    private final UsuarioJPARepository usuarioRepo;
    private final UsuarioSucursalJPARepository usuarioSucursalRepo;
    private final PasswordResetTokenRepository tokenRepo;
    private final PasswordEncoder passwordEncoder;

    public EmpresaPlataformaServiceImpl(EmpresaPlataformaQueryRepository queryRepo,
                                        EmpresaJPARepository empresaRepo,
                                        SucursalJPARepository sucursalRepo,
                                        TerceroJPARepository terceroRepo,
                                        UsuarioJPARepository usuarioRepo,
                                        UsuarioSucursalJPARepository usuarioSucursalRepo,
                                        PasswordResetTokenRepository tokenRepo,
                                        PasswordEncoder passwordEncoder) {
        this.queryRepo = queryRepo;
        this.empresaRepo = empresaRepo;
        this.sucursalRepo = sucursalRepo;
        this.terceroRepo = terceroRepo;
        this.usuarioRepo = usuarioRepo;
        this.usuarioSucursalRepo = usuarioSucursalRepo;
        this.tokenRepo = tokenRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public DashboardPlataformaDto dashboard() {
        return queryRepo.dashboard();
    }

    @Override
    public PageImpl<EmpresaTableDto> listar(PageableDto<Object> pageable) {
        int page  = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size  = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        return queryRepo.listar(page, size, pageable.getSearch());
    }

    @Override
    public EmpresaPlataformaDto obtenerPorId(Integer id) {
        return queryRepo.findById(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
    }

    @Override
    @Transactional
    public CreateEmpresaResponseDto crear(CreateEmpresaPlataformaDto dto) {
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
                .logoUrl(dto.getLogoUrl())
                .telefono(dto.getTelefono())
                .municipio(dto.getMunicipio())
                .municipioId(dto.getMunicipioId())
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

        // 3. Tercero — datos del admin + datos de contacto de la empresa
        TerceroEntity tercero = TerceroEntity.builder()
                .empresa(empresa)
                .tipoDocumento(dto.getTipoDocumentoAdmin() != null ? dto.getTipoDocumentoAdmin() : "CC")
                .nombres(dto.getNombresAdmin())
                .apellidos(dto.getApellidosAdmin())
                .numeroDocumento(dto.getDocumentoAdmin())
                .email(dto.getEmailAdmin())
                .telefono(dto.getTelefono())
                .municipio(dto.getMunicipio())
                .municipioId(dto.getMunicipioId() != null ? dto.getMunicipioId().longValue() : null)
                .tipoPersona(dto.getTipoPersonaAdmin() != null ? dto.getTipoPersonaAdmin() : "NATURAL")
                .regimen(dto.getRegimenAdmin() != null ? dto.getRegimenAdmin() : "NO_RESPONSABLE_IVA")
                .granContribuyente(Boolean.TRUE.equals(dto.getGranContribuyenteAdmin()))
                .autoRetenedor(Boolean.TRUE.equals(dto.getAutoRetenedorAdmin()))
                .pais(dto.getPaisAdmin() != null ? dto.getPaisAdmin() : "Colombia")
                .codigoPais(dto.getCodigoPaisAdmin() != null ? dto.getCodigoPaisAdmin() : "CO")
                .esCliente(false)
                .esProveedor(false)
                .esEmpleado(true)
                .activo(true)
                .build();
        tercero = terceroRepo.save(tercero);

        // 4. Usuario ADMIN
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
        usuarioSucursalRepo.save(UsuarioSucursalEntity.builder()
                .usuario(usuario)
                .sucursal(sucursal)
                .esDefault(true)
                .activo(true)
                .build());

        // 6. Generar token de reset (válido 72 h)
        String resetToken = UUID.randomUUID().toString().replace("-", "");
        tokenRepo.save(PasswordResetTokenEntity.builder()
                .usuario(usuario)
                .token(resetToken)
                .expiresAt(LocalDateTime.now().plusHours(72))
                .usado(false)
                .build());

        String resetLink = frontendUrl + "/reset-password?token=" + resetToken;

        return new CreateEmpresaResponseDto(
                obtenerPorId(empresa.getId()),
                dto.getEmailAdmin(),
                dto.getPasswordAdmin(),
                resetLink
        );
    }

    @Override
    @Transactional
    public EmpresaPlataformaDto actualizar(Integer id, UpdateEmpresaPlataformaDto dto) {
        EmpresaEntity empresa = empresaRepo.findById(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));

        if (dto.getRazonSocial()     != null) empresa.setRazonSocial(dto.getRazonSocial());
        if (dto.getNombreComercial() != null) empresa.setNombreComercial(dto.getNombreComercial());
        if (dto.getDv()              != null) empresa.setDv(dto.getDv());
        if (dto.getLogoUrl()         != null) empresa.setLogoUrl(dto.getLogoUrl());
        if (dto.getTelefono()        != null) empresa.setTelefono(dto.getTelefono());
        if (dto.getMunicipio()       != null) empresa.setMunicipio(dto.getMunicipio());
        if (dto.getMunicipioId()     != null) empresa.setMunicipioId(dto.getMunicipioId());
        if (dto.getActiva()          != null) empresa.setActiva(dto.getActiva());
        empresaRepo.save(empresa);

        return obtenerPorId(id);
    }

    @Override @Transactional
    public void suspender(Integer id) { cambiarEstado(id, false); }

    @Override @Transactional
    public void activar(Integer id) { cambiarEstado(id, true); }

    private void cambiarEstado(Integer id, boolean estado) {
        EmpresaEntity e = empresaRepo.findById(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
        e.setActiva(estado);
        empresaRepo.save(e);
    }
}
