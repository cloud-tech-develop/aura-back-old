package com.cloud_technological.aura_pos.contabilidad.application.generador;

import static org.mockito.ArgumentMatchers.any;
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
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorDiferenciaCaja;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorDiferenciaCaja.DiferenciaCaja;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.entity.ConceptoContable;

/** Diferencias de cierre de caja (E3) contra golden files. */
@ExtendWith(MockitoExtension.class)
class DiferenciaCajaGeneradorTest {

    private static final Integer EMPRESA = 1;
    private static final LocalDate FECHA = LocalDate.of(2026, 7, 9);

    @Mock
    private LectorDiferenciaCaja turnos;
    @Mock
    private ResolucionCuentas cuentas;

    @BeforeEach
    void resolvers() {
        lenient().when(cuentas.resolver(eq(EMPRESA), any(ConceptoContable.class)))
                .thenAnswer(inv -> Long.parseLong(
                        ((ConceptoContable) inv.getArgument(1)).getCodigoDefault()));
    }

    @Test
    void faltanteVaAGastoContraCaja() {
        when(turnos.cargar(40L, EMPRESA)).thenReturn(
                new DiferenciaCaja(FECHA, new BigDecimal("-15000")));

        Asiento asiento = new DiferenciaCajaGenerador(turnos, cuentas)
                .generar(new ContextoContabilizacion("DIFERENCIA_CAJA", 40L, EMPRESA, null));

        GoldenAsientos.assertCoincide("diferencia-caja-faltante.json", asiento);
    }

    @Test
    void sobranteVaACajaContraIngreso() {
        when(turnos.cargar(41L, EMPRESA)).thenReturn(
                new DiferenciaCaja(FECHA, new BigDecimal("8000")));

        Asiento asiento = new DiferenciaCajaGenerador(turnos, cuentas)
                .generar(new ContextoContabilizacion("DIFERENCIA_CAJA", 41L, EMPRESA, null));

        GoldenAsientos.assertCoincide("diferencia-caja-sobrante.json", asiento);
    }
}
