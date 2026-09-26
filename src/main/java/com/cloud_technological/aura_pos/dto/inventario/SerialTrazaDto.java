package com.cloud_technological.aura_pos.dto.inventario;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SerialTrazaDto {
    private Long serialId;
    private String serial;
    private Long productoId;
    private String productoNombre;
    private String sucursalNombre;
    private String estado;
    private BigDecimal costo;
    private LocalDateTime fechaIngreso;
    private LocalDate garantiaClienteHasta;
    private List<SerialEventoDto> eventos = new ArrayList<>();
}
