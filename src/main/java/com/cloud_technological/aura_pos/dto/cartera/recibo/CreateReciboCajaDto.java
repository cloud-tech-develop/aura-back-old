package com.cloud_technological.aura_pos.dto.cartera.recibo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateReciboCajaDto {
    private Long terceroId;
    /** Todo lo que entregó el cliente. */
    private BigDecimal valorRecibido;
    private String metodoPago;
    private String referencia;
    private Long cuentaContableId;
    private Long turnoCajaId;
    private Integer sucursalId;
    private Boolean cajaOtroDia;
    private LocalDateTime fechaPago;
    private String observaciones;
    /** A qué facturas va. Lo que no se aplique queda como anticipo. */
    private List<Aplicacion> aplicaciones;

    @Getter
    @Setter
    public static class Aplicacion {
        private Long cuentaCobrarId;
        /** Lo que se paga de la factura con el dinero recibido. */
        private BigDecimal monto;
        /** Lo que el cliente retuvo sobre esta factura (se suma a monto para bajar el saldo). */
        private java.util.List<com.cloud_technological.aura_pos.dto.cartera.RetencionRecaudoDto> retenciones;
    }
}
