package com.cloud_technological.aura_pos.dto.nomina.vacaciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** Vacaciones registradas, para el listado. */
@Getter
@Setter
public class VacacionDto {
    private Long id;
    private Long contratoId;
    private String codigoVinculacion;
    private Long empleadoId;
    private String empleadoNombre;
    private String empleadoDocumento;
    private String tipo;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private LocalDate fechaRegreso;
    private BigDecimal diasHabiles;
    private LocalDate fechaRegresoAnticipado;
    private BigDecimal diasNoDisfrutados;
    private String estado;
    private String lote;
    private String observacion;
    private LocalDateTime createdAt;
    private long totalRows;
}
