package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.contabilidad.BalancePruebaDto;
import com.cloud_technological.aura_pos.repositories.contabilidad.BalancePruebaQueryRepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.BalancePruebaQueryRepository.CuentaPlan;
import com.cloud_technological.aura_pos.repositories.contabilidad.BalancePruebaQueryRepository.Movimiento;

import lombok.RequiredArgsConstructor;

/**
 * Balance de prueba completo (Fase 4). El mayor se lee por cuenta auxiliar y
 * se sube a cada cuenta padre por prefijo de código, hasta el nivel pedido.
 */
@Service
@RequiredArgsConstructor
public class BalancePruebaService {

    public static final String MES_ANTERIOR = "MES_ANTERIOR";
    public static final String ANIO_ANTERIOR = "ANIO_ANTERIOR";

    private final BalancePruebaQueryRepository repo;

    public BalancePruebaDto generar(Integer empresaId, LocalDate desde, LocalDate hasta, Integer nivel,
            String cuentaDesde, String cuentaHasta, Long terceroId, Long centroCostoId, String comparar,
            boolean soloConMovimiento) {
        if (desde == null || hasta == null || desde.isAfter(hasta)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rango de fechas no es válido");
        }
        int nivelMax = nivel != null && nivel > 0 ? nivel : 99;
        List<CuentaPlan> plan = repo.plan(empresaId);

        Map<String, BalancePruebaDto.Fila> filas = acumular(plan,
                repo.movimientos(empresaId, desde, hasta, terceroId, centroCostoId), nivelMax);

        BalancePruebaDto dto = new BalancePruebaDto();
        dto.setDesde(desde);
        dto.setHasta(hasta);

        if (MES_ANTERIOR.equals(comparar) || ANIO_ANTERIOR.equals(comparar)) {
            LocalDate cDesde;
            LocalDate cHasta;
            if (MES_ANTERIOR.equals(comparar)) {
                long dias = ChronoUnit.DAYS.between(desde, hasta);
                cHasta = desde.minusDays(1);
                // Un mes calendario completo se compara con el mes anterior completo.
                boolean mesCompleto = desde.getDayOfMonth() == 1 && hasta.equals(hasta.withDayOfMonth(hasta.lengthOfMonth()))
                        && desde.getMonth() == hasta.getMonth();
                cDesde = mesCompleto ? cHasta.withDayOfMonth(1) : cHasta.minusDays(dias);
            } else {
                cDesde = desde.minusYears(1);
                cHasta = hasta.minusYears(1);
            }
            dto.setComparadoDesde(cDesde);
            dto.setComparadoHasta(cHasta);
            Map<String, BalancePruebaDto.Fila> comparadas = acumular(plan,
                    repo.movimientos(empresaId, cDesde, cHasta, terceroId, centroCostoId), nivelMax);
            for (var e : filas.entrySet()) {
                BalancePruebaDto.Fila c = comparadas.get(e.getKey());
                BigDecimal comparado = c != null ? c.getSaldoFinal() : BigDecimal.ZERO;
                completarComparado(e.getValue(), comparado);
            }
            // Cuentas que tenían saldo antes y hoy no aparecen.
            for (var e : comparadas.entrySet()) {
                if (!filas.containsKey(e.getKey())) {
                    BalancePruebaDto.Fila f = copiaVacia(e.getValue());
                    completarComparado(f, e.getValue().getSaldoFinal());
                    filas.put(e.getKey(), f);
                }
            }
        }

        List<BalancePruebaDto.Fila> resultado = new ArrayList<>();
        BigDecimal db = BigDecimal.ZERO;
        BigDecimal cr = BigDecimal.ZERO;
        for (CuentaPlan c : plan) {
            BalancePruebaDto.Fila f = filas.get(c.codigo());
            if (f == null) continue;
            if (cuentaDesde != null && !cuentaDesde.isBlank() && c.codigo().compareTo(cuentaDesde.trim()) < 0) continue;
            if (cuentaHasta != null && !cuentaHasta.isBlank() && !c.codigo().startsWith(cuentaHasta.trim())
                    && c.codigo().compareTo(cuentaHasta.trim()) > 0) continue;
            if (soloConMovimiento && f.getSaldoAnterior().signum() == 0 && f.getDebitos().signum() == 0
                    && f.getCreditos().signum() == 0 && f.getSaldoFinal().signum() == 0
                    && (f.getSaldoComparado() == null || f.getSaldoComparado().signum() == 0)) continue;
            resultado.add(f);
            // Los totales se toman de las cuentas de clase (nivel 1) para no sumar dos veces.
            if (c.codigo().length() == 1) {
                db = db.add(f.getDebitos());
                cr = cr.add(f.getCreditos());
            }
        }
        dto.setFilas(resultado);
        dto.setTotalDebitos(db);
        dto.setTotalCreditos(cr);
        dto.setCuadra(db.compareTo(cr) == 0);
        return dto;
    }

