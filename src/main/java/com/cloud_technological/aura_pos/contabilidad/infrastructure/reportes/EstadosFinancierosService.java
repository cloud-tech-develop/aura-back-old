package com.cloud_technological.aura_pos.contabilidad.infrastructure.reportes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.contabilidad.domain.ReglasAsiento;
import com.cloud_technological.aura_pos.dto.contabilidad.CambioPatrimonioLineaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.EstadoResultadosLineaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.MovimientoCuentaDto;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableQueryRepository;

import lombok.RequiredArgsConstructor;

/**
 * Estados financieros NIIF pymes Sección 3 que faltaban (E10 · C10): estado
 * de cambios en el patrimonio y estado de flujos de efectivo por el método
 * indirecto. Todo sale de asiento_detalle entre dos cortes; el EFE valida su
 * propio cuadre contra el Δ del disponible (clase 11).
 */
@Service
@RequiredArgsConstructor
public class EstadosFinancierosService {

    private final AsientoContableQueryRepository queryRepo;

    // ── Estado de cambios en el patrimonio ───────────────────────────────

    /** Cuentas clase 3: inicial / aumentos / disminuciones / final. */
    public List<CambioPatrimonioLineaDto> cambiosPatrimonio(Integer empresaId,
            String desde, String hasta) {
        return queryRepo.cambiosPatrimonio(empresaId, desde, hasta).stream()
                .filter(l -> l.saldoInicial().signum() != 0
                        || l.aumentos().signum() != 0
                        || l.disminuciones().signum() != 0)
                .toList();
    }

    // ── Estado de flujos de efectivo (método indirecto) ──────────────────

    /*
     * Clasificación por grupo PUC (nivel 2). Las contra-cuentas 1399/1499/1592
     * se excluyen de las variaciones porque su gasto (5199/5160) se suma de
     * vuelta como partida no monetaria: contarlas dos veces descuadra el EFE.
     */
    private static final List<String> ACTIVOS_OPERACION = List.of("13", "14", "17");
    private static final List<String> PASIVOS_OPERACION =
            List.of("22", "23", "24", "25", "26", "27", "28");
    private static final List<String> ACTIVOS_INVERSION = List.of("12", "15", "16", "18", "19");
    private static final List<String> PASIVOS_FINANCIACION = List.of("21", "29");
    private static final List<String> CONTRA_CUENTAS = List.of("1399", "1499", "1592");

