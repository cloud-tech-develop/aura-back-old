package com.cloud_technological.aura_pos.dto.bodegas;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BodegaTableDto {
    private Long id;
    private String codigo;
    private String nombre;
    private Integer sucursalId;
    private String sucursalNombre;
    private Integer responsableUsuarioId;
    private String responsableNombre;
    private Boolean esPrincipal;
    private Boolean permiteVenta;
    private String ubicacion;
    private String observacion;
    private Boolean activa;
    /** Cuántos productos tienen saldo distinto de cero aquí. */
    private Long referencias;
    /** Valor del inventario al costo. Es lo que responde el responsable. */
    private BigDecimal valorInventario;
    private Long totalRows;
}
