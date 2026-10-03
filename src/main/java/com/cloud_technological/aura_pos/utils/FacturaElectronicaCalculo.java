package com.cloud_technological.aura_pos.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cálculos para armar la factura electrónica desde lo que la venta guardó, no
 * desde el producto de hoy.
 *
 * <p>Antes cada ítem salía con descuento 0 y el IVA vigente del producto: en
 * cualquier venta con descuento el total ante la DIAN era mayor que lo cobrado,
 * y si el IVA del producto había cambiado, la factura no coincidía con la
 * venta. Estas funciones son puras para poder probarlas sin Factus.
 */
public final class FacturaElectronicaCalculo {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    /** Tarifas de IVA vigentes a las que se ajusta el cálculo si queda a menos de medio punto. */
    private static final BigDecimal[] TARIFAS = {
            BigDecimal.ZERO, BigDecimal.valueOf(5), BigDecimal.valueOf(19) };

    private FacturaElectronicaCalculo() {
    }

    /**
     * IVA de la línea como porcentaje. En la venta {@code subtotalLinea} ya
     * incluye el IVA, así que el porcentaje es imp / (subtotalLinea − imp), no
     * imp / subtotalLinea (eso daba 16 % en vez de 19 %). El redondeo por línea
     * se corrige llevándolo a la tarifa estándar más cercana.
     */
    public static BigDecimal ivaPorcentajeLinea(BigDecimal subtotalLinea, BigDecimal impuesto) {
        BigDecimal imp = impuesto != null ? impuesto : BigDecimal.ZERO;
        BigDecimal base = (subtotalLinea != null ? subtotalLinea : BigDecimal.ZERO).subtract(imp);
        if (imp.signum() <= 0 || base.signum() <= 0) return BigDecimal.ZERO.setScale(2);
        BigDecimal pct = imp.multiply(CIEN).divide(base, 4, RoundingMode.HALF_UP);
        for (BigDecimal t : TARIFAS) {
            if (pct.subtract(t).abs().compareTo(new BigDecimal("0.5")) < 0) return t.setScale(2);
        }
        return pct.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Porcentaje de descuento que, aplicado al precio con IVA × cantidad, deja
     * el ítem en lo que el cliente de verdad pagó (descuento de la línea más su
     * parte del descuento general). Entre 0 y 99.9999.
     *
     * @param brutoConIva precio unitario con IVA × cantidad, sin descuentos
     * @param cobrado     lo cobrado por la línea, con IVA
     */
    public static BigDecimal tasaDescuento(BigDecimal brutoConIva, BigDecimal cobrado) {
        if (brutoConIva == null || brutoConIva.signum() <= 0 || cobrado == null) return BigDecimal.ZERO;
        BigDecimal tasa = BigDecimal.ONE.subtract(cobrado.divide(brutoConIva, 8, RoundingMode.HALF_UP))
                .multiply(CIEN).setScale(4, RoundingMode.HALF_UP);
        if (tasa.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal max = new BigDecimal("99.9999");
        return tasa.compareTo(max) > 0 ? max : tasa;
    }

    /**
     * Código DIAN de medio de pago para el método guardado en la venta.
     * <b>Verificar contra la tabla vigente de Factus/DIAN</b> antes de producción.
     */
    public static String medioPagoDian(String metodo) {
        if (metodo == null) return "10";
        String m = metodo.trim().toUpperCase();
        if (m.contains("EFECTIVO")) return "10";
        if (m.contains("TRANSFER") || m.contains("NEQUI") || m.contains("DAVIPLATA")) return "47";
        if (m.contains("CONSIGNA")) return "42";
        if (m.contains("DEBITO") || m.contains("DÉBITO")) return "49";
        if (m.contains("TARJETA") || m.contains("CREDITO_TARJETA")) return "48";
        return "10";
    }
}
