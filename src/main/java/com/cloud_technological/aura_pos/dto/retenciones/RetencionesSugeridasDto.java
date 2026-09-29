package com.cloud_technological.aura_pos.dto.retenciones;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/** Respuesta del endpoint /sugeridas — porcentajes listos para pre-llenar el formulario */
@Getter @Setter @Builder
public class RetencionesSugeridasDto {
    private Long terceroId;
    private String tipoPersona;
    /** Porcentaje de Retefuente a aplicar */
    private BigDecimal retefuentePct;
    private String retefuenteConcepto;
    /** Porcentaje de ReteIVA a aplicar */
    private BigDecimal reteivaPct;
    private String reteivaConcepto;
    /** Porcentaje de ReteICA a aplicar */
    private BigDecimal reteicaPct;
    private String reteicaConcepto;
}
