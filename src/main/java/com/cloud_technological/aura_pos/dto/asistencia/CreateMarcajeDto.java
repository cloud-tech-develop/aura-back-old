package com.cloud_technological.aura_pos.dto.asistencia;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateMarcajeDto {
    private Long empleadoId;
    private LocalDateTime fechaHoraMarcaje; // si null, se toma ahora
    private String tipoMarcaje;             // ENTRADA | SALIDA | INICIO_DESCANSO | FIN_DESCANSO
    private String origenMarcaje;           // opcional, default ASISTENTE
    private String observacion;
    private String evidenciaUrl;
}
