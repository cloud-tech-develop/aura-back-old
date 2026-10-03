package com.cloud_technological.aura_pos.services.implementations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaPago;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaProducto;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaProducto.CompraProducto;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionImpuesto;
import com.cloud_technological.aura_pos.entity.AsientoDetalleEntity;
import com.cloud_technological.aura_pos.entity.ConceptoContable;
import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;
import com.cloud_technological.aura_pos.services.ConfiguracionContableService;
import com.cloud_technological.aura_pos.services.implementations.ContabilidadAutoServiceImpl.DatosCompraAsiento;
import com.cloud_technological.aura_pos.services.implementations.ContabilidadAutoServiceImpl.LineaCompraAsiento;
import com.cloud_technological.aura_pos.services.implementations.ContabilidadAutoServiceImpl.PagoCompraAsiento;
import com.cloud_technological.aura_pos.utils.ClasificacionItem;

/**
 * Asiento de compra con la clasificación del catálogo (Fase 2) y el reparto
 * proporcional de fletes. Es el mismo método que usa la vista previa (Fase 4).
 */
class AsientoCompraLineasTest {

    private static final Integer EMPRESA = 1;
    private static final long INVENTARIO = 1435, ACTIVO = 1528, IVA = 240802, RETEFUENTE = 2365,
            PROVEEDORES = 2205, CAJA = 110505;

    private ContabilidadAutoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ContabilidadAutoServiceImpl();
        ResolucionCuentaProducto producto = mock(ResolucionCuentaProducto.class);
        when(producto.resolverCompra(eq(10L), eq(EMPRESA)))
                .thenReturn(new CompraProducto(ClasificacionItem.PRODUCTO, INVENTARIO, null, null, null, null, null));
        when(producto.resolverCompra(eq(20L), eq(EMPRESA)))
                .thenReturn(new CompraProducto(ClasificacionItem.ACTIVO_FIJO, ACTIVO, 1592L, 5160L, 60, null, null));
        ResolucionImpuesto impuesto = mock(ResolucionImpuesto.class);
        when(impuesto.resolverDescontable(any(), eq(EMPRESA))).thenReturn(IVA);
        ConfiguracionContableService config = mock(ConfiguracionContableService.class);
        when(config.resolverCuenta(EMPRESA, ConceptoContable.RETEFUENTE_PRACTICADA)).thenReturn(cuenta(RETEFUENTE));
        when(config.resolverCuenta(EMPRESA, ConceptoContable.PROVEEDORES)).thenReturn(cuenta(PROVEEDORES));
        ResolucionCuentaPago pago = mock(ResolucionCuentaPago.class);
        when(pago.resolver(eq(EMPRESA), eq("EFECTIVO"), any(), any())).thenReturn(CAJA);
        PlanCuentaJPARepository plan = mock(PlanCuentaJPARepository.class);
        when(plan.findByIdAndEmpresaId(CAJA, EMPRESA)).thenReturn(java.util.Optional.of(cuenta(CAJA)));

        ReflectionTestUtils.setField(service, "resolucionCuentaProducto", producto);
        ReflectionTestUtils.setField(service, "resolucionImpuesto", impuesto);
        ReflectionTestUtils.setField(service, "config", config);
        ReflectionTestUtils.setField(service, "resolucionCuentaPago", pago);
        ReflectionTestUtils.setField(service, "planRepo", plan);
    }

    private static PlanCuentaEntity cuenta(long id) {
        PlanCuentaEntity c = new PlanCuentaEntity();
        c.setId(id);
        c.setCodigo(String.valueOf(id));
        return c;
    }

    private static Map<Long, BigDecimal> debitos(List<AsientoDetalleEntity> l) {
        return l.stream().filter(d -> d.getDebito().signum() > 0)
                .collect(Collectors.toMap(AsientoDetalleEntity::getCuentaId, AsientoDetalleEntity::getDebito, BigDecimal::add));
    }

    private static Map<Long, BigDecimal> creditos(List<AsientoDetalleEntity> l) {
        return l.stream().filter(d -> d.getCredito().signum() > 0)
                .collect(Collectors.toMap(AsientoDetalleEntity::getCuentaId, AsientoDetalleEntity::getCredito, BigDecimal::add));
    }

    private static void igual(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real), () -> "esperado " + esperado + " y fue " + real);
    }

    @Test
    void mercanciaYActivoVanACuentasDistintasYLosFletesSeReparten() {
        // $2.000 de mercancía + $1.000 de un computador, flete $300, IVA $570, contado.
        BigDecimal neta = new BigDecimal("3870");
        List<AsientoDetalleEntity> l = service.lineasCompra(EMPRESA, new DatosCompraAsiento(
                new BigDecimal("3000"), BigDecimal.ZERO, new BigDecimal("300"), new BigDecimal("570"), neta, 7L,
                false, null,
                List.of(new LineaCompraAsiento(10L, new BigDecimal("2000"), new BigDecimal("380")),
                        new LineaCompraAsiento(20L, new BigDecimal("1000"), new BigDecimal("190"))),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(new PagoCompraAsiento("EFECTIVO", null, null, neta))));

        Map<Long, BigDecimal> db = debitos(l);
        igual("2200", db.get(INVENTARIO)); // 2.000 + 2/3 del flete
        igual("1100", db.get(ACTIVO));     // 1.000 + 1/3 del flete: igual a la ficha del activo
        igual("570", db.get(IVA));
        igual("3870", creditos(l).get(CAJA));
    }

    @Test
    void compraACreditoConRetencionDejaElSaldoAlProveedor() {
        // $1.000 de mercancía, retefuente 2,5 % = $25, crédito.
        List<AsientoDetalleEntity> l = service.lineasCompra(EMPRESA, new DatosCompraAsiento(
                new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("975"), 7L,
                false, null, List.of(new LineaCompraAsiento(10L, new BigDecimal("1000"), BigDecimal.ZERO)),
                new BigDecimal("25"), BigDecimal.ZERO, BigDecimal.ZERO, List.of()));

        Map<Long, BigDecimal> cr = creditos(l);
        igual("1000", debitos(l).get(INVENTARIO));
        igual("25", cr.get(RETEFUENTE));
        igual("975", cr.get(PROVEEDORES));
    }

    @Test
    void notaCreditoInvierteLosLados() {
        List<AsientoDetalleEntity> l = service.lineasCompra(EMPRESA, new DatosCompraAsiento(
                new BigDecimal("-500"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-500"), 7L,
                true, null, List.of(new LineaCompraAsiento(10L, new BigDecimal("-500"), BigDecimal.ZERO)),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()));

        igual("500", creditos(l).get(INVENTARIO));
        igual("500", debitos(l).get(PROVEEDORES));
    }
}
