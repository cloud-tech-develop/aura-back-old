package com.cloud_technological.aura_pos.controllers;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.contabilidad.BalanceComprobacionDto;
import com.cloud_technological.aura_pos.dto.contabilidad.BalanceComprobacionLineaDto;
import com.cloud_technological.aura_pos.entity.PeriodoContableEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableQueryRepository;
import com.cloud_technological.aura_pos.repositories.periodo_contable.PeriodoContableJPARepository;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/reportes-contables")
public class ReporteContableController {

    @Autowired private AsientoContableQueryRepository queryRepo;
    @Autowired private PeriodoContableJPARepository periodoRepo;
    @Autowired private SecurityUtils securityUtils;
    @Autowired private com.cloud_technological.aura_pos.services.implementations.BalancePruebaService balancePrueba;

    /**
     * Balance de prueba completo (Fase 4): saldo anterior, débitos, créditos y
     * saldo final hasta el nivel pedido, con filtros y comparativo.
     *
     * @param comparar MES_ANTERIOR | ANIO_ANTERIOR (opcional)
     */
    @GetMapping("/balance-prueba")
    public ResponseEntity<ApiResponse<com.cloud_technological.aura_pos.dto.contabilidad.BalancePruebaDto>> balancePrueba(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate desde,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate hasta,
            @RequestParam(required = false) Integer nivel,
            @RequestParam(required = false) String cuentaDesde,
            @RequestParam(required = false) String cuentaHasta,
            @RequestParam(required = false) Long terceroId,
            @RequestParam(required = false) Long centroCostoId,
            @RequestParam(required = false) String comparar,
            @RequestParam(required = false, defaultValue = "true") boolean soloConMovimiento) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, balancePrueba.generar(empresaId, desde, hasta,
                nivel, cuentaDesde, cuentaHasta, terceroId, centroCostoId, comparar, soloConMovimiento)));
    }

    /**
     * Balance de Comprobación formal por período contable.
     * Muestra todas las cuentas con movimiento en el período,
     * sus débitos, créditos y saldo.
     */
    @GetMapping("/balance-comprobacion")
    public ResponseEntity<ApiResponse<BalanceComprobacionDto>> balanceComprobacion(
            @RequestParam Long periodoId) {

        Integer empresaId = securityUtils.getEmpresaId();

        PeriodoContableEntity periodo = periodoRepo.findByIdAndEmpresaId(periodoId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Período contable no encontrado"));

        List<BalanceComprobacionLineaDto> lineas =
                queryRepo.balanceComprobacion(periodoId, empresaId);

        BigDecimal totalDb = lineas.stream()
                .map(BalanceComprobacionLineaDto::getTotalDebito)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCr = lineas.stream()
                .map(BalanceComprobacionLineaDto::getTotalCredito)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BalanceComprobacionDto result = new BalanceComprobacionDto();
        result.setPeriodoId(periodoId);
        result.setAnio(periodo.getAnio());
        result.setMes(periodo.getMes());
        result.setEstadoPeriodo(periodo.getEstado());
        result.setLineas(lineas);
        result.setTotalDebito(totalDb);
        result.setTotalCredito(totalCr);
        result.setCuadrado(totalDb.subtract(totalCr).abs().compareTo(new BigDecimal("0.01")) < 0);

        return ResponseEntity.ok(
                new ApiResponse<>(HttpStatus.OK.value(), "Balance de Comprobación", false, result));
    }
}
