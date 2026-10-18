package com.cloud_technological.aura_pos.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.implementations.SesionAuraServiceImpl;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

class SesionAuraServiceTest {
    private final SecurityUtils security = mock(SecurityUtils.class);
    private final UsuarioJPARepository usuarios = mock(UsuarioJPARepository.class);
    private final EmpresaJPARepository empresas = mock(EmpresaJPARepository.class);
    private final SesionAuraServiceImpl service = new SesionAuraServiceImpl(security, usuarios, empresas);
    private UsuarioEntity usuario;

    @BeforeEach void preparar() {
        EmpresaEntity empresa = EmpresaEntity.builder().id(10).razonSocial("Cliente Aura").build();
        usuario = UsuarioEntity.builder().id(7).username("cliente").activo(true).empresa(empresa).rol("ADMIN").build();
        when(security.getUsuarioId()).thenReturn(7L);
        when(security.getEmpresaId()).thenReturn(10);
        when(usuarios.findById(7)).thenReturn(Optional.of(usuario));
        when(empresas.findById(10)).thenReturn(Optional.of(empresa));
        autenticar("ADMIN");
    }
    private void autenticar(String rol) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "cliente", "token", List.of(new SimpleGrantedAuthority(rol))));
    }
    @AfterEach void limpiar() { SecurityContextHolder.clearContext(); }
    @Test void identidadEsDeLaSesion() {
        var result = service.actual();
        assertEquals(10, result.empresaId()); assertEquals(7L, result.usuarioId());
        assertEquals("ADMIN", result.rol());
    }
    @Test void plataformaPuedeNoTenerEmpresa() {
        autenticar("PLATFORM_ADMIN"); when(security.getEmpresaId()).thenReturn(null);
        var result = service.actual(); assertNull(result.empresaId()); assertEquals("PLATFORM_ADMIN", result.rol());
    }
    @Test void usuarioInactivoNoPuedeValidarSesion() {
        usuario.setActivo(false); assertThrows(GlobalException.class, service::actual);
    }
    @Test void empresaAjenaSinVinculoEsRechazada() {
        when(security.getEmpresaId()).thenReturn(20); assertThrows(GlobalException.class, service::actual);
    }
    @Test void firmaUsaEmpresaSeleccionadaYRolEfectivo() {
        var cliente = EmpresaEntity.builder().id(20).razonSocial("Cliente de firma").build();
        when(security.getEmpresaId()).thenReturn(20); when(security.getVinculoId()).thenReturn(4L);
        when(empresas.findById(20)).thenReturn(Optional.of(cliente)); autenticar("CONTADOR_EXTERNO");
        var result = service.actual(); assertEquals(20, result.empresaId()); assertEquals("CONTADOR_EXTERNO", result.rol());
    }
}
