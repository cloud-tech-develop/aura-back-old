package com.cloud_technological.aura_pos.dto.obligaciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ObligacionDto {
    private Long id;
    private String entidad;
    private Long terceroId;
    private String numero;
    private BigDecimal montoPrincipal;
    private BigDecimal tasaMensual;
    private Integer plazoMeses;
    private LocalDate fechaDesembolso;
    private Long cuentaBancariaId;
    private BigDecimal saldoCapital;
    private String estado;
    private List<CuotaAmortizacionDto> cuotas;
}
