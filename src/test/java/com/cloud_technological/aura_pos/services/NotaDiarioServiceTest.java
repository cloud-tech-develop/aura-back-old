package com.cloud_technological.aura_pos.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.SaveNotaDiarioDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.SaveNotaDiarioLineaDto;
import com.cloud_technological.aura_pos.entity.AsientoContableEntity;
import com.cloud_technological.aura_pos.entity.AsientoDetalleEntity;
import com.cloud_technological.aura_pos.entity.PeriodoContableEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableQueryRepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.NotaDiarioQueryRepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.NotaDiarioQueryRepository.CuentaMovimiento;
import com.cloud_technological.aura_pos.repositories.contabilidad.NotaDiarioPlantillaJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.NotaDiarioSoporteJPARepository;
import com.cloud_technological.aura_pos.services.implementations.NotaDiarioLineasBuilder;
import com.cloud_technological.aura_pos.services.implementations.NotaDiarioServiceImpl;
import com.cloud_technological.aura_pos.services.implementations.R2StorageService;
import com.cloud_technological.aura_pos.services.implementations.PeriodoContableResolver;
import com.cloud_technological.aura_pos.utils.GlobalException;

/**
 * La nota contable nace en borrador (sin consecutivo, puede estar descuadrada)
 * y solo al contabilizarse recibe su CD-######, tras exigir cuadre, cuentas
 * auxiliares activas y período abierto.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotaDiarioServiceTest {

    private static final Integer EMPRESA = 1;
    private static final Integer USUARIO = 7;
    private static final Long CAJA = 10L;
    private static final Long GASTO = 20L;
    private static final Long MAYOR = 30L;

    @Mock private AsientoContableJPARepository repo;
    @Mock private AsientoContableQueryRepository asientoQueryRepo;
    @Mock private NotaDiarioQueryRepository queryRepo;
    @Mock private PeriodoContableResolver periodoResolver;
    @Mock private AsientoContableService asientoService;
    @Mock private NotaDiarioSoporteJPARepository soporteRepo;
    @Mock private NotaDiarioPlantillaJPARepository plantillaRepo;
    @Mock private R2StorageService storage;

    private NotaDiarioServiceImpl service;
    private long siguienteId;

    @BeforeEach
    void setUp() {
        // El builder de líneas va real (sobre el repositorio simulado): sus
        // validaciones son parte de lo que se prueba.
        service = new NotaDiarioServiceImpl(repo, asientoQueryRepo, queryRepo, periodoResolver, asientoService,
                new NotaDiarioLineasBuilder(queryRepo), soporteRepo, plantillaRepo, storage);
        siguienteId = 99L;
        PeriodoContableEntity periodo = new PeriodoContableEntity();
        periodo.setId(5L);
        when(periodoResolver.resolver(eq(EMPRESA), any())).thenReturn(periodo);
        when(queryRepo.cuentas(eq(EMPRESA), anyCollection())).thenReturn(Map.of(
                CAJA, new CuentaMovimiento(CAJA, "110505", "Caja general", true, true),
                GASTO, new CuentaMovimiento(GASTO, "519530", "Útiles", true, true),
                MAYOR, new CuentaMovimiento(MAYOR, "5195", "Diversos", true, false)));
        when(repo.saveAndFlush(any())).thenAnswer(inv -> {
            AsientoContableEntity a = inv.getArgument(0);
            if (a.getId() == null) a.setId(siguienteId++);
            return a;
        });
        NotaDiarioDto dto = new NotaDiarioDto();
        when(queryRepo.obtener(anyLong(), anyInt())).thenReturn(dto);
        when(asientoQueryRepo.siguienteNumeroComprobante(EMPRESA, "CD")).thenReturn("CD-000042");
    }

    @Test
    @DisplayName("El borrador descuadrado se guarda sin consecutivo")
    void borradorDescuadradoSeGuarda() {
        service.crear(EMPRESA, USUARIO, nota(false, linea(GASTO, 1000, 0), linea(CAJA, 0, 400)));

        AsientoContableEntity guardada = capturada();
        assertEquals("BORRADOR", guardada.getEstado());
        assertEquals("CD", guardada.getTipoComprobante());
        assertNull(guardada.getNumeroComprobante());
        assertEquals(0, new BigDecimal("1000").compareTo(guardada.getTotalDebito()));
        verify(asientoQueryRepo, never()).siguienteNumeroComprobante(anyInt(), any());
    }

    @Test
    @DisplayName("Contabilizar exige cuadre")
    void contabilizarDescuadradaFalla() {
        assertThrows(ResponseStatusException.class, () ->
                service.crear(EMPRESA, USUARIO, nota(true, linea(GASTO, 1000, 0), linea(CAJA, 0, 400))));
    }

    @Test
    @DisplayName("Contabilizar asigna consecutivo y deja quién aprobó")
    void contabilizarAsignaConsecutivo() {
        service.crear(EMPRESA, USUARIO, nota(true, linea(GASTO, 1000, 0), linea(CAJA, 0, 1000)));

        AsientoContableEntity guardada = capturada();
        assertEquals("CONTABILIZADO", guardada.getEstado());
        assertEquals("CD-000042", guardada.getNumeroComprobante());
        assertEquals(USUARIO, guardada.getContabilizadoPor());
        assertTrue(guardada.getContabilizadoAt() != null);
        verify(queryRepo).bloquearSerie(EMPRESA, "CD");
    }

    @Test
    @DisplayName("Una cuenta de mayor no admite movimiento")
    void cuentaNoAuxiliarFalla() {
        GlobalException ex = assertThrows(GlobalException.class, () ->
                service.crear(EMPRESA, USUARIO, nota(false, linea(MAYOR, 1000, 0), linea(CAJA, 0, 1000))));
        assertTrue(ex.getMessage().contains("no es de movimiento"));
    }

    @Test
    @DisplayName("Una línea no puede llevar débito y crédito a la vez")
    void debitoYCreditoEnLaMismaLineaFalla() {
        assertThrows(GlobalException.class, () ->
                service.crear(EMPRESA, USUARIO, nota(false, linea(GASTO, 1000, 1000))));
    }

    @Test
    @DisplayName("Una nota contabilizada no se edita ni se elimina")
    void contabilizadaEsInmutable() {
        AsientoContableEntity existente = existente("CONTABILIZADO");
        when(repo.findByIdAndEmpresaId(99L, EMPRESA)).thenReturn(Optional.of(existente));

        GlobalException editar = assertThrows(GlobalException.class, () ->
                service.actualizar(99L, EMPRESA, USUARIO, nota(false, linea(GASTO, 1, 0), linea(CAJA, 0, 1))));
        assertEquals(HttpStatus.CONFLICT, editar.getStatus());
        assertThrows(GlobalException.class, () -> service.eliminar(99L, EMPRESA));
        verify(repo, never()).delete(any());
    }

    @Test
    @DisplayName("Un borrador no se anula: se elimina")
    void borradorNoSeAnula() {
        when(repo.findByIdAndEmpresaId(99L, EMPRESA)).thenReturn(Optional.of(existente("BORRADOR")));

        assertThrows(GlobalException.class, () -> service.anular(99L, EMPRESA, USUARIO, "error de digitación"));
        verify(asientoService, never()).anular(anyLong(), anyInt());
    }

    @Test
    @DisplayName("Anular deja motivo y usuario")
    void anularDejaTraza() {
        AsientoContableEntity existente = existente("CONTABILIZADO");
        when(repo.findByIdAndEmpresaId(99L, EMPRESA)).thenReturn(Optional.of(existente));

        service.anular(99L, EMPRESA, USUARIO, "  valor mal digitado  ");

        verify(asientoService).anular(99L, EMPRESA);
        assertEquals("ANULADO", existente.getEstado());
        assertEquals("valor mal digitado", existente.getMotivoAnulacion());
        assertEquals(USUARIO, existente.getAnuladoPor());
    }

    // ── N3: reversión ────────────────────────────────────────────────────

    @Test
    @DisplayName("Reversar crea la nota inversa contabilizada y enlaza las dos")
    void reversarCreaInversa() {
        AsientoContableEntity original = contabilizada(LocalDate.of(2026, 9, 10));
        when(repo.findByIdAndEmpresaId(50L, EMPRESA)).thenReturn(Optional.of(original));

        service.reversar(50L, EMPRESA, USUARIO, LocalDate.of(2026, 10, 1), null);

        org.mockito.ArgumentCaptor<AsientoContableEntity> captor =
                org.mockito.ArgumentCaptor.forClass(AsientoContableEntity.class);
        verify(repo, org.mockito.Mockito.atLeastOnce()).saveAndFlush(captor.capture());
        AsientoContableEntity rev = captor.getAllValues().stream()
                .filter(a -> Long.valueOf(50L).equals(a.getReversaDeId())).findFirst().orElseThrow();

        assertEquals("CONTABILIZADO", rev.getEstado());
        assertEquals(LocalDate.of(2026, 10, 1), rev.getFecha());
        // Débito y crédito intercambiados, mismas cuentas.
        assertEquals(GASTO, rev.getDetalles().get(0).getCuentaId());
        assertEquals(0, new BigDecimal("1000").compareTo(rev.getDetalles().get(0).getCredito()));
        assertEquals(0, new BigDecimal("1000").compareTo(rev.getDetalles().get(1).getDebito()));
        // La original sigue contabilizada: netean en los informes.
        assertEquals("CONTABILIZADO", original.getEstado());
        assertEquals(rev.getId(), original.getRevertidoPorId());
    }

    @Test
    @DisplayName("No se reversa con fecha anterior a la nota ni dos veces")
    void reversarReglas() {
        AsientoContableEntity original = contabilizada(LocalDate.of(2026, 9, 10));
        when(repo.findByIdAndEmpresaId(50L, EMPRESA)).thenReturn(Optional.of(original));

        assertThrows(GlobalException.class,
                () -> service.reversar(50L, EMPRESA, USUARIO, LocalDate.of(2026, 9, 1), null));

        original.setRevertidoPorId(77L);
        GlobalException dos = assertThrows(GlobalException.class,
                () -> service.reversar(50L, EMPRESA, USUARIO, LocalDate.of(2026, 10, 1), null));
        assertEquals(HttpStatus.CONFLICT, dos.getStatus());
    }

    @Test
    @DisplayName("Una nota reversada no se anula antes que su reversión")
    void anularReversadaBloquea() {
        AsientoContableEntity original = contabilizada(LocalDate.of(2026, 9, 10));
        original.setRevertidoPorId(77L);
        when(repo.findByIdAndEmpresaId(50L, EMPRESA)).thenReturn(Optional.of(original));

        assertThrows(GlobalException.class, () -> service.anular(50L, EMPRESA, USUARIO, "no aplica ya"));
        verify(asientoService, never()).anular(anyLong(), anyInt());
    }

    @Test
    @DisplayName("Con reversión automática, contabilizar deja también la inversa el día 1 del mes siguiente")
    void reversionAutomatica() {
        SaveNotaDiarioDto dto = nota(true, linea(GASTO, 1000, 0), linea(CAJA, 0, 1000));
        dto.setFecha(LocalDate.of(2026, 9, 30));
        dto.setReversionAutomatica(true);
        service.crear(EMPRESA, USUARIO, dto);

        org.mockito.ArgumentCaptor<AsientoContableEntity> captor =
                org.mockito.ArgumentCaptor.forClass(AsientoContableEntity.class);
        verify(repo, org.mockito.Mockito.atLeastOnce()).saveAndFlush(captor.capture());
        AsientoContableEntity rev = captor.getAllValues().stream()
                .filter(a -> a.getReversaDeId() != null).findFirst().orElseThrow();
        assertEquals(LocalDate.of(2026, 10, 1), rev.getFecha());
        assertEquals("CONTABILIZADO", rev.getEstado());
    }

    @Test
    @DisplayName("Una clasificación desconocida se rechaza")
    void clasificacionInvalida() {
        SaveNotaDiarioDto dto = nota(false, linea(GASTO, 1000, 0), linea(CAJA, 0, 1000));
        dto.setClasificacion("INVENTADA");
        assertThrows(GlobalException.class, () -> service.crear(EMPRESA, USUARIO, dto));
    }

    // ── N5: importar desde Excel ─────────────────────────────────────────

    @Test
    @DisplayName("Importar resuelve por código y explica las filas malas")
    void importarLineas() {
        when(queryRepo.cuentasPorCodigo(eq(EMPRESA), anyCollection())).thenReturn(Map.of(
                "519530", new NotaDiarioQueryRepository.Resuelto(GASTO, "519530 - Útiles", true, null),
                "5195", new NotaDiarioQueryRepository.Resuelto(MAYOR, "5195 - Diversos", false, "no es de movimiento")));
        when(queryRepo.tercerosPorDocumento(eq(EMPRESA), anyCollection())).thenReturn(Map.of(
                "900123456", new NotaDiarioQueryRepository.Resuelto(3L, "900123456 — Proveedor", true, null)));
        when(queryRepo.centrosCostoPorCodigo(eq(EMPRESA), anyCollection())).thenReturn(Map.of());

        String texto = "Cuenta\tTercero\tCC\tDescripción\tDébito\tCrédito\n"
                + "519530\t900.123.456-7\t\tPapelería\t$ 1.500.000\t\n"
                + "5195\t\t\t\t\t1500000\n"
                + "999999\t\t\t\t10\t\n";
        var r = service.importarLineas(EMPRESA, texto);

        assertEquals(1, r.getLineas().size());
        assertEquals(0, new BigDecimal("1500000").compareTo(r.getLineas().get(0).getDebito()));
        assertEquals(3L, r.getLineas().get(0).getTerceroId());
        assertEquals(2, r.getErrores().size());
        assertTrue(r.getErrores().get(0).getMensaje().contains("no es de movimiento"));
        assertTrue(r.getErrores().get(1).getMensaje().contains("no existe"));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static AsientoContableEntity contabilizada(LocalDate fecha) {
        AsientoContableEntity a = AsientoContableEntity.builder()
                .id(50L).empresaId(EMPRESA).tipoOrigen("MANUAL").tipoComprobante("CD")
                .estado("CONTABILIZADO").numeroComprobante("CD-000010").fecha(fecha)
                .descripcion("Provisión de servicios")
                .totalDebito(new BigDecimal("1000")).totalCredito(new BigDecimal("1000")).build();
        a.getDetalles().add(AsientoDetalleEntity.builder().cuentaId(GASTO)
                .debito(new BigDecimal("1000")).credito(BigDecimal.ZERO).build());
        a.getDetalles().add(AsientoDetalleEntity.builder().cuentaId(CAJA)
                .debito(BigDecimal.ZERO).credito(new BigDecimal("1000")).build());
        return a;
    }

    private AsientoContableEntity capturada() {
        org.mockito.ArgumentCaptor<AsientoContableEntity> captor =
                org.mockito.ArgumentCaptor.forClass(AsientoContableEntity.class);
        verify(repo, org.mockito.Mockito.atLeastOnce()).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private static AsientoContableEntity existente(String estado) {
        AsientoContableEntity a = AsientoContableEntity.builder()
                .id(99L).empresaId(EMPRESA).tipoOrigen("MANUAL").tipoComprobante("CD")
                .estado(estado).numeroComprobante("CONTABILIZADO".equals(estado) ? "CD-000001" : null)
                .fecha(LocalDate.now()).build();
        a.getDetalles().add(AsientoDetalleEntity.builder().cuentaId(GASTO).debito(BigDecimal.ONE).build());
        return a;
    }

    private static SaveNotaDiarioDto nota(boolean contabilizar, SaveNotaDiarioLineaDto... lineas) {
        SaveNotaDiarioDto dto = new SaveNotaDiarioDto();
        dto.setFecha(LocalDate.now());
        dto.setDescripcion("Provisión de servicios");
        dto.setLineas(List.of(lineas));
        dto.setContabilizar(contabilizar);
        return dto;
    }

    private static SaveNotaDiarioLineaDto linea(Long cuentaId, long debito, long credito) {
        SaveNotaDiarioLineaDto l = new SaveNotaDiarioLineaDto();
        l.setCuentaId(cuentaId);
        l.setDebito(BigDecimal.valueOf(debito));
        l.setCredito(BigDecimal.valueOf(credito));
        return l;
    }
}
