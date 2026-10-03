package com.cloud_technological.aura_pos.dto.activos_fijos;

import java.math.BigDecimal;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Informe de activos agrupable por categoría, centro de costo o responsable. */
@Getter
@Setter
public class ReporteActivosDto {
    private List<Fila> filas;
    private List<Grupo> grupos;
    private BigDecimal totalCosto;
    private BigDecimal totalDepreciacion;
    private BigDecimal totalEnLibros;

    @Getter
    @Setter
    public static class Fila {
        private Long id;
        private String codigo;
        private String descripcion;
        private String categoria;
        private String placa;
        private String estado;
        private String centroCosto;
        private String responsable;
        private java.time.LocalDate fechaAdquisicion;
        private Integer vidaUtilMeses;
        private BigDecimal costo;
        private BigDecimal depreciacionAcumulada;
        private BigDecimal valorEnLibros;
        private BigDecimal depreciacionMes;
    }

    @Getter
    @Setter
    public static class Grupo {
        private String nombre;
        private Integer cantidad;
        private BigDecimal costo;
        private BigDecimal depreciacion;
        private BigDecimal enLibros;
    }
}
