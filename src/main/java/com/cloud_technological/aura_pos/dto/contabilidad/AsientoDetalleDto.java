package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AsientoDetalleDto {
    private Long id;
    private Long cuentaId;
    private String cuentaCodigo;
    private String cuentaNombre;
    private String cuentaTipo;
    private String descripcion;
    private BigDecimal debito;
    private BigDecimal credito;
}
