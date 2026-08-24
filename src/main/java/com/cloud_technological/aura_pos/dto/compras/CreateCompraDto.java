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

    // Retenciones (opcionales)
    private java.math.BigDecimal retefuentePct;
    private java.math.BigDecimal reteivaPct;
    private java.math.BigDecimal reteicaPct;

    // Forma de pago
    private String formaPago; // CONTADO | CREDITO

    // Tipo de documento
    private String tipoDocumento; // FACTURA_COMPRA | NOTA_DEBITO | NOTA_CREDITO | RECIBO

    /**
     * Factura de compra que corrige la nota crédito. Obligatorio cuando
     * {@link #tipoDocumento} es {@code NOTA_CREDITO}: la NC anula mercancía de
     * una compra concreta, no del proveedor en abstracto.
     */
    private Long compraOrigenId;

    /**
     * Qué pasa con la plata de la nota crédito:
     * {@code CRUCE_CXP} | {@code DEVOLUCION_DINERO} | {@code SALDO_A_FAVOR}.
     * Solo aplica a NOTA_CREDITO.
     */
    private String destinoNotaCredito;

    // Fletes / transporte
    private java.math.BigDecimal fletes;

    // Destino contable (E2): centro de costo propagado a las líneas del
    // asiento y cuenta débito alternativa (gasto/activo); null → inventario.
    private Long centroCostoId;
    private Long cuentaContableId;


    /**
     * La plata ya salió del cajón otro día y ese arqueo ya se cerró. Registra el
     * documento contablemente contra CAJA, sin tocar ningún arqueo.
     */
    private Boolean salidaCajaOtroDia = Boolean.FALSE;

    /**
     * Por qué una compra de fecha anterior se carga a la caja de hoy. Obligatorio
     * solo cuando excede la ventana de gracia de la empresa y va por caja.
     */
    private String motivoRetroactivo;

    // Dimensiones proyecto/frente (E7), propagadas a las líneas del asiento.
    private Long proyectoId;
    private Long frenteId;
}
