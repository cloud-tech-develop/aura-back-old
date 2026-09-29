package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.cartera.agenda.AgendaResumenDto;
import com.cloud_technological.aura_pos.dto.cartera.tablero.TableroCarteraDto;
import com.cloud_technological.aura_pos.dto.cartera.tablero.TableroDeudorDto;
import com.cloud_technological.aura_pos.dto.cartera.tablero.TableroKpisDto;
import com.cloud_technological.aura_pos.dto.cartera.tablero.TableroMesDto;
import com.cloud_technological.aura_pos.repositories.cartera.AgendaCobroQueryRepository;
import com.cloud_technological.aura_pos.repositories.cartera.TableroCarteraQueryRepository;

@Service
public class TableroCarteraService {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    @Autowired
    private TableroCarteraQueryRepository repo;

    @Autowired
    private AgendaCobroQueryRepository agendaRepo;

    @Autowired
    private PromesaPagoService promesaPagoService;

    public TableroCarteraDto tablero(Integer empresaId, int meses) {
        int n = Math.max(3, Math.min(meses, 24));
        promesaPagoService.evaluar(empresaId, null);

        List<TableroMesDto> historia = repo.meses(empresaId, n);
        for (TableroMesDto m : historia) {
            m.setDso(dso(m.getSaldoTotal(), m.getVentas90()));
            m.setEfectividad(pct(m.getRecaudoCobrable(), m.getCobrable()));
        }

        TableroMesDto actual = historia.isEmpty() ? null : historia.get(historia.size() - 1);
        TableroMesDto anterior = historia.size() > 1 ? historia.get(historia.size() - 2) : null;

        List<TableroDeudorDto> top = repo.topDeudores(empresaId, 10);
        BigDecimal total = actual != null ? actual.getSaldoTotal() : BigDecimal.ZERO;
        for (TableroDeudorDto d : top) d.setPctTotal(pct(d.getSaldo(), total));

        TableroKpisDto k = new TableroKpisDto();
        if (actual != null) {
            k.setSaldoTotal(actual.getSaldoTotal());
            k.setVencido(actual.getVencido());
            k.setPctVencido(pct(actual.getVencido(), actual.getSaldoTotal()));
            k.setDso(actual.getDso());
            k.setRecaudoMes(actual.getRecaudado());
            k.setEfectividadMes(actual.getEfectividad());
        }
        if (anterior != null) {
            k.setSaldoTotalAnterior(anterior.getSaldoTotal());
            k.setPctVencidoAnterior(pct(anterior.getVencido(), anterior.getSaldoTotal()));
            k.setDsoAnterior(anterior.getDso());
            k.setRecaudoMesAnterior(anterior.getRecaudado());
            k.setEfectividadMesAnterior(anterior.getEfectividad());
        }
        int[] clientes = repo.clientes(empresaId);
        k.setClientesConSaldo(clientes[0]);
        k.setClientesEnMora(clientes[1]);
        AgendaResumenDto promesas = agendaRepo.resumenMes(empresaId);
        k.setPromesasCumplidasMes(promesas.getPromesasCumplidasMes());
        k.setPromesasResueltasMes(promesas.getPromesasResueltasMes());
        k.setConcentracionTop5(pct(top.stream().limit(5).map(TableroDeudorDto::getSaldo)
                .reduce(BigDecimal.ZERO, BigDecimal::add), total));

        TableroCarteraDto dto = new TableroCarteraDto();
        dto.setKpis(k);
        dto.setMeses(historia);
        dto.setTopDeudores(top);
        dto.setPorVendedor(repo.porVendedor(empresaId));
        dto.setRecaudoPorMedio(repo.recaudoPorMedio(empresaId));
        return dto;
    }

    /** Sin ventas a crédito en 90 días el DSO no dice nada: null. */
    private static Integer dso(BigDecimal saldo, BigDecimal ventas90) {
        if (saldo == null || ventas90 == null || ventas90.signum() <= 0) return null;
        return saldo.multiply(BigDecimal.valueOf(90)).divide(ventas90, 0, RoundingMode.HALF_UP).intValue();
    }

    private static BigDecimal pct(BigDecimal parte, BigDecimal total) {
        if (parte == null || total == null || total.signum() <= 0) return null;
        return parte.multiply(CIEN).divide(total, 1, RoundingMode.HALF_UP);
    }
}