    public Map<String, Object> flujoEfectivo(Integer empresaId, String desde, String hasta) {
        // 1. Utilidad del período (excluye asientos de CIERRE, cerrado o no).
        BigDecimal utilidad = utilidadPeriodo(empresaId, desde, hasta);

        // 2. Movimientos de balance del período agrupados por grupo PUC.
        List<MovimientoCuentaDto> movimientos = queryRepo.movimientosPorCuenta(empresaId, desde, hasta);
        BigDecimal depreciacion = movimientoNeto(movimientos, "5160");
        BigDecimal deterioro = movimientoNeto(movimientos, "5199");

        Map<String, BigDecimal> porGrupo = new LinkedHashMap<>();
        for (MovimientoCuentaDto m : movimientos) {
            if (m.codigo() == null || m.codigo().length() < 2 || esContraCuenta(m.codigo())) {
                continue;
            }
            String grupo = m.codigo().substring(0, 2);
            porGrupo.merge(grupo, neto(m), BigDecimal::add);
        }

        // 3. Actividades de operación.
        List<Map<String, Object>> capitalTrabajo = new ArrayList<>();
        BigDecimal operacion = utilidad.add(depreciacion).add(deterioro);
        for (String grupo : ACTIVOS_OPERACION) {
            BigDecimal delta = porGrupo.getOrDefault(grupo, BigDecimal.ZERO);   // Δ activo (deb−cred)
            operacion = operacion.subtract(delta);
            agregar(capitalTrabajo, grupo, delta.negate());
        }
        for (String grupo : PASIVOS_OPERACION) {
            BigDecimal delta = porGrupo.getOrDefault(grupo, BigDecimal.ZERO).negate(); // Δ pasivo (cred−deb)
            operacion = operacion.add(delta);
            agregar(capitalTrabajo, grupo, delta);
        }

        // 4. Actividades de inversión: aumentos de activo fijo/inversiones usan caja.
        List<Map<String, Object>> inversionDetalle = new ArrayList<>();
        BigDecimal inversion = BigDecimal.ZERO;
        for (String grupo : ACTIVOS_INVERSION) {
            BigDecimal flujo = porGrupo.getOrDefault(grupo, BigDecimal.ZERO).negate();
            inversion = inversion.add(flujo);
            agregar(inversionDetalle, grupo, flujo);
        }

        // 5. Actividades de financiación: obligaciones y patrimonio (sin el
        //    cierre, ya excluido: los aportes y distribuciones sí cuentan).
        List<Map<String, Object>> financiacionDetalle = new ArrayList<>();
        BigDecimal financiacion = BigDecimal.ZERO;
        for (String grupo : PASIVOS_FINANCIACION) {
            BigDecimal flujo = porGrupo.getOrDefault(grupo, BigDecimal.ZERO).negate();
            financiacion = financiacion.add(flujo);
            agregar(financiacionDetalle, grupo, flujo);
        }
        for (Map.Entry<String, BigDecimal> e : porGrupo.entrySet()) {
            if (e.getKey().startsWith("3")) {
                BigDecimal flujo = e.getValue().negate();   // patrimonio: cred−deb
                financiacion = financiacion.add(flujo);
                agregar(financiacionDetalle, e.getKey(), flujo);
            }
        }

        // 6. Cuadre contra el Δ real del disponible (clase 11).
        BigDecimal flujoNeto = operacion.add(inversion).add(financiacion);
        String corteInicial = java.time.LocalDate.parse(desde).minusDays(1).toString();
        BigDecimal efectivoInicial = ReglasAsiento.nz(
                queryRepo.saldoPorPrefijo(empresaId, "11", corteInicial));
        BigDecimal efectivoFinal = ReglasAsiento.nz(
                queryRepo.saldoPorPrefijo(empresaId, "11", hasta));
        BigDecimal deltaEfectivo = efectivoFinal.subtract(efectivoInicial);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("desde", desde);
        r.put("hasta", hasta);
        r.put("utilidadPeriodo", utilidad);
        r.put("depreciacion", depreciacion);
        r.put("deterioroYProvisiones", deterioro);
        r.put("variacionesCapitalTrabajo", capitalTrabajo);
        r.put("flujoOperacion", operacion);
        r.put("detalleInversion", inversionDetalle);
        r.put("flujoInversion", inversion);
        r.put("detalleFinanciacion", financiacionDetalle);
        r.put("flujoFinanciacion", financiacion);
        r.put("flujoNeto", flujoNeto);
        r.put("efectivoInicial", efectivoInicial);
        r.put("efectivoFinal", efectivoFinal);
        r.put("deltaEfectivoLibros", deltaEfectivo);
        r.put("cuadra", flujoNeto.compareTo(deltaEfectivo) == 0);
        return r;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private BigDecimal utilidadPeriodo(Integer empresaId, String desde, String hasta) {
        BigDecimal utilidad = BigDecimal.ZERO;
        for (EstadoResultadosLineaDto l : queryRepo.estadoResultados(empresaId, desde, hasta)) {
            BigDecimal saldo = ReglasAsiento.nz(l.getSaldo());
            utilidad = "INGRESO".equals(l.getTipo())
                    ? utilidad.add(saldo)
                    : utilidad.subtract(saldo);
        }
        return utilidad;
    }

    /** Neto débito − crédito de las cuentas que empiezan por el prefijo. */
    private static BigDecimal movimientoNeto(List<MovimientoCuentaDto> movimientos, String prefijo) {
        return movimientos.stream()
                .filter(m -> m.codigo() != null && m.codigo().startsWith(prefijo))
                .map(EstadosFinancierosService::neto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal neto(MovimientoCuentaDto m) {
        return ReglasAsiento.nz(m.debito()).subtract(ReglasAsiento.nz(m.credito()));
    }

    private static boolean esContraCuenta(String codigo) {
        return CONTRA_CUENTAS.stream().anyMatch(codigo::startsWith);
    }

    private static void agregar(List<Map<String, Object>> detalle, String grupo, BigDecimal flujo) {
        if (flujo.signum() == 0) {
            return;
        }
        Map<String, Object> linea = new LinkedHashMap<>();
        linea.put("grupo", grupo);
        linea.put("flujo", flujo);
        detalle.add(linea);
    }
}
