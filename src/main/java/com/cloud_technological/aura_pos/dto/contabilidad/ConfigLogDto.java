package com.cloud_technological.aura_pos.dto.contabilidad;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Fila del historial de cambios del mapeo concepto→cuenta. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigLogDto {

    private Long id;
    private String concepto;
    private String descripcionConcepto;
    private Long cuentaAnteriorId;
    private String cuentaAnterior;
    private Long cuentaNuevaId;
    private String cuentaNueva;
    private Long usuarioId;
    private String fecha;
}
