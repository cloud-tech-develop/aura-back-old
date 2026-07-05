package com.cloud_technological.aura_pos.dto.asistencia_frente;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import lombok.Data;

@Data
public class RevisionFilterDto {
    private Integer page = 0;
    private Integer rows = 10;
    /** Estado a filtrar; por defecto ENVIADO_REVISION en el front. */
    private String estado;
    private Long proyectoId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fecha;
}
