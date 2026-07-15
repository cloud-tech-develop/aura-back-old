package com.cloud_technological.aura_pos.contabilidad.application.generador;

import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorCierreAnual;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaPago;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.entity.CierreAnualEntity;
import com.cloud_technological.aura_pos.entity.ConceptoContable;

/** Generadores del cierre anual fiscal (E8) contra golden files. */
@ExtendWith(MockitoExtension.class)
class CierreAnualGeneradoresTest {

    private static final Integer EMPRESA = 1;
    private static final LocalDate FIN_2025 = LocalDate.of(2025, 12, 31);
    private static final LocalDate INICIO_2026 = LocalDate.of(2026, 1, 2);

    @Mock
    private LectorCierreAnual cierres;
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
                .thenReturn(1110L);
    }

    @Test
    void provisionDeRentaDebitaGastoContraImpuestoPorPagar() {
        CierreAnualGenerador generador = new CierreAnualGenerador(cierres, cuentas);
        assertTrue(generador.siempreContabilizado(),
                "la provisión es acto deliberado del contador: nace contabilizada");

        when(cierres.cargarOperacion(85L, EMPRESA)).thenReturn(new LectorCierreAnual.OperacionContable(
                CierreAnualEntity.TIPO_PROVISION_RENTA, 2025, FIN_2025,
                new BigDecimal("12500000"), null));

        Asiento asiento = generador.generar(
                new ContextoContabilizacion("CIERRE_ANUAL", 85L, EMPRESA, 7));

        GoldenAsientos.assertCoincide("provision-renta.json", asiento);
    }

    @Test
    void trasladoDeUtilidadDebita3605ContraAcumulados() {
        when(cierres.cargarOperacion(86L, EMPRESA)).thenReturn(new LectorCierreAnual.OperacionContable(
                CierreAnualEntity.TIPO_TRASLADO, 2025, INICIO_2026,
                new BigDecimal("24300000"), "Utilidad del ejercicio 2025"));

        Asiento asiento = new CierreAnualGenerador(cierres, cuentas)
                .generar(new ContextoContabilizacion("CIERRE_ANUAL", 86L, EMPRESA, 7));

        GoldenAsientos.assertCoincide("traslado-utilidad.json", asiento);
    }

    @Test
    void trasladoDePerdidaInvierteElAsiento() {
        when(cierres.cargarOperacion(87L, EMPRESA)).thenReturn(new LectorCierreAnual.OperacionContable(
                CierreAnualEntity.TIPO_TRASLADO, 2025, INICIO_2026,
                new BigDecimal("-8000000"), "Pérdida del ejercicio 2025"));

        Asiento asiento = new CierreAnualGenerador(cierres, cuentas)
                .generar(new ContextoContabilizacion("CIERRE_ANUAL", 87L, EMPRESA, 7));

        GoldenAsientos.assertCoincide("traslado-perdida.json", asiento);
    }

    @Test
    void distribucionApropiaReservaYDecretaDividendos() {
        DistribucionUtilidadGenerador generador = new DistribucionUtilidadGenerador(cierres, cuentas);
        assertTrue(generador.siempreContabilizado(),
                "la distribución la decidió la asamblea: nace contabilizada");

        when(cierres.cargarDistribucion(88L, EMPRESA)).thenReturn(new LectorCierreAnual.DistribucionContable(
                2025, INICIO_2026, new BigDecimal("5000000"), new BigDecimal("10000000")));

        Asiento asiento = generador.generar(
                new ContextoContabilizacion("DISTRIBUCION_UTILIDAD", 88L, EMPRESA, 7));

        GoldenAsientos.assertCoincide("distribucion-utilidades.json", asiento);
    }

    @Test
    void distribucionSoloReservaOmiteLineaDeDividendos() {
        when(cierres.cargarDistribucion(89L, EMPRESA)).thenReturn(new LectorCierreAnual.DistribucionContable(
                2025, INICIO_2026, new BigDecimal("5000000"), BigDecimal.ZERO));

        Asiento asiento = new DistribucionUtilidadGenerador(cierres, cuentas)
                .generar(new ContextoContabilizacion("DISTRIBUCION_UTILIDAD", 89L, EMPRESA, 7));

        assertTrue(asiento.partidas().size() == 2,
                "sin dividendos el asiento solo lleva 3705 y 330505");
        assertTrue(asiento.totalDebito().compareTo(new BigDecimal("5000000")) == 0);
    }

    @Test
    void pagoDeDividendosDebita2360ConElSocioContraBanco() {
        DividendoPagoGenerador generador = new DividendoPagoGenerador(cierres, cuentas, cuentaPago);
        assertTrue(generador.siempreContabilizado());

        when(cierres.cargarPago(90L, EMPRESA)).thenReturn(new LectorCierreAnual.PagoDividendoContable(
                INICIO_2026, new BigDecimal("4000000"), "TRANSFERENCIA", 9L, 55L));

        Asiento asiento = generador.generar(
                new ContextoContabilizacion("DIVIDENDO_PAGO", 90L, EMPRESA, 7));

        GoldenAsientos.assertCoincide("pago-dividendos.json", asiento);
    }
}
