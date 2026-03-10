package com.cloud_technological.aura_pos.dto.reglas_descuento;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateReglaDescuentoDto {
    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private List<Integer> diasSemana;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private Long categoriaId;
    private Long productoId;
    @NotBlank(message = "El tipo de descuento es obligatorio")
    private String tipoDescuento;
    @NotNull(message = "El valor es obligatorio")
    private BigDecimal valor;
    private Boolean activo;
}
