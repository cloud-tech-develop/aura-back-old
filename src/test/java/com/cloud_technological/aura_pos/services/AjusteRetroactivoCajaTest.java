package com.cloud_technological.aura_pos.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import com.cloud_technological.aura_pos.contabilidad.application.port.PeriodoContablePort;
import com.cloud_technological.aura_pos.dto.caja.CreateAjusteRetroactivoDto;
import com.cloud_technological.aura_pos.entity.CajaEntity;
import com.cloud_technological.aura_pos.entity.MovimientoCajaEntity;
import com.cloud_technological.aura_pos.entity.TurnoCajaEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.mappers.TurnoCajaMapper;
import com.cloud_technological.aura_pos.repositories.caja.CajaJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.AbonoCobrarJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.CuentaCobrarJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_pagar.AbonoPagarJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_pagar.CuentaPagarJPARepository;
import com.cloud_technological.aura_pos.repositories.movimiento_caja.MovimientoCajaJPARepository;
import com.cloud_technological.aura_pos.repositories.turno_caja.TurnoCajaJPARepository;
import com.cloud_technological.aura_pos.repositories.turno_caja.TurnoCajaQueryRepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.implementations.TurnoCajaServiceImpl;
import com.cloud_technological.aura_pos.utils.GlobalException;

