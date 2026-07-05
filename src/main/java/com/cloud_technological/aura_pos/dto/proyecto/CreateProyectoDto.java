package com.cloud_technological.aura_pos.dto.proyecto;

import java.time.LocalDate;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import lombok.Data;

@Data
public class CreateProyectoDto {

    @NotBlank(message = "El código es obligatorio")
    @Size(max = 30, message = "El código no puede superar 30 caracteres")
    private String codigo;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
    private String nombre;

    private Long clienteId;
    private String descripcion;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    /** ACTIVO | SUSPENDIDO | FINALIZADO | ANULADO */
    private String estado;
    private Long centroCostoId;
    private Long responsableAdministrativoId;
    private Boolean requiereControlAsistencia;
    private String ciudad;
    private String ubicacion;
    private String observacion;
}
