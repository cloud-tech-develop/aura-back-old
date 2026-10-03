package com.cloud_technological.aura_pos.dto.contabilidad;

import javax.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Categoría contable de producto con sus cuentas destino (E4). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoriaContableProductoDto {

    private Long id;

    @NotBlank
    private String nombre;

    /** BIEN | SERVICIO | INSUMO | ACTIVO_FIJO | INTANGIBLE | GASTO | DOTACION | DIFERIDO */
    private String tipo;

    private Long cuentaIngresoId;
    private String cuentaIngreso;
    private Long cuentaInventarioId;
    private String cuentaInventario;
    private Long cuentaCostoId;
    private String cuentaCosto;
    private Long cuentaDevolucionId;
    private String cuentaDevolucion;

    // Activos, intangibles y diferidos (V185): valores por defecto de la ficha
    // o del diferido que crea la compra.
    private Long cuentaDepreciacionId;
    private String cuentaDepreciacion;
    private Long cuentaGastoDepreciacionId;
    private String cuentaGastoDepreciacion;
    private Integer vidaUtilMeses;
    private Integer mesesDiferido;
    private Boolean activo;
}
