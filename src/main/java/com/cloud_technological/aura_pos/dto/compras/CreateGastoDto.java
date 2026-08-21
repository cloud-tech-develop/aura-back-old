package com.cloud_technological.aura_pos.dto.compras;

import java.math.BigDecimal;
import java.time.LocalDate;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateGastoDto {

    @NotNull(message = "La sucursal es obligatoria")
    private Integer sucursalId;

    @NotBlank(message = "La categoría es obligatoria")
    private String categoria;

    private String descripcion;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    private BigDecimal monto;

    private LocalDate fecha;

    @NotNull(message = "Debe indicar si el gasto es deducible")
    private Boolean deducible;
    // Origen de fondos (V142): de dónde sale la plata.
    private String formaPago  = "CONTADO";   // CONTADO | CREDITO
    private String metodoPago = "EFECTIVO";
    /** Cuenta bancaria de la que sale el pago. */
    private Long cuentaBancariaId;
    /** Cuenta contable de la que sale el pago (crédito del asiento). */
    private Long cuentaPagoId;

    /**
     * La plata ya salió del cajón otro día y ese arqueo ya se cerró. Registra el
     * documento contablemente contra CAJA, sin tocar ningún arqueo.
     */
    private Boolean salidaCajaOtroDia = Boolean.FALSE;

    /**
     * Por qué un gasto de fecha anterior se carga a la caja de hoy. Obligatorio
     * solo cuando excede la ventana de gracia de la empresa y va por caja.
     */
    private String motivoRetroactivo;

    /** Vencimiento de la cuenta por pagar cuando el gasto es a crédito. */
    private java.time.LocalDateTime fechaVencimiento;

    // Campos tributarios (V54)
    private Long terceroId;
    /** Cuenta de gasto a la que se imputa (débito del asiento). */
    private Long cuentaContableId;
    private Long centroCostoId;
    private Long periodoContableId;
    private BigDecimal baseIva        = BigDecimal.ZERO;
    private BigDecimal tarifaIva      = BigDecimal.ZERO;
    private BigDecimal valorIva       = BigDecimal.ZERO;
    private BigDecimal baseRetefuente = BigDecimal.ZERO;
    private BigDecimal tarifaRetefuente = BigDecimal.ZERO;
    private BigDecimal valorRetefuente  = BigDecimal.ZERO;
    private BigDecimal baseReteica    = BigDecimal.ZERO;
    private BigDecimal tarifaReteica  = BigDecimal.ZERO;
    private BigDecimal valorReteica   = BigDecimal.ZERO;
    private String tipoDocSoporte;
    private String numeroDocSoporte;

    // Diferidos (E6): el pago va a 1705 y se amortiza mes a mes.
    private Boolean esDiferido;
    private Integer mesesDiferido;

    // Dimensiones proyecto/frente (E7).
    private Long proyectoId;
    private Long frenteId;
}
