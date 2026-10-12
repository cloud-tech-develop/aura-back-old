package com.cloud_technological.aura_pos.contabilidad.infrastructure.exogena;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cloud_technological.aura_pos.dto.contabilidad.CuentaTerceroMovimientoDto;
import com.cloud_technological.aura_pos.entity.ExogenaMapeoCuentaEntity;

/**
 * Lógica pura de la generación de exógena (E11): asigna cada cuenta al mapeo
 * más específico del formato, acumula valores por tercero × concepto × COLUMNA
 * según el tipo de valor y agrupa las cuantías menores (tercero 222222222). Sin
 * dependencias de Spring — se prueba directo.
 *
 * <p>Cada formato tiene varias columnas de valor (p. ej. el 1001: pago deducible,
 * retención practicada, retención de IVA…); un mapeo sin columna va a la principal.
 */
public final class ExogenaCalculadora {

    /** Mapeo listo para calcular. {@code columna} null = la principal del formato. */
    public record Mapeo(Long conceptoId, String desde, String hasta, String tipoValor, String columna) {

        /** Mapeo a la columna principal (compatibilidad con los mapeos de una sola columna). */
        public Mapeo(Long conceptoId, String desde, String hasta, String tipoValor) {
            this(conceptoId, desde, hasta, tipoValor, null);
        }
    }

    /**
     * Línea calculada del lote; terceroId null = cuantías menores/sin tercero.
     *
     * @param valores {codigoColumna: valor}; la principal siempre está
     */
    public record Linea(Long conceptoId, Long terceroId, Map<String, BigDecimal> valores, boolean cuantiaMenor,
                        String columnaPrincipal) {

        /** Valor de la columna principal (el que va en {@code exogena_linea.valor}). */
        public BigDecimal valor() {
            return valores.getOrDefault(columnaPrincipal, BigDecimal.ZERO);
        }

        /** Valor de una columna (cero si no tiene). */
        public BigDecimal valor(String columna) {
            return valores.getOrDefault(columna, BigDecimal.ZERO);
        }
    }

    /** Reglas del formato para calcular. */
    public record Reglas(String columnaPrincipal, String columnaRequerida, BigDecimal umbralCuantiaMenor) {
    }

    /** Columna principal por defecto cuando el formato aún no declara columnas. */
    public static final String VALOR = "VALOR";

    private ExogenaCalculadora() {
    }

    /** ¿La cuenta cae en el mapeo? hasta null = prefijo; con valor = rango. */
    public static boolean coincide(String codigo, Mapeo mapeo) {
        if (codigo == null) {
            return false;
        }
        if (mapeo.hasta() == null || mapeo.hasta().isBlank()) {
            return codigo.startsWith(mapeo.desde());
        }
        return codigo.compareTo(mapeo.desde()) >= 0
                && (codigo.compareTo(mapeo.hasta()) <= 0 || codigo.startsWith(mapeo.hasta()));
    }

    /** El mapeo aplicable a la cuenta: el del prefijo más largo (más específico). */
    public static Optional<Mapeo> mapeoPara(String codigo, List<Mapeo> mapeos) {
        Mapeo mejor = null;
        for (Mapeo m : mapeos) {
            if (coincide(codigo, m)
                    && (mejor == null || m.desde().length() > mejor.desde().length())) {
                mejor = m;
            }
        }
        return Optional.ofNullable(mejor);
    }

    /** Compatibilidad: una sola columna (VALOR), sin columna requerida. */
    public static List<Linea> calcular(List<CuentaTerceroMovimientoDto> movimientosAnio,
            List<CuentaTerceroMovimientoDto> saldosAlCorte,
            List<Mapeo> mapeos, BigDecimal umbralCuantiaMenor) {
        return calcular(movimientosAnio, saldosAlCorte, mapeos, new Reglas(VALOR, null, umbralCuantiaMenor));
    }

