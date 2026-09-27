package com.cloud_technological.aura_pos.dto.importacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cuerpos del importador desde Excel. El archivo se lee en el navegador y
 * llega aquí como filas; cada una trae su número de fila del Excel para que
 * los errores se puedan ubicar en el archivo original.
 */
public final class ImportacionDtos {

    private ImportacionDtos() {
    }

    // ── Plan de cuentas ─────────────────────────────────────────────────────

    @Data
    public static class FilaCuenta {
        private Integer fila;
        private String codigo;
        private String nombre;
        /** Opcional: DEBITO | CREDITO. Si no viene, sale de la clase (1xxx débito…). */
        private String naturaleza;
    }

    @Data
    public static class PlanCuentas {
        private List<FilaCuenta> filas = new ArrayList<>();
    }

    // ── Terceros ────────────────────────────────────────────────────────────

    @Data
    public static class FilaTercero {
        private Integer fila;
        private String tipoDocumento;
        private String numeroDocumento;
        private String dv;
        private String razonSocial;
        private String nombres;
        private String apellidos;
        private String direccion;
        /** Código DANE (05001) o nombre del municipio. */
        private String municipio;
        private String telefono;
        private String email;
        /** NATURAL | JURIDICA; si no viene, NIT = jurídica, lo demás natural. */
        private String tipoPersona;
        /** RESPONSABLE_IVA | NO_RESPONSABLE_IVA */
        private String regimen;
        private Boolean esCliente;
        private Boolean esProveedor;
    }

    @Data
    public static class Terceros {
        private List<FilaTercero> filas = new ArrayList<>();
    }

    // ── Saldos iniciales ────────────────────────────────────────────────────

    @Data
    public static class FilaSaldo {
        private Integer fila;
        private String codigoCuenta;
        /** Documento del tercero (opcional; obligatorio en cartera y proveedores). */
        private String documentoTercero;
        private BigDecimal debito;
        private BigDecimal credito;
    }

    @Data
    public static class Saldos {
        private LocalDate fechaApertura;
        /** Cuenta que absorbe el descuadre; por defecto resultados de ejercicios anteriores. */
        private String codigoCuentaAjuste;
        private List<FilaSaldo> filas = new ArrayList<>();
    }

    // ── Cartera y proveedores abiertos ──────────────────────────────────────

    @Data
    public static class FilaDocumentoAbierto {
        private Integer fila;
        private String documentoTercero;
        /** Número de la factura en el sistema anterior. */
        private String numeroFactura;
        private LocalDate fechaEmision;
        private LocalDate fechaVencimiento;
        /** Lo que falta por cobrar o pagar a la fecha de apertura. */
        private BigDecimal saldo;
    }

    @Data
    public static class DocumentosAbiertos {
        /** COBRAR (clientes) | PAGAR (proveedores) */
        private String tipo;
        private List<FilaDocumentoAbierto> filas = new ArrayList<>();
    }

    // ── Resultado ───────────────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResultadoFila {
        private Integer fila;
        /** NUEVO | EXISTE | ERROR | ADVERTENCIA */
        private String estado;
        private String detalle;
        private String mensaje;
    }

    @Data
    public static class Resultado {
        private boolean confirmado;
        private int nuevos;
        private int existentes;
        private int errores;
        private List<ResultadoFila> filas = new ArrayList<>();
        /** Mensajes generales: cuadre, cruces, lo que se hizo. */
        private List<String> avisos = new ArrayList<>();
        private BigDecimal totalDebito;
        private BigDecimal totalCredito;

        public boolean puedeConfirmarse() {
            return errores == 0 && nuevos > 0;
        }
    }
}
