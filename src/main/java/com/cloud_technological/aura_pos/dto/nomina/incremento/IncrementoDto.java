package com.cloud_technological.aura_pos.dto.nomina.incremento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Incremento salarial con sus totales. */
@Getter
@Setter
public class IncrementoDto {
    private Long id;
    private Integer numero;
    /** INC-00001. */
    private String numeroCompleto;
    private String tipoLista;
    private List<Long> ids = new ArrayList<>();
    private String tipoIncremento;
    private BigDecimal valor;
    private String redondeo;
    private LocalDate fechaAplicacion;
    private String descripcion;
    private String estado;
    private Long documentoId;
    private String documentoNumero;
    private String documentoEstado;
    private int empleados;
    private BigDecimal totalAnterior;
    private BigDecimal totalNuevo;
    /** Vinculaciones con nóminas ya confirmadas desde la fecha de aplicación (piden retroactivo). */
    private int conRetroactivo;
    /** Al calcular: vinculaciones de la lista a las que el incremento no les cambia el salario. */
    private int sinCambio;
    private LocalDateTime aplicadoAt;
    private LocalDateTime reversadoAt;
    private LocalDateTime createdAt;
    private long totalRows;
}
