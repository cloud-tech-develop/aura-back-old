package com.cloud_technological.aura_pos.services.nomina;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity.RedondeoModo;
import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity.RedondeoPrecision;

/**
 * Redondeo configurado en el concepto (F2 de docs/PLAN_CONCEPTOS_CARGOS_NOMINA.md):
 * precisión (entero, decena, centena, mil) y dirección (automático = mitad hacia
 * arriba, hacia arriba, hacia abajo). Regla pura.
 */
public final class RedondeoConcepto {

    private RedondeoConcepto() {
    }

    /**
     * @param precision NINGUNO | ENTERO | DECENA | CENTENA | MIL (null = NINGUNO)
     * @param modo      AUTOMATICO | ARRIBA | ABAJO (null = AUTOMATICO)
     * @return el valor redondeado, con 2 decimales
     */
    public static BigDecimal aplicar(BigDecimal valor, String precision, String modo) {
        if (valor == null) return null;
        int potencia = switch (precision != null ? precision : RedondeoPrecision.NINGUNO) {
            case RedondeoPrecision.ENTERO -> 0;
            case RedondeoPrecision.DECENA -> 1;
            case RedondeoPrecision.CENTENA -> 2;
            case RedondeoPrecision.MIL -> 3;
            default -> -1;
        };
        if (potencia < 0) return valor;
        // Arriba/abajo en magnitud: una deducción de 1.234 redondeada al mil hacia arriba es 2.000.
        RoundingMode rm = switch (modo != null ? modo : RedondeoModo.AUTOMATICO) {
            case RedondeoModo.ARRIBA -> RoundingMode.UP;
            case RedondeoModo.ABAJO -> RoundingMode.DOWN;
            default -> RoundingMode.HALF_UP;
        };
        return valor.setScale(-potencia, rm).setScale(2, RoundingMode.UNNECESSARY);
    }
}
