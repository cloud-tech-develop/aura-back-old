package com.cloud_technological.aura_pos.contabilidad.application.generador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.cloud_technological.aura_pos.contabilidad.application.port.LectorObsequio;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaProducto;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionImpuesto;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Partida;
import com.cloud_technological.aura_pos.entity.ConceptoContable;

/**
 * Asiento del obsequio: sale inventario contra gasto de promoción, sin ingreso,
 * y con el IVA que la empresa asume por el retiro.
 */
@ExtendWith(MockitoExtension.class)
class ObsequioGeneradorTest {

    private static final Integer EMPRESA = 1;
    private static final LocalDate FECHA = LocalDate.of(2026, 8, 13);
    private static final Long PRODUCTO = 77L;
    private static final Long INVENTARIO = 1435L;
    private static final Long IVA_GENERADO = 240801L;

    @Mock
    private LectorObsequio obsequios;
    @Mock
    private ResolucionCuentas cuentas;
    @Mock
    private ResolucionCuentaProducto cuentaProducto;
    @Mock
    private ResolucionImpuesto impuesto;

    private ObsequioGenerador generador;

    @BeforeEach
    void resolvers() {
        lenient().when(cuentas.resolver(eq(EMPRESA), any(ConceptoContable.class)))
                .thenAnswer(inv -> Long.parseLong(
                        ((ConceptoContable) inv.getArgument(1)).getCodigoDefault()));
        lenient().when(impuesto.resolverGenerado(eq(PRODUCTO), eq(EMPRESA)))
                .thenReturn(IVA_GENERADO);
        generador = new ObsequioGenerador(obsequios, cuentas, cuentaProducto, impuesto);
    }

    private void productoEsMercancia() {
        when(cuentaProducto.resolver(PRODUCTO, EMPRESA)).thenReturn(
                new ResolucionCuentaProducto.CuentasProducto(4135L, 6135L, INVENTARIO, 4135L, false));
    }

    @Test
    void obsequioDeMercanciaDescargaInventarioContraGastoYCausaElIva() {
        productoEsMercancia();
        when(obsequios.cargar(30L, EMPRESA)).thenReturn(new LectorObsequio.ObsequioContable(
                FECHA, "PROMOCION", 9L, 3L, true,
                List.of(new LectorObsequio.LineaObsequio(
                        PRODUCTO, new BigDecimal("5000.00"), new BigDecimal("1197.48")))));

        Asiento asiento = generador.generar(
                new ContextoContabilizacion("OBSEQUIO", 30L, EMPRESA, 7));

        assertEquals(4, asiento.partidas().size());
        assertEquals(0, asiento.totalDebito().compareTo(new BigDecimal("6197.48")));
        assertEquals(0, asiento.totalCredito().compareTo(asiento.totalDebito()),
                "regalar no descuadra: débito y crédito tienen que ser iguales");

        // El costo va al gasto de obsequios (523550), NO a la 5195 de mermas.
        assertEquals(0, debitoDe(asiento, 523550L).compareTo(new BigDecimal("5000.00")));
        assertEquals(0, creditoDe(asiento, INVENTARIO).compareTo(new BigDecimal("5000.00")));
        // El IVA del retiro lo asume la empresa contra el IVA generado.
        assertEquals(0, debitoDe(asiento, 529505L).compareTo(new BigDecimal("1197.48")));
        assertEquals(0, creditoDe(asiento, IVA_GENERADO).compareTo(new BigDecimal("1197.48")));

        // Regalar no es vender: no puede aparecer ingreso por ningún lado.
        assertTrue(asiento.partidas().stream().noneMatch(p -> p.cuentaId().equals(4135L)),
                "el obsequio no genera ingreso");
    }

    @Test
    void sinIvaSoloQuedaElParDeCostoEInventario() {
        productoEsMercancia();
        when(obsequios.cargar(31L, EMPRESA)).thenReturn(new LectorObsequio.ObsequioContable(
                FECHA, "MUESTRA_COMERCIAL", null, null, false,
                List.of(new LectorObsequio.LineaObsequio(
                        PRODUCTO, new BigDecimal("5000.00"), new BigDecimal("1197.48")))));

        Asiento asiento = generador.generar(
                new ContextoContabilizacion("OBSEQUIO", 31L, EMPRESA, 7));

        assertEquals(2, asiento.partidas().size(),
                "con genera_iva en false el impuesto no se causa aunque venga calculado");
        assertEquals(0, asiento.totalDebito().compareTo(new BigDecimal("5000.00")));
        verify(impuesto, never()).resolverGenerado(any(), any());
    }

    @Test
    void obsequioDeServicioNoDescargaInventario() {
        when(cuentaProducto.resolver(PRODUCTO, EMPRESA)).thenReturn(
                new ResolucionCuentaProducto.CuentasProducto(4135L, 6135L, INVENTARIO, 4135L, true));
        when(obsequios.cargar(32L, EMPRESA)).thenReturn(new LectorObsequio.ObsequioContable(
                FECHA, "CORTESIA_CLIENTE", null, null, true,
                List.of(new LectorObsequio.LineaObsequio(
                        PRODUCTO, new BigDecimal("5000.00"), new BigDecimal("950.00")))));

        Asiento asiento = generador.generar(
                new ContextoContabilizacion("OBSEQUIO", 32L, EMPRESA, 7));

        assertTrue(asiento.partidas().stream().noneMatch(p -> p.cuentaId().equals(INVENTARIO)),
                "regalar una hora de servicio no mueve el almacén");
        assertEquals(0, asiento.totalDebito().compareTo(new BigDecimal("950.00")));
    }

    private BigDecimal debitoDe(Asiento asiento, Long cuentaId) {
        return asiento.partidas().stream()
                .filter(p -> p.cuentaId().equals(cuentaId))
                .map(Partida::debito)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal creditoDe(Asiento asiento, Long cuentaId) {
        return asiento.partidas().stream()
                .filter(p -> p.cuentaId().equals(cuentaId))
                .map(Partida::credito)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
