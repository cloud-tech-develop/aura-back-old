package com.cloud_technological.aura_pos.dto.producto_presentacion;

import java.math.BigDecimal;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Todas las conversiones de un producto de una vez (sección "Unidades y
 * conversiones"): "1 Paca = 24 und", "1 Libra = 0,5 kg". Lo que no venga se
 * desactiva; lo que venga sin id se crea (o se reactiva si ya existía con el
 * mismo factor).
 */
@Getter
@Setter
public class GuardarConversionesDto {

    /** ¿Se vende también en la unidad base (suelto)? */
    private Boolean vendePorUnidad;

    private List<Conversion> conversiones;

    @Getter
    @Setter
    public static class Conversion {
        private Long id;
        private String nombre;
        /** Unidades base que equivale 1 de esta conversión. */
        private BigDecimal factor;
        private String codigoBarras;
        private BigDecimal precio;
        private BigDecimal costo;
        private Boolean seVende;
        /** La que se propone al comprar. Como máximo una. */
        private Boolean esDefaultCompra;
    }
}
