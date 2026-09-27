package com.cloud_technological.aura_pos.dto.contabilidad.notas;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class NotaDiarioLineaDto {
    private Long id;
    private Long cuentaId;
    private String cuentaCodigo;
    private String cuentaNombre;
    private String descripcion;
    private BigDecimal debito;
    private BigDecimal credito;
    private Long terceroId;
    private String terceroNombre;
    private String terceroDocumento;
    private Long centroCostoId;
    private String centroCostoNombre;
    private Long proyectoId;
    private String proyectoNombre;
    private Long frenteId;
    private String frenteNombre;
}
