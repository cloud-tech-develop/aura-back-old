package com.cloud_technological.aura_pos.dto.visitas;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class VisitaDto {
    private Long id;
    private Long empresaId;
    private Long localId;
    private String localNombre;
    private String localDireccion;
    private Long vendedorId;
    private String vendedorNombre;
    private Long rutaId;
    private String rutaNombre;
    private LocalDateTime fechaProgramada;
    private String horaProgramada;
    private LocalDateTime fechaReal;
    private Double latitudLlegada;
    private Double longitudLlegada;
    private String estado;
    private String observaciones;
}