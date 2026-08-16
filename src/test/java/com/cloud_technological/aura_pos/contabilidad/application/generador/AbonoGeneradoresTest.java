package com.cloud_technological.aura_pos.contabilidad.application.generador;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cloud_technological.aura_pos.contabilidad.GoldenAsientos;
import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorAbonos;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorAbonos.AbonoContable;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaPago;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.entity.ConceptoContable;

/**
 * Asientos de abonos RC/EG contra golden files (E2). Misma convención:
 * el resolver devuelve el código PUC default como id; la cuenta bancaria
 * parametrizada resuelve a 111005.
 */
@ExtendWith(MockitoExtension.class)
class AbonoGeneradoresTest {

    private static final Integer EMPRESA = 1;
    private static final LocalDate FECHA = LocalDate.of(2026, 7, 9);

    @Mock
    private LectorAbonos abonos;
    @Mock
    private ResolucionCuentas cuentas;

    /**
     * Implementación real, no mock: la prioridad de la cuenta elegida a mano
     * vive en el método {@code default} de la interfaz, y un mock lo
     * interceptaría en vez de ejecutarlo. Así la regla queda ejercitada.
     */
    private final ResolucionCuentaPago cuentaPago = (empresaId, metodoPago, cuentaBancariaId) -> {
        if (cuentaBancariaId != null) {
            return 111005L;
        }
        return metodoPago != null && metodoPago.toUpperCase().contains("EFECTIVO")
                ? 1105L : 1110L;
    };

    @BeforeEach
    void resolvers() {
        lenient().when(cuentas.resolver(eq(EMPRESA), any(ConceptoContable.class)))
                .thenAnswer(inv -> Long.parseLong(
                        ((ConceptoContable) inv.getArgument(1)).getCodigoDefault()));
    }

    @Test
    void recaudoCarteraEnEfectivo() {
        when(abonos.cargarCobro(10L, EMPRESA)).thenReturn(new AbonoContable(
                FECHA, new BigDecimal("20000"), 55L, "EFECTIVO", null, null));

        Asiento asiento = new AbonoCobroGenerador(abonos, cuentas, cuentaPago)
                .generar(new ContextoContabilizacion("ABONO_COBRAR", 10L, EMPRESA, 7));

        GoldenAsientos.assertCoincide("abono-cobro-efectivo.json", asiento);
    }

    @Test
    void pagoProveedorPorTransferenciaBancaria() {
        when(abonos.cargarPago(20L, EMPRESA)).thenReturn(new AbonoContable(
                FECHA, new BigDecimal("100000"), 77L, "TRANSFERENCIA", 9L, null));

        Asiento asiento = new AbonoPagoGenerador(abonos, cuentas, cuentaPago)
                .generar(new ContextoContabilizacion("ABONO_PAGAR", 20L, EMPRESA, 7));

        GoldenAsientos.assertCoincide("abono-pago-transferencia.json", asiento);
    }

    @Test
    void laCuentaElegidaAManoMandaSobreElMedioDePago() {
        // El administrador paga sin caja abierta y sin banco: elige la cuenta.
        // Aunque el método diga EFECTIVO (que resolvería a 1105), el asiento
        // tiene que acreditar la cuenta elegida.
        when(abonos.cargarPago(21L, EMPRESA)).thenReturn(new AbonoContable(
                FECHA, new BigDecimal("50000"), 77L, "EFECTIVO", null, 233505L));

        Asiento asiento = new AbonoPagoGenerador(abonos, cuentas, cuentaPago)
                .generar(new ContextoContabilizacion("ABONO_PAGAR", 21L, EMPRESA, 7));

        assertEquals(Long.valueOf(233505L), asiento.partidas().stream()
                .filter(p -> p.credito().signum() > 0)
                .findFirst().orElseThrow().cuentaId());
    }

    @Test
    void sinCuentaElegidaDecideElMedioDePago() {
        when(abonos.cargarCobro(11L, EMPRESA)).thenReturn(new AbonoContable(
                FECHA, new BigDecimal("50000"), 55L, "EFECTIVO", null, null));

        Asiento asiento = new AbonoCobroGenerador(abonos, cuentas, cuentaPago)
                .generar(new ContextoContabilizacion("ABONO_COBRAR", 11L, EMPRESA, 7));

        assertEquals(Long.valueOf(1105L), asiento.partidas().stream()
                .filter(p -> p.debito().signum() > 0)
                .findFirst().orElseThrow().cuentaId());
    }
}