/**
 * Fase 7: corregir un arqueo cerrado SIN reabrirlo.
 *
 * <p>Lo que estas pruebas protegen no es el cálculo, es la evidencia: el cierre
 * original tiene que sobrevivir intacto a cualquier corrección.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AjusteRetroactivoCajaTest {

    private static final Integer EMPRESA = 1;
    private static final Long TURNO = 56L;
    private static final Long USUARIO = 9L;

    @Mock private TurnoCajaQueryRepository turnoRepository;
    @Mock private TurnoCajaJPARepository turnoJPARepository;
    @Mock private CajaJPARepository cajaJPARepository;
    @Mock private UsuarioJPARepository usuarioJPARepository;
    @Mock private TurnoCajaMapper turnoMapper;
    @Mock private AbonoCobrarJPARepository abonoCobrarRepository;
    @Mock private AbonoPagarJPARepository abonoPagarRepository;
    @Mock private CuentaCobrarJPARepository cuentaCobrarRepository;
    @Mock private CuentaPagarJPARepository cuentaPagarRepository;
    @Mock private MovimientoCajaJPARepository movimientoCajaRepository;
    @Mock private ComprobanteCajaService comprobanteCajaService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ControlFechaRetroactivaService controlFechaRetroactiva;
    @Mock private PeriodoContablePort periodoContable;

    private TurnoCajaServiceImpl service;
    private TurnoCajaEntity turno;

    @BeforeEach
    void setUp() {
        service = new TurnoCajaServiceImpl(turnoRepository, turnoJPARepository, cajaJPARepository,
                usuarioJPARepository, turnoMapper, abonoCobrarRepository, abonoPagarRepository,
                cuentaCobrarRepository, cuentaPagarRepository, movimientoCajaRepository,
                comprobanteCajaService, eventPublisher, controlFechaRetroactiva, periodoContable);

        turno = new TurnoCajaEntity();
        turno.setId(TURNO);
        turno.setEstado("CERRADA");
        turno.setFechaCierre(LocalDateTime.of(2026, 8, 18, 20, 0));
        turno.setDiferencia(new BigDecimal("-5000.00"));
        turno.setCaja(new CajaEntity());

        when(turnoJPARepository.findByIdAndCajaSucursalEmpresaId(TURNO, EMPRESA))
                .thenReturn(Optional.of(turno));
        when(usuarioJPARepository.findById(USUARIO.intValue()))
                .thenReturn(Optional.of(new UsuarioEntity()));
        when(movimientoCajaRepository.save(any(MovimientoCajaEntity.class)))
                .thenAnswer(inv -> {
                    MovimientoCajaEntity m = inv.getArgument(0);
                    m.setId(77L);
                    return m;
                });
        when(movimientoCajaRepository.findByTurnoCajaIdOrderByCreatedAtAsc(TURNO))
                .thenReturn(List.of());
    }

    private CreateAjusteRetroactivoDto dto(String tipo, String monto) {
        CreateAjusteRetroactivoDto d = new CreateAjusteRetroactivoDto();
        d.setTipo(tipo);
        d.setMonto(new BigDecimal(monto));
        d.setMotivo("Se pagó al proveedor y no quedó registrado ese día");
        d.setConcepto("Pago no registrado");
        return d;
    }

    private void registrar(CreateAjusteRetroactivoDto d) {
        service.registrarAjusteRetroactivo(TURNO, d, EMPRESA, USUARIO);
    }

    @Test
    void elCierreOriginalSobreviveAlAjuste() {
        registrar(dto("EGRESO", "189106.49"));

        // Lo que el cajero firmó ese día no se toca. Es la razón entera de que
        // este método exista en vez de reabrir el turno.
        assertEquals(new BigDecimal("-5000.00"), turno.getDiferencia());
        assertEquals(new BigDecimal("-5000.00"), turno.getDiferenciaOriginal());
    }

    @Test
    void unEgresoNoRegistradoAgrandaElFaltante() {
        when(movimientoCajaRepository.findByTurnoCajaIdOrderByCreatedAtAsc(TURNO))
                .thenReturn(List.of(ajuste("EGRESO", "1000.00")));

        registrar(dto("EGRESO", "1000.00"));

        // La plata ya no estaba: el faltante crece.
        assertEquals(0, new BigDecimal("-4000.00").compareTo(turno.getDiferenciaAjustada()));
    }

    @Test
    void unIngresoNoRegistradoReduceElFaltante() {
        when(movimientoCajaRepository.findByTurnoCajaIdOrderByCreatedAtAsc(TURNO))
                .thenReturn(List.of(ajuste("INGRESO", "1000.00")));

        registrar(dto("INGRESO", "1000.00"));

        assertEquals(0, new BigDecimal("-6000.00").compareTo(turno.getDiferenciaAjustada()));
    }

    @Test
    void elMovimientoQuedaMarcadoConMotivoYAutorizador() {
        registrar(dto("EGRESO", "189106.49"));

        ArgumentCaptor<MovimientoCajaEntity> captor =
                ArgumentCaptor.forClass(MovimientoCajaEntity.class);
        verify(movimientoCajaRepository).save(captor.capture());
        MovimientoCajaEntity guardado = captor.getValue();

        assertTrue(guardado.getEsAjusteRetroactivo());
        assertEquals(USUARIO.intValue(), guardado.getAutorizadoPor());
        assertTrue(guardado.getMotivoAjuste().startsWith("Se pagó al proveedor"));
        // Sin fecha propia, el ajuste pertenece al día del cierre, no a hoy.
        assertEquals(LocalDate.of(2026, 8, 18), guardado.getFecha());
    }

    @Test
    void sobreUnTurnoAbiertoSeRechazaConLaAlternativa() {
        turno.setEstado("ABIERTA");

        GlobalException ex = assertThrows(GlobalException.class,
                () -> registrar(dto("EGRESO", "1000.00")));

        assertTrue(ex.getMessage().contains("Registre el movimiento normalmente"));
        verify(movimientoCajaRepository, never()).save(any());
    }

    @Test
    void sinElRolAutorizadorNoSeCorrigeNada() {
        doThrow(new GlobalException(org.springframework.http.HttpStatus.FORBIDDEN, "Solo ADMIN"))
                .when(controlFechaRetroactiva).exigirRolAutorizador(anyInt(), anyString());

        assertThrows(GlobalException.class, () -> registrar(dto("EGRESO", "1000.00")));

        verify(movimientoCajaRepository, never()).save(any());
    }

    @Test
    void conElPeriodoContableCerradoSeRechazaAntesDeGuardar() {
        when(periodoContable.abiertoPara(anyInt(), any()))
                .thenThrow(new IllegalStateException("cerrado"));

        GlobalException ex = assertThrows(GlobalException.class,
                () -> registrar(dto("EGRESO", "1000.00")));

        assertTrue(ex.getMessage().contains("período contable"));
        verify(movimientoCajaRepository, never()).save(any());
    }

    @Test
    void unTipoQueNoEsIngresoNiEgresoSeRechaza() {
        assertThrows(GlobalException.class, () -> registrar(dto("AJUSTE", "1000.00")));

        verify(movimientoCajaRepository, never()).save(any());
    }

    @Test
    void elSegundoAjusteNoPisaElOriginalGuardado() {
        turno.setDiferenciaOriginal(new BigDecimal("-5000.00"));
        turno.setDiferencia(new BigDecimal("-99999.00")); // como si algo la hubiera tocado

        registrar(dto("EGRESO", "1000.00"));

        // El original ya estaba fijado: no se vuelve a copiar.
        assertEquals(new BigDecimal("-5000.00"), turno.getDiferenciaOriginal());
    }

    private MovimientoCajaEntity ajuste(String tipo, String monto) {
        return MovimientoCajaEntity.builder()
                .tipo(tipo)
                .monto(new BigDecimal(monto))
                .esAjusteRetroactivo(Boolean.TRUE)
                .build();
    }
}
