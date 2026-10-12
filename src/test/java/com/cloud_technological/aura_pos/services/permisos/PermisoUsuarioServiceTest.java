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
import com.cloud_technological.aura_pos.utils.SecurityUtils;

class PermisoUsuarioServiceTest {

    private static final int EMPRESA = 7;
    private static final List<String> TODAS = List.of("VER", "CREAR", "EDITAR", "ANULAR");

    private PermisoUsuarioQueryRepository query;
    private PerfilesSistemaService perfilesSistema;
    private PermisoUsuarioService service;
    private SecurityUtils securityUtils;
    private com.cloud_technological.aura_pos.repositories.firma_contable.FirmaContableQueryRepository firmaQuery;

    @BeforeEach
    void setUp() {
        query = mock(PermisoUsuarioQueryRepository.class);
        perfilesSistema = mock(PerfilesSistemaService.class);
        securityUtils = mock(SecurityUtils.class);
        // Sin token (como un job): la empresa sale del usuario. Mockito daría 0.
        when(securityUtils.getEmpresaId()).thenReturn(null);
        firmaQuery = mock(com.cloud_technological.aura_pos.repositories.firma_contable.FirmaContableQueryRepository.class);
        service = new PermisoUsuarioService(query, perfilesSistema, securityUtils, firmaQuery);
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

    // ── Aislamiento por empresa de la sesión (PLAN_FIRMA_CONTABLE F0) ──────

    @Test
    void sesionEnLaEmpresaDelUsuarioDaSusPermisos() {
        usuario(1, "ADMIN", 10L);
        perfil(10L, true, true);
        when(securityUtils.getEmpresaId()).thenReturn(EMPRESA);

        PermisosUsuarioDto p = service.efectivos(1);

        assertTrue(p.isAccesoTotal());
        assertEquals(3, p.getPermisos().size());
    }

    @Test
    void sesionEnOtraEmpresaNoDaPermisos() {
        usuario(1, "ADMIN", 10L);
        perfil(10L, true, true);
        when(securityUtils.getEmpresaId()).thenReturn(EMPRESA + 1);

        PermisosUsuarioDto p = service.efectivos(1);

        assertFalse(p.isAccesoTotal());
        assertTrue(p.getPermisos().isEmpty());
        assertTrue(p.getEspeciales().isEmpty());
    }

    @Test
    void laCacheNoMezclaEmpresas() {
        usuario(1, "ADMIN", 10L);
        perfil(10L, true, true);

        when(securityUtils.getEmpresaId()).thenReturn(EMPRESA);
        assertEquals(3, service.efectivos(1).getPermisos().size());

        // Mismo usuario, la sesión ahora es de otra empresa: no reusa lo de la primera.
        when(securityUtils.getEmpresaId()).thenReturn(EMPRESA + 1);
        assertTrue(service.efectivos(1).getPermisos().isEmpty());

        // Y al volver, sigue con lo suyo.
        when(securityUtils.getEmpresaId()).thenReturn(EMPRESA);
        assertEquals(3, service.efectivos(1).getPermisos().size());
    }

    @Test
    void invalidarUsuarioBorraTodasSusEmpresas() {
        usuario(1, "ADMIN", 10L);
        perfil(10L, true, true);
        when(securityUtils.getEmpresaId()).thenReturn(EMPRESA);
        service.efectivos(1);

        service.invalidarUsuario(1);
        service.efectivos(1);

        verify(query, times(2)).usuario(1);
    }

    @Test
    void fueraDeUnaPeticionUsaLaEmpresaDelUsuario() {
        usuario(1, "ADMIN", 10L);
        perfil(10L, true, true);
        when(securityUtils.getEmpresaId()).thenThrow(new IllegalStateException("sin token"));

        assertEquals(3, service.efectivos(1).getPermisos().size());
    }

    // ── Contador externo en un cliente (PLAN_FIRMA_CONTABLE F3) ─────────────

    private static final int CLIENTE = 50;

    private String modalidad = "CLIENTE_PAGA";
    private String rolEnFirma = "ADMIN";

    private void accesoAlCliente(long perfilId) {
        when(securityUtils.getEmpresaId()).thenReturn(CLIENTE);
        when(firmaQuery.acceso(1, CLIENTE)).thenReturn(
                new com.cloud_technological.aura_pos.repositories.firma_contable.FirmaContableQueryRepository.AccesoCliente(
                        9L, EMPRESA, CLIENTE, perfilId, modalidad, rolEnFirma));
        when(query.perfil(perfilId)).thenReturn(new Perfil(perfilId, CLIENTE, "Contador externo", false, true));
        when(query.habilitadosEmpresa(CLIENTE)).thenReturn(List.of(
                new SubmoduloHabilitado(2L, "contabilidad.asientos-contables"),
                new SubmoduloHabilitado(4L, "ventas.facturas")));
        Map<Long, Boolean[]> delPerfil = new HashMap<>();
        delPerfil.put(2L, new Boolean[] { true, true, true, true });
        when(query.permisosPerfil(perfilId)).thenReturn(delPerfil);
    }

    @Test
    void enElClienteUsaElPerfilDelVinculoConRolContadorExterno() {
        usuario(1, "ADMIN", 10L);
        perfil(10L, true, true);
        accesoAlCliente(80L);

        PermisosUsuarioDto p = service.efectivos(1);

        assertEquals(PerfilesSistema.CONTADOR_EXTERNO, p.getRol());
        assertFalse(p.isAccesoTotal());
        assertEquals(TODAS, p.getPermisos().get("contabilidad.asientos-contables"));
        assertFalse(p.getPermisos().containsKey("ventas.facturas"));
        assertTrue(p.isTodasLasSedes());
    }

    @Test
    void enElClienteNoValenLasExcepcionesDeSuFirma() {
        usuario(1, "ADMIN", 10L);
        perfil(10L, true, true);
        accesoAlCliente(80L);
        // En su firma le dieron facturas de venta por excepción: en el cliente no cuenta.
        when(query.excepcionesUsuario(1)).thenReturn(Map.of(4L, new Boolean[] { true, true, true, true }));

        assertFalse(service.efectivos(1).getPermisos().containsKey("ventas.facturas"));
    }

    @Test
    void perfilDelVinculoDeOtraEmpresaNoDaNada() {
        usuario(1, "ADMIN", 10L);
        perfil(10L, true, true);
        accesoAlCliente(80L);
        when(query.perfil(80L)).thenReturn(new Perfil(80L, EMPRESA, "Ajeno", true, true));

        assertTrue(service.efectivos(1).getPermisos().isEmpty());
    }

    @Test
    void enUnGestionadoElAdminDeLaFirmaEntraComoAdministrador() {
        usuario(1, "ADMIN", 10L);
        perfil(10L, true, true);
        modalidad = "GESTIONADO";
        accesoAlCliente(80L);
        when(query.perfilIdPorCodigo(CLIENTE, PerfilesSistema.ADMINISTRADOR)).thenReturn(81L);
        when(query.perfil(81L)).thenReturn(new Perfil(81L, CLIENTE, "Administrador", true, true));

        PermisosUsuarioDto p = service.efectivos(1);

        assertEquals("ADMIN", p.getRol());
        assertTrue(p.isAccesoTotal());
        assertTrue(p.getPermisos().containsKey("ventas.facturas"));
    }

    @Test
    void enUnGestionadoElAuxiliarSigueConContadorExterno() {
        usuario(1, "CONTADOR", 10L);
        perfil(10L, false, true);
        modalidad = "GESTIONADO";
        rolEnFirma = "CONTADOR";
        accesoAlCliente(80L);

        PermisosUsuarioDto p = service.efectivos(1);

        assertEquals(PerfilesSistema.CONTADOR_EXTERNO, p.getRol());
        assertFalse(p.getPermisos().containsKey("ventas.facturas"));
    }
}
