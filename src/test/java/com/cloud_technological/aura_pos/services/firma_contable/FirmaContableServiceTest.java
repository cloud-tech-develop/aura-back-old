package com.cloud_technological.aura_pos.services.firma_contable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.EmpresaFirmaClienteEntity;
import com.cloud_technological.aura_pos.entity.PerfilEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.firma_contable.EmpresaFirmaClienteJPARepository;
import com.cloud_technological.aura_pos.repositories.firma_contable.FirmaContableQueryRepository;
import com.cloud_technological.aura_pos.repositories.firma_contable.FirmaContableQueryRepository.VinculoFila;
import com.cloud_technological.aura_pos.repositories.permisos.PerfilJPARepository;
import com.cloud_technological.aura_pos.repositories.permisos.PerfilPermisoJPARepository;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository;
import com.cloud_technological.aura_pos.services.empresa.ConfiguracionEmpresaService;
import com.cloud_technological.aura_pos.services.permisos.BitacoraService;
import com.cloud_technological.aura_pos.services.permisos.PerfilesSistema;
import com.cloud_technological.aura_pos.utils.GlobalException;

class FirmaContableServiceTest {

    private static final int CLIENTE = 10;
    private static final int FIRMA = 20;
    private static final int OTRA_FIRMA = 30;
    private static final int ACTOR = 1;

    private EmpresaJPARepository empresaRepo;
    private EmpresaFirmaClienteJPARepository vinculoRepo;
    private FirmaContableQueryRepository query;
    private PerfilJPARepository perfilRepo;
    private PermisoUsuarioQueryRepository permisoQuery;
    private ConfiguracionEmpresaService configuracion;
    private BitacoraService bitacora;
    private FirmaContableService service;
    private com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository usuarioRepo;
    private com.cloud_technological.aura_pos.repositories.firma_contable.UsuarioFirmaClienteJPARepository asignacionRepo;
    private com.cloud_technological.aura_pos.repositories.firma_contable.SolicitudFirmaClienteJPARepository solicitudRepo =
            mock(com.cloud_technological.aura_pos.repositories.firma_contable.SolicitudFirmaClienteJPARepository.class);

    @BeforeEach
    void setUp() {
        empresaRepo = mock(EmpresaJPARepository.class);
        vinculoRepo = mock(EmpresaFirmaClienteJPARepository.class);
        query = mock(FirmaContableQueryRepository.class);
        perfilRepo = mock(PerfilJPARepository.class);
        permisoQuery = mock(PermisoUsuarioQueryRepository.class);
        configuracion = mock(ConfiguracionEmpresaService.class);
        bitacora = mock(BitacoraService.class);
        usuarioRepo = mock(com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository.class);
        asignacionRepo = mock(com.cloud_technological.aura_pos.repositories.firma_contable.UsuarioFirmaClienteJPARepository.class);
        service = new FirmaContableService(empresaRepo, vinculoRepo, query, perfilRepo,
                mock(PerfilPermisoJPARepository.class), permisoQuery, configuracion, bitacora, usuarioRepo,
                asignacionRepo, solicitudRepo);

        empresa(CLIENTE, false, true);
        empresa(FIRMA, true, true);
        empresa(OTRA_FIRMA, true, true);
        when(permisoQuery.perfilIdPorCodigo(any(), any())).thenReturn(99L);
    }

    private EmpresaEntity empresa(int id, boolean firma, boolean activa) {
        EmpresaEntity e = EmpresaEntity.builder().id(id).razonSocial("E" + id).nit("9" + id)
                .esFirmaContable(firma).activa(activa).build();
        when(empresaRepo.findById(id)).thenReturn(Optional.of(e));
        return e;
    }

    private VinculoFila vinculo(long id, int firma) {
        return new VinculoFila(id, firma, "E" + firma, "9" + firma, CLIENTE, "E" + CLIENTE, "9" + CLIENTE, 99L,
                LocalDateTime.now(), 0, "CLIENTE_PAGA");
    }

    @Test
    void asignaLaFirmaConElPerfilContadorExterno() {
        service.asignarFirma(CLIENTE, FIRMA, ACTOR);

        ArgumentCaptor<EmpresaFirmaClienteEntity> c = ArgumentCaptor.forClass(EmpresaFirmaClienteEntity.class);
        verify(vinculoRepo).saveAndFlush(c.capture());
        assertEquals(FIRMA, c.getValue().getFirmaEmpresaId());
        assertEquals(CLIENTE, c.getValue().getClienteEmpresaId());
        assertEquals(99L, c.getValue().getPerfilId());
        assertTrue(c.getValue().getActivo());
    }

