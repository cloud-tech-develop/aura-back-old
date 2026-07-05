package com.cloud_technological.aura_pos.dto.proyecto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class ProyectoTableDto {
    private Long id;
    private String codigo;
    private String nombre;
    private Long clienteId;
    private String clienteNombre;
    private String descripcion;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String estado;
    private Long centroCostoId;
    private String centroCostoNombre;
    private Long responsableAdministrativoId;
    private Boolean requiereControlAsistencia;
    private String ciudad;
    private String ubicacion;
    private String observacion;
    private long frentesCount;
    private long totalRows;
}
