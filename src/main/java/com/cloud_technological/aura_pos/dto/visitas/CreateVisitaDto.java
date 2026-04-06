package com.cloud_technological.aura_pos.dto.visitas;

import lombok.Data;

@Data
public class CreateVisitaDto {
    private Long localId;
    private Long rutaId;
    private Long vendedorId;
    private String fechaProgramada;
    private String horaProgramada;
    private String observaciones;
}