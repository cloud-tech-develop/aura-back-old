package com.cloud_technological.aura_pos.controllers;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.cartera.agenda.AgendaCobroDto;
import com.cloud_technological.aura_pos.dto.cartera.agenda.AgendaResumenDto;
import com.cloud_technological.aura_pos.repositories.cartera.AgendaCobroQueryRepository;
import com.cloud_technological.aura_pos.services.implementations.PromesaPagoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Agenda del cobrador: promesas de hoy, incumplidas, por vencer y vencidos sin gestión. */
@RestController
@RequestMapping("/api/cartera/agenda")
public class AgendaCobroController {

    @Autowired
    private AgendaCobroQueryRepository repo;

    @Autowired
    private PromesaPagoService promesaPagoService;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<AgendaCobroDto>> agenda(@RequestParam(defaultValue = "7") int dias) {
        Integer empresaId = securityUtils.getEmpresaId();
        int ventana = Math.max(1, Math.min(dias, 60));
        // Antes de mostrar, las promesas se ponen al día con lo que ya entró.
        promesaPagoService.evaluar(empresaId, null);

        AgendaCobroDto dto = new AgendaCobroDto();
        dto.setPromesasHoy(repo.promesasHoy(empresaId));
        dto.setPromesasIncumplidas(repo.promesasIncumplidas(empresaId));
        dto.setPromesasProximas(repo.promesasProximas(empresaId, ventana));
        dto.setPorVencer(repo.porVencer(empresaId, ventana));
        dto.setVencidosSinGestion(repo.vencidosSinGestion(empresaId));

        AgendaResumenDto r = repo.resumenMes(empresaId);
        r.setPromesasHoy(dto.getPromesasHoy().size());
        r.setMontoPromesasHoy(dto.getPromesasHoy().stream()
                .map(p -> p.getMontoPrometido().subtract(p.getMontoPagado()).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        r.setPromesasIncumplidas(dto.getPromesasIncumplidas().size());
        r.setMontoPromesasIncumplidas(dto.getPromesasIncumplidas().stream()
                .map(p -> p.getMontoPrometido().subtract(p.getMontoPagado()).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        r.setFacturasPorVencer(dto.getPorVencer().size());
        r.setSaldoPorVencer(sumar(dto.getPorVencer().stream().map(f -> f.getSaldoPendiente()).toList()));
        r.setClientesSinGestion(dto.getVencidosSinGestion().size());
        r.setSaldoSinGestion(sumar(dto.getVencidosSinGestion().stream().map(c -> c.getSaldoVencido()).toList()));
        dto.setResumen(r);
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, dto));
    }

    private static BigDecimal sumar(List<BigDecimal> valores) {
        return valores.stream().map(v -> v != null ? v : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
