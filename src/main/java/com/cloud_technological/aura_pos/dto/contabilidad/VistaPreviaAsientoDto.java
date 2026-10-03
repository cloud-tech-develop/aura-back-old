package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Asiento que se registraría con los datos del formulario, sin guardar nada
 * (Fase 4). Sale del mismo método que construye el asiento real.
 */
@Getter
@Setter
public class VistaPreviaAsientoDto {

    private List<Linea> lineas;
    private BigDecimal totalDebito;
    private BigDecimal totalCredito;
    private boolean cuadra;

    @Getter
    @Setter
    public static class Linea {
        private Long cuentaId;
        private String cuentaCodigo;
        private String cuentaNombre;
        private String descripcion;
        private BigDecimal debito;
        private BigDecimal credito;
        private Long terceroId;
    }

    /** Compra que se está escribiendo: líneas netas de descuento, en pesos. */
    @Getter
    @Setter
    public static class CompraRequest {
        private String tipoDocumento;
        private Long proveedorId;
        /** Cuenta destino explícita de toda la compra (opcional). */
        private Long cuentaContableId;
        private BigDecimal fletes;
        private BigDecimal retefuentePct;
        private BigDecimal reteivaPct;
        private BigDecimal reteicaPct;
        /** CONTADO | CREDITO */
        private String formaPago;
        private List<LineaCompra> lineas;
        private List<PagoCompra> pagos;
    }

    @Getter
    @Setter
    public static class LineaCompra {
        private Long productoId;
        /** Valor de la línea neto de su descuento, sin IVA. */
        private BigDecimal neto;
        private BigDecimal iva;
    }

    @Getter
    @Setter
    public static class PagoCompra {
        private String metodoPago;
        private Long cuentaBancariaId;
        private Long cuentaContableId;
        private BigDecimal monto;
    }
}
