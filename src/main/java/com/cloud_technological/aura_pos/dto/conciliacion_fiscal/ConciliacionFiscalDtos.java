package com.cloud_technological.aura_pos.dto.conciliacion_fiscal;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** DTOs de la conciliación fiscal 2516 / 2517 (docs/PLAN_EXCEL_IMPORTADORES_MODULOS.md §5). */
public final class ConciliacionFiscalDtos {

    private ConciliacionFiscalDtos() {
    }

    @Getter
    @Setter
    public static class Crear {
        private Integer anio;
        /** 2516 | 2517 */
        private String formato;
        private BigDecimal tarifaRenta;
    }

    @Getter
    @Setter
    public static class Cabecera {
        private Long id;
        private Integer anio;
        private String formato;
        private BigDecimal tarifaRenta;
        private String origen;
        private LocalDateTime cargadoAt;
        private String estado;
        private Integer cuentas;
        private Integer ajustes;
    }

    @Getter
    @Setter
    public static class Cuenta {
        private Long id;
        private String codigo;
        private String nombre;
        private BigDecimal saldo;
        private String renglon;
        private String renglonNombre;
        private Boolean renglonManual;
    }

    @Getter
    @Setter
    public static class Ajuste {
        private Long id;
        private String renglon;
        private String renglonNombre;
        /** MENOR | MAYOR */
        private String tipo;
        /** PERMANENTE | TEMPORARIA */
        private String diferencia;
        private BigDecimal valor;
        private String concepto;
        private String cuentaCodigo;
        private String sugerencia;
    }

    /** Un renglón del formato con lo contable, los ajustes y lo fiscal. */
    @Getter
    @Setter
    public static class Renglon {
        private String codigo;
        private String seccion;
        private String seccionTitulo;
        private String nombre;
        private BigDecimal contable = BigDecimal.ZERO;
        private BigDecimal menorValor = BigDecimal.ZERO;
        private BigDecimal mayorValor = BigDecimal.ZERO;
        private BigDecimal fiscal = BigDecimal.ZERO;
        private int cuentas;
    }

    /** Ajuste que el sistema propone a partir del nombre y el código de la cuenta. */
    @Getter
    @Setter
    public static class Sugerencia {
        private String clave;
        private String renglon;
        private String renglonNombre;
        private String cuentaCodigo;
        private String cuentaNombre;
        private String tipo;
        private String diferencia;
        private BigDecimal valor;
        private String concepto;
        private String fundamento;
        /** true: el valor es una propuesta que el contador debe revisar. */
        private boolean revisar;
    }

    @Getter
    @Setter
    public static class Validacion {
        /** ERROR | ADVERTENCIA | OK */
        private String nivel;
        private String mensaje;

        public Validacion() {
        }

        public Validacion(String nivel, String mensaje) {
            this.nivel = nivel;
            this.mensaje = mensaje;
        }
    }

    @Getter
    @Setter
    public static class ImpuestoDiferido {
        private String renglon;
        private String nombre;
        private String seccion;
        private BigDecimal contable;
        private BigDecimal fiscal;
        private BigDecimal diferencia;
        /** DEDUCIBLE | IMPONIBLE */
        private String tipo;
        private BigDecimal activo = BigDecimal.ZERO;
        private BigDecimal pasivo = BigDecimal.ZERO;
    }

    @Getter
    @Setter
    public static class Resumen {
        private Cabecera cabecera;
        private List<Renglon> renglones = new ArrayList<>();
        private BigDecimal activosContable = BigDecimal.ZERO;
        private BigDecimal activosFiscal = BigDecimal.ZERO;
        private BigDecimal pasivosContable = BigDecimal.ZERO;
        private BigDecimal pasivosFiscal = BigDecimal.ZERO;
        private BigDecimal patrimonioContable = BigDecimal.ZERO;
        /** Patrimonio líquido fiscal = activos fiscales − pasivos fiscales. */
        private BigDecimal patrimonioFiscal = BigDecimal.ZERO;
        private BigDecimal ingresosContable = BigDecimal.ZERO;
        private BigDecimal ingresosFiscal = BigDecimal.ZERO;
        private BigDecimal costosContable = BigDecimal.ZERO;
        private BigDecimal costosFiscal = BigDecimal.ZERO;
        private BigDecimal gastosContable = BigDecimal.ZERO;
        private BigDecimal gastosFiscal = BigDecimal.ZERO;
        private BigDecimal resultadoContable = BigDecimal.ZERO;
        private BigDecimal rentaLiquida = BigDecimal.ZERO;
        private BigDecimal impuestoRenta = BigDecimal.ZERO;
        private BigDecimal impuestoDiferidoActivo = BigDecimal.ZERO;
        private BigDecimal impuestoDiferidoPasivo = BigDecimal.ZERO;
        private List<ImpuestoDiferido> impuestoDiferido = new ArrayList<>();
        private List<Cuenta> sinClasificar = new ArrayList<>();
        private List<Sugerencia> sugerencias = new ArrayList<>();
        private List<Validacion> validaciones = new ArrayList<>();
        private List<Ajuste> ajustes = new ArrayList<>();
    }

    /** Fila del balance que se sube en Excel. */
    @Getter
    @Setter
    public static class FilaBalance {
        private Integer fila;
        private String cuenta;
        private String nombre;
        /** Saldo final con signo; o débito y crédito por separado. */
        private BigDecimal saldo;
        private BigDecimal debito;
        private BigDecimal credito;
    }

    @Getter
    @Setter
    public static class CargarBalance {
        private List<FilaBalance> filas = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Reclasificar {
        private String renglon;
    }
}
