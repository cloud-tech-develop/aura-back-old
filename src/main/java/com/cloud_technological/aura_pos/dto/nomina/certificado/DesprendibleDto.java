package com.cloud_technological.aura_pos.dto.nomina.certificado;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Desprendible de pago.
 *
 * <p>Sale de {@code nomina_detalle} con su {@code traza} (Fase 3). Sin ese
 * desglose no habría nada que imprimir: los campos agregados de {@code nomina}
 * dan totales, no explicaciones.
 */
@Getter
@Setter
public class DesprendibleDto {

    private Long nominaId;
    private String empresaRazonSocial;
    private String empresaNit;

    private String empleadoNombre;
    private String empleadoDocumento;
    private String cargo;

    private LocalDate periodoInicio;
    private LocalDate periodoFin;
    private Integer diasTrabajados;
    private BigDecimal salarioBase;

    private List<Linea> devengados = new ArrayList<>();
    private List<Linea> deducciones = new ArrayList<>();

    private BigDecimal totalDevengado = BigDecimal.ZERO;
    private BigDecimal totalDeducciones = BigDecimal.ZERO;
    private BigDecimal netoPagar = BigDecimal.ZERO;

    private String banco;
    private String tipoCuenta;
    private String numeroCuenta;

    /**
     * Una línea del desprendible.
     *
     * <p>{@code traza} es lo que permite responder un reclamo: no solo "salud:
     * 52.000" sino "base 1.300.000 × 4% = 52.000".
     */
    @Getter
    @Setter
    public static class Linea {
        private String concepto;
        private BigDecimal cantidad;
        private BigDecimal base;
        private BigDecimal porcentaje;
        private BigDecimal valor;
        /** Desglose paso a paso, como viene de nomina_detalle.traza. */
        private String traza;
    }
}
