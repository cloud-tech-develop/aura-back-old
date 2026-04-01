package com.cloud_technological.aura_pos.dto.reportes;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReporteRotacionInventarioDto {
    private Long productoId;
    private String productoNombre;
    private String sku;
    private String categoriaNombre;
    private BigDecimal stockActual;
    private BigDecimal stockMinimo;
    private BigDecimal unidadesVendidas;
    private Long diasSinMovimiento;
    private BigDecimal rotacion;
    /** CRITICO | BAJO | NORMAL | ALTO */
    private String estadoStock;
}
