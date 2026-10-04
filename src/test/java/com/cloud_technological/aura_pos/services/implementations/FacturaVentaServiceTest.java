package com.cloud_technological.aura_pos.services.implementations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.cloud_technological.aura_pos.dto.factura_venta.FacturaVentaDtos;
import com.cloud_technological.aura_pos.dto.ventas.CreateVentaDto;
import com.cloud_technological.aura_pos.entity.FacturaVentaEntity;

/** Facturación (FV1): totales de línea y la venta que se arma al emitir. */
class FacturaVentaServiceTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void laLineaCalculaBaseNetaEImpuesto() {
        var t = FacturaVentaService.totales(bd("3"), bd("100000"), bd("30000"), bd("19"));
        assertEquals(bd("270000.00"), t.base());
        assertEquals(bd("51300.00"), t.impuesto());
        assertEquals(bd("321300.00"), t.total());
    }

    @Test
    void servicioExentoNoLlevaImpuesto() {
        var t = FacturaVentaService.totales(bd("1"), bd("850000"), BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(bd("0.00"), t.impuesto());
        assertEquals(bd("850000.00"), t.total());
    }

    private static FacturaVentaDtos.Detalle conLinea(String subtotal) {
        FacturaVentaDtos.LineaDto l = new FacturaVentaDtos.LineaDto();
        l.setProductoId(9L);
        l.setCantidad(bd("1"));
        l.setPrecioUnitario(bd("100000"));
        l.setDescuentoValor(BigDecimal.ZERO);
        l.setImpuestoValor(bd("19000.00"));
        l.setSubtotalLinea(bd(subtotal));
        l.setDescripcion("Mantenimiento preventivo octubre");
        FacturaVentaDtos.Detalle d = new FacturaVentaDtos.Detalle();
        d.setLineas(List.of(l));
        return d;
    }

    @Test
    void aCreditoLaVentaVaACarteraConElVencimiento() {
        FacturaVentaEntity f = FacturaVentaEntity.builder().id(5L).sucursalId(1).clienteId(77L)
                .formaPago("CREDITO").fechaVencimiento(LocalDate.of(2026, 11, 2)).ordenCompra("OC-123").build();
        CreateVentaDto v = FacturaVentaService.armarVenta(f, conLinea("119000.00"));
        assertTrue(v.isDesdeFacturacion());
        assertEquals("FACTURA", v.getTipoDocumento());
        assertEquals("CREDITO", v.getPagos().get(0).getMetodoPago());
        assertEquals(bd("119000.00"), v.getPagos().get(0).getMonto());
        assertEquals(LocalDate.of(2026, 11, 2), v.getFechaVencimiento().toLocalDate());
        assertEquals("OC cliente: OC-123", v.getObservaciones());
        assertEquals("Mantenimiento preventivo octubre", v.getDetalles().get(0).getDescripcion());
        assertNull(v.getTurnoCajaId());
    }

    @Test
    void desdeCotizacionLaVentaLlevaElOrigenPorLinea() {
        FacturaVentaEntity f = FacturaVentaEntity.builder().id(7L).sucursalId(1).clienteId(77L)
                .formaPago("CREDITO").cotizacionId(40L).pedidoVendedorId(null).build();
        FacturaVentaDtos.Detalle d = conLinea("119000.00");
        d.getLineas().get(0).setCotizacionDetalleId(401L);
        CreateVentaDto v = FacturaVentaService.armarVenta(f, d);
        assertEquals(40L, v.getCotizacionId());
        assertEquals(401L, v.getDetalles().get(0).getCotizacionDetalleId());
        assertNull(v.getPedidoVendedorId());
    }

    @Test
    void deContadoEntraPorElBancoElegido() {
        FacturaVentaEntity f = FacturaVentaEntity.builder().id(6L).sucursalId(1).clienteId(77L)
                .formaPago("CONTADO").metodoPago("TRANSFERENCIA").cuentaBancariaId(3L).build();
        CreateVentaDto v = FacturaVentaService.armarVenta(f, conLinea("119000.00"));
        assertEquals("TRANSFERENCIA", v.getPagos().get(0).getMetodoPago());
        assertEquals(3L, v.getPagos().get(0).getCuentaBancariaId());
        assertNull(v.getFechaVencimiento());
    }
}
