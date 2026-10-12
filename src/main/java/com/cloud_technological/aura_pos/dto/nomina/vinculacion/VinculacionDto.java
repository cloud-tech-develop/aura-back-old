package com.cloud_technological.aura_pos.dto.nomina.vinculacion;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** Detalle de una vinculación, con los nombres de lo que referencia. */
@Getter
@Setter
public class VinculacionDto {
    private Long id;
    private String codigo;
    private String estado;

    private Long empleadoId;
    private Long terceroId;
    private String empleadoNombre;
    private String empleadoDocumento;

    private Long tipoVinculacionId;
    private String tipoVinculacionNombre;
    private Long cargoId;
    private String cargoNombre;
    private Long grupoNominaId;
    private String grupoNominaNombre;
    private String tipoContrato;
    private Boolean temporal;
    private String tipoRemuneracion;
    private BigDecimal salarioBase;
    private String frecuenciaPago;
    private BigDecimal horasSemana;
    private Long calendarioId;
    private String calendarioNombre;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private LocalDate fechaFinPrueba;
    private String fase;
    private Boolean esPrincipal;
    private Boolean primerEmpleo;

    private Integer sucursalId;
    private String sucursalNombre;
    private String area;
    private String municipioTrabajo;
    private Long municipioTrabajoId;
    private String direccionTrabajo;
    private String telefonoTrabajo;
    private Long jefeDirectoId;
    private Long jefeDirectoTerceroId;
    private String jefeDirectoNombre;
    private Long evaluadorId;
    private Long evaluadorTerceroId;
    private String evaluadorNombre;

    private String tipoCotizante;
    private String subtipoCotizante;
    private Long riesgoArlId;
    private String riesgoArlNombre;
    private Boolean enExtranjero;

    private String docInternoTipo;
    private String docInternoNumero;
    private LocalDate docInternoFecha;
    private String docInternoUrl;
    private String docExternoUrl;

    private String causaRetiro;
    private String observacion;
}
