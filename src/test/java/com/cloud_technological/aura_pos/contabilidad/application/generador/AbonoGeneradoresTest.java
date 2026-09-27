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
    void recaudoConRetencionesSaldaLaFacturaPorElPagoMasLoRetenido() {
        // Factura de 1.190.000: el cliente paga 1.139.000 y retiene 20.000 de
        // renta, 26.180 de IVA y 4.820 de ICA.
        when(abonos.cargarCobro(11L, EMPRESA)).thenReturn(new AbonoContable(
                FECHA, new BigDecimal("1139000"), 55L, "TRANSFERENCIA", null, null,
                java.util.List.of(
                        new LectorAbonos.Retencion("RETEFUENTE", new BigDecimal("20000")),
                        new LectorAbonos.Retencion("RETEIVA", new BigDecimal("26180")),
                        new LectorAbonos.Retencion("RETEICA", new BigDecimal("4820")))));

        Asiento asiento = new AbonoCobroGenerador(abonos, cuentas, cuentaPago)
                .generar(new ContextoContabilizacion("ABONO_COBRAR", 11L, EMPRESA, 7));

        java.util.Map<Long, BigDecimal> debitos = new java.util.HashMap<>();
        java.util.Map<Long, BigDecimal> creditos = new java.util.HashMap<>();
        asiento.partidas().forEach(p -> {
            if (p.debito() != null) debitos.merge(p.cuentaId(), p.debito(), BigDecimal::add);
            if (p.credito() != null) creditos.merge(p.cuentaId(), p.credito(), BigDecimal::add);
        });
        org.junit.jupiter.api.Assertions.assertEquals(0, new BigDecimal("1139000").compareTo(debitos.get(1110L)));
        org.junit.jupiter.api.Assertions.assertEquals(0, new BigDecimal("20000").compareTo(debitos.get(135515L)));
        org.junit.jupiter.api.Assertions.assertEquals(0, new BigDecimal("26180").compareTo(debitos.get(135517L)));
        org.junit.jupiter.api.Assertions.assertEquals(0, new BigDecimal("4820").compareTo(debitos.get(135518L)));
        // Clientes baja por todo: pago + retenciones
        org.junit.jupiter.api.Assertions.assertEquals(0, new BigDecimal("1190000").compareTo(creditos.get(1305L)));
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
