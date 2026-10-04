package com.cloud_technological.aura_pos.dto.factura_venta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** DTOs de Facturación (factura de venta fuera del POS, docs/PLAN_FACTURACION.md). */
public final class FacturaVentaDtos {

    private FacturaVentaDtos() {
    }

    /** Línea que llega del formulario. El impuesto y los totales los calcula el servidor. */
    @Data
    public static class Linea {
        private Long productoId;
        private Long productoPresentacionId;
        private String descripcion;
        private BigDecimal cantidad;
        /** Sin IVA. */
        private BigDecimal precioUnitario;
        private BigDecimal descuentoValor;
        /** Null = el IVA del producto. */
        private BigDecimal impuestoPorcentaje;
        /** Línea de la cotización de origen. */
        private Long cotizacionDetalleId;
    }

    /** Crear o editar un borrador. */
    @Data
    public static class Guardar {
        private Integer sucursalId;
        private Long bodegaId;
        private Long clienteId;
        private Integer vendedorId;
        private Long condicionPagoId;
        /** CREDITO | CONTADO */
        private String formaPago;
        /** Contado: EFECTIVO | TRANSFERENCIA | CONSIGNACION | TARJETA | CHEQUE */
        private String metodoPago;
        private Long cuentaBancariaId;
        private Long centroCostoId;
        /** Null = hoy + días de la condición. */
        private LocalDate fechaVencimiento;
        private String ordenCompra;
        private String notas;
        /** Contrato AIU: las líneas son costo directo; A, I y U se agregan solas. */
        private Boolean aiu;
        private BigDecimal aiuAdministracionPct;
        private BigDecimal aiuImprevistosPct;
        private BigDecimal aiuUtilidadPct;
        private BigDecimal aiuIvaPct;
        private List<Linea> lineas = new ArrayList<>();
    }

    @Data
    public static class LineaDto {
        private Long id;
        private Long productoId;
        private String productoNombre;
        private String productoSku;
        private String unidadAbreviatura;
        private Boolean manejaInventario;
        private Long productoPresentacionId;
        private String presentacionNombre;
        private String descripcion;
        private BigDecimal cantidad;
        private BigDecimal precioUnitario;
        private BigDecimal descuentoValor;
        private BigDecimal impuestoPorcentaje;
        private BigDecimal impuestoValor;
        private BigDecimal subtotalLinea;
        /** ADMINISTRACION | IMPREVISTOS | UTILIDAD si la generó el AIU. */
        private String aiuTipo;
        private Long cotizacionDetalleId;
    }

    /** Ficha de la factura: borrador o emitida (con los datos de su venta). */
    @Data
    public static class Detalle {
        private Long id;
        private String estado;
        private Integer sucursalId;
        private String sucursalNombre;
        private Long bodegaId;
        private String bodegaNombre;
        private Long clienteId;
        private String clienteNombre;
        private String clienteDocumento;
        private Integer vendedorId;
        private String vendedorNombre;
        private Long condicionPagoId;
        private String condicionPagoNombre;
        private String formaPago;
        private String metodoPago;
        private Long cuentaBancariaId;
        private String cuentaBancariaNombre;
        private String cuentaBancariaBanco;
        private String cuentaBancariaTipo;
        private String cuentaBancariaNumero;
        private LocalDate fechaVencimiento;
        private String ordenCompra;
        private String notas;
        private Long centroCostoId;
        private String centroCostoNombre;
        private Long cotizacionId;
        private String cotizacionNumero;
        private Long pedidoVendedorId;
        private String pedidoNumero;
        private Boolean aiu;
        private BigDecimal aiuAdministracionPct;
        private BigDecimal aiuImprevistosPct;
        private BigDecimal aiuUtilidadPct;
        private BigDecimal aiuIvaPct;
        private BigDecimal subtotal;
        private BigDecimal descuentoTotal;
        private BigDecimal impuestosTotal;
        private BigDecimal total;
        private LocalDateTime createdAt;
        private LocalDateTime emitidaAt;
        // De la venta, ya emitida
        private Long ventaId;
        private String numero;
        private String estadoVenta;
        private BigDecimal saldoPendiente;
        private String cufe;
        private String estadoDian;
        private String factusNumero;
        /** Contenido del QR de la FE (lo devuelve Factus). */
        private String qrData;
        private String factusUrl;
        /** Fecha y hora real de la venta emitida. */
        private LocalDateTime fechaEmision;
        private List<LineaDto> lineas = new ArrayList<>();
    }

    /** Fila del listado. */
    @Data
    public static class Fila {
        private Long id;
        private String estado;
        private String numero;
        private LocalDateTime fecha;
        private String clienteNombre;
        private String clienteDocumento;
        private String sucursalNombre;
        private String formaPago;
        private String condicionPagoNombre;
        private LocalDate fechaVencimiento;
        private BigDecimal total;
        private BigDecimal saldoPendiente;
        private String estadoDian;
        private Long ventaId;
        private Long totalRows;
    }

    /** Anticipo del cliente que se cruza contra la factura al emitirla a crédito. */
    @Data
    public static class AnticipoAplicar {
        private Long anticipoId;
        private BigDecimal monto;
    }

    @Data
    public static class Emitir {
        private List<AnticipoAplicar> anticipos = new ArrayList<>();
    }

    @Data
    public static class CondicionPago {
        private Long id;
        private String nombre;
        private Integer dias;
        private Boolean activa;
    }
}
