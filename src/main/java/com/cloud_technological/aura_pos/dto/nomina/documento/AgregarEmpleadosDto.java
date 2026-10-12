package com.cloud_technological.aura_pos.dto.nomina.documento;

import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** Agregar empleados a una prenómina por "tipo de lista". */
@Getter
@Setter
public class AgregarEmpleadosDto {

    @NotBlank(message = "El tipo de lista es obligatorio")
    @Pattern(regexp = "EMPLEADO|VINCULACION|GRUPO|TIPO_VINCULACION|TODOS", message = "Tipo de lista no válido")
    private String tipoLista;

    /** Terceros, contratos, grupos o tipos de vinculación según el tipo de lista; vacío en TODOS. */
    private List<Long> ids = new ArrayList<>();
}
