package com.cloud_technological.aura_pos.dto.inventario;

import java.math.BigDecimal;
import java.time.LocalDate;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateLoteDto {
    @NotNull(message = "El producto es obligatorio")
    private Long productoId;
    @NotNull(message = "La sucursal es obligatoria")
    private Long sucursalId;
    @NotBlank(message = "El código de lote es obligatorio")
    private String codigoLote;
    private LocalDate fechaVencimiento;
    @NotNull(message = "El stock inicial es obligatorio")
    private BigDecimal stockActual;
    @NotNull(message = "El costo unitario es obligatorio")
    private BigDecimal costoUnitario;
    private Boolean activo = true;
}