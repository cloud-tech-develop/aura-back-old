package com.cloud_technological.aura_pos.dto.nomina.nomina;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * Estado local de la nómina electrónica de una nómina. Lo consume el front para
 * saber si ya se envió (y no volver a mostrar "Enviar") y mostrar el CUNE.
 */
@Getter
@Setter
public class NominaElectronicaEstadoDto {
    private Long id;
    private Long nominaId;
    private String empleadoNombre;
    private Integer agno;
    private Integer mes;
    private String estado;          // PENDIENTE | ENVIADO | ACEPTADO | RECHAZADO | ANULADO
    private String cune;
    private String referenceCode;
    private String prefijo;
    private Long consecutivo;
    private Boolean esAjuste;
    private LocalDateTime fechaEnvio;
    private Boolean tieneXml;
}
