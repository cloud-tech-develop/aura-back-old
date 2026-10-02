package com.cloud_technological.aura_pos.utils;

import org.springframework.http.HttpStatus;

import com.cloud_technological.aura_pos.entity.ConceptoContable;

/**
 * Qué es un ítem del catálogo (V185). Todo lo que la empresa compra vive en el
 * mismo catálogo; la clasificación decide qué hace la compra con la línea y a
 * qué cuenta va el débito cuando ni el producto ni su categoría contable dicen
 * otra cosa. Eje distinto de {@code tipo_producto} (cómo se vende: estándar,
 * kit, pesable) y de {@code uso_producto} (venta o insumo de receta).
 */
public enum ClasificacionItem {

    /** Mercancía: stock, kardex, costo promedio. */
    PRODUCTO(ConceptoContable.INVENTARIO, "14"),
    /** Se vende sin existencias; lo que se compra de él es costo. */
    SERVICIO(ConceptoContable.COSTO_VENTAS, "5", "6", "7"),
    GASTO(ConceptoContable.GASTO_GENERAL, "5"),
    DOTACION(ConceptoContable.GASTO_DOTACION, "5"),
    /** Al comprarlo crea una ficha de activo por unidad. */
    ACTIVO_FIJO(ConceptoContable.ACTIVO_FIJO_COMPRA, "15"),
    /** Licencias y software: ficha que se amortiza como el activo. */
    INTANGIBLE(ConceptoContable.INTANGIBLES, "16"),
    /** Seguros, suscripciones: al comprarlo crea el diferido y sus cuotas. */
    DIFERIDO(ConceptoContable.GASTOS_PAGADOS_ANTICIPADO, "17");

    private final ConceptoContable conceptoCompra;
    private final String[] prefijosCompra;

    ClasificacionItem(ConceptoContable conceptoCompra, String... prefijosCompra) {
        this.conceptoCompra = conceptoCompra;
        this.prefijosCompra = prefijosCompra;
    }

    /** Concepto de la empresa al que va la compra si nadie configuró otra cuenta. */
    public ConceptoContable conceptoCompra() {
        return conceptoCompra;
    }

    /** Clases del PUC que acepta la cuenta de la compra de esta clasificación. */
    public String[] prefijosCompra() {
        return prefijosCompra.clone();
    }

    public boolean mueveInventario() {
        return this == PRODUCTO;
    }

    /** Solo lo que se vende aparece en el POS. */
    public boolean seVende() {
        return this == PRODUCTO || this == SERVICIO;
    }

    public boolean creaActivo() {
        return this == ACTIVO_FIJO || this == INTANGIBLE;
    }

    public boolean creaDiferido() {
        return this == DIFERIDO;
    }

    /** Lee la clasificación guardada; null o vacío es PRODUCTO (lo previo a V185). */
    public static ClasificacionItem de(String valor) {
        if (valor == null || valor.isBlank()) {
            return PRODUCTO;
        }
        try {
            return valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Clasificación inválida: use PRODUCTO, SERVICIO, GASTO, DOTACION, ACTIVO_FIJO, INTANGIBLE o DIFERIDO");
        }
    }
}