    @Test
    void laMismaFirmaNoHaceNada() {
        VinculoFila actual = vinculo(5L, FIRMA);
        when(query.vinculoActivoDelCliente(CLIENTE)).thenReturn(actual);

        assertSame(actual, service.asignarFirma(CLIENTE, FIRMA, ACTOR));
        verify(vinculoRepo, never()).saveAndFlush(any());
    }

    @Test
    void cambiarDeFirmaDesactivaElVinculoAnterior() {
        when(query.vinculoActivoDelCliente(CLIENTE)).thenReturn(vinculo(5L, FIRMA));
        EmpresaFirmaClienteEntity viejo = new EmpresaFirmaClienteEntity();
        viejo.setId(5L);
        viejo.setActivo(true);
        when(vinculoRepo.findById(5L)).thenReturn(Optional.of(viejo));

        service.asignarFirma(CLIENTE, OTRA_FIRMA, ACTOR);

        assertFalse(viejo.getActivo());
        assertEquals(ACTOR, viejo.getRevocadoPor());
        verify(vinculoRepo).saveAndFlush(viejo);
    }

    @Test
    void quitarSinFirmaNoHaceNada() {
        assertNull(service.asignarFirma(CLIENTE, null, ACTOR));
        verify(vinculoRepo, never()).saveAndFlush(any());
    }

    @Test
    void unaEmpresaQueNoEsFirmaNoSePuedeAsignar() {
        empresa(40, false, true);
        assertThrows(GlobalException.class, () -> service.asignarFirma(CLIENTE, 40, ACTOR));
    }

    @Test
    void unaFirmaSuspendidaNoSePuedeAsignar() {
        empresa(40, true, false);
        assertThrows(GlobalException.class, () -> service.asignarFirma(CLIENTE, 40, ACTOR));
    }

    @Test
    void unaFirmaNoTieneFirma() {
        assertThrows(GlobalException.class, () -> service.asignarFirma(OTRA_FIRMA, FIRMA, ACTOR));
    }

    @Test
    void noSeDesmarcaUnaFirmaConClientes() {
        EmpresaEntity firma = empresa(FIRMA, true, true);
        when(query.clientesDeLaFirma(FIRMA)).thenReturn(List.of(vinculo(5L, FIRMA)));
        assertThrows(GlobalException.class, () -> service.marcarFirma(firma, false));
    }

    @Test
    void elPerfilContadorSeCreaConLaPlantillaSinVentasNiCaja() {
        when(permisoQuery.perfilIdPorCodigo(CLIENTE, PerfilesSistema.CONTADOR_EXTERNO)).thenReturn(null);
        when(configuracion.submodulosPorPatron(PerfilesSistema.CONTADOR.claves())).thenReturn(Set.of(1, 2));
        when(perfilRepo.save(any())).thenAnswer(i -> {
            PerfilEntity p = i.getArgument(0);
            p.setId(77L);
            return p;
        });

        assertEquals(77L, service.asegurarPerfilContador(CLIENTE));
        assertFalse(PerfilesSistema.CONTADOR.claves().stream()
                .anyMatch(c -> c.startsWith("ventas.") || c.startsWith("caja.") || c.startsWith("principal.punto")));
    }

    // ── F4: clientes de cada usuario de la firma ────────────────────────────

