package com.cloud_technological.aura_pos.services.conciliacion_fiscal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Cuenta;
import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Renglon;
import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Resumen;

import lombok.Data;

/**
 * Borrador del formulario 110 (renta, personas jurídicas) armado sobre la
 * conciliación fiscal: los valores fiscales de cada renglón del ESF y del ERI ya
 * traen los ajustes. Lo que la contabilidad no sabe (ingresos no constitutivos,
 * compensaciones, descuentos, anticipo del año siguiente, saldo a favor anterior)
 * lo escribe el contador.
 *
 * <p>No numera los renglones: la DIAN los cambia cada año. Se presentan con el
 * nombre del formulario y en su orden, redondeados a miles (art. 577 E.T.).
 */
public final class Formulario110 {

    private Formulario110() {
    }

    @Data
    public static class Manuales {
        private BigDecimal ingresosNoConstitutivos = BigDecimal.ZERO;
        private BigDecimal compensaciones = BigDecimal.ZERO;
        private BigDecimal descuentosTributarios = BigDecimal.ZERO;
        private BigDecimal anticipoSiguiente = BigDecimal.ZERO;
        private BigDecimal saldoFavorAnterior = BigDecimal.ZERO;
    }

    @Data
    public static class Linea {
        private String concepto;
        private BigDecimal valor;
        /** true: viene de lo que escribió el contador. */
        private boolean manual;
        /** true: total del bloque (se resalta). */
        private boolean total;

        static Linea de(String concepto, BigDecimal valor, boolean manual, boolean total) {
            Linea l = new Linea();
            l.setConcepto(concepto);
            l.setValor(miles(valor));
            l.setManual(manual);
            l.setTotal(total);
            return l;
        }
    }

    @Data
    public static class Bloque {
        private String titulo;
        private List<Linea> lineas = new ArrayList<>();
    }

    @Data
    public static class Resultado {
        private Integer anio;
        private BigDecimal tarifa;
        private List<Bloque> bloques = new ArrayList<>();
        private BigDecimal saldoPagar = BigDecimal.ZERO;
        private BigDecimal saldoFavor = BigDecimal.ZERO;
        private List<String> advertencias = new ArrayList<>();
    }

