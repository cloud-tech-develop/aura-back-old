package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;
import java.time.LocalDate;

import javax.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Impuesto parametrizable con sus cuentas (E5). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImpuestoDto {

    private Long id;

    @NotBlank
    private String nombre;

    /** IVA | INC | EXCLUIDO | EXENTO */
    private String tipo;

    private BigDecimal porcentaje;
    private Long cuentaGeneradoId;
    private String cuentaGenerado;
    private Long cuentaDescontableId;
    private String cuentaDescontable;
    /** A quién se le paga el impuesto (DIAN para IVA e INC). */
    private Long terceroAcreedorId;
    private String terceroAcreedor;
    private LocalDate vigenteDesde;
    private LocalDate vigenteHasta;
    private Boolean activo;
}