    private com.cloud_technological.aura_pos.entity.UsuarioEntity usuarioDe(int id, int empresaId) {
        var u = com.cloud_technological.aura_pos.entity.UsuarioEntity.builder().id(id).username("u" + id)
                .empresa(empresaRepo.findById(empresaId).orElseThrow()).activo(true).build();
        when(usuarioRepo.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    void asignaYQuitaClientesAlUsuarioDeLaFirma() {
        usuarioDe(5, FIRMA);
        when(query.clientesDeLaFirma(FIRMA)).thenReturn(List.of(vinculo(1L, FIRMA), vinculo(2L, FIRMA)));
        when(query.vinculosDelUsuario(5)).thenReturn(List.of(1L));
        when(query.asignacionId(5, 1L)).thenReturn(11L);
        var fila1 = new com.cloud_technological.aura_pos.entity.UsuarioFirmaClienteEntity();
        fila1.setActivo(true);
        when(asignacionRepo.findById(11L)).thenReturn(Optional.of(fila1));

        service.asignarClientes(FIRMA, 5, List.of(2L), ACTOR);

        assertFalse(fila1.getActivo());
        ArgumentCaptor<com.cloud_technological.aura_pos.entity.UsuarioFirmaClienteEntity> c =
                ArgumentCaptor.forClass(com.cloud_technological.aura_pos.entity.UsuarioFirmaClienteEntity.class);
        verify(asignacionRepo, org.mockito.Mockito.times(2)).save(c.capture());
        var nueva = c.getAllValues().get(1);
        assertEquals(2L, nueva.getVinculoId());
        assertTrue(nueva.getActivo());
    }

    @Test
    void noAsignaUnClienteQueNoEsDeLaFirma() {
        usuarioDe(5, FIRMA);
        when(query.clientesDeLaFirma(FIRMA)).thenReturn(List.of(vinculo(1L, FIRMA)));
        assertThrows(GlobalException.class, () -> service.asignarClientes(FIRMA, 5, List.of(99L), ACTOR));
    }

    @Test
    void noAsignaClientesAUnUsuarioDeOtraEmpresa() {
        usuarioDe(5, CLIENTE);
        assertThrows(GlobalException.class, () -> service.asignarClientes(FIRMA, 5, List.of(1L), ACTOR));
    }

    // ── Solicitudes cliente → firma (V200) ──────────────────────────────────

    private com.cloud_technological.aura_pos.entity.SolicitudFirmaClienteEntity solicitudPendiente(long id, int firma) {
        var sol = new com.cloud_technological.aura_pos.entity.SolicitudFirmaClienteEntity();
        sol.setId(id);
        sol.setClienteEmpresaId(CLIENTE);
        sol.setFirmaEmpresaId(firma);
        when(solicitudRepo.findById(id)).thenReturn(Optional.of(sol));
        return sol;
    }

    @Test
    void conFirmaActualNoPuedeSolicitarOtra() {
        when(query.vinculoActivoDelCliente(CLIENTE)).thenReturn(vinculo(5L, FIRMA));
        assertThrows(GlobalException.class, () -> service.solicitar(CLIENTE, OTRA_FIRMA, ACTOR));
        verify(solicitudRepo, never()).saveAndFlush(any());
    }

    @Test
    void soloSePuedeSolicitarAUnaFirma() {
        empresa(40, false, true);
        assertThrows(GlobalException.class, () -> service.solicitar(CLIENTE, 40, ACTOR));
    }

    @Test
    void solicitarGuardaLaSolicitudPendiente() {
        service.solicitar(CLIENTE, FIRMA, ACTOR);

        ArgumentCaptor<com.cloud_technological.aura_pos.entity.SolicitudFirmaClienteEntity> c =
                ArgumentCaptor.forClass(com.cloud_technological.aura_pos.entity.SolicitudFirmaClienteEntity.class);
        verify(solicitudRepo).saveAndFlush(c.capture());
        assertEquals(FIRMA, c.getValue().getFirmaEmpresaId());
        assertEquals("PENDIENTE", c.getValue().getEstado());
    }

    @Test
    void aceptarCreaElVinculoQuePagaElCliente() {
        var sol = solicitudPendiente(7L, FIRMA);

        service.responderSolicitud(FIRMA, 7L, true, null, ACTOR);

        assertEquals("ACEPTADA", sol.getEstado());
        ArgumentCaptor<EmpresaFirmaClienteEntity> c = ArgumentCaptor.forClass(EmpresaFirmaClienteEntity.class);
        verify(vinculoRepo).saveAndFlush(c.capture());
        assertEquals("CLIENTE_PAGA", c.getValue().getModalidad());
        assertEquals(FIRMA, c.getValue().getFirmaEmpresaId());
    }

    @Test
    void rechazarNoCreaVinculoYGuardaElMotivo() {
        var sol = solicitudPendiente(7L, FIRMA);

        service.responderSolicitud(FIRMA, 7L, false, "  Sin cupo  ", ACTOR);

        assertEquals("RECHAZADA", sol.getEstado());
        assertEquals("Sin cupo", sol.getMotivo());
        verify(vinculoRepo, never()).saveAndFlush(any());
    }

    @Test
    void unaFirmaNoRespondeSolicitudesDeOtra() {
        solicitudPendiente(7L, FIRMA);
        assertThrows(GlobalException.class, () -> service.responderSolicitud(OTRA_FIRMA, 7L, true, null, ACTOR));
    }
}
