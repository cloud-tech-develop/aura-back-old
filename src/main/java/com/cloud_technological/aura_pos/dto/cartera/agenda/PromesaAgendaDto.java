package com.cloud_technological.aura_pos.dto.cartera.agenda;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PromesaAgendaDto {
    private Long gestionId;
    private Long terceroId;
    private String terceroNombre;
    private String telefono;
    private LocalDate fechaPromesaPago;
    private BigDecimal montoPrometido;
    private BigDecimal montoPagado;
    private String estadoPromesa;
    private Long cuentaCobrarId;
    private String numeroCuenta;
    private String nota;
    private String usuarioNombre;
    private LocalDateTime createdAt;
    /** Positivo = días de atraso; negativo = días que faltan. */
    private Integer dias;
    private BigDecimal saldoCliente;
}
