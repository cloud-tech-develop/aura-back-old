package com.cloud_technological.aura_pos.services.conciliacion_fiscal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Ajuste;
import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Cuenta;
import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.ImpuestoDiferido;
import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Renglon;
import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Resumen;
import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Sugerencia;
import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Validacion;
import com.cloud_technological.aura_pos.services.conciliacion_fiscal.RenglonesFiscales.Seccion;

/**
 * El análisis de la conciliación fiscal: suma lo contable por renglón, aplica los
 * ajustes, valida el balance, propone los ajustes típicos y calcula el impuesto
 * diferido y la renta líquida. Sin base de datos: recibe las cuentas y los ajustes.
 */
public final class AnalizadorConciliacion {

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal TOLERANCIA = BigDecimal.ONE;

    private AnalizadorConciliacion() {
    }

    public static Resumen analizar(List<Cuenta> cuentas, List<Ajuste> ajustes, BigDecimal tarifaRenta) {
        Resumen r = new Resumen();
        Map<String, Renglon> renglones = new LinkedHashMap<>();
        for (RenglonesFiscales.Renglon def : RenglonesFiscales.TODOS) {
            Renglon x = new Renglon();
            x.setCodigo(def.codigo());
            x.setSeccion(def.seccion().name());
            x.setSeccionTitulo(def.seccion().titulo);
            x.setNombre(def.nombre());
            renglones.put(def.codigo(), x);
        }

        // ── Lo contable ──
        for (Cuenta c : cuentas) {
            if (c.getRenglon() == null) {
                if (nz(c.getSaldo()).signum() != 0) r.getSinClasificar().add(c);
                continue;
            }
            Renglon x = renglones.get(c.getRenglon());
            if (x == null) continue; // NO_APLICA
            x.setContable(x.getContable().add(nz(c.getSaldo())));
            x.setCuentas(x.getCuentas() + 1);
        }
        BigDecimal ingresos = suma(renglones, Seccion.ERI_INGRESO, Renglon::getContable);
        BigDecimal costos = suma(renglones, Seccion.ERI_COSTO, Renglon::getContable);
        BigDecimal gastos = suma(renglones, Seccion.ERI_GASTO, Renglon::getContable);
        BigDecimal resultado = ingresos.subtract(costos).subtract(gastos);
        // El resultado del año entra al patrimonio: si el balance ya se cerró, el
        // ERI viene en cero y el resultado ya está en la 36.
        Renglon t06 = renglones.get(RenglonesFiscales.RESULTADO_EJERCICIO);
        t06.setContable(t06.getContable().add(resultado));

        // ── Ajustes ──
        Map<String, BigDecimal[]> temporarias = new LinkedHashMap<>();
        Set<String> sugerenciasAplicadas = new HashSet<>();
        for (Ajuste a : ajustes) {
            Renglon x = renglones.get(a.getRenglon());
            if (a.getSugerencia() != null) sugerenciasAplicadas.add(a.getSugerencia());
            if (x == null) continue;
            a.setRenglonNombre(x.getNombre());
            BigDecimal v = nz(a.getValor());
            boolean menor = "MENOR".equals(a.getTipo());
            if (menor) x.setMenorValor(x.getMenorValor().add(v));
            else x.setMayorValor(x.getMayorValor().add(v));
            if ("TEMPORARIA".equals(a.getDiferencia())) {
                BigDecimal[] t = temporarias.computeIfAbsent(a.getRenglon(), k -> new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
                if (menor) t[0] = t[0].add(v);
                else t[1] = t[1].add(v);
            }
        }
        for (Renglon x : renglones.values()) {
            x.setFiscal(x.getContable().subtract(x.getMenorValor()).add(x.getMayorValor()));
        }

        // ── Totales ──
        r.setActivosContable(suma(renglones, Seccion.ESF_ACTIVO, Renglon::getContable));
        r.setActivosFiscal(suma(renglones, Seccion.ESF_ACTIVO, Renglon::getFiscal));
        r.setPasivosContable(suma(renglones, Seccion.ESF_PASIVO, Renglon::getContable));
        r.setPasivosFiscal(suma(renglones, Seccion.ESF_PASIVO, Renglon::getFiscal));
        r.setPatrimonioContable(suma(renglones, Seccion.ESF_PATRIMONIO, Renglon::getContable));
        r.setPatrimonioFiscal(r.getActivosFiscal().subtract(r.getPasivosFiscal()));
        r.setIngresosContable(ingresos);
        r.setIngresosFiscal(suma(renglones, Seccion.ERI_INGRESO, Renglon::getFiscal));
        r.setCostosContable(costos);
        r.setCostosFiscal(suma(renglones, Seccion.ERI_COSTO, Renglon::getFiscal));
        r.setGastosContable(gastos);
        r.setGastosFiscal(suma(renglones, Seccion.ERI_GASTO, Renglon::getFiscal));
        r.setResultadoContable(resultado);
        r.setRentaLiquida(r.getIngresosFiscal().subtract(r.getCostosFiscal()).subtract(r.getGastosFiscal()));
        BigDecimal tarifa = nz(tarifaRenta);
        r.setImpuestoRenta(r.getRentaLiquida().signum() > 0 ? pct(r.getRentaLiquida(), tarifa) : BigDecimal.ZERO);
        r.setRenglones(List.copyOf(renglones.values()));
        r.setAjustes(ajustes);

        // ── Impuesto diferido (solo diferencias temporarias del ESF) ──
        temporarias.forEach((codigo, t) -> {
            Renglon x = renglones.get(codigo);
            Seccion s = Seccion.valueOf(x.getSeccion());
            if (s != Seccion.ESF_ACTIVO && s != Seccion.ESF_PASIVO) return;
            BigDecimal fiscalTemp = x.getContable().subtract(t[0]).add(t[1]);
            BigDecimal dif = x.getContable().subtract(fiscalTemp);
            if (dif.signum() == 0) return;
            ImpuestoDiferido id = new ImpuestoDiferido();
            id.setRenglon(codigo);
            id.setNombre(x.getNombre());
            id.setSeccion(x.getSeccion());
            id.setContable(x.getContable());
            id.setFiscal(fiscalTemp);
            id.setDiferencia(dif.abs());
            // Activo contable mayor que el fiscal, o pasivo contable menor: se
            // pagará más impuesto después (imponible → pasivo por impuesto diferido).
            boolean imponible = (s == Seccion.ESF_ACTIVO) == (dif.signum() > 0);
            id.setTipo(imponible ? "IMPONIBLE" : "DEDUCIBLE");
            BigDecimal valor = pct(dif.abs(), tarifa);
            if (imponible) id.setPasivo(valor);
            else id.setActivo(valor);
            r.getImpuestoDiferido().add(id);
            r.setImpuestoDiferidoActivo(r.getImpuestoDiferidoActivo().add(id.getActivo()));
            r.setImpuestoDiferidoPasivo(r.getImpuestoDiferidoPasivo().add(id.getPasivo()));
        });

        validar(r, cuentas, renglones);
        sugerir(r, cuentas, sugerenciasAplicadas);
        return r;
    }

    // ── Validaciones ─────────────────────────────────────────────────────────

    private static void validar(Resumen r, List<Cuenta> cuentas, Map<String, Renglon> renglones) {
        var v = r.getValidaciones();
        if (cuentas.isEmpty()) {
            v.add(new Validacion("ERROR", "Cargue el balance: súbalo en Excel o tráigalo de la contabilidad."));
            return;
        }
        BigDecimal descuadre = r.getActivosContable().subtract(r.getPasivosContable()).subtract(r.getPatrimonioContable());
        if (descuadre.abs().compareTo(TOLERANCIA) > 0) {
            v.add(new Validacion("ERROR", "El balance no cuadra: activos − pasivos − patrimonio (con el resultado del año) da "
                    + pesos(descuadre) + ". Revise que el archivo traiga todas las cuentas auxiliares y los saldos con su signo."));
        } else {
            v.add(new Validacion("OK", "El balance cuadra: activos = pasivos + patrimonio."));
        }
        if (!r.getSinClasificar().isEmpty()) {
            v.add(new Validacion("ADVERTENCIA", r.getSinClasificar().size()
                    + " cuenta(s) con saldo no caen en ningún renglón. Clasifíquelas a mano."));
        }
        boolean eriVacio = r.getIngresosContable().signum() == 0 && r.getCostosContable().signum() == 0
                && r.getGastosContable().signum() == 0;
        if (eriVacio) {
            v.add(new Validacion("ADVERTENCIA", "El estado de resultados viene en cero: el balance parece de después del cierre. "
                    + "Para conciliar la renta use el balance de prueba antes del cierre."));
        }
        for (Renglon x : renglones.values()) {
            if (x.getMenorValor().signum() > 0 && x.getMenorValor().compareTo(x.getContable().abs()) > 0) {
                v.add(new Validacion("ADVERTENCIA", "En \"" + x.getNombre() + "\" el menor valor fiscal ("
                        + pesos(x.getMenorValor()) + ") es mayor que el valor contable (" + pesos(x.getContable()) + ")."));
            }
        }
    }

    // ── Sugerencias ──────────────────────────────────────────────────────────

    private record Regla(String clave, String tipo, String diferencia, BigDecimal porcentaje,
            String concepto, String fundamento, boolean revisar) {
    }

    private static final Regla RENTA = new Regla("RENTA", "MENOR", "PERMANENTE", CIEN,
            "Impuesto de renta y complementarios", "El impuesto de renta no es deducible (art. 115 E.T.).", false);
    private static final Regla MULTAS = new Regla("MULTAS", "MENOR", "PERMANENTE", CIEN,
            "Multas, sanciones y litigios", "Las multas y sanciones no son deducibles.", false);
    private static final Regla GMF = new Regla("GMF", "MENOR", "PERMANENTE", new BigDecimal("50"),
            "50 % del gravamen a los movimientos financieros", "Solo el 50 % del GMF es deducible (art. 115 E.T.).", false);
    private static final Regla DONACIONES = new Regla("DONACIONES", "MENOR", "PERMANENTE", CIEN,
            "Donaciones", "No son deducción; pueden tomarse como descuento tributario (art. 257 E.T.).", false);
    private static final Regla ASUMIDOS = new Regla("ASUMIDOS", "MENOR", "PERMANENTE", CIEN,
            "Impuestos asumidos", "Los impuestos asumidos de terceros no son deducibles.", false);
    private static final Regla NO_DEDUCIBLE = new Regla("NODED", "MENOR", "PERMANENTE", CIEN,
            "Gastos no deducibles", "La cuenta está marcada como no deducible.", false);
    private static final Regla VALORIZACION = new Regla("VALORIZ", "MENOR", "PERMANENTE", CIEN,
            "Valorizaciones", "Las valorizaciones no hacen parte del patrimonio fiscal.", false);
    private static final Regla ESTIMADOS = new Regla("ESTIMADOS", "MENOR", "TEMPORARIA", CIEN,
            "Pasivos estimados y provisiones", "Las provisiones no son pasivo fiscal hasta que la obligación se causa.", true);
    private static final Regla DETERIORO = new Regla("DETERIORO", "MENOR", "TEMPORARIA", CIEN,
            "Deterioro y provisiones", "Solo es deducible el deterioro de cartera calculado por el método fiscal; revise el valor.", true);

    private static Regla regla(Cuenta c) {
        String cod = c.getCodigo();
        String n = normalizar(c.getNombre());
        char clase = cod.charAt(0);
        if (clase == '5') {
            if (cod.startsWith("5405") || n.contains("RENTA Y COMPLEMENTARIOS")) return RENTA;
            if (n.contains("MULTA") || n.contains("SANCION") || n.contains("LITIGIO")) return MULTAS;
            if (n.contains("GRAVAMEN") || n.contains("4X1000") || n.contains("4 X 1000") || n.contains("GMF")
                    || n.contains("MOVIMIENTOS FINANCIEROS")) return GMF;
            if (n.contains("DONACION")) return DONACIONES;
            if (n.contains("IMPUESTOS ASUMIDOS")) return ASUMIDOS;
            if (n.contains("NO DEDUCIBLE")) return NO_DEDUCIBLE;
            if (cod.startsWith("5199") || cod.startsWith("5299") || n.contains("DETERIORO")) return DETERIORO;
        }
        if (cod.startsWith("19") || cod.startsWith("38")) return VALORIZACION;
        if (cod.startsWith("26") && !cod.startsWith("2610")) return ESTIMADOS;
        return null;
    }

    private static void sugerir(Resumen r, List<Cuenta> cuentas, Set<String> aplicadas) {
        for (Cuenta c : cuentas) {
            if (c.getRenglon() == null || RenglonesFiscales.NO_APLICA.equals(c.getRenglon())) continue;
            BigDecimal saldo = nz(c.getSaldo());
            if (saldo.signum() <= 0) continue;
            Regla g = regla(c);
            if (g == null) continue;
            String clave = g.clave() + ":" + c.getCodigo();
            if (aplicadas.contains(clave)) continue;
            Sugerencia s = new Sugerencia();
            s.setClave(clave);
            s.setRenglon(c.getRenglon());
            var def = RenglonesFiscales.POR_CODIGO.get(c.getRenglon());
            s.setRenglonNombre(def != null ? def.nombre() : c.getRenglon());
            s.setCuentaCodigo(c.getCodigo());
            s.setCuentaNombre(c.getNombre());
            s.setTipo(g.tipo());
            s.setDiferencia(g.diferencia());
            s.setValor(pct(saldo, g.porcentaje()));
            s.setConcepto(g.concepto() + " (" + c.getCodigo() + ")");
            s.setFundamento(g.fundamento());
            s.setRevisar(g.revisar());
            r.getSugerencias().add(s);
        }
    }

    // ── Utilidades ───────────────────────────────────────────────────────────

    private static BigDecimal suma(Map<String, Renglon> renglones, Seccion s,
            java.util.function.Function<Renglon, BigDecimal> campo) {
        return renglones.values().stream().filter(x -> x.getSeccion().equals(s.name()))
                .map(campo).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static BigDecimal pct(BigDecimal base, BigDecimal tarifa) {
        return base.multiply(tarifa).divide(CIEN, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    static String normalizar(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
    }

    private static String pesos(BigDecimal v) {
        return "$" + String.format(Locale.forLanguageTag("es-CO"), "%,.0f", v);
    }
}
