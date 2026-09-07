package com.cloud_technological.aura_pos.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.contabilidad.AplicacionCarteraDto;
import com.cloud_technological.aura_pos.dto.contabilidad.CreateAsientoDetalleDto;
import com.cloud_technological.aura_pos.dto.contabilidad.CreateComprobanteDto;
import com.cloud_technological.aura_pos.entity.AsientoContableEntity;
import com.cloud_technological.aura_pos.entity.CajaEntity;
import com.cloud_technological.aura_pos.entity.MovimientoCajaEntity;
import com.cloud_technological.aura_pos.entity.PeriodoContableEntity;
import com.cloud_technological.aura_pos.entity.TurnoCajaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableQueryRepository;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.movimiento_caja.MovimientoCajaJPARepository;
import com.cloud_technological.aura_pos.repositories.periodo_contable.PeriodoContableJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.turno_caja.TurnoCajaJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.implementations.AsientoContableServiceImpl;

/**
 * El comprobante manual (CE/RC) mueve dinero real y tiene que aparecer en el
 * cierre de la caja por donde pasó.
 *
 * <p>Antes no lo hacía: el cruce de cartera creaba el abono sin
 * {@code turno_caja_id} y con {@code metodo_pago = "COMPROBANTE"} — un valor que
 * {@code MediosPago.esEfectivo()} no reconoce — mientras el cierre arma su
 * detalle con tres consultas, todas por turno. El comprobante era la única vía
 * por la que entraba o salía efectivo sin rastro en ningún arqueo.
 *
 * <p>Estos tests fijan las dos mitades de la solución: que el origen declarado
 * viaje hasta el abono, y que la parte que no es cartera deje su propio
 * movimiento de caja sin duplicar la que sí lo es.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ComprobanteOrigenFondosTest {

    private static final Integer EMPRESA_ID = 1;
    private static final Integer USUARIO_ID = 7;
    private static final Long CUENTA_CAJA = 1105L;
    private static final Long CUENTA_BANCO = 1110L;
    private static final Long CUENTA_PROVEEDOR = 2205L;

    @Mock private AsientoContableJPARepository repo;
    @Mock private AsientoContableQueryRepository queryRepo;
    @Mock private PeriodoContableJPARepository periodoRepo;
    @Mock private TerceroJPARepository terceroRepo;
    @Mock private CuentaCobrarService cuentaCobrarService;
    @Mock private CuentaPagarService cuentaPagarService;
    @Mock private EmpresaJPARepository empresaRepo;
    @Mock private OrigenFondosService origenFondosService;
    @Mock private MovimientoCajaJPARepository movimientoCajaRepo;
    @Mock private TurnoCajaJPARepository turnoCajaRepo;
    @Mock private UsuarioJPARepository usuarioRepo;
    @Mock private ComprobanteCajaService comprobanteCajaService;

    @InjectMocks private AsientoContableServiceImpl service;

    @Captor private ArgumentCaptor<MovimientoCajaEntity> movimientoCaptor;
    @Captor private ArgumentCaptor<AsientoContableEntity> asientoCaptor;

    private TurnoCajaEntity turnoAbierto;

    @BeforeEach
    void setUp() {
        PeriodoContableEntity periodo = new PeriodoContableEntity();
        periodo.setId(10L);
        when(periodoRepo.findByEmpresaIdAndEstado(EMPRESA_ID, "ABIERTO"))
                .thenReturn(Optional.of(periodo));
        when(queryRepo.siguienteNumeroComprobante(eq(EMPRESA_ID), anyString()))
                .thenReturn("CE-000001");
        when(queryRepo.obtenerDetalles(anyLong())).thenReturn(new ArrayList<>());
        when(repo.save(any(AsientoContableEntity.class))).thenAnswer(inv -> {
            AsientoContableEntity a = inv.getArgument(0);
            a.setId(500L);
            return a;
        });

        CajaEntity caja = new CajaEntity();
        caja.setId(3L);
        caja.setNombre("Caja principal");

        turnoAbierto = new TurnoCajaEntity();
        turnoAbierto.setId(88L);
        turnoAbierto.setCaja(caja);
        turnoAbierto.setEstado("ABIERTA");
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private CreateComprobanteDto comprobanteEgreso() {
        CreateComprobanteDto dto = new CreateComprobanteDto();
        dto.setTipoComprobante("CE");
        dto.setFecha(LocalDate.now());
        dto.setConcepto("Pago a proveedor");
        dto.setMetodoPago("EFECTIVO");
        dto.setSucursalId(1);
        dto.setDetalles(new ArrayList<>(List.of(
                linea(CUENTA_PROVEEDOR, "100000", "0", CreateAsientoDetalleDto.ORIGEN_CARTERA),
                // La cuenta va vacía a propósito: la pone el origen de fondos.
                linea(null, "0", "100000", CreateAsientoDetalleDto.ORIGEN_BANCO))));
        return dto;
    }

    private CreateAsientoDetalleDto linea(Long cuentaId, String debito, String credito, String origen) {
        CreateAsientoDetalleDto d = new CreateAsientoDetalleDto();
        d.setCuentaId(cuentaId);
        d.setDebito(new BigDecimal(debito));
        d.setCredito(new BigDecimal(credito));
        d.setOrigen(origen);
        return d;
    }

    private AplicacionCarteraDto aplicacion(String tipo, long cuentaId, String monto) {
        AplicacionCarteraDto ap = new AplicacionCarteraDto();
        ap.setTipo(tipo);
        ap.setCuentaId(cuentaId);
        ap.setMonto(new BigDecimal(monto));
        return ap;
    }

    private void origenEsCaja() {
        when(origenFondosService.resolver(eq(EMPRESA_ID), any()))
                .thenReturn(new OrigenFondosService.OrigenFondos(
                        OrigenFondosService.Tipo.CAJA, CUENTA_CAJA, turnoAbierto));
    }

    private void origenEsBanco() {
        when(origenFondosService.resolver(eq(EMPRESA_ID), any()))
                .thenReturn(new OrigenFondosService.OrigenFondos(
                        OrigenFondosService.Tipo.BANCO, CUENTA_BANCO, null));
    }

    // ── El origen viaja hasta el abono ──────────────────────────────────

    @Test
    @DisplayName("El cruce de cartera recibe el turno y el método reales, no el literal COMPROBANTE")
    void cruceRecibeElOrigenDeclarado() {
        origenEsCaja();
        CreateComprobanteDto dto = comprobanteEgreso();
        dto.setAplicaciones(List.of(aplicacion("CXP", 42L, "100000")));

        service.crearComprobante(EMPRESA_ID, USUARIO_ID, dto);

        ArgumentCaptor<OrigenFondosService.OrigenFondos> origenCaptor =
                ArgumentCaptor.forClass(OrigenFondosService.OrigenFondos.class);
        verify(cuentaPagarService).aplicarCruce(eq(42L), eq(new BigDecimal("100000")),
                eq(EMPRESA_ID), eq(USUARIO_ID), eq("Comprobante CE-000001"),
                origenCaptor.capture(), eq("EFECTIVO"));

        // Sin este turno el abono no entra a ninguna de las tres consultas del
        // cierre, que buscan todas por turno_caja_id.
        assertEquals(88L, origenCaptor.getValue().turnoId());
    }

    @Test
    @DisplayName("El comprobante guarda lo que declaró: turno, método y caja de otro día")
    void asientoGuardaLaDeclaracion() {
        origenEsCaja();
        CreateComprobanteDto dto = comprobanteEgreso();
        dto.setAplicaciones(List.of(aplicacion("CXP", 42L, "100000")));

        service.crearComprobante(EMPRESA_ID, USUARIO_ID, dto);

        verify(repo).save(asientoCaptor.capture());
        AsientoContableEntity asiento = asientoCaptor.getValue();
        assertEquals(88L, asiento.getTurnoCajaId());
        assertEquals("EFECTIVO", asiento.getMetodoPago());
        assertEquals(Boolean.FALSE, asiento.getCajaOtroDia());
    }

    @Test
    @DisplayName("La contrapartida toma la cuenta del origen, no la que se digitó")
    void contrapartidaLaResuelveElOrigen() {
        origenEsBanco();
        CreateComprobanteDto dto = comprobanteEgreso();
        dto.setMetodoPago("TRANSFERENCIA");
        dto.setCuentaBancariaId(9L);
        // El usuario dejó puesta la caja en la línea; el origen dice banco.
        dto.getDetalles().get(1).setCuentaId(CUENTA_CAJA);
        dto.setAplicaciones(List.of(aplicacion("CXP", 42L, "100000")));

        service.crearComprobante(EMPRESA_ID, USUARIO_ID, dto);

        verify(repo).save(asientoCaptor.capture());
        assertEquals(CUENTA_BANCO, asientoCaptor.getValue().getDetalles().get(1).getCuentaId(),
                "acreditar la caja un pago hecho por banco deja la caja contable en negativo");
    }

    // ── Movimiento de caja: ni de menos ni por duplicado ────────────────

    @Test
    @DisplayName("El CE en efectivo sin cartera deja su movimiento de caja")
    void egresoSinCarteraGeneraMovimiento() {
        origenEsCaja();
        CreateComprobanteDto dto = comprobanteEgreso();
        dto.setConcepto("Pago de servicio suelto");
        dto.getDetalles().get(0).setCuentaId(5195L);
        dto.getDetalles().get(0).setOrigen(CreateAsientoDetalleDto.ORIGEN_MANUAL);

        service.crearComprobante(EMPRESA_ID, USUARIO_ID, dto);

        verify(movimientoCajaRepo).save(movimientoCaptor.capture());
        MovimientoCajaEntity mov = movimientoCaptor.getValue();
        assertEquals("EGRESO", mov.getTipo());
        assertEquals(new BigDecimal("100000"), mov.getMonto());
        assertEquals(88L, mov.getTurnoCaja().getId());
        assertEquals(MovimientoCajaEntity.ORIGEN_COMPROBANTE, mov.getOrigenTipo());
        assertEquals(500L, mov.getOrigenId());
    }

    @Test
    @DisplayName("El RC en efectivo sin cartera entra como INGRESO")
    void reciboSinCarteraGeneraIngreso() {
        origenEsCaja();
        CreateComprobanteDto dto = comprobanteEgreso();
        dto.setTipoComprobante("RC");
        dto.getDetalles().get(0).setCuentaId(4295L);
        dto.getDetalles().get(0).setOrigen(CreateAsientoDetalleDto.ORIGEN_MANUAL);

        service.crearComprobante(EMPRESA_ID, USUARIO_ID, dto);

        verify(movimientoCajaRepo).save(movimientoCaptor.capture());
        assertEquals("INGRESO", movimientoCaptor.getValue().getTipo());
    }

    @Test
    @DisplayName("Si toda la contrapartida es cartera no hay movimiento: el abono ya cuenta en el arqueo")
    void carteraCompletaNoDuplicaElMovimiento() {
        origenEsCaja();
        CreateComprobanteDto dto = comprobanteEgreso();
        dto.setAplicaciones(List.of(aplicacion("CXP", 42L, "100000")));

        service.crearComprobante(EMPRESA_ID, USUARIO_ID, dto);

        verify(movimientoCajaRepo, never()).save(any(MovimientoCajaEntity.class));
    }

    @Test
    @DisplayName("El pago por banco no toca ningún arqueo")
    void bancoNoTocaCaja() {
        origenEsBanco();
        CreateComprobanteDto dto = comprobanteEgreso();
        dto.setMetodoPago("TRANSFERENCIA");
        dto.setCuentaBancariaId(9L);
        dto.getDetalles().get(0).setCuentaId(5195L);
        dto.getDetalles().get(0).setOrigen(CreateAsientoDetalleDto.ORIGEN_MANUAL);

        service.crearComprobante(EMPRESA_ID, USUARIO_ID, dto);

        verify(movimientoCajaRepo, never()).save(any(MovimientoCajaEntity.class));
    }

    // ── Lo que se exige y lo que no ─────────────────────────────────────

    @Test
    @DisplayName("Un CE sin método de pago se rechaza en vez de caer en una cuenta por defecto")
    void egresoSinMetodoDePagoSeRechaza() {
        CreateComprobanteDto dto = comprobanteEgreso();
        dto.setMetodoPago(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.crearComprobante(EMPRESA_ID, USUARIO_ID, dto));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(origenFondosService, never()).resolver(anyInt(), any());
    }

    @Test
    @DisplayName("La nota de diario no mueve plata: ni exige origen ni toca caja")
    void notaDeDiarioNoPideOrigen() {
        CreateComprobanteDto dto = comprobanteEgreso();
        dto.setTipoComprobante("CD");
        dto.setMetodoPago(null);
        dto.getDetalles().get(0).setCuentaId(5195L);
        dto.getDetalles().get(0).setOrigen(CreateAsientoDetalleDto.ORIGEN_MANUAL);
        dto.getDetalles().get(1).setCuentaId(CUENTA_CAJA);
        dto.getDetalles().get(1).setOrigen(CreateAsientoDetalleDto.ORIGEN_MANUAL);

        service.crearComprobante(EMPRESA_ID, USUARIO_ID, dto);

        verify(origenFondosService, never()).resolver(anyInt(), any());
        verify(movimientoCajaRepo, never()).save(any(MovimientoCajaEntity.class));
        verify(repo).save(asientoCaptor.capture());
        assertNull(asientoCaptor.getValue().getTurnoCajaId());
    }

    @Test
    @DisplayName("Una línea que no es la contrapartida sigue exigiendo cuenta")
    void lineaSinCuentaSeRechaza() {
        origenEsCaja();
        CreateComprobanteDto dto = comprobanteEgreso();
        dto.getDetalles().get(0).setCuentaId(null);
        dto.getDetalles().get(0).setOrigen(CreateAsientoDetalleDto.ORIGEN_MANUAL);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.crearComprobante(EMPRESA_ID, USUARIO_ID, dto));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    // ── El soporte imprimible ───────────────────────────────────────────

    @Test
    @DisplayName("El comprobante emite su soporte de caja con SU MISMO número")
    void emiteComprobanteDeCajaConElNumeroDelAsiento() {
        origenEsCaja();
        CreateComprobanteDto dto = comprobanteEgreso();
        dto.setAplicaciones(List.of(aplicacion("CXP", 42L, "100000")));

        service.crearComprobante(EMPRESA_ID, USUARIO_ID, dto);

        // El monto es el total que se movió, cartera incluida: el soporte ampara
        // la plata entregada, no la parte que además cruzó cartera.
        // Y el número es el del asiento: la serie RC/CE es única para las dos
        // tablas, así que pedir uno nuevo partiría el pago en dos documentos.
        verify(comprobanteCajaService).sincronizarDeDocumento(
                eq(EMPRESA_ID), eq(USUARIO_ID), eq("EGRESO"), anyString(),
                eq(new BigDecimal("100000")), eq("EFECTIVO"), any(),
                eq(MovimientoCajaEntity.ORIGEN_COMPROBANTE), eq(500L), eq(88L),
                eq("CE-000001"));
    }

    @Test
    @DisplayName("El pago por banco también deja soporte, aunque no toque caja")
    void bancoTambienEmiteSoporte() {
        origenEsBanco();
        CreateComprobanteDto dto = comprobanteEgreso();
        dto.setMetodoPago("TRANSFERENCIA");
        dto.setCuentaBancariaId(9L);
        dto.getDetalles().get(0).setCuentaId(5195L);
        dto.getDetalles().get(0).setOrigen(CreateAsientoDetalleDto.ORIGEN_MANUAL);

        service.crearComprobante(EMPRESA_ID, USUARIO_ID, dto);

        verify(comprobanteCajaService).sincronizarDeDocumento(
                eq(EMPRESA_ID), eq(USUARIO_ID), eq("EGRESO"), anyString(),
                eq(new BigDecimal("100000")), eq("TRANSFERENCIA"), any(),
                eq(MovimientoCajaEntity.ORIGEN_COMPROBANTE), eq(500L), eq(null),
                eq("CE-000001"));
    }

    @Test
    @DisplayName("La nota de diario no emite soporte de caja: no mueve plata")
    void notaDeDiarioNoEmiteSoporte() {
        CreateComprobanteDto dto = comprobanteEgreso();
        dto.setTipoComprobante("CD");
        dto.setMetodoPago(null);
        dto.getDetalles().get(0).setCuentaId(5195L);
        dto.getDetalles().get(0).setOrigen(CreateAsientoDetalleDto.ORIGEN_MANUAL);
        dto.getDetalles().get(1).setCuentaId(CUENTA_CAJA);
        dto.getDetalles().get(1).setOrigen(CreateAsientoDetalleDto.ORIGEN_MANUAL);

        service.crearComprobante(EMPRESA_ID, USUARIO_ID, dto);

        verify(comprobanteCajaService, never()).sincronizarDeDocumento(
                anyInt(), anyInt(), anyString(), anyString(), any(), any(), any(),
                anyString(), anyLong(), any(), any());
    }

    // ── Anulación ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Anular revierte el cruce de cartera y borra el movimiento de caja")
    void anularDeshaceLoQueElComprobanteMovio() {
        AsientoContableEntity asiento = AsientoContableEntity.builder()
                .id(500L)
                .empresaId(EMPRESA_ID)
                .tipoOrigen("MANUAL")
                .estado("CONTABILIZADO")
                .numeroComprobante("CE-000001")
                .turnoCajaId(88L)
                .build();
        when(repo.findByIdAndEmpresaId(500L, EMPRESA_ID)).thenReturn(Optional.of(asiento));
        when(turnoCajaRepo.findByIdAndCajaSucursalEmpresaId(88L, EMPRESA_ID))
                .thenReturn(Optional.of(turnoAbierto));
        MovimientoCajaEntity mov = MovimientoCajaEntity.builder().id(1L).build();
        when(movimientoCajaRepo.findByOrigenTipoAndOrigenId(
                MovimientoCajaEntity.ORIGEN_COMPROBANTE, 500L)).thenReturn(List.of(mov));

        service.anular(500L, EMPRESA_ID);

        verify(cuentaCobrarService).revertirCrucesDeDocumento("Comprobante CE-000001", EMPRESA_ID);
        verify(cuentaPagarService).revertirCrucesDeDocumento("Comprobante CE-000001", EMPRESA_ID);
        verify(movimientoCajaRepo).deleteAll(List.of(mov));
        // El soporte no se borra: se invalida conservando su número, para no
        // dejar un hueco en la serie.
        verify(comprobanteCajaService).anularDeDocumento(
                eq(EMPRESA_ID), eq(MovimientoCajaEntity.ORIGEN_COMPROBANTE), eq(500L), anyString());
        assertEquals("ANULADO", asiento.getEstado());
    }

    @Test
    @DisplayName("Anular un comprobante de un turno cerrado se bloquea: el arqueo ya se firmó")
    void anularConTurnoCerradoSeBloquea() {
        turnoAbierto.setEstado("CERRADA");
        AsientoContableEntity asiento = AsientoContableEntity.builder()
                .id(500L)
                .empresaId(EMPRESA_ID)
                .tipoOrigen("MANUAL")
                .estado("CONTABILIZADO")
                .numeroComprobante("CE-000001")
                .turnoCajaId(88L)
                .build();
        when(repo.findByIdAndEmpresaId(500L, EMPRESA_ID)).thenReturn(Optional.of(asiento));
        when(turnoCajaRepo.findByIdAndCajaSucursalEmpresaId(88L, EMPRESA_ID))
                .thenReturn(Optional.of(turnoAbierto));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.anular(500L, EMPRESA_ID));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("ajuste retroactivo"));
        assertEquals("CONTABILIZADO", asiento.getEstado());
        verify(cuentaPagarService, never()).revertirCrucesDeDocumento(anyString(), anyInt());
        verify(movimientoCajaRepo, never()).deleteAll(any());
    }
}
