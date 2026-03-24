package com.cloud_technological.aura_pos.dto.nomina.nomina;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NominaDto {
    private Long id;
    private Long periodoId;
    private LocalDate periodoFechaInicio;
    private LocalDate periodoFechaFin;
    private Long empleadoId;
    private String empleadoNombre;
    private String empleadoDocumento;
    private String cargo;
    private String tipoContrato;
    // Devengados
    private BigDecimal salarioBase;
    private Integer diasTrabajados;
    private BigDecimal salarioProporcional;
    private BigDecimal auxilioTransporte;
    private BigDecimal totalNovedadesDev;
    private BigDecimal totalDevengado;
    // Deducciones empleado
    private BigDecimal deduccionSalud;
    private BigDecimal deduccionPension;
    private BigDecimal deduccionOtros;
    private BigDecimal totalDeducciones;
    // Neto
    private BigDecimal netoPagar;
    // Aportes empleador
    private BigDecimal aporteSalud;
    private BigDecimal aportePension;
    private BigDecimal aporteArl;
    private BigDecimal aporteCaja;
    private BigDecimal aporteIcbf;
    private BigDecimal aporteSena;
    // Provisiones
    private BigDecimal provisionPrima;
    private BigDecimal provisionCesantias;
    private BigDecimal provisionIntCesantias;
    private BigDecimal provisionVacaciones;
    // Estado y novedades
    private String estado;
    private List<NominaNovedadDto> novedades;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
