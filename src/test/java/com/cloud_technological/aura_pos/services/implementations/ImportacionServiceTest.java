package com.cloud_technological.aura_pos.services.implementations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.FilaCuenta;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.PlanCuentas;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.Resultado;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;
import com.cloud_technological.aura_pos.repositories.importacion.ImportacionQueryRepository;
import com.cloud_technological.aura_pos.repositories.importacion.ImportacionQueryRepository.Cuenta;
import com.cloud_technological.aura_pos.repositories.importacion.ImportacionQueryRepository.Municipio;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.services.AperturaContableService;
import com.cloud_technological.aura_pos.services.ConfiguracionContableService;
import com.cloud_technological.aura_pos.services.CuentaCobrarService;
import com.cloud_technological.aura_pos.services.CuentaPagarService;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class ImportacionServiceTest {

    @Mock private ImportacionQueryRepository queryRepo;
    @Mock private PlanCuentaJPARepository planRepo;
    @Mock private TerceroJPARepository terceroRepo;
    @Mock private AsientoContableJPARepository asientoRepo;
    @Mock private AperturaContableService aperturaService;
    @Mock private ConfiguracionContableService config;
    @Mock private CuentaCobrarService cuentaCobrarService;
    @Mock private CuentaPagarService cuentaPagarService;
    @Mock private EntityManager em;
    private ImportacionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ImportacionServiceImpl(queryRepo, planRepo, terceroRepo, asientoRepo,
                aperturaService, config, cuentaCobrarService, cuentaPagarService, em);
    }

    @Test
    void nivelYPadreSegunElPuc() {
        assertEquals(1, ImportacionServiceImpl.nivel("1"));
        assertEquals(2, ImportacionServiceImpl.nivel("11"));
        assertEquals(3, ImportacionServiceImpl.nivel("1105"));
        assertEquals(4, ImportacionServiceImpl.nivel("110505"));
        assertEquals(5, ImportacionServiceImpl.nivel("11050501"));
        assertNull(ImportacionServiceImpl.codigoPadre("1"));
        assertEquals("1", ImportacionServiceImpl.codigoPadre("11"));
        assertEquals("11", ImportacionServiceImpl.codigoPadre("1105"));
        assertEquals("1105", ImportacionServiceImpl.codigoPadre("110505"));
        assertEquals("CREDITO", ImportacionServiceImpl.naturalezaPorClase("2205"));
        assertEquals("ACTIVO", ImportacionServiceImpl.tipoPorClase("1435"));
        assertEquals("COSTO", ImportacionServiceImpl.tipoPorClase("7105"));
    }

    @Test
    void documentoSeLimpiaDePuntosYDigitoDeVerificacion() {
        assertEquals("900123456", ImportacionServiceImpl.limpiarDocumento("900.123.456-7"));
        assertEquals("1098765432", ImportacionServiceImpl.limpiarDocumento(" 1.098.765.432 "));
        assertEquals("", ImportacionServiceImpl.limpiarDocumento(null));
    }

    @Test
    void municipioPorCodigoONombreYAmbiguoSePideCodigo() {
        var m = new ImportacionServiceImpl.Municipios(List.of(
                new Municipio(980L, "68679", "San Gil"),
                new Municipio(1L, "05001", "Medellín"),
                new Municipio(2L, "05756", "La Unión"),
                new Municipio(3L, "52399", "La Unión")));
        assertEquals(980L, m.buscar("68679").id());
        assertEquals(1L, m.buscar("5001").id());       // Excel se come el cero inicial
        assertEquals(1L, m.buscar("medellin").id());   // sin tilde ni mayúscula
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> m.buscar("La Union"));
        assertTrue(ex.getMessage().contains("código DANE"));
    }

    private static FilaCuenta fila(int n, String codigo, String nombre) {
        FilaCuenta f = new FilaCuenta();
        f.setFila(n);
        f.setCodigo(codigo);
        f.setNombre(nombre);
        return f;
    }

    @Test
    void planValidaPadresExistentesYEnElMismoArchivo() {
        when(queryRepo.cuentas(1)).thenReturn(Map.of(
                "1", new Cuenta(1L, "1", false, true, false),
                "11", new Cuenta(2L, "11", false, true, false),
                "1105", new Cuenta(3L, "1105", false, true, false)));
        PlanCuentas req = new PlanCuentas();
        req.setFilas(List.of(
                fila(2, "11050501", "Caja sede norte"), // su padre viene en la fila 3
                fila(3, "110505", "Caja general"),
                fila(4, "1105", "Caja"),                 // ya existe
                fila(5, "9999", "Sin padre"),
                fila(6, "11AB", "Letras")));

        Resultado r = service.validarPlan(1, req);

        assertEquals(2, r.getNuevos());
        assertEquals(1, r.getExistentes());
        assertEquals(2, r.getErrores());
        assertTrue(r.getFilas().stream().anyMatch(x -> x.getFila() == 5 && x.getMensaje().contains("padre 99")));
    }
}
