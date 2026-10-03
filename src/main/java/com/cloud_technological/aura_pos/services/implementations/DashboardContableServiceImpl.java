package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.contabilidad.DashboardContableDto;
import com.cloud_technological.aura_pos.dto.contabilidad.DashboardContableDto.EstadoContableDto;
import com.cloud_technological.aura_pos.dto.contabilidad.DashboardContableDto.ResultadoMesDto;
import com.cloud_technological.aura_pos.repositories.contabilidad.DashboardContableQueryRepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.DashboardContableQueryRepository.TotalMesTipo;
import com.cloud_technological.aura_pos.services.DashboardContableService;

@Service
public class DashboardContableServiceImpl implements DashboardContableService {

    @Autowired
    private DashboardContableQueryRepository repo;

    @Override
    @Transactional(readOnly = true)
    public DashboardContableDto resumen(Integer empresaId, int anio, int mes) {
        YearMonth actual = YearMonth.of(anio, mes);
        YearMonth anterior = actual.minusMonths(1);

        // Una sola consulta cubre la serie (enero..mes) y el mes anterior,
        // que en enero cae en el año pasado.
        YearMonth desdeMes = anterior.isBefore(YearMonth.of(anio, 1)) ? anterior : YearMonth.of(anio, 1);
        LocalDate desde = desdeMes.atDay(1);
        LocalDate hasta = actual.atEndOfMonth();

        Map<YearMonth, ResultadoMesDto> porMes = new LinkedHashMap<>();
        for (YearMonth m = desdeMes; !m.isAfter(actual); m = m.plusMonths(1)) {
            porMes.put(m, vacio(m));
        }
        for (TotalMesTipo t : repo.totalesPorMes(empresaId, desde, hasta)) {
            ResultadoMesDto r = porMes.get(YearMonth.of(t.anio(), t.mes()));
            if (r == null || t.saldo() == null) continue;
            switch (t.tipo()) {
                case "INGRESO" -> r.setIngresos(t.saldo());
                case "COSTO" -> r.setCostos(t.saldo());
                case "GASTO" -> r.setGastos(t.saldo());
                default -> { }
            }
        }

        DashboardContableDto dto = new DashboardContableDto();
        dto.setAnio(anio);
        dto.setMes(mes);
        dto.setMesActual(porMes.get(actual));
        dto.setMesAnterior(porMes.get(anterior));
        porMes.forEach((m, r) -> {
            if (m.getYear() == anio) dto.getSerie().add(r);
        });
        dto.setDistribucionGastos(repo.gastosPorGrupo(empresaId, actual.atDay(1), hasta));
        dto.setEstado(estado(empresaId, actual));
        return dto;
    }

    private EstadoContableDto estado(Integer empresaId, YearMonth mes) {
        EstadoContableDto e = new EstadoContableDto();
        String periodo = repo.estadoPeriodo(empresaId, mes.getYear(), mes.getMonthValue());
        e.setPeriodoEstado(periodo != null ? periodo : "SIN_PERIODO");
        e.setComprobantesMes(repo.comprobantes(empresaId, mes.atDay(1), mes.atEndOfMonth()));
        e.setComprobantesBorrador(repo.borradores(empresaId));
        e.setConciliacionesAbiertas(repo.extractosAbiertos(empresaId));

        // El cierre que importa en el año en curso es el del año anterior.
        int anioCierre = mes.getYear() - 1;
        List<String> ops = repo.operacionesCierreAnual(empresaId, anioCierre);
        e.setCierreAnualAnio(anioCierre);
        e.setCierreAnualEstado(ops.contains("TRASLADO") ? "CERRADO"
                : ops.contains("PROVISION_RENTA") ? "PROVISIONADO" : "NO_INICIADO");
        return e;
    }

    private static ResultadoMesDto vacio(YearMonth m) {
        ResultadoMesDto r = new ResultadoMesDto();
        r.setAnio(m.getYear());
        r.setMes(m.getMonthValue());
        return r;
    }
}
