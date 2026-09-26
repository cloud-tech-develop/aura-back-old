package com.cloud_technological.aura_pos.dto.inventario;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoteTableDto {
    private Long id;
    private Long productoId;
    private String productoNombre;
    private Long sucursalId;
    private String sucursalNombre;
    private String codigoLote;
    private LocalDate fechaVencimiento;
    private BigDecimal stockActual;
    private BigDecimal costoUnitario;
    private Boolean activo;
    private LocalDate fechaFabricacion;
    /** Negativo = ya venció. Null si el lote no tiene vencimiento. */
    private Integer diasParaVencer;
    private String unidadAbreviatura;
    private Long compraId;
    private String compraNumero;
    private String proveedorNombre;
    private long totalRows;
}