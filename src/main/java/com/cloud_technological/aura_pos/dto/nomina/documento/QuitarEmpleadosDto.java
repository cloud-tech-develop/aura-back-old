package com.cloud_technological.aura_pos.dto.nomina.documento;

import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotEmpty;

import lombok.Getter;
import lombok.Setter;

/** Quitar filas (empleados) de una prenómina. */
@Getter
@Setter
public class QuitarEmpleadosDto {

    @NotEmpty(message = "Elija al menos un empleado")
    private List<Long> nominaIds = new ArrayList<>();
}
