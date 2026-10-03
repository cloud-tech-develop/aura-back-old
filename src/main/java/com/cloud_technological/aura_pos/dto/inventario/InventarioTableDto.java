package com.cloud_technological.aura_pos.dto.inventario;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InventarioTableDto {
    private Long id;
    private Long sucursalId;
    private String sucursalNombre;
    private Long bodegaId;
    private String bodegaNombre;
    private Long productoId;
    private String productoNombre;
    private String productoSku;
    private BigDecimal stockActual;
    private BigDecimal stockMinimo;
    private BigDecimal stockMaximo;
    private BigDecimal puntoReorden;
    /** Para mostrar el stock "como se cuenta": 3 Cajas + 4 und (F7 presentaciones). */
    private String unidadAbreviatura;
    private String presentacionNombre;
    private BigDecimal presentacionFactor;
    private String ubicacion;
    private long totalRows;
}
