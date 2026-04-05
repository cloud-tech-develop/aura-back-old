package com.cloud_technological.aura_pos.dto.rutas;

import java.util.List;

import lombok.Data;

@Data
public class RutaDto {
    private Long id;
    private Long empresaId;
    private Long vendedorId;
    private String vendedorNombre;
    private String nombre;
    private String descripcion;
    private Integer diaSemana;
    private Boolean activo;
    private List<Long> localIds;
}