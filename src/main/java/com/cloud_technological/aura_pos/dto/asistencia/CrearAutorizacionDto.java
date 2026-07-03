package com.cloud_technological.aura_pos.dto.asistencia;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CrearAutorizacionDto {
    private Long empleadoId;
    private Long periodoNominaId;
    private String motivo;       // FALLA_SISTEMA_ASISTENCIA | MARCACION_NO_DISPONIBLE | ORDEN_ADMINISTRATIVA | CIERRE_URGENTE_NOMINA | CORRECCION_POSTERIOR
    private String observacion;
}
