package com.cloud_technological.aura_pos.dto.consumo_interno;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConceptoConsumoInternoDto {
    private Long id;
    private String nombre;
    private Long cuentaId;
    /** "519525 · Aseo y cafetería"; null si usa la cuenta por defecto. */
    private String cuentaCodigo;
    private String cuentaNombre;
    private Boolean generaIva;
    private Boolean activo;
}