    /** Sube el movimiento de cada auxiliar a todas sus cuentas padre (por prefijo) hasta el nivel pedido. */
    private Map<String, BalancePruebaDto.Fila> acumular(List<CuentaPlan> plan, List<Movimiento> movimientos,
            int nivelMax) {
        Map<Long, CuentaPlan> porId = new HashMap<>();
        plan.forEach(c -> porId.put(c.id(), c));
        Map<String, BalancePruebaDto.Fila> filas = new LinkedHashMap<>();
        for (Movimiento m : movimientos) {
            CuentaPlan aux = porId.get(m.cuentaId());
            if (aux == null) continue;
            for (CuentaPlan c : plan) {
                if (c.nivel() > nivelMax || !aux.codigo().startsWith(c.codigo())) continue;
                BalancePruebaDto.Fila f = filas.computeIfAbsent(c.codigo(), k -> nueva(c));
                boolean debito = !"CREDITO".equals(c.naturaleza());
                BigDecimal anterior = nz(m.anterior());
                f.setSaldoAnterior(f.getSaldoAnterior().add(debito ? anterior : anterior.negate()));
                f.setDebitos(f.getDebitos().add(nz(m.debitos())));
                f.setCreditos(f.getCreditos().add(nz(m.creditos())));
            }
        }
        for (var e : filas.entrySet()) {
            BalancePruebaDto.Fila f = e.getValue();
            boolean debito = !"CREDITO".equals(f.getNaturaleza());
            BigDecimal movimiento = debito ? f.getDebitos().subtract(f.getCreditos())
                    : f.getCreditos().subtract(f.getDebitos());
            f.setSaldoFinal(f.getSaldoAnterior().add(movimiento));
        }
        return filas;
    }

    private static BalancePruebaDto.Fila nueva(CuentaPlan c) {
        BalancePruebaDto.Fila f = new BalancePruebaDto.Fila();
        f.setCuentaId(c.id());
        f.setCodigo(c.codigo());
        f.setNombre(c.nombre());
        f.setNivel(c.nivel());
        f.setNaturaleza(c.naturaleza());
        f.setAuxiliar(c.auxiliar());
        return f;
    }

    private static BalancePruebaDto.Fila copiaVacia(BalancePruebaDto.Fila o) {
        BalancePruebaDto.Fila f = new BalancePruebaDto.Fila();
        f.setCuentaId(o.getCuentaId());
        f.setCodigo(o.getCodigo());
        f.setNombre(o.getNombre());
        f.setNivel(o.getNivel());
        f.setNaturaleza(o.getNaturaleza());
        f.setAuxiliar(o.isAuxiliar());
        return f;
    }

    private static void completarComparado(BalancePruebaDto.Fila f, BigDecimal comparado) {
        f.setSaldoComparado(comparado);
        f.setVariacion(f.getSaldoFinal().subtract(comparado));
        f.setVariacionPct(comparado.signum() != 0
                ? f.getVariacion().multiply(BigDecimal.valueOf(100)).divide(comparado.abs(), 1, RoundingMode.HALF_UP)
                : null);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
