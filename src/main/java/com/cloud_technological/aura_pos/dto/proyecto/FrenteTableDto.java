package com.cloud_technological.aura_pos.dto.proyecto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class FrenteTableDto {
    private Long id;
    private Long proyectoId;
    private String codigo;
    private String nombre;
    private String descripcion;
    private String ubicacion;
    private Long liderId;
    private String liderNombre;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String estado;
    private String observacion;
    private long trabajadoresCount;
}
