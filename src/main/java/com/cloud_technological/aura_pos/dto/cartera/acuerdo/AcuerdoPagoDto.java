package com.cloud_technological.aura_pos.dto.cartera.acuerdo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Acuerdo de pago; en los listados cuotas y cuentas vienen en null. */
@Getter
@Setter
public class AcuerdoPagoDto {
    private Long id;
    private String numero;
    private Long terceroId;
    private String terceroNombre;
    private String terceroDocumento;
    private String terceroTelefono;
    private BigDecimal valorTotal;
    private BigDecimal valorPagado;
    private BigDecimal saldo;
    private Integer numeroCuotas;
    private Integer cuotasPagadas;
    private String frecuencia;
    private Integer diasGracia;
    /** VIGENTE, INCUMPLIDO, CUMPLIDO, ANULADO. */
    private String estado;
    private String observaciones;
    private String motivoAnulacion;
    private String usuarioNombre;
    private LocalDateTime createdAt;
    private LocalDateTime anuladoAt;
    private LocalDateTime cumplidoAt;
    private LocalDateTime incumplidoAt;
    /** Primera cuota sin pagar completa. */
    private LocalDate proximaCuotaFecha;
    /** Lo que falta de esa cuota. */
    private BigDecimal proximaCuotaSaldo;
    /** Días desde la cuota vencida más antigua sin pagar; 0 si va al día. */
    private Integer diasMora;
    private List<AcuerdoCuotaDto> cuotas;
    private List<AcuerdoCuentaDto> cuentas;
}
