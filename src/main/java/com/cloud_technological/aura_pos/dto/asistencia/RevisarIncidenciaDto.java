package com.cloud_technological.aura_pos.dto.asistencia;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RevisarIncidenciaDto {
    // Estado destino: JUSTIFICADA | NO_JUSTIFICADA | APROBADA_COMO_NOVEDAD | RECHAZADA | CORREGIDA | ANULADA
    private String estado;
    private String observacionRevision;
    private String soporteUrl;
}
