package com.cloud_technological.aura_pos.services.firma_contable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.cloud_technological.aura_pos.dto.firma_contable.CrearGestionadoDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.firma_contable.FirmaContableQueryRepository;
import com.cloud_technological.aura_pos.repositories.permisos.PerfilJPARepository;
import com.cloud_technological.aura_pos.repositories.permisos.PerfilPermisoJPARepository;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.UsuarioSucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.BodegaService;
import com.cloud_technological.aura_pos.services.PermisoService;
import com.cloud_technological.aura_pos.services.empresa.ConfiguracionEmpresaService;
import com.cloud_technological.aura_pos.services.permisos.BitacoraService;
import com.cloud_technological.aura_pos.services.permisos.PerfilesSistemaService;
import com.cloud_technological.aura_pos.utils.GlobalException;

/** Clientes gestionados que crea una firma (docs/PLAN_FIRMA_CONTABLE.md §9). */
class GestionadoServiceTest {

    private static final int FIRMA = 20;

    private EmpresaJPARepository empresaRepo;
    private FirmaContableQueryRepository query;
    private FirmaContableService firmaContable;
    private UsuarioJPARepository usuarioRepo;
    private GestionadoService service;

    @BeforeEach
    void setUp() {
        empresaRepo = mock(EmpresaJPARepository.class);
        query = mock(FirmaContableQueryRepository.class);
        firmaContable = mock(FirmaContableService.class);
        usuarioRepo = mock(UsuarioJPARepository.class);
        SucursalJPARepository sucursalRepo = mock(SucursalJPARepository.class);
        ConfiguracionEmpresaService configuracion = mock(ConfiguracionEmpresaService.class);
        when(configuracion.submodulosPorPatron(any())).thenReturn(Set.of(1, 2));
        when(sucursalRepo.save(any())).thenAnswer(i -> i.getArgument(0, SucursalEntity.class));
        when(empresaRepo.save(any())).thenAnswer(i -> {
            EmpresaEntity e = i.getArgument(0);
            if (e.getId() == null) e.setId(50);
            return e;
        });
        service = new GestionadoService(empresaRepo, sucursalRepo, mock(BodegaService.class),
                mock(TerceroJPARepository.class), usuarioRepo, mock(UsuarioSucursalJPARepository.class),
                mock(PasswordEncoder.class), mock(PermisoService.class), configuracion,
                mock(PerfilesSistemaService.class), mock(PerfilJPARepository.class),
                mock(PerfilPermisoJPARepository.class), mock(PermisoUsuarioQueryRepository.class), firmaContable, query,
                mock(BitacoraService.class));

        EmpresaEntity firma = EmpresaEntity.builder().id(FIRMA).razonSocial("Firma").nit("900")
                .esFirmaContable(true).activa(true).cupoGestionados(2).build();
        when(empresaRepo.findById(FIRMA)).thenReturn(Optional.of(firma));
    }

    private CrearGestionadoDto dto(String... paquetes) {
        CrearGestionadoDto d = new CrearGestionadoDto();
        d.setRazonSocial("Cliente SAS");
        d.setNit("901234567");
        d.setPaquetes(List.of(paquetes));
        return d;
    }

    @Test
    void creaElClienteYLoVinculaComoGestionado() {
        service.crear(FIRMA, dto("NOMINA"), 1);
        verify(firmaContable).vincular(eq(50), eq(FIRMA), eq(1), eq("GESTIONADO"));
    }

    @Test
    void conElCupoLlenoNoCrea() {
        when(query.gestionadosActivos(FIRMA)).thenReturn(2);
        assertThrows(GlobalException.class, () -> service.crear(FIRMA, dto(), 1));
        verify(empresaRepo, never()).save(any());
    }

    @Test
    void unNitQueYaExisteNoSeCrea() {
        when(empresaRepo.existsByNit("901234567")).thenReturn(true);
        assertThrows(GlobalException.class, () -> service.crear(FIRMA, dto(), 1));
        verify(empresaRepo, never()).save(any());
    }

    @Test
    void elPuntoDeVentaNoEsUnPaquetePermitido() {
        assertThrows(GlobalException.class, () -> service.crear(FIRMA, dto("POS"), 1));
        verify(firmaContable, never()).vincular(anyInt(), anyInt(), any(), any());
    }

    @Test
    void unaEmpresaQueNoEsFirmaNoCreaGestionados() {
        EmpresaEntity normal = EmpresaEntity.builder().id(30).razonSocial("Normal").nit("800")
                .esFirmaContable(false).activa(true).build();
        when(empresaRepo.findById(30)).thenReturn(Optional.of(normal));
        assertThrows(GlobalException.class, () -> service.crear(30, dto(), 1));
    }

    @Test
    void losPaquetesNuncaTraenPosCajaNiInventario() {
        List<String> todas = PaqueteGestionado.todasLasClaves();
        assertFalse(todas.stream().anyMatch(c -> c.startsWith("principal.punto-de-venta") || c.startsWith("inventario.")
                || c.equals("caja.*") || c.equals("caja.turnos") || c.equals("caja.cajas") || c.startsWith("vendedores.")));
        assertTrue(todas.contains("recursos-humanos.*"));
        assertTrue(todas.contains("contabilidad.*"));
    }

    @Test
    void contabilidadVaSiempre() {
        assertTrue(PaqueteGestionado.claves(List.of(PaqueteGestionado.NOMINA)).contains("contabilidad.*"));
    }
}
