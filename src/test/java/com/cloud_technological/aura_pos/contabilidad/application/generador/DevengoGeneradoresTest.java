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
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorAnticipos;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorDeterioro;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaPago;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.entity.ConceptoContable;

/** Generadores de devengo (E6) contra golden files. */
@ExtendWith(MockitoExtension.class)
class DevengoGeneradoresTest {

    private static final Integer EMPRESA = 1;
    private static final LocalDate FECHA = LocalDate.of(2026, 7, 9);

    @Mock
    private LectorAnticipos anticipos;
    @Mock
    private LectorDeterioro deterioros;
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
    void anticipoDeClienteEsPasivoNoIngreso() {
        when(anticipos.cargar(70L, EMPRESA)).thenReturn(new LectorAnticipos.AnticipoContable(
                "CLIENTE", FECHA, new BigDecimal("500000"), 55L, "TRANSFERENCIA", 9L));

        Asiento asiento = new AnticipoGenerador(anticipos, cuentas, cuentaPago)
                .generar(new ContextoContabilizacion("ANTICIPO", 70L, EMPRESA, 7));

        GoldenAsientos.assertCoincide("anticipo-cliente.json", asiento);
    }

    @Test
    void cruceDeAnticipoCancelaCarteraSinCaja() {
        when(anticipos.cargarCruce(71L, EMPRESA)).thenReturn(new LectorAnticipos.CruceContable(
                "CLIENTE", FECHA, new BigDecimal("200000"), 55L));

        Asiento asiento = new AnticipoCruceGenerador(anticipos, cuentas)
                .generar(new ContextoContabilizacion("ANTICIPO_CRUCE", 71L, EMPRESA, 7));

        GoldenAsientos.assertCoincide("anticipo-cruce-cliente.json", asiento);
    }

    @Test
    void deterioroNaceSiempreEnBorrador() {
        DeterioroGenerador generador = new DeterioroGenerador(deterioros, cuentas);
        assertTrue(generador.siempreBorrador(),
                "el deterioro debe nacer en borrador sin importar el modo");

        when(deterioros.cargar(80L, EMPRESA)).thenReturn(new LectorDeterioro.DeterioroContable(
                FECHA, new BigDecimal("350000"), "12 facturas"));

        Asiento asiento = generador.generar(
                new ContextoContabilizacion("DETERIORO", 80L, EMPRESA, 7));

        assertTrue(asiento.totalDebito().compareTo(new BigDecimal("350000")) == 0);
        assertTrue(asiento.partidas().get(0).cuentaId() == 5199L
                && asiento.partidas().get(1).cuentaId() == 1399L);
    }
}
