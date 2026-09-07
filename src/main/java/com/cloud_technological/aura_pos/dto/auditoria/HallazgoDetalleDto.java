package com.cloud_technological.aura_pos.dto.auditoria;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** Una fila concreta detrás de un hallazgo: el soporte para ir a revisarla. */
@Getter
@Setter
public class HallazgoDetalleDto {

    /** El documento tal como lo ve el usuario: "CE-000045", "Turno #88". */
    private String referencia;
    private LocalDate fecha;
    private String descripcion;
    private BigDecimal monto;

    /** Tipo e id del registro, para que el front pueda enlazar al documento. */
    private String origenTipo;
    private Long origenId;
}
