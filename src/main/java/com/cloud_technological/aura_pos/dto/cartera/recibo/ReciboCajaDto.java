package com.cloud_technological.aura_pos.dto.cartera.recibo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReciboCajaDto {
    private Long id;
    private String numero;
    private Long terceroId;
    private String terceroNombre;
    private String terceroDocumento;
    private LocalDateTime fechaPago;
    private BigDecimal valorRecibido;
    private BigDecimal valorAplicado;
    private BigDecimal valorAnticipo;
    private String metodoPago;
    private String referencia;
    private Boolean cajaOtroDia;
    private Long anticipoId;
    private String observaciones;
    private String estado;
    private String motivoAnulacion;
    private LocalDateTime anuladoAt;
    private String usuarioNombre;
    private LocalDateTime createdAt;
    private List<AplicacionDto> aplicaciones;

    @Getter
    @Setter
    public static class AplicacionDto {
        private Long cuentaCobrarId;
        private String numeroCuenta;
        private LocalDateTime fechaVencimiento;
        private BigDecimal saldoAnterior;
        private BigDecimal monto;
        /** Lo que el cliente retuvo sobre esta factura (V181). */
        private BigDecimal retenciones;
        private BigDecimal saldoDespues;
    }
}
