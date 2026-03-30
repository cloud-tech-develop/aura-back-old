package com.cloud_technological.aura_pos.dto.cartera;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClienteCarteraDto {
    private Long       terceroId;
    private String     terceroNombre;
    private String     tipoDocumento;
    private String     numeroDocumento;
    private String     telefono;
    private String     email;

    // Crédito
    private Long       creditoId;           // null si no tiene cupo configurado
    private BigDecimal cupoCreditoActual;
    private BigDecimal saldoCartera;        // suma saldo_pendiente cuentas activas
    private BigDecimal saldoDisponible;     // cupo - saldo_cartera
    private String     estadoCredito;
    private String     nivelRiesgo;
    private Integer    scoreCrediticio;
    private Integer    plazoDias;

    // Mora
    private BigDecimal totalVencido;        // saldo_pendiente de docs vencidos
    private Integer    diasMoraMaximo;      // mayor cantidad de días vencida
    private long       documentosVencidos;  // cantidad de docs vencidos

    // Última gestión
    private String     ultimaGestion;
    private String     fechaUltimaGestion;

    // Para paginación
    private long totalRows;
}
