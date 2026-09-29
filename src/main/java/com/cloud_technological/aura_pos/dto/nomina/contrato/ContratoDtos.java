package com.cloud_technological.aura_pos.dto.nomina.contrato;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** DTOs de contratos laborales (Fase 2). Agrupados: son del mismo subdominio. */
public final class ContratoDtos {

    private ContratoDtos() {}

    // ── Salida ──────────────────────────────────────────────────────────────

    @Getter @Setter
    public static class ContratoDto {
        private Long id;
        private Long empleadoId;
        private String empleadoNombre;
        private String empleadoDocumento;

        private String tipoContrato;
        private String cargo;
        private LocalDate fechaInicio;
        private LocalDate fechaFin;
        private BigDecimal salarioBase;
        private Boolean esSalarioIntegral;
        /** Fase del aprendiz SENA: LECTIVA | PRACTICA. Null si no es aprendiz. */
        private String fase;
        private String periodicidad;
        private Boolean esPrincipal;

        private String procedimientoRetefuente;
        private BigDecimal porcentajeFijoRetencion;

        private String estado;
        private String causaRetiro;
        private String observacion;

        // Seguridad social (Fase 5.5)
        private String tipoCotizante;
        private String subtipoCotizante;
        private Integer nivelRiesgoArl;
        private BigDecimal tarifaArl;

        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter @Setter
    public static class ContratoTableDto {
        private Long id;
        private Long empleadoId;
        private String empleadoNombre;
        private String tipoContrato;
        private String cargo;
        private LocalDate fechaInicio;
        private LocalDate fechaFin;
        private BigDecimal salarioBase;
        private Boolean esPrincipal;
        private String estado;
        /** Días que faltan para el vencimiento. Null si es indefinido. */
        private Long diasParaVencer;
    }

    @Getter @Setter
    public static class SalarioHistorialDto {
        private Long id;
        private BigDecimal salario;
        private LocalDate fechaDesde;
        private LocalDate fechaHasta;
        private String motivo;
        private LocalDateTime createdAt;
        /** Variación respecto al anterior. Para mostrar el % de aumento. */
        private BigDecimal variacionPorcentaje;
    }

    @Getter @Setter
    public static class RenovacionDto {
        private Long id;
        private LocalDate fechaInicial;
        private LocalDate fechaFinal;
        private String descripcion;
        private LocalDateTime createdAt;
    }

    @Getter @Setter
    public static class ContratoDetalleDto {
        private ContratoDto contrato;
        private List<SalarioHistorialDto> historialSalarios = new ArrayList<>();
        private List<RenovacionDto> renovaciones = new ArrayList<>();
        private List<CentroCostoDto> centrosCosto = new ArrayList<>();
    }

    @Getter @Setter
    public static class CentroCostoDto {
        private Long id;
        private Long centroCostoId;
        private String centroCostoNombre;
        private BigDecimal porcentaje;
    }

    // ── Entrada ─────────────────────────────────────────────────────────────

    @Getter @Setter
    public static class CreateContratoDto {
        private Long empleadoId;
        private String tipoContrato;   // INDEFINIDO | FIJO | OBRA_LABOR | PRESTACION_SERVICIOS | APRENDIZAJE
        private String cargo;
        private LocalDate fechaInicio;
        /** Obligatorio si tipoContrato = FIJO; prohibido si INDEFINIDO. */
        private LocalDate fechaFin;
        private BigDecimal salarioBase;
        private Boolean esSalarioIntegral = Boolean.FALSE;
        /** Fase del aprendiz SENA: LECTIVA | PRACTICA. Solo para tipoContrato = APRENDIZAJE. */
        private String fase;
        private String periodicidad;
        private Boolean esPrincipal = Boolean.TRUE;
        private String procedimientoRetefuente = "1";
        private String observacion;
        /** Nivel de riesgo ARL (1..5). Lo exige PILA; vive en el contrato. */
        private Integer nivelRiesgoArl;
    }

    /**
     * Cambio de salario.
     *
     * <p><b>No es un update.</b> Preserva el histórico: cierra la vigencia
     * anterior y abre una nueva. Por eso {@code fechaDesde} y {@code motivo} no
     * son opcionales — sin ellos no se puede liquidar un retroactivo ni calcular
     * la bandera {@code vsp} de PILA.
     */
    @Getter @Setter
    public static class CambiarSalarioDto {
        private BigDecimal nuevoSalario;
        /** Desde cuándo rige. Puede ser retroactiva. */
        private LocalDate fechaDesde;
        private String motivo;
    }

    @Getter @Setter
    public static class TerminarContratoDto {
        private LocalDate fechaFin;
        /**
         * Determina si hay indemnización y cómo se calcula (Fase 8).
         *
         * <p>JUSTA_CAUSA | SIN_JUSTA_CAUSA | RENUNCIA | MUTUO_ACUERDO |
         * VENCIMIENTO_TERMINO | OBRA_TERMINADA | MUERTE
         */
        private String causaRetiro;
    }

    @Getter @Setter
    public static class CreateRenovacionDto {
        private LocalDate fechaInicial;
        private LocalDate fechaFinal;
        private String descripcion;
    }
}
