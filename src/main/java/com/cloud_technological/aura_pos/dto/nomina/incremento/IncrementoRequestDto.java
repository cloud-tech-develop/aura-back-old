package com.cloud_technological.aura_pos.dto.nomina.incremento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** Crear o recalcular un incremento salarial (queda pendiente, con la vista previa). */
@Getter
@Setter
public class IncrementoRequestDto {

    @NotBlank(message = "Elija a quién aplica")
    @Pattern(regexp = "EMPLEADO|VINCULACION|GRUPO|TIPO_VINCULACION|TODOS", message = "Tipo de lista no válido")
    private String tipoLista;

    /** Terceros, vinculaciones, grupos o tipos según el tipo de lista; vacío en TODOS. */
    private List<Long> ids = new ArrayList<>();

    @NotBlank(message = "Elija el tipo de incremento")
    @Pattern(regexp = "PORCENTAJE|VALOR_ADICIONAL|NUEVO_VALOR|SMMLV", message = "Tipo de incremento no válido")
    private String tipoIncremento;

    /** Porcentaje, valor adicional o nuevo valor (no aplica en SMMLV). */
    private BigDecimal valor;

    @NotBlank(message = "Elija el redondeo")
    @Pattern(regexp = "NINGUNO|PESO|CENTENA|CENTENA_ARRIBA|CENTENA_ABAJO|MIL", message = "Redondeo no válido")
    private String redondeo;

    @NotNull(message = "La fecha de aplicación es obligatoria")
    private LocalDate fechaAplicacion;

    @Size(max = 300, message = "La descripción admite hasta 300 caracteres")
    private String descripcion;
}
