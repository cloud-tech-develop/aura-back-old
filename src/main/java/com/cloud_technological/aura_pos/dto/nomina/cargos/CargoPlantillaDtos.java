package com.cloud_technological.aura_pos.dto.nomina.cargos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * DTOs de la plantilla de nómina del cargo (V231): conceptos, novedades
 * permitidas, topes de horas extra y recargos, y adjuntos.
 */
public final class CargoPlantillaDtos {

    private CargoPlantillaDtos() {}

    /** Concepto que el cargo liquida siempre, con los datos del concepto vigente. */
    @Getter @Setter
    public static class CargoConceptoDto {
        private Long id;
        private Integer conceptoNumero;
        private String conceptoNombre;
        private String clase;
        private String tipoNombre;
        private String entrada;
        private String redondeoPrecision;
        private String redondeoModo;
        private BigDecimal cantidad;
        private BigDecimal valor;
        private BigDecimal porcentaje;
        private String observacion;
    }

    /** Novedad permitida en el cargo, con sus topes. */
    @Getter @Setter
    public static class CargoNovedadDto {
        private Long id;
        private Integer conceptoNumero;
        private String conceptoNombre;
        private String clase;
        private String tipoNombre;
        private String entrada;
        private BigDecimal topeCantidad;
        private BigDecimal topeValor;
        private Boolean requiereAutorizacion;
    }

    /** Agregar varios conceptos de una vez (desde el buscador avanzado). */
    @Getter @Setter
    public static class AgregarConceptosDto {
        private List<Integer> numeros;
    }

    /** Valores propios de un concepto del cargo (vacíos = los del concepto). */
    @Getter @Setter
    public static class GuardarCargoConceptoDto {
        private BigDecimal cantidad;
        private BigDecimal valor;
        private BigDecimal porcentaje;
        private String observacion;
    }

    /** Topes y autorización de una novedad del cargo. */
    @Getter @Setter
    public static class GuardarCargoNovedadDto {
        private BigDecimal topeCantidad;
        private BigDecimal topeValor;
        private Boolean requiereAutorizacion;
    }

    /** Máximo de horas de un tipo (horas extra o recargo). Topes vacíos = sin tope. */
    @Getter @Setter
    public static class CargoTopeHorasDto {
        private Integer tipoConceptoId;
        private String tipoNombre;
        /** true = hora extra (tope legal); false = recargo. */
        private Boolean esHoraExtra;
        private BigDecimal topeDia;
        private BigDecimal topeSemana;
        private BigDecimal topeMes;
    }

    /** Archivo adjunto del cargo. */
    @Getter @Setter
    public static class CargoAdjuntoDto {
        private Long id;
        private String nombreArchivo;
        private String archivoUrl;
        private String contentType;
        private Long tamanoBytes;
        private LocalDateTime createdAt;
    }

    /** Resultado de copiar la plantilla de otro cargo: cuánto se agregó de cada cosa. */
    @Getter @Setter
    public static class CopiaPlantillaDto {
        private int conceptos;
        private int novedades;
        private int topes;
    }
}
