package com.cloud_technological.aura_pos.dto.ventas;

import java.time.LocalDateTime;
import java.util.List;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateVentaDto {
    private Long clienteId; // opcional, puede ser consumidor final
    /**
     * Pedido de vendedor que origina esta venta. Lo envía el despacho: la venta
     * se enlaza al pedido existente en vez de crear un pedido espejo nuevo.
     */
    private Long pedidoVendedorId;
    /**
     * Cotización de la que sale la venta (cadena documental D1). Cada línea que
     * venga de ella trae su {@code cotizacionDetalleId}; las líneas agregadas en
     * el POS van sin él.
     */
    private Long cotizacionId;
    private Long turnoCajaId;  // null cuando el usuario es VENDEDOR (sin caja)
    private Integer sucursalId; // requerido cuando turnoCajaId es null
    /** Bodega que despacha. Sin ella, la principal de la sucursal. */
    private Long bodegaId;
    private String tipoDocumento = "POS";
    private String observaciones;
    private LocalDateTime fechaVencimiento; // Para cuentas por cobrar
    @NotEmpty(message = "Debe agregar al menos un producto")
    private List<CreateVentaDetalleDto> detalles;
    @NotEmpty(message = "Debe agregar al menos un método de pago")
    private List<CreateVentaPagoDto> pagos;
    private java.math.BigDecimal descuentoGeneral;
    /**
     * Autorización del supervisor cuando el descuento o la rebaja de precio pasan
     * el límite del usuario (POST /api/autorizaciones). De un solo uso.
     */
    private Long autorizacionId;

    /**
     * La venta la arma Facturación (FacturaVentaService), no el POS: sin turno
     * de caja aunque haya efectivo (entra a la caja general), sin límites de
     * descuento del cajero y sin pedido de vendedor espejo. Nunca llega del
     * JSON: solo lo pone el servidor.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private boolean desdeFacturacion;
}

