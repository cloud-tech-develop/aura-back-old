package com.cloud_technological.aura_pos.services.implementations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/** AIU: A, I y U sobre el costo directo; IVA solo sobre la Utilidad. */
class FacturaAiuServiceTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void ivaSoloSobreLaUtilidad() {
        // Obra de $10.000.000 con A 10%, I 5%, U 5% e IVA 19%.
        List<FacturaAiuService.LineaAiu> l = FacturaAiuService.calcular(bd("10000000"), bd("10"), bd("5"), bd("5"), bd("19"));
        assertEquals(3, l.size());
        assertEquals(bd("1000000.00"), l.get(0).valor());
        assertEquals(bd("0.00"), l.get(0).iva());
        assertEquals(bd("500000.00"), l.get(1).valor());
        assertEquals(bd("0.00"), l.get(1).iva());
        assertEquals(bd("500000.00"), l.get(2).valor());
        assertEquals(bd("95000.00"), l.get(2).iva());
        BigDecimal total = bd("10000000");
        for (var x : l) total = total.add(x.valor()).add(x.iva());
        assertEquals(bd("12095000.00"), total);
    }

    @Test
    void elComponenteEnCeroNoSeAgrega() {
        List<FacturaAiuService.LineaAiu> l = FacturaAiuService.calcular(bd("1000000"), bd("8"), BigDecimal.ZERO, bd("4"), bd("19"));
        assertEquals(2, l.size());
        assertEquals(FacturaAiuService.ADMINISTRACION, l.get(0).tipo());
        assertEquals(FacturaAiuService.UTILIDAD, l.get(1).tipo());
        assertEquals(bd("7600.00"), l.get(1).iva());
    }
}
