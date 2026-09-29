package com.cloud_technological.aura_pos.contabilidad.application.generador;

import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorAjusteBancario;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.entity.ConceptoContable;
import com.cloud_technological.aura_pos.entity.ExtractoLineaEntity;

/**
 * Ajustes de conciliación bancaria (E9) contra golden files: cargos del
 * banco (comisión, GMF) debitan gasto contra el banco; los abonos de
 * intereses debitan el banco contra ingresos financieros.
 */
@ExtendWith(MockitoExtension.class)
class AjusteBancarioGeneradorTest {

    private static final Integer EMPRESA = 1;
    private static final LocalDate FECHA = LocalDate.of(2026, 7, 5);
    private static final Long CUENTA_BANCO = 111005L;
    private static final Long TERCERO_BANCO = 33L;

    @Mock
    private LectorAjusteBancario ajustes;
    @Mock
    private ResolucionCuentas cuentas;

    private AjusteBancarioGenerador generador;

    @BeforeEach
    void setUp() {
        generador = new AjusteBancarioGenerador(ajustes, cuentas);
        lenient().when(cuentas.resolver(eq(EMPRESA), any(ConceptoContable.class)))
                .thenAnswer(inv -> Long.parseLong(
                        ((ConceptoContable) inv.getArgument(1)).getCodigoDefault()));
    }

    @Test
    void naceSiempreContabilizado() {
        assertTrue(generador.siempreContabilizado(),
                "el ajuste lo dispara el contador desde la pantalla de conciliación");
    }

    @Test
    void cargoDeComisionDebitaGastoBancarioContraBanco() {
        when(ajustes.cargar(70L, EMPRESA)).thenReturn(new LectorAjusteBancario.AjusteBancario(
                FECHA, "COMISION MANEJO CUENTA", new BigDecimal("-25000.00"),
                ExtractoLineaEntity.AJUSTE_GASTO_BANCARIO, CUENTA_BANCO, TERCERO_BANCO));

        Asiento asiento = generador.generar(
                new ContextoContabilizacion("AJUSTE_BANCARIO", 70L, EMPRESA, 7));

        GoldenAsientos.assertCoincide("ajuste-comision-bancaria.json", asiento);
    }

    @Test
    void cargoDeGmfDebita530595ContraBanco() {
        when(ajustes.cargar(71L, EMPRESA)).thenReturn(new LectorAjusteBancario.AjusteBancario(
                FECHA, "GMF 4X1000", new BigDecimal("-4000.00"),
                ExtractoLineaEntity.AJUSTE_GMF, CUENTA_BANCO, TERCERO_BANCO));

        Asiento asiento = generador.generar(
                new ContextoContabilizacion("AJUSTE_BANCARIO", 71L, EMPRESA, 7));

        GoldenAsientos.assertCoincide("ajuste-gmf.json", asiento);
    }

    @Test
    void abonoDeInteresesDebitaBancoContraIngresosFinancieros() {
        when(ajustes.cargar(72L, EMPRESA)).thenReturn(new LectorAjusteBancario.AjusteBancario(
                FECHA, "ABONO INTERESES CUENTA AHORROS", new BigDecimal("12345.67"),
                ExtractoLineaEntity.AJUSTE_INTERES, CUENTA_BANCO, TERCERO_BANCO));

        Asiento asiento = generador.generar(
                new ContextoContabilizacion("AJUSTE_BANCARIO", 72L, EMPRESA, 7));

        GoldenAsientos.assertCoincide("ajuste-intereses-abono.json", asiento);
    }

    @Test
    void cargoDeInteresesVaAGastosFinancieros() {
        when(ajustes.cargar(73L, EMPRESA)).thenReturn(new LectorAjusteBancario.AjusteBancario(
                FECHA, "INTERESES SOBREGIRO", new BigDecimal("-9000.00"),
                ExtractoLineaEntity.AJUSTE_INTERES, CUENTA_BANCO, TERCERO_BANCO));

        Asiento asiento = generador.generar(
                new ContextoContabilizacion("AJUSTE_BANCARIO", 73L, EMPRESA, 7));

        assertTrue(asiento.partidas().get(0).cuentaId() == 5305L,
                "el interés pagado debita gastos financieros (5305)");
        assertTrue(asiento.partidas().get(1).cuentaId().equals(CUENTA_BANCO));
        assertTrue(asiento.totalDebito().compareTo(new BigDecimal("9000.00")) == 0);
    }
}
