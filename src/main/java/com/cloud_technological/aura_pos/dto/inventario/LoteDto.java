package com.cloud_technological.aura_pos.dto.inventario;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoteDto {
    private Long id;
    private Long productoId;
    private String productoNombre;
    private Long sucursalId;
    private String sucursalNombre;
    private String codigoLote;
    private LocalDate fechaVencimiento;
    private BigDecimal stockActual;
    private BigDecimal costoUnitario;
    private Boolean activo;
}