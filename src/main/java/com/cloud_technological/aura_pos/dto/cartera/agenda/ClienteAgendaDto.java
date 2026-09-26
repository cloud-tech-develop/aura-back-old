package com.cloud_technological.aura_pos.dto.cartera.agenda;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClienteAgendaDto {
    private Long terceroId;
    private String terceroNombre;
    private String telefono;
    private Integer facturasVencidas;
    private BigDecimal saldoVencido;
    private Integer diasMoraMaximo;
    private LocalDateTime ultimaGestion;
    private String ultimoResultado;
}
