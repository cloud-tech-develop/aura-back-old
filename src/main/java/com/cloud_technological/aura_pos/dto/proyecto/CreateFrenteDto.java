package com.cloud_technological.aura_pos.dto.proyecto;

import java.time.LocalDate;
import java.util.List;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import lombok.Data;

@Data
public class CreateFrenteDto {

    @NotBlank(message = "El código es obligatorio")
    @Size(max = 30, message = "El código no puede superar 30 caracteres")
    private String codigo;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
    private String nombre;

    private String descripcion;
    private String ubicacion;
    private Long liderId;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String estado;
    private String observacion;

    /** IDs de empleados a asignar como trabajadores del frente (opcional). */
    private List<Long> trabajadorIds;
}
