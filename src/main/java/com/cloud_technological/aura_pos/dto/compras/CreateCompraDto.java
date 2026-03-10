package com.cloud_technological.aura_pos.dto.compras;

import java.time.LocalDateTime;
import java.util.List;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCompraDto {
    @NotNull(message = "El proveedor es obligatorio")
    private Long proveedorId;
    @NotNull(message = "La sucursal es obligatoria")
    private Long sucursalId;
    private String numeroCompra;
    private LocalDateTime fecha;
    private LocalDateTime fechaVencimiento;
    private String observaciones;
    @NotEmpty(message = "Debe agregar al menos un producto")
    private List<CreateCompraDetalleDto> detalles;
    
    // Pagos de la compra (opcional)
    private List<CreateCompraPagoDto> pagos;
}
