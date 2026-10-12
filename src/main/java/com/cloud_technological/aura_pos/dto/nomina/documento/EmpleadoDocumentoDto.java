package com.cloud_technological.aura_pos.dto.nomina.documento;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** Fila de la pestaña Empleados de una prenómina. */
@Getter
@Setter
public class EmpleadoDocumentoDto {
    /** Fila {@code nomina} del empleado en el documento. */
    private Long nominaId;
    private Long contratoId;
    private String codigoVinculacion;
    private String empleadoNombre;
    private String empleadoDocumento;
    private String cargo;
    private String tipoVinculacion;
    private String grupoNomina;
    private BigDecimal salarioBase;
    /** "Administración 60 %, Ventas 40 %"; null si no tiene distribución. */
    private String centrosCosto;
    private String estado;
}
