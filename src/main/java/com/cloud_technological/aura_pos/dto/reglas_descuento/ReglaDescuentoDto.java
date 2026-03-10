package com.cloud_technological.aura_pos.dto.reglas_descuento;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReglaDescuentoDto {
    private Integer id;
    private Integer empresaId;
    private String nombre;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private List<Integer> diasSemana; // [1,2,3] Lunes, Martes, Miércoles
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private Long categoriaId;
    private String categoriaNombre;
    private Long productoId;
    private String productoNombre;
    private String tipoDescuento; // PORCENTAJE, MONTO
    private BigDecimal valor;
    private Boolean activo;
}
