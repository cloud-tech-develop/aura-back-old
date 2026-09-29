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

    /** BIEN | SERVICIO | INSUMO | ACTIVO_FIJO */
    private String tipo;

    private Long cuentaIngresoId;
    private String cuentaIngreso;
    private Long cuentaInventarioId;
    private String cuentaInventario;
    private Long cuentaCostoId;
    private String cuentaCosto;
    private Long cuentaDevolucionId;
    private String cuentaDevolucion;
    private Boolean activo;
}
