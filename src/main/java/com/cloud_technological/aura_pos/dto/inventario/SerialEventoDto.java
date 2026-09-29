package com.cloud_technological.aura_pos.dto.inventario;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** Un paso en la vida de un serial: compra, venta, traslado, devolución… */
@Getter
@Setter
public class SerialEventoDto {
    private Long serialId;
    private LocalDateTime fecha;
    private String tipo;
    private String documento;
    /** Proveedor, cliente, responsable o "Sede A → Sede B". */
    private String tercero;
    private String sucursal;
    private String estadoDocumento;
}