    public static Resultado calcular(Resumen r, List<Cuenta> cuentas, Manuales m) {
        Map<String, Renglon> por = r.getRenglones().stream()
                .collect(Collectors.toMap(Renglon::getCodigo, Function.identity(), (a, b) -> a));
        Function<String, BigDecimal> fiscal = c -> por.containsKey(c) ? nz(por.get(c).getFiscal()) : BigDecimal.ZERO;
        Resultado out = new Resultado();
        out.setAnio(r.getCabecera() != null ? r.getCabecera().getAnio() : null);
        BigDecimal tarifa = r.getCabecera() != null ? nz(r.getCabecera().getTarifaRenta()) : BigDecimal.ZERO;
        out.setTarifa(tarifa);

        // ── Patrimonio ──
        Bloque pat = bloque(out, "Patrimonio");
        pat.getLineas().add(Linea.de("Efectivo y equivalentes al efectivo", fiscal.apply("A01"), false, false));
        pat.getLineas().add(Linea.de("Inversiones e instrumentos financieros derivados", fiscal.apply("A02"), false, false));
        pat.getLineas().add(Linea.de("Cuentas, documentos y arrendamientos financieros por cobrar",
                fiscal.apply("A03").add(fiscal.apply("A04")), false, false));
        pat.getLineas().add(Linea.de("Inventarios", fiscal.apply("A05"), false, false));
        pat.getLineas().add(Linea.de("Activos intangibles", fiscal.apply("A07"), false, false));
        pat.getLineas().add(Linea.de("Propiedades, planta y equipo, propiedades de inversión y ANCMV",
                fiscal.apply("A06"), false, false));
        pat.getLineas().add(Linea.de("Otros activos",
                fiscal.apply("A08").add(fiscal.apply("A09")).add(fiscal.apply("A10")), false, false));
        BigDecimal bruto = nz(r.getActivosFiscal());
        BigDecimal pasivos = nz(r.getPasivosFiscal());
        pat.getLineas().add(Linea.de("Total patrimonio bruto", bruto, false, true));
        pat.getLineas().add(Linea.de("Pasivos", pasivos, false, false));
        pat.getLineas().add(Linea.de("Total patrimonio líquido", bruto.subtract(pasivos).max(BigDecimal.ZERO), false, true));

        // ── Ingresos ──
        Bloque ing = bloque(out, "Ingresos");
        BigDecimal ordinarios = fiscal.apply("I01");
        BigDecimal otros = fiscal.apply("I02");
        BigDecimal brutos = ordinarios.add(otros);
        BigDecimal noConst = nz(m.getIngresosNoConstitutivos());
        BigDecimal netos = brutos.subtract(noConst).max(BigDecimal.ZERO);
        ing.getLineas().add(Linea.de("Ingresos brutos de actividades ordinarias (netos de devoluciones)", ordinarios, false, false));
        ing.getLineas().add(Linea.de("Ingresos financieros y otros ingresos", otros, false, false));
        ing.getLineas().add(Linea.de("Total ingresos brutos", brutos, false, true));
        ing.getLineas().add(Linea.de("Ingresos no constitutivos de renta ni ganancia ocasional", noConst, true, false));
        ing.getLineas().add(Linea.de("Total ingresos netos", netos, false, true));

        // ── Costos y deducciones ──
        Bloque cos = bloque(out, "Costos y deducciones");
        BigDecimal costos = fiscal.apply("C01").add(fiscal.apply("C02"));
        BigDecimal adm = fiscal.apply("G01");
        BigDecimal ventas = fiscal.apply("G02");
        BigDecimal financieros = fiscal.apply("G03");
        BigDecimal deducibles = costos.add(adm).add(ventas).add(financieros);
        cos.getLineas().add(Linea.de("Costos", costos, false, false));
        cos.getLineas().add(Linea.de("Gastos de administración", adm, false, false));
        cos.getLineas().add(Linea.de("Gastos de distribución y ventas", ventas, false, false));
        cos.getLineas().add(Linea.de("Gastos financieros y otros", financieros, false, false));
        cos.getLineas().add(Linea.de("Total costos y gastos deducibles", deducibles, false, true));
        if (fiscal.apply("G04").signum() != 0) {
            out.getAdvertencias().add("El gasto de impuesto de renta (54) no es deducible: no entra en los costos y "
                    + "deducciones del formulario aunque esté en el ERI.");
        }

        // ── Renta ──
        Bloque renta = bloque(out, "Renta");
        BigDecimal diferencia = netos.subtract(deducibles);
        BigDecimal rlo = diferencia.max(BigDecimal.ZERO);
        BigDecimal perdida = diferencia.negate().max(BigDecimal.ZERO);
        BigDecimal comp = nz(m.getCompensaciones()).min(rlo);
        BigDecimal rentaLiquida = rlo.subtract(comp);
        BigDecimal gravable = rentaLiquida; // renta presuntiva en 0 % desde 2021
        renta.getLineas().add(Linea.de("Renta líquida ordinaria del ejercicio", rlo, false, false));
        renta.getLineas().add(Linea.de("Pérdida líquida del ejercicio", perdida, false, false));
        renta.getLineas().add(Linea.de("Compensaciones", comp, true, false));
        renta.getLineas().add(Linea.de("Renta líquida", rentaLiquida, false, false));
        renta.getLineas().add(Linea.de("Renta presuntiva", BigDecimal.ZERO, false, false));
        renta.getLineas().add(Linea.de("Renta líquida gravable", gravable, false, true));
        if (nz(m.getCompensaciones()).compareTo(rlo) > 0) {
            out.getAdvertencias().add("Las compensaciones no pueden superar la renta líquida ordinaria: se tomó el tope.");
        }

        // ── Liquidación privada ──
        Bloque liq = bloque(out, "Liquidación privada");
        BigDecimal impuesto = gravable.multiply(tarifa).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        BigDecimal desc = nz(m.getDescuentosTributarios()).min(impuesto);
        BigDecimal neto = impuesto.subtract(desc);
        BigDecimal anticipoAnterior = saldo(cuentas, "135505");
        BigDecimal retenciones = saldo(cuentas, "135515");
        BigDecimal favorAnterior = nz(m.getSaldoFavorAnterior());
        BigDecimal anticipoSig = nz(m.getAnticipoSiguiente());
        liq.getLineas().add(Linea.de("Impuesto sobre la renta líquida gravable (" + tarifa.stripTrailingZeros()
                .toPlainString() + " %)", impuesto, false, false));
        liq.getLineas().add(Linea.de("Descuentos tributarios", desc, true, false));
        liq.getLineas().add(Linea.de("Impuesto neto de renta", neto, false, true));
        liq.getLineas().add(Linea.de("Anticipo renta liquidado año gravable anterior (135505)", anticipoAnterior, false, false));
        liq.getLineas().add(Linea.de("Saldo a favor año anterior sin solicitud de devolución", favorAnterior, true, false));
        liq.getLineas().add(Linea.de("Retenciones en la fuente que le practicaron (135515)", retenciones, false, false));
        liq.getLineas().add(Linea.de("Anticipo renta para el año gravable siguiente", anticipoSig, true, false));
        BigDecimal saldo = neto.add(anticipoSig).subtract(anticipoAnterior).subtract(favorAnterior).subtract(retenciones);
        out.setSaldoPagar(miles(saldo.max(BigDecimal.ZERO)));
        out.setSaldoFavor(miles(saldo.negate().max(BigDecimal.ZERO)));
        liq.getLineas().add(Linea.de("Saldo a pagar", out.getSaldoPagar(), false, true));
        liq.getLineas().add(Linea.de("Total saldo a favor", out.getSaldoFavor(), false, true));

        if (tarifa.signum() == 0) out.getAdvertencias().add("La conciliación no tiene tarifa de renta.");
        if (anticipoSig.signum() == 0 && neto.signum() > 0) {
            out.getAdvertencias().add("Calcule el anticipo del año siguiente (art. 807 E.T.) y escríbalo: "
                    + "depende de los dos años anteriores y del año de declaración.");
        }
        out.getAdvertencias().add("Verifique la numeración de renglones con el formulario vigente: este borrador "
                + "sigue el orden del 110 pero no reemplaza el prevalidador de la DIAN.");
        return out;
    }

    private static Bloque bloque(Resultado out, String titulo) {
        Bloque b = new Bloque();
        b.setTitulo(titulo);
        out.getBloques().add(b);
        return b;
    }

    /** Saldo débito de las cuentas del balance cargado que empiezan por el prefijo. */
    private static BigDecimal saldo(List<Cuenta> cuentas, String prefijo) {
        return cuentas.stream()
                .filter(c -> c.getCodigo() != null && c.getCodigo().startsWith(prefijo))
                .map(c -> nz(c.getSaldo()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .abs();
    }

    private static BigDecimal miles(BigDecimal v) {
        return nz(v).divide(BigDecimal.valueOf(1000), 0, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(1000));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
