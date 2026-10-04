package com.cloud_technological.aura_pos.services.permisos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cloud_technological.aura_pos.dto.permisos.PermisosUsuarioDto;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository.AccionEspecial;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository.LimitesPerfil;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository.LimitesUsuario;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository.Perfil;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository.SubmoduloHabilitado;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository.UsuarioPerfil;
import com.cloud_technological.aura_pos.utils.GlobalException;

/** Segunda etapa de permisos (V192): acciones especiales, límites y sedes. */
class PermisoEspecialesTest {

    private static final int EMPRESA = 7;
    private static final String REABRIR = "contabilidad.periodos-contables:REABRIR";
    private static final String AUTORIZAR = "ventas.ventas:AUTORIZAR_DESCUENTO";

    private PermisoUsuarioQueryRepository query;
    private PermisoUsuarioService service;

    @BeforeEach
    void setUp() {
        query = mock(PermisoUsuarioQueryRepository.class);
        service = new PermisoUsuarioService(query, mock(PerfilesSistemaService.class));
        when(query.habilitadosEmpresa(EMPRESA)).thenReturn(List.of(
                new SubmoduloHabilitado(1L, "contabilidad.periodos-contables"),
                new SubmoduloHabilitado(2L, "ventas.ventas")));
        // REABRIR hereda de EDITAR; AUTORIZAR_DESCUENTO no hereda (solo si se da).
        when(query.especialesEmpresa(EMPRESA)).thenReturn(List.of(
                new AccionEspecial(100L, 1L, "contabilidad.periodos-contables", "REABRIR", "Reabrir", null, "EDITAR"),
                new AccionEspecial(200L, 2L, "ventas.ventas", "AUTORIZAR_DESCUENTO", "Autorizar", null, null)));
        when(query.excepcionesUsuario(any())).thenReturn(Map.of());
        when(query.especialesUsuario(any())).thenReturn(Map.of());
        when(query.especialesPerfil(any())).thenReturn(Map.of());
        when(query.sucursalesUsuario(any())).thenReturn(List.of(3L));
    }

    private void usuario(int id, Long perfilId) {
        when(query.usuario(id)).thenReturn(new UsuarioPerfil(id, EMPRESA, "CAJERO", perfilId));
    }

    private void perfil(long id, boolean total) {
        when(query.perfil(id)).thenReturn(new Perfil(id, EMPRESA, "P" + id, total, true));
    }

    /** ver/editar en períodos y ver en ventas. */
    private void perfilConEditarPeriodos(long id) {
        perfil(id, false);
        when(query.permisosPerfil(id)).thenReturn(Map.of(
                1L, new Boolean[] { true, false, true, false },
                2L, new Boolean[] { true, false, false, false }));
    }

    @Test
    void laEspecialHeredaDeSuAccionBase() {
        usuario(1, 10L);
        perfilConEditarPeriodos(10L);

        PermisosUsuarioDto p = service.efectivos(1);

        assertTrue(p.getEspeciales().contains(REABRIR), "quien edita períodos, reabre (día 1 igual)");
        assertFalse(p.getEspeciales().contains(AUTORIZAR), "autorizar descuentos no se hereda");
    }

    @Test
    void sinLaAccionBaseNoHereda() {
        usuario(1, 10L);
        perfil(10L, false);
        when(query.permisosPerfil(10L)).thenReturn(Map.of(1L, new Boolean[] { true, false, false, false }));

        assertFalse(service.efectivos(1).getEspeciales().contains(REABRIR));
    }

    @Test
    void elPerfilLaQuitaExplicitamente() {
        usuario(1, 10L);
        perfilConEditarPeriodos(10L);
        when(query.especialesPerfil(10L)).thenReturn(Map.of(100L, false, 200L, true));

        PermisosUsuarioDto p = service.efectivos(1);

        assertFalse(p.getEspeciales().contains(REABRIR));
        assertTrue(p.getEspeciales().contains(AUTORIZAR));
    }

    @Test
    void laExcepcionDelUsuarioMandaSobreElPerfil() {
        usuario(1, 10L);
        perfilConEditarPeriodos(10L);
        when(query.especialesUsuario(1)).thenReturn(Map.of(100L, false));

        assertFalse(service.efectivos(1).getEspeciales().contains(REABRIR));
    }

    @Test
    void sinVerElSubmoduloNoHayEspecial() {
        usuario(1, 10L);
        perfil(10L, false);
        when(query.permisosPerfil(10L)).thenReturn(Map.of());
        when(query.especialesPerfil(10L)).thenReturn(Map.of(200L, true));

        assertFalse(service.efectivos(1).getEspeciales().contains(AUTORIZAR));
    }

    @Test
    void accesoTotalTieneTodasLasEspecialesSinLimitesYTodasLasSedes() {
        usuario(1, 10L);
        perfil(10L, true);
        when(query.limitesPerfil(10L)).thenReturn(new LimitesPerfil(new BigDecimal("5"), null, false));

        PermisosUsuarioDto p = service.efectivos(1);

        assertTrue(p.getEspeciales().containsAll(List.of(REABRIR, AUTORIZAR)));
        assertNull(p.getDescuentoMaxPct());
        assertTrue(p.isTodasLasSedes());
        assertTrue(service.puedeEspecial(1, "contabilidad.*:REABRIR"));
    }

    @Test
    void elLimiteDelUsuarioReemplazaAlDelPerfil() {
        usuario(1, 10L);
        perfilConEditarPeriodos(10L);
        when(query.limitesPerfil(10L)).thenReturn(new LimitesPerfil(new BigDecimal("10"), new BigDecimal("3"), true));
        when(query.limitesUsuario(1)).thenReturn(new LimitesUsuario(new BigDecimal("15"), null));

        PermisosUsuarioDto p = service.efectivos(1);

        assertEquals(0, new BigDecimal("15").compareTo(p.getDescuentoMaxPct()));
        assertEquals(0, new BigDecimal("3").compareTo(p.getRebajaPrecioMaxPct()));
    }

    @Test
    void soloSusSedes() {
        usuario(1, 10L);
        perfilConEditarPeriodos(10L);
        when(query.limitesPerfil(10L)).thenReturn(new LimitesPerfil(null, null, false));

        assertFalse(service.efectivos(1).isTodasLasSedes());
        assertEquals(3L, service.sedePermitida(1, null, 3L), "sin pedir: la de su sesión");
        assertEquals(3L, service.sedePermitida(1, 3L, 3L));
        assertThrows(GlobalException.class, () -> service.sedePermitida(1, 9L, 3L));
    }

    @Test
    void conTodasLasSedesPasaLoQuePida() {
        usuario(1, 10L);
        perfilConEditarPeriodos(10L);
        when(query.limitesPerfil(10L)).thenReturn(new LimitesPerfil(null, null, true));

        assertNull(service.sedePermitida(1, null, 3L), "null = todas");
        assertEquals(9L, service.sedePermitida(1, 9L, 3L));
    }
}
