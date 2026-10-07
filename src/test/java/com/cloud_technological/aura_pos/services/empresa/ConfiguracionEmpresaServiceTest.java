package com.cloud_technological.aura_pos.services.empresa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.cloud_technological.aura_pos.dto.empresas.ConfiguracionEmpresaDtos.ActualizarConfiguracionDto;
import com.cloud_technological.aura_pos.dto.empresas.ConfiguracionEmpresaDtos.ConfiguracionEmpresaDto;
import com.cloud_technological.aura_pos.dto.empresas.ConfiguracionEmpresaDtos.LineaUsoDto;
import com.cloud_technological.aura_pos.dto.permisos.ModuloPermisoDto;
import com.cloud_technological.aura_pos.dto.permisos.SubmoduloPermisoDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.platform.ModuloQueryRepository;
import com.cloud_technological.aura_pos.services.PermisoService;
import com.cloud_technological.aura_pos.services.permisos.BitacoraService;
import com.cloud_technological.aura_pos.utils.GlobalException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConfiguracionEmpresaServiceTest {

    @Mock EmpresaJPARepository empresaRepo;
    @Mock ModuloQueryRepository moduloQuery;
    @Mock PermisoService permisoService;
    @Mock ArranqueEmpresaService arranque;
    @Mock BitacoraService bitacora;

    ConfiguracionEmpresaService service;
    EmpresaEntity empresa;

    @BeforeEach
    void setUp() {
        service = new ConfiguracionEmpresaService(empresaRepo, moduloQuery, permisoService, arranque, bitacora);
        empresa = EmpresaEntity.builder().id(7).nit("900").razonSocial("X").build();
        when(empresaRepo.findById(7)).thenReturn(Optional.of(empresa));
    }

    // Catálogo con códigos "sucios" como en producción (Caja, Recursos Humanos).
    private static List<ModuloPermisoDto> catalogo(boolean contabilidadActiva, boolean rrhhActivo) {
        List<ModuloPermisoDto> out = new ArrayList<>();
        out.add(modulo("principal", sub(1, "dashboard", "Dashboard", true), sub(2, "punto-de-venta", "POS", false)));
        out.add(modulo("Caja", sub(10, "usuarios", "Usuarios", true), sub(11, "cajas", "Cajas", false)));
        out.add(modulo("contabilidad", sub(20, "plan-de-cuentas", "Plan de cuentas", contabilidadActiva),
                sub(21, "notas-contables", "Notas contables", contabilidadActiva)));
        out.add(modulo("Recursos Humanos", sub(30, "empleados", "Empleados", rrhhActivo),
                sub(31, "liquidacion-nomina", "Liquidación de nómina", rrhhActivo)));
        out.add(modulo("inventario", sub(40, "stock", "Stock", false)));
        return out;
    }

    private static ModuloPermisoDto modulo(String codigo, SubmoduloPermisoDto... subs) {
        return ModuloPermisoDto.builder().moduloCodigo(codigo).submodulos(new ArrayList<>(List.of(subs))).build();
    }

    private static SubmoduloPermisoDto sub(int id, String codigo, String nombre, boolean activo) {
        return SubmoduloPermisoDto.builder().submoduloId(id).submoduloCodigo(codigo).submoduloNombre(nombre)
                .activo(activo).build();
    }

    @Test
    void sinConfiguracionSeAsumePosYNoEstaDeclarada() {
        assertEquals(List.of(LineaUso.POS), service.lineas(empresa));
        assertEquals(LineaUso.POS, service.inicio(empresa));
        ConfiguracionEmpresaDto d = service.obtener(7);
        assertFalse(d.isDeclarada());
        assertTrue(d.isArranquePendiente());
        assertEquals("PENDIENTE", d.getArranque().get(0).getResultado());
    }

    @Test
    void jsonDanadoOAjenoNoRompeYSeConservanClavesAjenas() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("otraCosa", 123);
        cfg.put("lineas", List.of("nomina", "INVENTADA", "contabilidad", "NOMINA"));
        empresa.setConfiguracion(cfg);
        assertEquals(List.of(LineaUso.CONTABILIDAD, LineaUso.NOMINA), service.lineas(empresa));

        empresa.setConfiguracion("{esto no es json");
        assertEquals(List.of(LineaUso.POS), service.lineas(empresa));
    }

    @Test
    void inicioPreferidoSoloSiSigueEntreLasLineas() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("lineas", List.of("CONTABILIDAD", "NOMINA"));
        cfg.put("inicio", "NOMINA");
        empresa.setConfiguracion(cfg);
        assertEquals(LineaUso.NOMINA, service.inicio(empresa));

        cfg.put("inicio", "POS");
        empresa.setConfiguracion(cfg);
        assertEquals(LineaUso.CONTABILIDAD, service.inicio(empresa));
    }

    @Test
    void plantillaNormalizaCodigosDeModulo() {
        when(moduloQuery.listarPermisosPorEmpresa(null)).thenReturn(catalogo(false, false));
        Set<Integer> nomina = service.plantilla(List.of(LineaUso.NOMINA));
        // base (dashboard, caja.usuarios) + todo "Recursos Humanos"
        assertEquals(Set.of(1, 10, 30, 31), nomina);

        LineaUsoDto conta = service.catalogo().stream().filter(l -> l.getCodigo().equals("CONTABILIDAD"))
                .findFirst().orElseThrow();
        assertTrue(conta.getSubmodulos().containsAll(List.of(1, 10, 20, 21)));
        assertFalse(conta.getSubmodulos().contains(40));
        assertEquals(List.of(20, 21), conta.getMinimos());
    }

    @Test
    void codigoDeLineaDesconocidoEsError() {
        assertThrows(GlobalException.class, () -> ConfiguracionEmpresaService.parsear(List.of("CONTA")));
        assertEquals(List.of(LineaUso.CONTABILIDAD, LineaUso.NOMINA),
                ConfiguracionEmpresaService.parsear(List.of("nomina", "CONTABILIDAD", "nomina")));
    }

    @Test
    void actualizarExigePantallasMinimas() {
        when(moduloQuery.listarPermisosPorEmpresa(7)).thenReturn(catalogo(false, false));
        ActualizarConfiguracionDto dto = new ActualizarConfiguracionDto();
        dto.setLineas(List.of("CONTABILIDAD"));
        GlobalException ex = assertThrows(GlobalException.class, () -> service.actualizar(7, dto));
        assertTrue(ex.getMessage().contains("Plan de cuentas"), ex.getMessage());
        verify(empresaRepo, never()).save(any());
    }

    @Test
    void actualizarGuardaBitacoraYArrancaSoloLoPendiente() {
        when(moduloQuery.listarPermisosPorEmpresa(7)).thenReturn(catalogo(true, true));
        Map<String, Object> cfg = new LinkedHashMap<>();
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("resultado", "OK");
        cfg.put("arranque", Map.of("CONTABLE", ok));
        empresa.setConfiguracion(cfg);

        ActualizarConfiguracionDto dto = new ActualizarConfiguracionDto();
        dto.setLineas(List.of("NOMINA", "CONTABILIDAD"));
        dto.setInicio("NOMINA");
        ConfiguracionEmpresaDto d = service.actualizar(7, dto);

        // Hibernate mapea la columna como texto JSON: un Map revienta al confirmar.
        assertTrue(empresa.getConfiguracion() instanceof String, "se guarda como texto JSON");
        assertTrue(((String) empresa.getConfiguracion()).contains("\"lineas\":[\"CONTABILIDAD\",\"NOMINA\"]"));
        assertEquals(List.of("CONTABILIDAD", "NOMINA"), d.getLineas());
        assertEquals("NOMINA", d.getInicioResuelto());
        assertTrue(d.isDeclarada());
        assertFalse(d.isArranquePendiente());
        verify(bitacora).registrarEnEmpresa(eq(7), eq("plataforma.empresas"), eq("CONFIGURAR"), eq("empresa"),
                eq(7), any(), any(), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<PasoArranque>> pasos = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(arranque).programar(eq(7), pasos.capture());
        assertTrue(pasos.getValue().isEmpty(), "CONTABLE ya estaba OK: no se repite");
    }

    @Test
    void inicioQueNoEsDeLasLineasEsError() {
        when(moduloQuery.listarPermisosPorEmpresa(7)).thenReturn(catalogo(true, true));
        ActualizarConfiguracionDto dto = new ActualizarConfiguracionDto();
        dto.setLineas(List.of("CONTABILIDAD"));
        dto.setInicio("NOMINA");
        assertThrows(GlobalException.class, () -> service.actualizar(7, dto));
    }

    @Test
    void inicializarSinLineasNoDeclaraPeroArrancaContable() {
        service.inicializar(empresa, List.of());
        verify(empresaRepo, never()).save(any());
        verify(arranque).programar(eq(7), eq(Set.of(PasoArranque.CONTABLE)));
        verify(moduloQuery, never()).listarPermisosPorEmpresa(isNull());
        verify(permisoService, never()).activarSubmodulos(any(), anyCollection());
    }
}
