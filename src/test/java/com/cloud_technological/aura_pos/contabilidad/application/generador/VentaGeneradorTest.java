package com.cloud_technological.aura_pos.contabilidad.application.generador;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cloud_technological.aura_pos.contabilidad.GoldenAsientos;
import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorVenta;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorVenta.LineaVenta;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorVenta.PagoVenta;
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorVenta.VentaContable;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaPago;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaProducto;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaProducto.CuentasProducto;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.domain.AsientoDescuadradoException;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.entity.ConceptoContable;

/**
 * Asiento de venta contra los golden files (matriz E0 + categorías E4).
 * Sin categorías, los productos resuelven a las cuentas default de la
 * empresa (comportamiento idéntico al motor original).
 */
@ExtendWith(MockitoExtension.class)
class VentaGeneradorTest {

    private static final Integer EMPRESA = 1;
    private static final LocalDate FECHA = LocalDate.of(2026, 7, 9);
    private static final Long CLIENTE = 55L;

    private static final CuentasProducto GENERAL =
            new CuentasProducto(4135L, 6135L, 1435L, 4135L, false);

    @Mock
    private LectorVenta ventas;
    @Mock
    private ResolucionCuentas cuentas;
    @Mock
    private ResolucionCuentaPago cuentaPago;
    @Mock
    private ResolucionCuentaProducto cuentaProducto;
    @Mock
    private com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionImpuesto impuesto;

    @InjectMocks
    private VentaGenerador generador;

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
        lenient().when(cuentaProducto.resolver(anyLong(), eq(EMPRESA))).thenReturn(GENERAL);
    }

    private Asiento generar(Long ventaId, VentaContable venta) {
        when(ventas.cargar(eq(ventaId), eq(EMPRESA))).thenReturn(venta);
        return generador.generar(new ContextoContabilizacion("VENTA", ventaId, EMPRESA, 7));
    }

    @Test
    void ventaContadoEfectivoConCosto() {
        Asiento asiento = generar(100L, new VentaContable(
                FECHA, " — FE-123", CLIENTE,
                new BigDecimal("119000"), new BigDecimal("19000"),
                BigDecimal.ZERO,
                null,
                List.of(new LineaVenta(1L, new BigDecimal("100000"), BigDecimal.ZERO, new BigDecimal("60000"))),
                List.of(new PagoVenta("EFECTIVO", new BigDecimal("119000"), null))));

        GoldenAsientos.assertCoincide("venta-contado-efectivo.json", asiento);
    }

    @Test
    void ventaCreditoPuroConCartera() {
        Asiento asiento = generar(200L, new VentaContable(
                FECHA, " — FE-124", CLIENTE,
                new BigDecimal("119000"), new BigDecimal("19000"),
                new BigDecimal("119000"),
                null,
                List.of(new LineaVenta(1L, new BigDecimal("100000"), BigDecimal.ZERO, new BigDecimal("60000"))),
                List.of(new PagoVenta("CREDITO", new BigDecimal("119000"), null))));

        GoldenAsientos.assertCoincide("venta-credito.json", asiento);
    }

    @Test
    void ventaMixtaMultipagoSinCosto() {
        Asiento asiento = generar(300L, new VentaContable(
                FECHA, " — FE-125", CLIENTE,
                new BigDecimal("119000"), new BigDecimal("19000"),
                new BigDecimal("39000"),
                null,
                List.of(new LineaVenta(1L, new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO)),
                List.of(new PagoVenta("EFECTIVO", new BigDecimal("50000"), null),
                        new PagoVenta("TARJETA", new BigDecimal("30000"), 9L))));

        GoldenAsientos.assertCoincide("venta-mixta-multipago.json", asiento);
    }

    /**
     * Aceptación E4: 2 productos de categorías distintas + 1 servicio →
     * 3 créditos de ingreso a cuentas distintas, un par COGS por cada
     * cuenta de costo de los bienes, y ninguno para el servicio.
     */
    @Test
    void ventaMixtaPorCategorias() {
        Map<Long, CuentasProducto> porProducto = Map.of(
                10L, new CuentasProducto(413505L, 613505L, 143505L, 413505L, false),
                20L, new CuentasProducto(413510L, 613510L, 143510L, 413510L, false),
                30L, new CuentasProducto(4145L, 6135L, 1435L, 4145L, true));
        lenient().when(cuentaProducto.resolver(anyLong(), eq(EMPRESA)))
                .thenAnswer(inv -> porProducto.get((Long) inv.getArgument(0)));

        Asiento asiento = generar(500L, new VentaContable(
                FECHA, " — FE-200", CLIENTE,
                new BigDecimal("238000"), new BigDecimal("38000"),
                BigDecimal.ZERO,
                null,
                List.of(new LineaVenta(10L, new BigDecimal("100000"), BigDecimal.ZERO, new BigDecimal("55000")),
                        new LineaVenta(20L, new BigDecimal("60000"), BigDecimal.ZERO, new BigDecimal("30000")),
                        new LineaVenta(30L, new BigDecimal("40000"), BigDecimal.ZERO, BigDecimal.ZERO)),
                List.of(new PagoVenta("EFECTIVO", new BigDecimal("238000"), null))));

        GoldenAsientos.assertCoincide("venta-mixta-categorias.json", asiento);
    }

    /**
     * Aceptación E5: producto con IVA 19% + producto con INC 8% → dos
     * líneas de impuesto a cuentas distintas (240801 / 246801).
     */
    @Test
    void ventaConIvaEIncACuentasSeparadas() {
        lenient().when(impuesto.resolverGenerado(eq(10L), eq(EMPRESA))).thenReturn(240801L);
        lenient().when(impuesto.resolverGenerado(eq(20L), eq(EMPRESA))).thenReturn(246801L);

        Asiento asiento = generar(600L, new VentaContable(
                FECHA, " — FE-300", CLIENTE,
                new BigDecimal("173000"), new BigDecimal("23000"),
                BigDecimal.ZERO,
                null,
                List.of(new LineaVenta(10L, new BigDecimal("100000"), new BigDecimal("19000"), BigDecimal.ZERO),
                        new LineaVenta(20L, new BigDecimal("50000"), new BigDecimal("4000"), BigDecimal.ZERO)),
                List.of(new PagoVenta("EFECTIVO", new BigDecimal("173000"), null))));

        GoldenAsientos.assertCoincide("venta-iva-inc.json", asiento);
    }

    @Test
    void ventaSinMovimientosRevienta() {
        when(ventas.cargar(anyLong(), eq(EMPRESA))).thenReturn(new VentaContable(
                FECHA, "", null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, List.of(), List.of()));

        assertThrows(AsientoDescuadradoException.class,
                () -> generador.generar(new ContextoContabilizacion("VENTA", 400L, EMPRESA, 7)));
    }
}
