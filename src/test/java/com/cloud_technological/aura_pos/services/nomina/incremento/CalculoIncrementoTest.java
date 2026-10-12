package com.cloud_technological.aura_pos.services.nomina.incremento;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.cloud_technological.aura_pos.services.nomina.incremento.CalculoIncremento.Redondeo;
import com.cloud_technological.aura_pos.services.nomina.incremento.CalculoIncremento.Tipo;

class CalculoIncrementoTest {

    private static final BigDecimal SMMLV = new BigDecimal("1423500");

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void porcentajeConRedondeosALaCentena() {
        // 1.234.567 × 1,095 = 1.351.850,865
        BigDecimal actual = bd("1234567");
        assertEquals(bd("1351850.87"), CalculoIncremento.nuevoSalario(actual, Tipo.PORCENTAJE, bd("9.5"), SMMLV, Redondeo.NINGUNO));
        assertEquals(bd("1351851.00"), CalculoIncremento.nuevoSalario(actual, Tipo.PORCENTAJE, bd("9.5"), SMMLV, Redondeo.PESO));
        assertEquals(bd("1351900.00"), CalculoIncremento.nuevoSalario(actual, Tipo.PORCENTAJE, bd("9.5"), SMMLV, Redondeo.CENTENA));
        assertEquals(bd("1351900.00"), CalculoIncremento.nuevoSalario(actual, Tipo.PORCENTAJE, bd("9.5"), SMMLV, Redondeo.CENTENA_ARRIBA));
        assertEquals(bd("1351800.00"), CalculoIncremento.nuevoSalario(actual, Tipo.PORCENTAJE, bd("9.5"), SMMLV, Redondeo.CENTENA_ABAJO));
        assertEquals(bd("1352000.00"), CalculoIncremento.nuevoSalario(actual, Tipo.PORCENTAJE, bd("9.5"), SMMLV, Redondeo.MIL));
    }

    @Test
    void valorAdicionalYNuevoValor() {
        assertEquals(bd("1600000.00"),
                CalculoIncremento.nuevoSalario(bd("1500000"), Tipo.VALOR_ADICIONAL, bd("100000"), SMMLV, Redondeo.NINGUNO));
        assertEquals(bd("2000000.00"),
                CalculoIncremento.nuevoSalario(bd("1500000"), Tipo.NUEVO_VALOR, bd("2000000"), SMMLV, Redondeo.NINGUNO));
    }

    @Test
    void smmlvSoloSubeAQuienGanaMenos() {
        assertEquals(bd("1423500.00"),
                CalculoIncremento.nuevoSalario(bd("1300000"), Tipo.SMMLV, null, SMMLV, Redondeo.NINGUNO));
        assertEquals(bd("2000000.00"),
                CalculoIncremento.nuevoSalario(bd("2000000"), Tipo.SMMLV, null, SMMLV, Redondeo.NINGUNO));
    }
}
