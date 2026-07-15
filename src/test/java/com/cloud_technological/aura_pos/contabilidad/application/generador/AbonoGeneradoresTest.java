package com.cloud_technological.aura_pos.contabilidad.application.generador;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock
    private ResolucionCuentaPago cuentaPago;

    @BeforeEach
    void resolvers() {
        lenient().when(cuentas.resolver(eq(EMPRESA), any(ConceptoContable.class)))
                .thenAnswer(inv -> Long.parseLong(
                        ((ConceptoContable) inv.getArgument(1)).getCodigoDefault()));
        lenient().when(cuentaPago.resolver(eq(EMPRESA), anyString(), any()))
                .thenAnswer(inv -> {
                    Long cuentaBancariaId = inv.getArgument(2);
                    if (cuentaBancariaId != null) {
                        return 111005L;
                    }
                    String metodo = inv.getArgument(1);
                    return metodo != null && metodo.toUpperCase().contains("EFECTIVO")
                            ? 1105L : 1110L;
                });
    }

    @Test
    void recaudoCarteraEnEfectivo() {
        when(abonos.cargarCobro(10L, EMPRESA)).thenReturn(new AbonoContable(
                FECHA, new BigDecimal("20000"), 55L, "EFECTIVO", null));

        Asiento asiento = new AbonoCobroGenerador(abonos, cuentas, cuentaPago)
                .generar(new ContextoContabilizacion("ABONO_COBRAR", 10L, EMPRESA, 7));

        GoldenAsientos.assertCoincide("abono-cobro-efectivo.json", asiento);
    }

    @Test
    void pagoProveedorPorTransferenciaBancaria() {
        when(abonos.cargarPago(20L, EMPRESA)).thenReturn(new AbonoContable(
                FECHA, new BigDecimal("100000"), 77L, "TRANSFERENCIA", 9L));

        Asiento asiento = new AbonoPagoGenerador(abonos, cuentas, cuentaPago)
                .generar(new ContextoContabilizacion("ABONO_PAGAR", 20L, EMPRESA, 7));

        GoldenAsientos.assertCoincide("abono-pago-transferencia.json", asiento);
    }
}
