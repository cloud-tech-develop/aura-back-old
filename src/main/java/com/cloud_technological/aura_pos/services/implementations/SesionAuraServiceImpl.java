package com.cloud_technological.aura_pos.services.implementations;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.auth.SesionAuraDto;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.SesionAuraService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SesionAuraServiceImpl implements SesionAuraService {
    private final SecurityUtils security;
    private final UsuarioJPARepository usuarios;
    private final EmpresaJPARepository empresas;

    @Override
    @Transactional(readOnly = true)
    public SesionAuraDto actual() {
        // JwtAuthenticationFilter ya comprobó firma, caducidad, versión y vínculo.
        Long usuarioId = security.getUsuarioId();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (usuarioId == null || authentication == null || !authentication.isAuthenticated()) {
            throw new GlobalException(HttpStatus.UNAUTHORIZED, "Inicie sesión");
        }
        UsuarioEntity usuario = usuarios.findById(Math.toIntExact(usuarioId))
                .orElseThrow(() -> new GlobalException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
        if (!Boolean.TRUE.equals(usuario.getActivo()) || !usuario.getUsername().equals(authentication.getName())) {
            throw new GlobalException(HttpStatus.UNAUTHORIZED, "Sesión inválida");
        }
        String rol = authentication.getAuthorities().stream().map(a -> a.getAuthority())
                .findFirst().orElseThrow(() -> new GlobalException(HttpStatus.UNAUTHORIZED, "Sesión sin rol"));
        Integer empresaId = security.getEmpresaId();
        boolean plataforma = "PLATFORM_ADMIN".equals(rol);
        if (!plataforma && (empresaId == null || (security.getVinculoId() == null
                && (usuario.getEmpresa() == null || !empresaId.equals(usuario.getEmpresa().getId()))))) {
            throw new GlobalException(HttpStatus.UNAUTHORIZED, "Seleccione una empresa autorizada");
        }
        String empresaNombre = empresaId == null ? null : empresas.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.UNAUTHORIZED, "Empresa no encontrada"))
                .getRazonSocial();
        String nombre = usuario.getTercero() == null ? usuario.getUsername()
                : (usuario.getTercero().getNombres() + " " + usuario.getTercero().getApellidos()).trim();
        return new SesionAuraDto(usuarioId, usuario.getUsername(), nombre, empresaId,
                empresaNombre, security.getSucursalId(), rol);
    }
}
