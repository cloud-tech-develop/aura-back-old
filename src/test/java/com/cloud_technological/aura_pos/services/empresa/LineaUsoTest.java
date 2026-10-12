package com.cloud_technological.aura_pos.services.empresa;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** docs/PLAN_LINEA_CONTABILIDAD.md: la línea Contabilidad factura, pero sin POS ni inventario. */
class LineaUsoTest {

    private final List<String> plantilla = LineaUso.CONTABILIDAD.getPlantilla();

    @Test
    void contabilidadFacturaYCompra() {
        assertTrue(plantilla.contains("ventas.facturas"));
        assertTrue(plantilla.contains("ventas.notas-credito/debito"));
        assertTrue(plantilla.contains("catalogo.productos"));
        assertTrue(plantilla.contains("compras.compras"));
    }

    @Test
    void contabilidadSinPosNiInventario() {
        assertFalse(plantilla.contains("principal.punto-de-venta"));
        assertFalse(plantilla.contains("principal.*"));
        assertFalse(plantilla.contains("catalogo.*"));
        assertFalse(plantilla.contains("precios.*"));
        assertTrue(plantilla.stream().noneMatch(c -> c.startsWith("inventario.")));
    }

    @Test
    void losMinimosNoExigenFacturas() {
        // El cliente que factura en otro sistema no usa las facturas de Aura.
        assertFalse(LineaUso.CONTABILIDAD.getMinimos().contains("ventas.facturas"));
    }
}
