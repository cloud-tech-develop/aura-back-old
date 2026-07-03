package com.cloud_technological.aura_pos.dto.nomina.prestacion;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LiquidacionDefinitivaDto {
    private Long empleadoId;
    private LocalDate fechaRetiro; // opcional: si null, se toma del empleado (retiro o fin de contrato)
    private String motivo;         // RENUNCIA | DESPIDO_SIN_JUSTA_CAUSA | DESPIDO_CON_JUSTA_CAUSA | MUTUO_ACUERDO | FIN_CONTRATO
}