    /**
     * Calcula las líneas del lote. Los movimientos del año alimentan los mapeos
     * MOVIMIENTO_*; los saldos al corte, los SALDO_*. Si el formato exige una
     * columna (1003: la retención) se descartan las líneas sin ella. Los terceros
     * cuyo mayor valor no alcanza el umbral — y los movimientos sin tercero — se
     * agrupan en una línea de cuantías menores por concepto.
     */
    public static List<Linea> calcular(List<CuentaTerceroMovimientoDto> movimientosAnio,
            List<CuentaTerceroMovimientoDto> saldosAlCorte,
            List<Mapeo> mapeos, Reglas reglas) {

        // (concepto|tercero) → {columna: valor}; LinkedHashMap para orden estable.
        Map<String, Map<String, BigDecimal>> acumulado = new LinkedHashMap<>();
        Map<String, Long[]> claves = new LinkedHashMap<>();

        acumular(movimientosAnio, mapeos, true, reglas, acumulado, claves);
        acumular(saldosAlCorte, mapeos, false, reglas, acumulado, claves);

        BigDecimal umbral = reglas.umbralCuantiaMenor() != null ? reglas.umbralCuantiaMenor() : BigDecimal.ZERO;
        Map<Long, Map<String, BigDecimal>> menoresPorConcepto = new LinkedHashMap<>();
        List<Linea> resultado = new ArrayList<>();
        for (Map.Entry<String, Map<String, BigDecimal>> e : acumulado.entrySet()) {
            Long conceptoId = claves.get(e.getKey())[0];
            Long terceroId = claves.get(e.getKey())[1];
            Map<String, BigDecimal> valores = e.getValue();
            if (todoCero(valores)) {
                continue;
            }
            if (reglas.columnaRequerida() != null
                    && valores.getOrDefault(reglas.columnaRequerida(), BigDecimal.ZERO).signum() == 0) {
                continue;   // p. ej. 1003: el tercero no le retuvo
            }
            boolean menor = terceroId == null
                    || (umbral.signum() > 0 && mayorAbsoluto(valores).compareTo(umbral) < 0);
            if (menor) {
                Map<String, BigDecimal> grupo = menoresPorConcepto.computeIfAbsent(conceptoId,
                        k -> new LinkedHashMap<>());
                valores.forEach((col, v) -> grupo.merge(col, v, BigDecimal::add));
            } else {
                resultado.add(new Linea(conceptoId, terceroId, conPrincipal(valores, reglas), false,
                        reglas.columnaPrincipal()));
            }
        }
        for (Map.Entry<Long, Map<String, BigDecimal>> e : menoresPorConcepto.entrySet()) {
            if (!todoCero(e.getValue())) {
                resultado.add(new Linea(e.getKey(), null, conPrincipal(e.getValue(), reglas), true,
                        reglas.columnaPrincipal()));
            }
        }
        return resultado;
    }

    /**
     * Porcentaje de participación de cada línea sobre el total de la columna base
     * (1010: valor patrimonial de cada socio sobre el total de aportes), con 2 decimales.
     */
    public static List<Linea> conPorcentaje(List<Linea> lineas, String columnaBase, String columnaPorcentaje) {
        BigDecimal total = lineas.stream().map(l -> l.valor(columnaBase)).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() == 0) {
            return lineas;
        }
        List<Linea> out = new ArrayList<>();
        for (Linea l : lineas) {
            Map<String, BigDecimal> v = new LinkedHashMap<>(l.valores());
            v.put(columnaPorcentaje, l.valor(columnaBase).multiply(new BigDecimal("100"))
                    .divide(total, 2, RoundingMode.HALF_UP));
            out.add(new Linea(l.conceptoId(), l.terceroId(), v, l.cuantiaMenor(), l.columnaPrincipal()));
        }
        return out;
    }

    private static void acumular(List<CuentaTerceroMovimientoDto> movimientos,
            List<Mapeo> mapeos, boolean delAnio, Reglas reglas,
            Map<String, Map<String, BigDecimal>> acumulado, Map<String, Long[]> claves) {
        for (CuentaTerceroMovimientoDto mov : movimientos) {
            Optional<Mapeo> aplicable = mapeoPara(mov.codigo(), mapeos);
            if (aplicable.isEmpty()) {
                continue;
            }
            Mapeo m = aplicable.get();
            boolean esMovimiento = m.tipoValor().startsWith("MOVIMIENTO");
            if (esMovimiento != delAnio) {
                continue;   // cada tipo de valor come de su propio corte
            }
            BigDecimal neto = mov.debito().subtract(mov.credito());
            BigDecimal valor = m.tipoValor().endsWith("_CR") ? neto.negate() : neto;
            String columna = m.columna() != null && !m.columna().isBlank() ? m.columna() : reglas.columnaPrincipal();
            String clave = m.conceptoId() + "|" + mov.terceroId();
            acumulado.computeIfAbsent(clave, k -> new LinkedHashMap<>()).merge(columna, valor, BigDecimal::add);
            claves.putIfAbsent(clave, new Long[] { m.conceptoId(), mov.terceroId() });
        }
    }

    private static Map<String, BigDecimal> conPrincipal(Map<String, BigDecimal> valores, Reglas reglas) {
        Map<String, BigDecimal> v = new LinkedHashMap<>(valores);
        v.putIfAbsent(reglas.columnaPrincipal(), BigDecimal.ZERO);
        return v;
    }

    private static boolean todoCero(Map<String, BigDecimal> valores) {
        return valores.values().stream().allMatch(v -> v.signum() == 0);
    }

    private static BigDecimal mayorAbsoluto(Map<String, BigDecimal> valores) {
        return valores.values().stream().map(BigDecimal::abs).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    /** Proyecta las entidades de mapeo al modelo de cálculo. */
    public static List<Mapeo> proyectar(List<ExogenaMapeoCuentaEntity> entidades) {
        return entidades.stream()
                .map(e -> new Mapeo(e.getConceptoId(), e.getCuentaDesde(),
                        e.getCuentaHasta(), e.getTipoValor(), e.getColumna()))
                .toList();
    }
}
