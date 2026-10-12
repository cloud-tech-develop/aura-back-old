package com.cloud_technological.aura_pos.contabilidad.application.generador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorCausacionGasto;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorCausacionGasto.CausacionGastoContable;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorCausacionGasto.LineaContable;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Partida;
import com.cloud_technological.aura_pos.entity.ConceptoContable;

/** Asiento de la causación de gasto (V201). */
@ExtendWith(MockitoExtension.class)
class CausacionGastoGeneradorTest {

    private static final Integer EMPRESA = 1;
    private static final Long PROVEEDOR = 50L;
    private static final Long ARRIENDO = 512010L;
    private static final Long HONORARIOS = 511025L;

    @Mock
    private LectorCausacionGasto lector;
    @Mock
    private ResolucionCuentas cuentas;

    private CausacionGastoGenerador generador;

    @BeforeEach
    void setUp() {
        generador = new CausacionGastoGenerador(lector, cuentas);
        lenient().when(cuentas.resolver(eq(EMPRESA), any(ConceptoContable.class)))
                .thenAnswer(inv -> Long.parseLong(((ConceptoContable) inv.getArgument(1)).getCodigoDefault()));
    }

    private static BigDecimal v(String s) {
        return new BigDecimal(s);
    }

    private static BigDecimal debito(Asiento a, Long cuenta) {
        return a.partidas().stream().filter(p -> p.cuentaId().equals(cuenta))
                .map(Partida::debito).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal credito(Asiento a, Long cuenta) {
        return a.partidas().stream().filter(p -> p.cuentaId().equals(cuenta))
                .map(Partida::credito).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void gastoConIvaYRetencionesQuedaCuadradoContraLaCuentaPorPagar() {
        // Arriendo 1.000.000 + IVA 19 % (190.000) − retefuente 3,5 % (35.000)
        // − reteIVA 15 % (28.500) − reteICA 0,966 % (9.660) = 1.116.840
        when(lector.cargar(9L, EMPRESA)).thenReturn(new CausacionGastoContable(
                "CG-1", LocalDate.of(2026, 10, 1), PROVEEDOR, "Inmobiliaria", "FE-77", null, v("1116840.00"),
                List.of(new LineaContable(ARRIENDO, "Arriendo octubre", 3L, v("1000000.00"), v("190000.00"), true,
                        v("35000.00"), null, v("28500.00"), v("9660.00")))));

        Asiento a = generador.generar(new ContextoContabilizacion("CAUSACION_GASTO", 9L, EMPRESA, 7));

        assertEquals(0, a.totalDebito().compareTo(a.totalCredito()));
        assertEquals(0, v("1000000").compareTo(debito(a, ARRIENDO)));
        assertEquals(0, v("190000").compareTo(debito(a, 240802L)));
        assertEquals(0, v("35000").compareTo(credito(a, 2365L)));
        assertEquals(0, v("28500").compareTo(credito(a, 2367L)));
        assertEquals(0, v("9660").compareTo(credito(a, 2368L)));
        assertEquals(0, v("1116840").compareTo(credito(a, 2205L)));
        // El gasto lleva su centro de costo y el proveedor.
        Partida gasto = a.partidas().stream().filter(p -> p.cuentaId().equals(ARRIENDO)).findFirst().orElseThrow();
        assertEquals(3L, gasto.centroCostoId());
        assertEquals(PROVEEDOR, gasto.terceroId());
    }

    @Test
    void ivaNoDescontableVaAlGastoYLaRetencionALaCuentaDelConcepto() {
        Long cuentaConcepto = 236515L;
        Long cuentaPagar = 2335L;
        when(lector.cargar(10L, EMPRESA)).thenReturn(new CausacionGastoContable(
                "CG-2", LocalDate.of(2026, 10, 2), PROVEEDOR, "Abogado", null, cuentaPagar, v("1009000.00"),
                List.of(new LineaContable(HONORARIOS, null, null, v("900000.00"), v("171000.00"), false,
                        v("99000.00"), cuentaConcepto, BigDecimal.ZERO, BigDecimal.ZERO),
                        new LineaContable(ARRIENDO, null, null, v("37000.00"), BigDecimal.ZERO, true,
                                BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO))));

        Asiento a = generador.generar(new ContextoContabilizacion("CAUSACION_GASTO", 10L, EMPRESA, 7));

        assertEquals(0, a.totalDebito().compareTo(a.totalCredito()));
        assertEquals(0, v("1071000").compareTo(debito(a, HONORARIOS)), "el IVA no descontable se suma al gasto");
        assertEquals(0, BigDecimal.ZERO.compareTo(debito(a, 240802L)));
        assertEquals(0, v("99000").compareTo(credito(a, cuentaConcepto)));
        assertEquals(0, v("1009000").compareTo(credito(a, cuentaPagar)));
    }
}
