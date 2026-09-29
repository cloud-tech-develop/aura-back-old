package com.cloud_technological.aura_pos.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaPago;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.GastoEntity;
import com.cloud_technological.aura_pos.repositories.gastos.GastoJPARepository;
import com.cloud_technological.aura_pos.services.implementations.GastoServiceImpl;

/**
 * Al reabrir un gasto guardado tiene que volver a verse el origen que se
 * eligió, no otro.
 *
 * <p>El formulario pregunta una sola cosa — "¿de dónde sale la plata?" — y el
 * backend la guarda descompuesta en las piezas que necesita el asiento. La
 * trampa está en {@code cuenta_pago_id}: se llena SIEMPRE, también en un pago
 * de caja normal, con la cuenta de CAJA que resolvió el sistema. El formulario
 * lo leía como "alguien eligió una cuenta" y reabría cualquier gasto en
 * efectivo — y también los "ya salió de la caja" — como "Otra cuenta", con el
 * selector de cuenta vacío porque esa cuenta no está en su lista.
 *
 * <p>Por eso la reconstrucción vive en el backend: distinguir CAJA de CUENTA
 * exige saber cuál es la cuenta de efectivo de la empresa, y eso solo lo sabe
 * el resolutor.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GastoOrigenFondosTest {

    private static final Integer EMPRESA = 4;
    /** La cuenta de efectivo que resuelve el sistema para la empresa. */
    private static final Long CUENTA_CAJA = 11L;
    /** La CAJA MENOR que el administrador elige a mano. */
    private static final Long CUENTA_MENOR = 87L;

    @Mock private GastoJPARepository gastoJPARepository;
    @Mock private ResolucionCuentaPago resolucionCuentaPago;

    @InjectMocks private GastoServiceImpl service;

    @BeforeEach
    void setUp() {
        when(resolucionCuentaPago.resolver(anyInt(), any(), any())).thenReturn(CUENTA_CAJA);
    }

    // ── Casos ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Un gasto en efectivo vuelve como CAJA, no como Otra cuenta")
    void efectivoVuelveComoCaja() {
        // El sistema le puso la cuenta de CAJA al guardarlo: eso no es
        // "alguien eligió una cuenta".
        assertEquals("CAJA", origenDe(gasto(g -> {
            g.setMetodoPago("EFECTIVO");
            g.setCuentaPagoId(CUENTA_CAJA);
        })));
    }

    @Test
    @DisplayName("Ya salió de la caja gana sobre la cuenta que quedó guardada")
    void cajaOtroDiaGanaSobreLaCuenta() {
        // Este es el caso que reportó el usuario: guardó "ya salió de la caja",
        // reabrió y el formulario decía "Otra cuenta". El flag es una
        // afirmación sobre un hecho pasado y va antes que cualquier cuenta —
        // la que quedó guardada es la de CAJA, indistinguible de un pago
        // normal.
        assertEquals("CAJA_OTRO_DIA", origenDe(gasto(g -> {
            g.setMetodoPago("EFECTIVO");
            g.setCuentaPagoId(CUENTA_CAJA);
            g.setSalidaCajaOtroDia(true);
        })));
    }

    @Test
    @DisplayName("Una cuenta elegida a mano sí vuelve como Otra cuenta")
    void cuentaElegidaVuelveComoCuenta() {
        assertEquals("CUENTA", origenDe(gasto(g -> {
            g.setMetodoPago("EFECTIVO");
            g.setCuentaPagoId(CUENTA_MENOR);
        })));
    }

    @Test
    @DisplayName("El banco manda sobre la cuenta acreditada")
    void bancoVuelveComoBanco() {
        // `cuenta_pago_id` guarda la cuenta contable del banco, no la del
        // banco elegido: sin mirar `cuenta_bancaria_id` primero se leería como
        // "Otra cuenta".
        assertEquals("BANCO", origenDe(gasto(g -> {
            g.setMetodoPago("TRANSFERENCIA");
            g.setCuentaBancariaId(9L);
            g.setCuentaPagoId(CUENTA_MENOR);
        })));
    }

    @Test
    @DisplayName("El crédito manda sobre todo lo demás")
    void creditoVuelveComoCredito() {
        assertEquals("CREDITO", origenDe(gasto(g -> {
            g.setFormaPago("CREDITO");
            g.setCuentaPagoId(CUENTA_MENOR);
        })));
    }

    @Test
    @DisplayName("Un gasto viejo sin cuenta acreditada se lee como CAJA")
    void sinCuentaAcreditadaEsCaja() {
        // Anteriores a V142: no tienen de dónde deducir nada y el efectivo era
        // la única vía que existía.
        assertEquals("CAJA", origenDe(gasto(g -> g.setMetodoPago("EFECTIVO"))));
    }

    // ── Utilidades ────────────────────────────────────────────────────

    private String origenDe(GastoEntity g) {
        when(gastoJPARepository.findByIdAndEmpresaId(eq(g.getId()), eq(EMPRESA)))
                .thenReturn(Optional.of(g));
        return service.obtener(g.getId(), EMPRESA).getOrigenFondos();
    }

    private GastoEntity gasto(java.util.function.Consumer<GastoEntity> ajuste) {
        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(EMPRESA);

        GastoEntity g = new GastoEntity();
        g.setId(6L);
        g.setEmpresa(empresa);
        g.setCategoria("SERVICIOS_PUBLICOS");
        g.setMonto(new BigDecimal("100"));
        g.setFecha(LocalDate.of(2026, 8, 19));
        g.setDeducible(true);
        g.setEstado("ACTIVO");
        g.setFormaPago("CONTADO");
        ajuste.accept(g);
        return g;
    }
}
