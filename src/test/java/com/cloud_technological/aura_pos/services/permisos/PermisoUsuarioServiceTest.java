package com.cloud_technological.aura_pos.services.permisos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cloud_technological.aura_pos.dto.permisos.PermisosUsuarioDto;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository.Perfil;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository.SubmoduloHabilitado;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository.UsuarioPerfil;

class PermisoUsuarioServiceTest {

    private static final int EMPRESA = 7;
    private static final List<String> TODAS = List.of("VER", "CREAR", "EDITAR", "ANULAR");

    private PermisoUsuarioQueryRepository query;
    private PerfilesSistemaService perfilesSistema;
    private PermisoUsuarioService service;

    @BeforeEach
    void setUp() {
        query = mock(PermisoUsuarioQueryRepository.class);
        perfilesSistema = mock(PerfilesSistemaService.class);
        service = new PermisoUsuarioService(query, perfilesSistema);
        // La empresa tiene activos: ventas.cotizaciones (1), contabilidad.asientos (2), caja.usuarios (3).
        when(query.habilitadosEmpresa(EMPRESA)).thenReturn(List.of(
                new SubmoduloHabilitado(1L, "ventas.cotizaciones"),
                new SubmoduloHabilitado(2L, "contabilidad.asientos-contables"),
                new SubmoduloHabilitado(3L, "caja.usuarios")));
        when(query.excepcionesUsuario(any())).thenReturn(Map.of());
    }

    private void usuario(int id, String rol, Long perfilId) {
        when(query.usuario(id)).thenReturn(new UsuarioPerfil(id, EMPRESA, rol, perfilId));
    }

    private void perfil(long id, boolean total, boolean activo) {
        when(query.perfil(id)).thenReturn(new Perfil(id, EMPRESA, "P" + id, total, activo));
    }

    @Test
    void accesoTotalDaTodoLoQueLaEmpresaTieneActivo() {
        usuario(1, "ADMIN", 10L);
        perfil(10L, true, true);

        PermisosUsuarioDto p = service.efectivos(1);

        assertTrue(p.isAccesoTotal());
        assertEquals(3, p.getPermisos().size());
        assertEquals(TODAS, p.getPermisos().get("contabilidad.asientos-contables"));
    }

    @Test
    void adminSinContabilidadPorExcepcion() {
        usuario(1, "ADMIN", 10L);
        perfil(10L, true, true);
        when(query.excepcionesUsuario(1)).thenReturn(Map.of(2L, new Boolean[] { false, false, false, false }));

        PermisosUsuarioDto p = service.efectivos(1);

        assertFalse(p.getPermisos().containsKey("contabilidad.asientos-contables"));
        assertTrue(p.getPermisos().containsKey("ventas.cotizaciones"));
    }

    @Test
    void perfilParcialYExcepcionQueSoloQuitaEditar() {
        usuario(2, "CAJERO", 20L);
        perfil(20L, false, true);
        Map<Long, Boolean[]> base = new HashMap<>();
        base.put(1L, new Boolean[] { true, true, true, true });
        when(query.permisosPerfil(20L)).thenReturn(base);
        // Excepción: ve y crea cotizaciones, pero no edita (null = lo del perfil).
        when(query.excepcionesUsuario(2)).thenReturn(Map.of(1L, new Boolean[] { null, null, false, null }));

        PermisosUsuarioDto p = service.efectivos(2);

        assertEquals(List.of("VER", "CREAR", "ANULAR"), p.getPermisos().get("ventas.cotizaciones"));
        assertFalse(p.getPermisos().containsKey("contabilidad.asientos-contables"));
    }

    @Test
    void excepcionQueDaAlgoQueElPerfilNoTiene() {
        usuario(2, "CAJERO", 20L);
        perfil(20L, false, true);
        when(query.permisosPerfil(20L)).thenReturn(Map.of());
        when(query.excepcionesUsuario(2)).thenReturn(Map.of(2L, new Boolean[] { true, null, null, null }));

        PermisosUsuarioDto p = service.efectivos(2);

        assertEquals(List.of("VER"), p.getPermisos().get("contabilidad.asientos-contables"));
    }

    @Test
    void loQueLaEmpresaNoTieneNoSeDaNiConExcepcion() {
        usuario(2, "CAJERO", 20L);
        perfil(20L, false, true);
        when(query.permisosPerfil(20L)).thenReturn(Map.of(99L, new Boolean[] { true, true, true, true }));
        when(query.excepcionesUsuario(2)).thenReturn(Map.of(98L, new Boolean[] { true, true, true, true }));

        assertTrue(service.efectivos(2).getPermisos().isEmpty());
    }

    @Test
    void superAdminNuncaPierdeUsuarios() {
        usuario(3, "SUPER_ADMIN", 30L);
        perfil(30L, false, true);
        when(query.permisosPerfil(30L)).thenReturn(Map.of());

        assertEquals(TODAS, service.efectivos(3).getPermisos().get("caja.usuarios"));
    }

    @Test
    void perfilDesactivadoDejaSinPermisos() {
        usuario(4, "ADMIN", 40L);
        perfil(40L, true, false);

        PermisosUsuarioDto p = service.efectivos(4);

        assertTrue(p.getPermisos().isEmpty());
        assertNull(p.getPerfilId());
    }

    @Test
    void perfilDeOtraEmpresaNoSirve() {
        usuario(5, "ADMIN", 50L);
        when(query.perfil(50L)).thenReturn(new Perfil(50L, EMPRESA + 1, "Ajeno", true, true));

        assertTrue(service.efectivos(5).getPermisos().isEmpty());
    }

    @Test
    void sinPerfilUsaElDeSistemaDeSuRolYLoCreaSiFalta() {
        usuario(6, "ADMIN", null);
        when(query.perfilIdPorCodigo(EMPRESA, PerfilesSistema.ADMINISTRADOR)).thenReturn(null, 60L);
        perfil(60L, true, true);

        PermisosUsuarioDto p = service.efectivos(6);

        verify(perfilesSistema, times(1)).asegurar(EMPRESA);
        assertEquals(60L, p.getPerfilId());
        assertEquals(3, p.getPermisos().size());
    }

    @Test
    void platformAdminVeTodaLaPlataforma() {
        when(query.usuario(8)).thenReturn(new UsuarioPerfil(8, null, "PLATFORM_ADMIN", null));
        when(query.todosActivos()).thenReturn(List.of(new SubmoduloHabilitado(1L, "ventas.cotizaciones"),
                new SubmoduloHabilitado(5L, "reportes.ventas")));

        PermisosUsuarioDto p = service.efectivos(8);

        assertTrue(p.isAccesoTotal());
        assertEquals(2, p.getPermisos().size());
    }

    @Test
    void puedeYCache() {
        usuario(2, "CAJERO", 20L);
        perfil(20L, false, true);
        when(query.permisosPerfil(20L)).thenReturn(Map.of(1L, new Boolean[] { true, false, false, false }));

        assertTrue(service.puede(2, "ventas.cotizaciones", AccionPermiso.VER));
        assertFalse(service.puede(2, "ventas.cotizaciones", AccionPermiso.CREAR));
        service.efectivos(2);
        verify(query, times(1)).usuario(2); // la segunda y tercera lectura salen de caché

        service.invalidarUsuario(2);
        service.efectivos(2);
        verify(query, times(2)).usuario(2);
    }
}
