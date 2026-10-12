package com.cloud_technological.aura_pos.dto.retenciones;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class TarifaRetencionDto {
    private Long id;
    private Integer empresaId;
    private String tipo;
    private String concepto;
    private String codigoConcepto;
    private BigDecimal tarifaNatural;
    private BigDecimal tarifaJuridica;
    private BigDecimal baseMinima;
    private Boolean activo;
    /** Cuenta del concepto (2365xx, 2367xx, 2368xx). Sin ella, la cuenta por defecto. */
    private Long cuentaContableId;
    private String cuentaCodigo;
    private String cuentaNombre;
    /** A quién se le paga: DIAN o la secretaría de hacienda del municipio. */
    private Long terceroAcreedorId;
    private String terceroAcreedorNombre;
}
