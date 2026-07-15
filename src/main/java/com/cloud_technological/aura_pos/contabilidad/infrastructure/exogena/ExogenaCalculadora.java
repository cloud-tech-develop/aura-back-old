package com.cloud_technological.aura_pos.contabilidad.infrastructure.exogena;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cloud_technological.aura_pos.dto.contabilidad.CuentaTerceroMovimientoDto;
import com.cloud_technological.aura_pos.entity.ExogenaMapeoCuentaEntity;

/**
 * Lógica pura de la generación de exógena (E11): asigna cada cuenta al mapeo
 * más específico del formato, acumula valores por tercero × concepto según el
 * tipo de valor y agrupa las cuantías menores (tercero 222222222). Sin
 * dependencias de Spring — se prueba directo.
 */
public final class ExogenaCalculadora {

    /** Mapeo listo para calcular (proyección de la entidad). */
    public record Mapeo(Long conceptoId, String desde, String hasta, String tipoValor) {
    }

    /** Línea calculada del lote; terceroId null = cuantías menores/sin tercero. */
    public record Linea(Long conceptoId, Long terceroId, BigDecimal valor, boolean cuantiaMenor) {
    }

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

    /**
     * Calcula las líneas del lote. Los movimientos del año alimentan los
     * mapeos MOVIMIENTO_*; los saldos al corte, los SALDO_*. Los terceros
     * cuyo total absoluto por concepto no alcanza el umbral — y los
     * movimientos sin tercero — se agrupan en una línea de cuantías menores.
     */
    public static List<Linea> calcular(List<CuentaTerceroMovimientoDto> movimientosAnio,
            List<CuentaTerceroMovimientoDto> saldosAlCorte,
            List<Mapeo> mapeos, BigDecimal umbralCuantiaMenor) {

        // (concepto, tercero) → valor acumulado; LinkedHashMap para orden estable.
        Map<String, BigDecimal> acumulado = new LinkedHashMap<>();
        Map<String, Linea> claves = new LinkedHashMap<>();

        acumular(movimientosAnio, mapeos, true, acumulado, claves);
        acumular(saldosAlCorte, mapeos, false, acumulado, claves);

        // Cuantías menores por concepto.
        Map<Long, BigDecimal> menoresPorConcepto = new LinkedHashMap<>();
        List<Linea> resultado = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : acumulado.entrySet()) {
            Linea base = claves.get(e.getKey());
            BigDecimal valor = e.getValue();
            if (valor.signum() == 0) {
                continue;
            }
            boolean menor = base.terceroId() == null
                    || valor.abs().compareTo(umbralCuantiaMenor) < 0;
            if (menor) {
                menoresPorConcepto.merge(base.conceptoId(), valor, BigDecimal::add);
            } else {
                resultado.add(new Linea(base.conceptoId(), base.terceroId(), valor, false));
            }
        }
        for (Map.Entry<Long, BigDecimal> e : menoresPorConcepto.entrySet()) {
            if (e.getValue().signum() != 0) {
                resultado.add(new Linea(e.getKey(), null, e.getValue(), true));
            }
        }
        return resultado;
    }

    private static void acumular(List<CuentaTerceroMovimientoDto> movimientos,
            List<Mapeo> mapeos, boolean delAnio,
            Map<String, BigDecimal> acumulado, Map<String, Linea> claves) {
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
            String clave = m.conceptoId() + "|" + mov.terceroId();
            acumulado.merge(clave, valor, BigDecimal::add);
            claves.putIfAbsent(clave, new Linea(m.conceptoId(), mov.terceroId(), null, false));
        }
    }

    /** Proyecta las entidades de mapeo al modelo de cálculo. */
    public static List<Mapeo> proyectar(List<ExogenaMapeoCuentaEntity> entidades) {
        return entidades.stream()
                .map(e -> new Mapeo(e.getConceptoId(), e.getCuentaDesde(),
                        e.getCuentaHasta(), e.getTipoValor()))
                .toList();
    }
}
