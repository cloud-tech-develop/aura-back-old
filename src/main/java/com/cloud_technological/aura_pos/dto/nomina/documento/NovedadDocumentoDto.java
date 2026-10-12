package com.cloud_technological.aura_pos.dto.nomina.documento;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** Novedad de un empleado de la prenómina (pestaña Novedades). */
@Getter
@Setter
public class NovedadDocumentoDto {
    private Long id;
    private Long nominaId;
    private String empleadoNombre;
    private String empleadoDocumento;
    private String tipo;
    private String subtipo;
    private String descripcion;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Integer dias;
    private BigDecimal cantidad;
    private BigDecimal valorUnitario;
    private BigDecimal valorTotal;
    private Boolean esDeduccion;
    /** MANUAL | ASISTENCIA… */
    private String origen;
}
